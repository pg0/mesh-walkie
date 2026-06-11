package com.meshwalkie.core

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ChannelCryptoTest {

    @Test
    fun roundTrips() {
        val c = ChannelCrypto("team-alpha")
        val plain = "hello mesh, voice + gps".toByteArray()
        assertArrayEquals(plain, c.decrypt(c.encrypt(plain)))
    }

    @Test
    fun wrongChannelCannotDecrypt() {
        val enc = ChannelCrypto("channel-1").encrypt("secret".toByteArray())
        try {
            ChannelCrypto("channel-2").decrypt(enc)
            fail("a different channel must not decrypt")
        } catch (_: Exception) {
            // expected - GCM tag check fails
        }
    }

    @Test
    fun sameKeyDifferentNonceProducesDifferentCiphertext() {
        val c = ChannelCrypto("c")
        val plain = "x".repeat(40).toByteArray()
        val a = c.encrypt(plain)
        val b = c.encrypt(plain)
        assertFalse("random IV should differ", a.contentEquals(b))
        assertTrue("iv + tag overhead", a.size > plain.size)
    }

    @Test
    fun ciphertextIsNotPlaintext() {
        val plain = "ABCDEFGHIJKLMNOP".toByteArray()
        val enc = ChannelCrypto("c").encrypt(plain)
        assertFalse(enc.copyOfRange(12, enc.size).contentEquals(plain))
    }
}
