package com.example.readerarbicbook.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET
import android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import java.net.Inet4Address
import java.net.NetworkInterface
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart

object NetworkUtils {

    fun isInternetAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        
        // Use backward-compatible network checking for API level 21+
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            val activeNetwork = connectivityManager.activeNetwork ?: return false
            val networkCapabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
            return networkCapabilities.hasCapability(NET_CAPABILITY_INTERNET) &&
                networkCapabilities.hasCapability(NET_CAPABILITY_VALIDATED)
        } else {
            // Fallback for older API levels
            val networkInfo = connectivityManager.activeNetworkInfo
            return networkInfo != null && networkInfo.isConnected
        }
    }

    fun observeNetworkStatus(context: Context, intervalMs: Long = 4000L): Flow<Boolean> {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        val callbackEvents = callbackFlow {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                val callback = object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        trySend(isInternetAvailable(context))
                    }

                    override fun onLost(network: Network) {
                        trySend(false)
                    }

                    override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                        trySend(
                            networkCapabilities.hasCapability(NET_CAPABILITY_INTERNET) &&
                                networkCapabilities.hasCapability(NET_CAPABILITY_VALIDATED)
                        )
                    }
                }
                connectivityManager.registerDefaultNetworkCallback(callback)
                trySend(isInternetAvailable(context))
                awaitClose { connectivityManager.unregisterNetworkCallback(callback) }
            } else {
                // Fallback for older API levels - use periodic checks only
                trySend(isInternetAvailable(context))
                awaitClose { }
            }
        }

        val periodicChecks = flow {
            while (true) {
                emit(isInternetAvailable(context))
                delay(intervalMs)
            }
        }

        return merge(callbackEvents, periodicChecks)
            .onStart { emit(isInternetAvailable(context)) }
            .distinctUntilChanged()
    }
    
    fun getDeviceIpAddress(context: Context): String {
        try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                val activeNetwork = connectivityManager.activeNetwork
                val networkCapabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
                
                if (networkCapabilities != null) {
                    if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                        val ipAddress = wifiManager.connectionInfo.ipAddress
                        return String.format(
                            "%d.%d.%d.%d",
                            (ipAddress and 0xff),
                            (ipAddress shr 8 and 0xff),
                            (ipAddress shr 16 and 0xff),
                            (ipAddress shr 24 and 0xff)
                        )
                    } else if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                        val networkInterfaces = NetworkInterface.getNetworkInterfaces()
                        while (networkInterfaces.hasMoreElements()) {
                            val networkInterface = networkInterfaces.nextElement()
                            val inetAddresses = networkInterface.inetAddresses
                            while (inetAddresses.hasMoreElements()) {
                                val inetAddress = inetAddresses.nextElement()
                                if (!inetAddress.isLoopbackAddress && inetAddress is Inet4Address) {
                                    return inetAddress.hostAddress ?: "Unavailable"
                                }
                            }
                        }
                    }
                }
            } else {
                // Fallback for older API levels
                val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                val ipAddress = wifiManager.connectionInfo.ipAddress
                return String.format(
                    "%d.%d.%d.%d",
                    (ipAddress and 0xff),
                    (ipAddress shr 8 and 0xff),
                    (ipAddress shr 16 and 0xff),
                    (ipAddress shr 24 and 0xff)
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return "Error: ${e.message}"
        }
        return "Unavailable"
    }
}
