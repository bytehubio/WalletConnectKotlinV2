package com.walletconnect.android.internal.utils

import android.util.Log
import com.walletconnect.android.internal.common.model.Expiry
import java.util.*
import java.util.concurrent.TimeUnit

object CoreValidator {

    @JvmSynthetic
    fun isAccountIdCAIP10Compliant(accountId: String): Boolean {
        val elements = accountId.split(":")
        if (elements.isEmpty()) return false

        val (namespace: String, reference: String, accountAddress: String) = when (elements.size) {
            3 -> elements
            4 -> listOf(elements[0], elements[1], "${elements[2]}:${elements[3]}")
            else -> return false
        }

        Log.e("DebugSome", "accId: $accountId")
        Log.e("DebugSome", "namespace: $namespace")
        Log.e("DebugSome", "reference: $reference")
        Log.e("DebugSome", "accountAddress: $accountAddress")

        return NAMESPACE_REGEX.toRegex().matches(namespace) &&
                REFERENCE_REGEX.toRegex().matches(reference) &&
                ACCOUNT_ADDRESS_REGEX.toRegex().matches(accountAddress)
    }

    @JvmSynthetic
    fun isChainIdCAIP2Compliant(chainId: String): Boolean {
        val elements: List<String> = chainId.split(":")
        if (elements.isEmpty() || elements.size != 2) return false
        val (namespace: String, reference: String) = elements
        return NAMESPACE_REGEX.toRegex().matches(namespace) && REFERENCE_REGEX.toRegex().matches(reference)
    }

    @JvmSynthetic
    fun isExpiryNotWithinBounds(userExpiry: Expiry?, now: Long = TimeUnit.SECONDS.convert(Date().time, TimeUnit.MILLISECONDS)): Boolean =
        userExpiry?.seconds?.run {
            val remainder = this - now

            remainder < 0 || !(FIVE_MINUTES_IN_SECONDS..WEEK_IN_SECONDS).contains(remainder)
        } ?: false

    const val NAMESPACE_REGEX: String = "^[-a-z0-9]{3,9}$"
    private const val REFERENCE_REGEX: String = "^[-a-zA-Z0-9]{1,32}$"
    private const val ACCOUNT_ADDRESS_REGEX: String = "^([a-zA-Z0-9]{1,64})|([a-zA-Z0-9]{1,128})$"
}