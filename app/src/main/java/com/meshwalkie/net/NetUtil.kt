package com.meshwalkie.net

import java.net.Inet4Address
import java.net.Inet6Address
import java.net.NetworkInterface

object NetUtil {

    /**
     * Best address for clients to reach this host. Prefers a private LAN IPv4
     * (192.168/10/172.16) so phones on the SAME WiFi connect directly, else a
     * global IPv6 for cross-internet. Null if neither.
     */
    fun bestHostAddress(): String? = privateIpv4() ?: globalIpv6()

    /** Site-local LAN IPv4 (reachable by devices on the same network). */
    fun privateIpv4(): String? {
        try {
            for (ni in NetworkInterface.getNetworkInterfaces()) {
                if (!ni.isUp || ni.isLoopback) continue
                for (addr in ni.inetAddresses) {
                    if (addr is Inet4Address && addr.isSiteLocalAddress) {
                        return addr.hostAddress
                    }
                }
            }
        } catch (_: Exception) {
        }
        return null
    }
    /**
     * The device's globally-routable IPv6 address (not link-local fe80::, not
     * unique-local fc00::/fd00::, not loopback). This is the address other
     * phones connect to over the internet. Null if none (no public IPv6).
     */
    fun globalIpv6(): String? {
        try {
            for (ni in NetworkInterface.getNetworkInterfaces()) {
                if (!ni.isUp || ni.isLoopback) continue
                for (addr in ni.inetAddresses) {
                    if (addr is Inet6Address &&
                        !addr.isLinkLocalAddress &&
                        !addr.isSiteLocalAddress &&
                        !addr.isLoopbackAddress &&
                        !addr.isAnyLocalAddress &&
                        !addr.isMulticastAddress
                    ) {
                        var ip = addr.hostAddress ?: continue
                        val pct = ip.indexOf('%')
                        if (pct >= 0) ip = ip.substring(0, pct)   // strip %scope
                        return ip
                    }
                }
            }
        } catch (_: Exception) {
        }
        return null
    }

    const val DEFAULT_PORT = 51820
}
