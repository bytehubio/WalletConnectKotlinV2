@file:JvmSynthetic

package com.walletconnect.chat.di

import com.walletconnect.android.internal.common.di.AndroidCommonDITags
import com.walletconnect.chat.engine.domain.ChatEngine
import com.walletconnect.chat.json_rpc.GetPendingJsonRpcHistoryEntryByIdUseCase
import com.walletconnect.chat.jwt.DidJwtRepository
import org.koin.core.qualifier.named
import org.koin.dsl.module

@JvmSynthetic
internal fun engineModule() = module {
    single { DidJwtRepository() }
    single { GetPendingJsonRpcHistoryEntryByIdUseCase(get(), get()) }

    single {
        ChatEngine(
            get(named(ChatDITags.KEYSERVER_URL)), get(), get(), get(), get(), get(), get(), get(), get(),
            get(), get(), get(), get(), get(), get(), get(), get(), get(), get(named(AndroidCommonDITags.LOGGER))
        )
    }
}