package app.filebeam

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Handler
import android.os.Looper

data class Peer(val name: String, val host: String, val port: Int)

/** Finds other FileBeam devices on the local network (mDNS / NSD). */
class Discovery(context: Context) {
    companion object {
        const val TYPE = "_filebeam._tcp."
    }

    private val nsd = context.applicationContext.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val main = Handler(Looper.getMainLooper())
    private var regListener: NsdManager.RegistrationListener? = null
    private var discListener: NsdManager.DiscoveryListener? = null
    private val queue = ArrayDeque<NsdServiceInfo>()
    private var resolving = false

    fun register(name: String, port: Int) {
        val info = NsdServiceInfo()
        info.serviceName = name
        info.serviceType = TYPE
        info.port = port
        val l = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(i: NsdServiceInfo) {}
            override fun onRegistrationFailed(i: NsdServiceInfo, e: Int) {}
            override fun onServiceUnregistered(i: NsdServiceInfo) {}
            override fun onUnregistrationFailed(i: NsdServiceInfo, e: Int) {}
        }
        regListener = l
        try {
            nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, l)
        } catch (e: Exception) {
            regListener = null
        }
    }

    fun discover(onFound: (Peer) -> Unit, onLost: (String) -> Unit) {
        val l = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(t: String) {}
            override fun onDiscoveryStopped(t: String) {}
            override fun onStartDiscoveryFailed(t: String, e: Int) {}
            override fun onStopDiscoveryFailed(t: String, e: Int) {}
            override fun onServiceFound(s: NsdServiceInfo) {
                synchronized(this@Discovery) { queue.addLast(s) }
                resolveNext(onFound)
            }
            override fun onServiceLost(s: NsdServiceInfo) {
                main.post { onLost(s.serviceName) }
            }
        }
        discListener = l
        try {
            nsd.discoverServices(TYPE, NsdManager.PROTOCOL_DNS_SD, l)
        } catch (e: Exception) {
            discListener = null
        }
    }

    // Android can only resolve one service at a time, so resolve through a queue.
    private fun resolveNext(onFound: (Peer) -> Unit) {
        var next: NsdServiceInfo? = null
        synchronized(this) {
            if (!resolving) {
                next = queue.removeFirstOrNull()
                if (next != null) resolving = true
            }
        }
        val target = next ?: return
        @Suppress("DEPRECATION")
        nsd.resolveService(target, object : NsdManager.ResolveListener {
            override fun onResolveFailed(i: NsdServiceInfo, e: Int) {
                finishResolve(onFound)
            }
            override fun onServiceResolved(i: NsdServiceInfo) {
                val host = i.host?.hostAddress
                // IPv6 link-local addresses are unreliable; manual IPv4 entry covers that case.
                if (host != null && !host.contains(':')) {
                    main.post { onFound(Peer(i.serviceName, host, i.port)) }
                }
                finishResolve(onFound)
            }
        })
    }

    private fun finishResolve(onFound: (Peer) -> Unit) {
        synchronized(this) { resolving = false }
        resolveNext(onFound)
    }

    fun stop() {
        try { regListener?.let { nsd.unregisterService(it) } } catch (e: Exception) {}
        try { discListener?.let { nsd.stopServiceDiscovery(it) } } catch (e: Exception) {}
        regListener = null
        discListener = null
    }
}
