@file:JvmSynthetic

package com.walletconnect.chat.jwt.use_case

import com.walletconnect.android.internal.common.jwt.EncodeDidJwtPayloadUseCase
import com.walletconnect.android.internal.common.model.AccountId
import com.walletconnect.chat.jwt.ChatDidJwtClaims
import com.walletconnect.foundation.util.jwt.encodeDidPkh

internal class EncodeInviteKeyDidJwtPayloadUseCase(
    private val invitePublicKey: String,
    private val accountId: AccountId,
) : EncodeDidJwtPayloadUseCase<ChatDidJwtClaims.InviteKey> {
    override fun invoke(params: EncodeDidJwtPayloadUseCase.Params): ChatDidJwtClaims.InviteKey = with(params) {
        ChatDidJwtClaims.InviteKey(
            issuer = issuer,
            issuedAt = issuedAt,
            expiration = expiration,
            audience = keyserverUrl,
            subject = invitePublicKey,
            pkh = encodeDidPkh(accountId.value)
        )
    }
}