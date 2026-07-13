package com.meshwalkie.core

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * CompositeTransport decrypts each child's ciphertext and tags the plaintext
 * with the [Route] that child was added under, so the engine can label who is
 * reachable over BLE mesh vs the internet. A frame that fails to decrypt
 * (wrong channel / corrupt) is dropped without reaching the handler.
 */
class CompositeTransportTest {

    /** Feed one ciphertext frame into a child as if it arrived off that link. */
    private fun deliverTo(child: FakeTransport, cipher: ByteArray) {
        val sender = FakeTransport()
        FakeTransport.link(sender, child)
        sender.broadcast(cipher)
    }

    @Test
    fun tagsPlaintextWithTheChildsRoute() {
        val crypto = ChannelCrypto("team")
        val composite = CompositeTransport(crypto)
        val mesh = FakeTransport()
        val server = FakeTransport()
        composite.add(mesh, Route.MESH)
        composite.add(server, Route.SERVER)

        val seen = mutableListOf<Pair<ByteArray, Route>>()
        composite.onReceiveRouted { bytes, route -> seen += bytes to route }

        val fromMesh = "near".toByteArray()
        val fromServer = "far".toByteArray()
        deliverTo(mesh, crypto.encrypt(fromMesh))
        deliverTo(server, crypto.encrypt(fromServer))

        assertEquals(2, seen.size)
        assertArrayEquals(fromMesh, seen[0].first)
        assertEquals(Route.MESH, seen[0].second)
        assertArrayEquals(fromServer, seen[1].first)
        assertEquals(Route.SERVER, seen[1].second)
    }

    @Test
    fun undecryptableFrameIsDropped() {
        val composite = CompositeTransport(ChannelCrypto("team"))
        val child = FakeTransport()
        composite.add(child, Route.SERVER)
        var got: ByteArray? = null
        composite.onReceiveRouted { bytes, _ -> got = bytes }

        deliverTo(child, ChannelCrypto("other-channel").encrypt("secret".toByteArray()))

        assertNull("wrong-channel ciphertext must not surface", got)
    }

    @Test
    fun legacyOnReceiveStillDeliversBytes() {
        val crypto = ChannelCrypto("team")
        val composite = CompositeTransport(crypto)
        val child = FakeTransport()
        composite.add(child, Route.MESH)
        val seen = mutableListOf<ByteArray>()
        composite.onReceive { seen += it }   // single-arg legacy intake

        deliverTo(child, crypto.encrypt("hi".toByteArray()))

        assertEquals(1, seen.size)
        assertArrayEquals("hi".toByteArray(), seen[0])
    }

    @Test
    fun removedChildStopsForwarding() {
        val crypto = ChannelCrypto("team")
        val composite = CompositeTransport(crypto)
        val child = FakeTransport()
        composite.add(child, Route.SERVER)
        composite.remove(child)
        var broadcasts = 0
        // After removal the child must not be broadcast to any more.
        val prev = child.broadcastCount
        composite.broadcast("x".toByteArray())
        assertEquals("removed child must not receive broadcasts", prev, child.broadcastCount)
        assertTrue(broadcasts == 0)
    }
}
