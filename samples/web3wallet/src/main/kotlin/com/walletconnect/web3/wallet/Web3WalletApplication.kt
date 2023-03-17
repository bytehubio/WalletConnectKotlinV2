package com.walletconnect.web3.wallet

import android.app.Application
import android.util.Log
import com.walletconnect.android.Core
import com.walletconnect.android.CoreClient
import com.walletconnect.android.relay.ConnectionType
import com.walletconnect.push.common.Push
import com.walletconnect.push.wallet.client.PushWalletClient
import com.walletconnect.sample_common.tag
import com.walletconnect.web3.wallet.client.Wallet
import com.walletconnect.web3.wallet.client.Web3Wallet
import com.walletconnect.web3.wallet.sample.BuildConfig

class Web3WalletApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        val projectId = "f156aeb97cc8145f8c5e06f10bd808ed"
        val relayUrl = "relay.walletconnect.com"
        val serverUrl = "wss://$relayUrl?projectId=${projectId}"
        val appMetaData = Core.Model.AppMetaData(
            name = "Kotlin.Web3Wallet",
            description = "Kotlin Web3Wallet Implementation",
            url = "kotlin.web3wallet.walletconnect.com",
            icons = listOf("https://raw.githubusercontent.com/WalletConnect/walletconnect-assets/master/Icon/Gradient/Icon.png"),
            redirect = "kotlin-web3wallet:/request"
        )

        CoreClient.initialize(
            relayServerUrl = serverUrl,
            connectionType = ConnectionType.AUTOMATIC,
            application = this,
            metaData = appMetaData
        ) { error ->

        }

        Web3Wallet.initialize(Wallet.Params.Init(core = CoreClient)) { error ->
        }

        PushWalletClient.initialize(Push.Wallet.Params.Init(CoreClient)) { error ->
            Log.e(tag(this), error.throwable.stackTraceToString())
        }
    }
}