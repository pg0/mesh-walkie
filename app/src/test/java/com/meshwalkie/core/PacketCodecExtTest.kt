package com.meshwalkie.core

import org.junit.Assert.assertEquals
import org.junit.Test

class PacketCodecExtTest {

    @Test
    fun textRoundTrips() {
        val p = Packet.Text("a1b2", 7, 4, 1234L, senderName = "Alice", text = "regroup at the hut")
        assertEquals(p, PacketCodec.decode(PacketCodec.encode(p)))
    }

    @Test
    fun textWithUnicodeRoundTrips() {
        val p = Packet.Text("a1b2", 8, 3, 99L, senderName = "Bjorn", text = "uber muhle, 200m")
        assertEquals(p, PacketCodec.decode(PacketCodec.encode(p)))
    }

    @Test
    fun waypointRoundTrips() {
        val p = Packet.Waypoint(
            "c3d4", 11, 4, 5678L, senderName = "Bob",
            lat = 52.5200, lon = 13.4050, label = "car"
        )
        assertEquals(p, PacketCodec.decode(PacketCodec.encode(p)))
    }

    @Test
    fun ackRoundTrips() {
        val p = Packet.Ack("e5f6", 3, 4, 42L, refOriginId = "a1b2", refClipId = 9)
        assertEquals(p, PacketCodec.decode(PacketCodec.encode(p)))
    }

    @Test
    fun hostRoundTrips() {
        val p = Packet.Host("a1b2", 5, 4, 100L, name = "Alice", ip = "2001:db8::1", port = 51820)
        assertEquals(p, PacketCodec.decode(PacketCodec.encode(p)))
    }

    @Test
    fun emptyTextRoundTrips() {
        val p = Packet.Text("a1b2", 1, 4, 1L, senderName = "X", text = "")
        assertEquals(p, PacketCodec.decode(PacketCodec.encode(p)))
    }
}
