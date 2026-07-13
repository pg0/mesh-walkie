package com.meshwalkie.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Cases for [NetUtil.parseServerAddr]: WS/wss URLs vs the raw host:port fallback. */
class NetUtilParserTest {

    @Test
    fun wssDefaultsToTls443RootPath() {
        val addr = NetUtil.parseServerAddr("wss://relay.example.com")
        assertEquals(
            NetUtil.ServerAddr(NetUtil.ServerKind.WS, "relay.example.com", 443, "/", tls = true),
            addr
        )
    }

    @Test
    fun wssWithPortAndPath() {
        val addr = NetUtil.parseServerAddr("wss://relay.example.com:8443/mesh")
        assertEquals(
            NetUtil.ServerAddr(NetUtil.ServerKind.WS, "relay.example.com", 8443, "/mesh", tls = true),
            addr
        )
    }

    @Test
    fun wsPlainDefaultsToPort80() {
        val addr = NetUtil.parseServerAddr("ws://192.168.1.10:8080")
        assertEquals(
            NetUtil.ServerAddr(NetUtil.ServerKind.WS, "192.168.1.10", 8080, "/", tls = false),
            addr
        )
    }

    @Test
    fun rawHostPortFallsBackToRawKind() {
        val addr = NetUtil.parseServerAddr("example.com:1234")
        assertEquals(NetUtil.ServerAddr(NetUtil.ServerKind.RAW, "example.com", 1234), addr)
    }

    @Test
    fun bracketedIpv6FallsBackToRaw() {
        val addr = NetUtil.parseServerAddr("[2a02:8106::1]:1234")
        assertEquals(NetUtil.ServerAddr(NetUtil.ServerKind.RAW, "2a02:8106::1", 1234), addr)
    }

    @Test
    fun bracketedIpv6WithWsScheme() {
        val addr = NetUtil.parseServerAddr("ws://[2a02:8106::1]:8080/mesh")
        assertEquals(
            NetUtil.ServerAddr(NetUtil.ServerKind.WS, "2a02:8106::1", 8080, "/mesh", tls = false),
            addr
        )
    }

    @Test
    fun blankReturnsNull() {
        assertNull(NetUtil.parseServerAddr(""))
        assertNull(NetUtil.parseServerAddr("   "))
    }
}
