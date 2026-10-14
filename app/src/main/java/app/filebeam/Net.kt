package app.filebeam

import java.net.Inet4Address
import java.net.NetworkInterface

/** First private IPv4 address of this device, or null when not on a LAN. */
fun localIp(): String? = try {
    NetworkInterface.getNetworkInterfaces().toList()
        .flatMap { it.inetAddresses.toList() }
        .firstOrNull { it is Inet4Address && !it.isLoopbackAddress && it.isSiteLocalAddress }
        ?.hostAddress
} catch (e: Exception) {
    null
}
