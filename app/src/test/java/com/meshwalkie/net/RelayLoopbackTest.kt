package com.meshwalkie.net

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.io.DataOutputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * Loopback tests against real sockets: HostServer + ServerLink talking the
 * actual wire format (length-framed, length==0 keepalive) on localhost. The
 * handler wired to HostServer here just re-broadcasts, standing in for the
 * engine's flood/dedup relay.
 */
class RelayLoopbackTest {

    private fun freePort(): Int = ServerSocket(0).use { it.localPort }

    @Test
    fun broadcastReachesOtherClientAndKeepaliveIsIgnored() {
        val port = freePort()
        val hs = HostServer(port)
        hs.onReceive { hs.broadcast(it) }
        hs.start()
        Thread.sleep(300)   // let the accept loop bind

        val received = LinkedBlockingQueue<ByteArray>()
        val a = ServerLink("127.0.0.1", port)
        val b = ServerLink("127.0.0.1", port)
        b.onReceive { received.add(it) }
        a.connect()
        b.connect()
        Thread.sleep(500)   // let both clients connect

        // A raw keepalive frame (4 zero bytes) must not be relayed as a packet.
        Socket("127.0.0.1", port).use { raw ->
            DataOutputStream(raw.getOutputStream()).apply { writeInt(0); flush() }
            Thread.sleep(300)
        }
        assertNull(received.poll(300, TimeUnit.MILLISECONDS))

        val payload = byteArrayOf(1, 2, 3, 4, 5)
        a.broadcast(payload)
        val got = received.poll(3, TimeUnit.SECONDS)
        assertNotNull(got)
        assertArrayEquals(payload, got)

        a.close()
        b.close()
        hs.stop()
    }

    @Test
    fun parseHostPortCases() {
        assertEquals("example.com" to NetUtil.DEFAULT_PORT, NetUtil.parseHostPort("example.com"))
        assertEquals("example.com" to 1234, NetUtil.parseHostPort("example.com:1234"))
        assertEquals("2a02:8106::1" to NetUtil.DEFAULT_PORT, NetUtil.parseHostPort("2a02:8106::1"))
        assertEquals("2a02:8106::1" to 1234, NetUtil.parseHostPort("[2a02:8106::1]:1234"))
        assertNull(NetUtil.parseHostPort(""))
    }
}
