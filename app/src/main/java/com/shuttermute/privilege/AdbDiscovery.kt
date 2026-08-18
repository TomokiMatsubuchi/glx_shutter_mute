package com.shuttermute.privilege

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * Discovers Wireless debugging pairing / connect ports advertised over mDNS.
 */
class AdbDiscovery(context: Context) {

    private val appContext = context.applicationContext
    private val nsdManager = appContext.getSystemService(NsdManager::class.java)
    private val executor = Executors.newSingleThreadExecutor()
    private val listeners = mutableMapOf<String, NsdManager.DiscoveryListener>()
    private val endpoints = ConcurrentHashMap<String, AdbEndpoint>()

    private val _pairing = MutableStateFlow<List<AdbEndpoint>>(emptyList())
    val pairing: StateFlow<List<AdbEndpoint>> = _pairing.asStateFlow()

    private val _connect = MutableStateFlow<List<AdbEndpoint>>(emptyList())
    val connect: StateFlow<List<AdbEndpoint>> = _connect.asStateFlow()

    @Synchronized
    fun start() {
        if (listeners.isNotEmpty()) return
        discover(TYPE_PAIRING, pairing = true)
        discover(TYPE_CONNECT, pairing = false)
    }

    @Synchronized
    fun stop() {
        listeners.values.forEach { listener ->
            runCatching { nsdManager.stopServiceDiscovery(listener) }
        }
        listeners.clear()
        endpoints.clear()
        _pairing.value = emptyList()
        _connect.value = emptyList()
    }

    private fun discover(type: String, pairing: Boolean) {
        val listener = object : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) = Unit
            override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) = Unit
            override fun onDiscoveryStarted(serviceType: String?) = Unit
            override fun onDiscoveryStopped(serviceType: String?) = Unit

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                resolve(serviceInfo, pairing)
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                endpoints.remove(key(serviceInfo, pairing))
                publish()
            }
        }
        listeners[type] = listener
        runCatching {
            nsdManager.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, listener)
        }
    }

    private fun resolve(info: NsdServiceInfo, pairing: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            runCatching {
                nsdManager.registerServiceInfoCallback(
                    info,
                    executor,
                    object : NsdManager.ServiceInfoCallback {
                        override fun onServiceUpdated(serviceInfo: NsdServiceInfo) {
                            remember(serviceInfo, pairing)
                        }

                        override fun onServiceLost() {
                            endpoints.remove(key(info, pairing))
                            publish()
                        }

                        override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) = Unit
                        override fun onServiceInfoCallbackUnregistered() = Unit
                    },
                )
            }
            return
        }

        @Suppress("DEPRECATION")
        nsdManager.resolveService(
            info,
            object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) = Unit
                override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                    remember(serviceInfo, pairing)
                }
            },
        )
    }

    private fun remember(info: NsdServiceInfo, pairing: Boolean) {
        val host = resolvedHost(info) ?: return
        if (info.port <= 0) return
        endpoints[key(info, pairing)] = AdbEndpoint(host = host, port = info.port, pairing = pairing)
        publish()
    }

    private fun publish() {
        val values = endpoints.values.toList()
        _pairing.value = values.filter { it.pairing }
        _connect.value = values.filter { !it.pairing }
    }

    @Suppress("DEPRECATION")
    private fun resolvedHost(info: NsdServiceInfo): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            info.hostAddresses.firstOrNull()?.hostAddress?.let { return it }
        }
        return info.host?.hostAddress
    }

    private fun key(info: NsdServiceInfo, pairing: Boolean): String =
        "${if (pairing) "p" else "c"}:${info.serviceName}:${info.port}"

    companion object {
        private const val TYPE_PAIRING = "_adb-tls-pairing._tcp"
        private const val TYPE_CONNECT = "_adb-tls-connect._tcp"
    }
}
