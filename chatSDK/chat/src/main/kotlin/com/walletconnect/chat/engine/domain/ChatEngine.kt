@file:JvmSynthetic

package com.walletconnect.chat.engine.domain

import com.walletconnect.chat.copiedFromSign.core.exceptions.client.WalletConnectException
import com.walletconnect.chat.copiedFromSign.core.model.type.enums.EnvelopeType
import com.walletconnect.chat.copiedFromSign.core.model.vo.PublicKey
import com.walletconnect.chat.copiedFromSign.core.model.vo.TopicVO
import com.walletconnect.chat.copiedFromSign.core.model.vo.jsonRpc.JsonRpcResponseVO
import com.walletconnect.chat.copiedFromSign.core.model.vo.sync.ParticipantsVO
import com.walletconnect.chat.copiedFromSign.core.model.vo.sync.WCRequestVO
import com.walletconnect.chat.copiedFromSign.core.model.vo.sync.WCResponseVO
import com.walletconnect.chat.copiedFromSign.core.scope.scope
import com.walletconnect.chat.copiedFromSign.crypto.KeyManagementRepository
import com.walletconnect.chat.copiedFromSign.json_rpc.domain.RelayerInteractor
import com.walletconnect.chat.copiedFromSign.util.Empty
import com.walletconnect.chat.copiedFromSign.util.Logger
import com.walletconnect.chat.copiedFromSign.util.generateId
import com.walletconnect.chat.core.model.vo.AccountIdVO
import com.walletconnect.chat.core.model.vo.AccountIdWithPublicKeyVO
import com.walletconnect.chat.core.model.vo.clientsync.ChatRpcVO
import com.walletconnect.chat.core.model.vo.clientsync.params.ChatParamsVO
import com.walletconnect.chat.discovery.keyserver.domain.use_case.RegisterAccountUseCase
import com.walletconnect.chat.discovery.keyserver.domain.use_case.ResolveAccountUseCase
import com.walletconnect.chat.engine.model.EngineDO
import com.walletconnect.chat.storage.ChatStorageRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope

