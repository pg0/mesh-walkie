package com.meshwalkie.net

import java.net.Inet6Address
import java.net.NetworkInterface

object NetUtil {
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
