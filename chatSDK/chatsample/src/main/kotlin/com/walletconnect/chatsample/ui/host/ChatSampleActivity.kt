package com.walletconnect.chatsample.ui.host

import android.os.Bundle
import android.util.Log
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.walletconnect.chat.client.Chat
import com.walletconnect.chatsample.databinding.ActivityChatSampleBinding
import com.walletconnect.chatsample.ui.ChatSampleEvents
import com.walletconnect.sample_common.tag
import com.walletconnect.sample_common.viewBinding
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class ChatSampleActivity : AppCompatActivity() {
    private val binding by viewBinding(ActivityChatSampleBinding::inflate)

    private val viewModel: ChatSampleViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        binding.btnResolve.setOnClickListener {
            viewModel.resolve(object : Chat.Listeners.Resolve {
                override fun onSuccess(publicKey: String) {
                    onResolve(publicKey)
                }

                override fun onError(error: Chat.Model.Error) {
                    this@ChatSampleActivity.onError(error)
                }
            })
        }

        binding.btnRegister.setOnClickListener {
            viewModel.register(object : Chat.Listeners.Register {
                override fun onSuccess(publicKey: String) {
                    onRegister(publicKey)
                }

                override fun onError(error: Chat.Model.Error) {
                    this@ChatSampleActivity.onError(error)
                }
            })
        }
    }

    private fun onResolve(publicKey: String) = Snackbar.make(binding.root, "Resolved: $publicKey", Snackbar.LENGTH_LONG).show()

    private fun onRegister(publicKey: String) = Snackbar.make(binding.root, "Registered: $publicKey", Snackbar.LENGTH_LONG).show()

    private fun onError(error: Chat.Model.Error) {
        Snackbar.make(binding.root, "Error: ${error.throwable.localizedMessage}", Snackbar.LENGTH_LONG).show()
        Log.e(tag(this), error.throwable.stackTraceToString())
    }
}