internal class ChatEngine(
    private val registerAccountUseCase: RegisterAccountUseCase,
    private val resolveAccountUseCase: ResolveAccountUseCase,
    private val keyManagementRepository: KeyManagementRepository,
    private val relayer: RelayerInteractor,
    private val chatStorage: ChatStorageRepository,
) {
    private val _events: MutableSharedFlow<EngineDO.Events> = MutableSharedFlow()
    val events: SharedFlow<EngineDO.Events> = _events.asSharedFlow()
    private val inviteRequestMap: MutableMap<Long, WCRequestVO> = mutableMapOf()

    init {
        collectJsonRpcRequests()
        collectPeerResponses()
        relayer.initializationErrorsFlow.onEach { error -> Logger.error(error) }.launchIn(scope)
        relayer.isConnectionAvailable
            .onEach { isAvailable ->
//                _events.emit(EngineDO.ConnectionState(isAvailable)) todo add connection state callbacks
            }
            .filter { isAvailable: Boolean -> isAvailable }
            .onEach {
                coroutineScope {
                    launch(Dispatchers.IO) { trySubscribeToInviteTopic() }
                }
            }
            .launchIn(scope)

    }

    internal fun resolveAccount(accountId: AccountIdVO, onSuccess: (String) -> Unit, onFailure: (Throwable) -> Unit) {
        scope.launch {
            supervisorScope {
                resolveAccountUseCase(accountId).fold(
                    onSuccess = { accountIdWithPublicKeyVO -> onSuccess(accountIdWithPublicKeyVO.publicKey.keyAsHex) },
                    onFailure = { error -> onFailure(error) }
                )
            }
        }
    }

    internal fun registerAccount(
        accountId: AccountIdVO,
        onSuccess: (String) -> Unit,
        onFailure: (Throwable) -> Unit,
        private: Boolean,
    ) {
        fun _onSuccess(publicKey: PublicKey) {
            Logger.log("_onSuccess($publicKey)")
            val topic = TopicVO(keyManagementRepository.getHash(publicKey.keyAsHex))
            keyManagementRepository.setInviteSelfPublicKey(topic, publicKey)
            trySubscribeToInviteTopic()
            onSuccess(publicKey.keyAsHex)
        }

        val (publicKey, _) = keyManagementRepository.getOrGenerateInviteSelfKeyPair()

        if (!private) {
            scope.launch {
                supervisorScope {
                    Logger.log("registerAccount($publicKey)")

                    registerAccountUseCase(AccountIdWithPublicKeyVO(accountId, publicKey)).fold(
                        onSuccess = { _onSuccess(publicKey) },
                        onFailure = { error -> onFailure(error) }
                    )
                }
            }
        } else {
            _onSuccess(publicKey)
        }
    }
//todo zrobić logi zeby pokazac ze juz byłem zaproszony raz

    private fun trySubscribeToInviteTopic() {
        Logger.log("trySubscribeToInviteTopic()")

        try {
            val publicKey = keyManagementRepository.getInviteSelfPublicKey()
            val topic = TopicVO(keyManagementRepository.getHash(publicKey.keyAsHex))
            relayer.subscribe(topic)
            Logger.log("Listening for invite on: $topic, pubKey X:$publicKey")
        } catch (error: Exception) {
            Logger.log(error) // It will log if run before registerAccount()
            //TODO: Create exception if there is no key created
        }
    }

    internal fun invite(invite: EngineDO.Invite, onFailure: (Throwable) -> Unit) = try {
        val senderPublicKey = keyManagementRepository.generateKeyPair() // KeyPair Y
//
        val contact = chatStorage.getContact(invite.accountId)
        val publicKeyString = contact.public_key // TODO: What about camelCase?
        val receiverPublicKey = PublicKey(publicKeyString) // KeyPair X
//
        val symmetricKey = keyManagementRepository.generateSymmetricKeyFromKeyAgreement(senderPublicKey, receiverPublicKey) // SymKey I
        val inviteTopic = TopicVO(keyManagementRepository.getHash(publicKeyString)) // Topic I
        keyManagementRepository.setKeyAgreement(inviteTopic, senderPublicKey, receiverPublicKey)

        val participantsVO = ParticipantsVO(senderPublicKey = senderPublicKey, receiverPublicKey = receiverPublicKey)
        val envelopeType = EnvelopeType.ONE

        val inviteParams = ChatParamsVO.InviteParams(invite.message, invite.accountId.value, senderPublicKey.keyAsHex, invite.signature)
        val payload = ChatRpcVO.ChatInvite(id = generateId(), params = inviteParams)
        Logger.log("Invite sent on: $inviteTopic, pubKey X:$publicKeyString")

        val acceptTopic = TopicVO(keyManagementRepository.getHash(symmetricKey.keyAsHex))
        keyManagementRepository.setSymmetricKey(acceptTopic, symmetricKey)
        relayer.subscribe(acceptTopic)
        Logger.log("invite acceptTopic: $acceptTopic")
        relayer.publishJsonRpcRequests(inviteTopic, payload, envelopeType,
            {
                Logger.log("Chat invite sent successfully")
            },
            { throwable ->
                Logger.log("Chat invite error: $throwable")
//                relayer.unsubscribe(acceptTopic)  todo

                onFailure(throwable)
            }, participantsVO)

    } catch (error: Exception) {
        onFailure(error)
    }

    private fun onInviteResponse(wcResponse: WCResponseVO) {
        Logger.log("Chat invite response received successfully")
        when (val response = wcResponse.response) {
            is JsonRpcResponseVO.JsonRpcError -> {
                Logger.log("Chat invite was rejected")
            }
            is JsonRpcResponseVO.JsonRpcResult -> {
                Logger.log("Chat invite was accepted")
                val acceptParams = response.result as ChatParamsVO.AcceptanceParams
                val pubKeyZ = PublicKey(acceptParams.publicKey) // PubKey Z
                val (selfPubKey, _) = keyManagementRepository.getKeyAgreement(wcResponse.topic)
                val symmetricKey = keyManagementRepository.generateSymmetricKeyFromKeyAgreement(selfPubKey, pubKeyZ) // SymKey T
                val threadTopic = TopicVO(keyManagementRepository.getHash(symmetricKey.keyAsHex))
                keyManagementRepository.setSymmetricKey(threadTopic, symmetricKey)
                relayer.subscribe(threadTopic)
                scope.launch {
                    _events.emit(EngineDO.Events.OnJoined(threadTopic.value))
                }
            }
        }
    }

    private fun onInviteRequest(wcRequest: WCRequestVO, params: ChatParamsVO.InviteParams) {
        Logger.log("onInviteRequest: $params")

        inviteRequestMap[wcRequest.id] = wcRequest // todo when to remove it?
        scope.launch {
            _events.emit(EngineDO.Events.OnInvite(wcRequest.id,
                EngineDO.Invite(AccountIdVO(params.account), params.message, params.signature)))
            Logger.log("onInviteRequest: eventEmitted")
        }

        //TODO: Add adding invites to storage. For MVP we will use only emitted event.
    }


    internal fun accept(inviteId: Long, onFailure: (Throwable) -> Unit) = try {
        val request = inviteRequestMap[inviteId] ?: throw WalletConnectException.GenericException("No request for inviteId")
        val senderPublicKey = PublicKey((request.params as ChatParamsVO.InviteParams).publicKey) // PubKey Y
        inviteRequestMap.remove(inviteId)
//
        val invitePublicKey = keyManagementRepository.getInviteSelfPublicKey() // PubKey X
        val symmetricKey = keyManagementRepository.generateSymmetricKeyFromKeyAgreement(invitePublicKey, senderPublicKey) // SymKey I
        val acceptTopic = TopicVO(keyManagementRepository.getHash(symmetricKey.keyAsHex)) // Topic T
        keyManagementRepository.setSymmetricKey(acceptTopic, symmetricKey)
//
        val publicKey = keyManagementRepository.generateKeyPair() // KeyPair Z
//
        val envelopeType = EnvelopeType.ZERO

        val acceptanceParams = ChatParamsVO.AcceptanceParams(publicKey.keyAsHex)
        relayer.respondWithParams(request.copy(topic = acceptTopic), acceptanceParams, envelopeType)

        val threadSymmetricKey = keyManagementRepository.generateSymmetricKeyFromKeyAgreement(publicKey, senderPublicKey) // SymKey T
        val threadTopic = TopicVO(keyManagementRepository.getHash(threadSymmetricKey.keyAsHex)) // Topic T
        keyManagementRepository.setSymmetricKey(threadTopic, threadSymmetricKey)
        relayer.subscribe(threadTopic)
    } catch (error: Exception) {
        onFailure(error)
    }


    internal fun reject(inviteId: String, onFailure: (Throwable) -> Unit) {
//        //todo: correct define params
//        val envelopeType = EnvelopeType.ZERO
//        val request = WCRequestVO()
//        val error = PeerError.Error("reason", 1)
//
//        relayer.respondWithError(request, error, envelopeType) { throwable -> onFailure(throwable) }
    }

    internal fun message(topic: String, sendMessage: EngineDO.SendMessage, onFailure: (Throwable) -> Unit) {
        //todo: correct define params
        val authorAccount = "eip:1:0xCD2a3d9F938E13CD947Ec05AbC7FE734Df8DD826"
        val envelopeType = EnvelopeType.ZERO
        val timestamp = System.currentTimeMillis()

        val messageParams = ChatParamsVO.MessageParams(sendMessage.message, authorAccount, timestamp, sendMessage.media)
        val payload = ChatRpcVO.ChatMessage(id = generateId(), params = messageParams)
        relayer.publishJsonRpcRequests(TopicVO(topic), payload, envelopeType,
            { Logger.log("Chat message sent successfully") },
            { throwable ->
                Logger.log("Chat message error: $throwable")
                onFailure(throwable)
            })
    }

    private fun onMessage(wcRequest: WCRequestVO, params: ChatParamsVO.MessageParams) {
        Logger.log("onMessage: $params")

        scope.launch {
            _events.emit(EngineDO.Events.OnMessage(wcRequest.topic.value,
                EngineDO.Message(params.message, AccountIdVO(params.authorAccount), params.timestamp, params.media)))
            Logger.log("onMessage: eventEmitted")
        }

        //TODO: Add adding messages to storage. For MVP we will use only emitted event.
    }

    internal fun leave(topic: String, onFailure: (Throwable) -> Unit) {
        //todo: correct define params
        val envelopeType = EnvelopeType.ZERO

        val leaveParams = ChatParamsVO.LeaveParams()
        val payload = ChatRpcVO.ChatLeave(id = generateId(), params = leaveParams)
        relayer.publishJsonRpcRequests(TopicVO(topic), payload, envelopeType,
            { Logger.log("Chat message sent successfully") },
            { throwable ->
                Logger.log("Chat message error: $throwable")
                onFailure(throwable)
            })
    }

    internal fun ping(topic: String, onFailure: (Throwable) -> Unit) {
        //TODO
    }

    private fun collectJsonRpcRequests() {
        scope.launch {
            relayer.clientSyncJsonRpc.collect { request ->
                Logger.log("collectJsonRpcRequests: $request")

                when (val params = request.params) {
                    is ChatParamsVO.InviteParams -> onInviteRequest(request, params)
                    is ChatParamsVO.MessageParams -> onMessage(request, params)
                }
            }
        }
    }

    private fun collectPeerResponses() {
        scope.launch {
            relayer.peerResponse.collect { response ->
                Logger.log("collectPeerResponses: ${response.params}")

                when (val params = response.params) {
                    is ChatParamsVO.InviteParams -> onInviteResponse(response)
                }
            }
        }
    }

    internal fun addContact(accountIdWithPublicKeyVO: AccountIdWithPublicKeyVO, onFailure: (Throwable) -> Unit) = try {
        if (chatStorage.doesContactNotExists(accountIdWithPublicKeyVO.accountId)) {
            chatStorage.createContact(
                EngineDO.Contact(
                    accountIdWithPublicKeyVO,
                    mockUsernames[accountIdWithPublicKeyVO.accountId.value] ?: String.Empty)
            )
            Logger.log("New contact added")
        } else {
            chatStorage.updateContact(
                accountIdWithPublicKeyVO.accountId,
                accountIdWithPublicKeyVO.publicKey,
                mockUsernames[accountIdWithPublicKeyVO.accountId.value] ?: String.Empty
            )
            Logger.log("Contact updated")
        }
    } catch (error: Exception) {
        onFailure(error)
    }


    private val mockUsernames = mapOf("eip:1:0xCD2a3d9F938E13CD947Ec05AbC7FE734Df8DD826" to "example.eth")
}