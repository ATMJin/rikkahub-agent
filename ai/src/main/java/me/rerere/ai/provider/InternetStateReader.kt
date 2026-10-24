package me.rerere.ai.provider

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/** A captive portal or a failed validation is unknown, not permission to run a local LLM. */
fun readInternetState(context: Context): InternetState = try {
    val manager = context.getSystemService(ConnectivityManager::class.java)
    if (manager == null) {
        InternetState.UNVERIFIED
    } else {
        val network = manager.activeNetwork
        if (network == null) {
            InternetState.OFFLINE
        } else {
            val capabilities = manager.getNetworkCapabilities(network)
            classifyInternetEvidence(
                hasActiveNetwork = true,
                hasInternetCapability = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),
                validated = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
            )
        }
    }
} catch (_: SecurityException) {
    InternetState.UNVERIFIED
}
