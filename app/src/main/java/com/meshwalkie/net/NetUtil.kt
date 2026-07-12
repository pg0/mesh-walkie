package com.meshwalkie.net

import java.net.Inet4Address
import java.net.Inet6Address
import java.net.NetworkInterface

object NetUtil {

    /**
     * Best address for clients to reach this host:
     * 1) the WiFi (wlan) IPv4 - reachable by every device on the same WiFi,
     *    including IPv4-only ones (the common case),
     * 2) else a global IPv6 (host on mobile, cross-internet),
     * 3) else any site-local IPv4. Null if none.
     */
    fun bestHostAddress(): String? = wlanIpv4() ?: globalIpv6() ?: privateIpv4()

    /** IPv4 on the WiFi interface (wlan*). */
    fun wlanIpv4(): String? {
        try {
            for (ni in NetworkInterface.getNetworkInterfaces()) {
                if (!ni.isUp || ni.isLoopback) continue
                if (!ni.name.startsWith("wlan")) continue
                for (addr in ni.inetAddresses) {
                    if (addr is Inet4Address && !addr.isLoopbackAddress) return addr.hostAddress
                }
            }
        } catch (_: Exception) {
        }
        return null
    }

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

    /**
     * Parse a user-typed "host[:port]" address for the online server field.
     * Accepts bracketed IPv6 ("[addr]" or "[addr]:port"), bare IPv6 (2+ colons,
     * no brackets - the whole string is the host, port defaults), "host:port"
     * (single colon), or a bare host/IPv4 (port defaults). Blank input or an
     * unclosed bracket returns null. An invalid port falls back to [defaultPort].
     */
    fun parseHostPort(input: String, defaultPort: Int = DEFAULT_PORT): Pair<String, Int>? {
        val s = input.trim()
        if (s.isEmpty()) return null
        if (s.startsWith("[")) {
            val end = s.indexOf(']')
            if (end < 0) return null
            val host = s.substring(1, end)
            val rest = s.substring(end + 1)
            val port = if (rest.startsWith(":")) rest.substring(1).toIntOrNull() ?: defaultPort else defaultPort
            return host to port
        }
        val colonCount = s.count { it == ':' }
        if (colonCount >= 2) return s to defaultPort   // bare IPv6, no brackets
        if (colonCount == 1) {
            val idx = s.indexOf(':')
            val host = s.substring(0, idx)
            val port = s.substring(idx + 1).toIntOrNull() ?: defaultPort
            return host to port
        }
        return s to defaultPort
    }
}
