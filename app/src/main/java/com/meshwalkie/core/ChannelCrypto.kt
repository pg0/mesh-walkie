package com.meshwalkie.core

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES-256-GCM payload encryption keyed on the channel code (the shared group
 * secret). Applied at the transport seam, so every byte that leaves the device
 * - over the BLE mesh AND the internet relay - is ciphertext. Only devices that
 * know the channel code can decrypt; the channel code becomes a real password,
 * and the relay/host only ever sees ciphertext.
 *
 * Wire format: [12-byte random IV][GCM ciphertext + 16-byte tag].
 */
class ChannelCrypto(channelCode: String) {
    private val key: SecretKey = deriveKey(channelCode)
    private val rng = SecureRandom()

    fun encrypt(plain: ByteArray): ByteArray {
        val iv = ByteArray(IV_LEN).also { rng.nextBytes(it) }
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        val ct = cipher.doFinal(plain)
        return iv + ct
    }

    /** @throws Exception if the data is corrupt or from a different channel. */
    fun decrypt(data: ByteArray): ByteArray {
        require(data.size > IV_LEN) { "ciphertext too short" }
        val iv = data.copyOfRange(0, IV_LEN)
        val ct = data.copyOfRange(IV_LEN, data.size)
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        return cipher.doFinal(ct)
    }

    private fun deriveKey(code: String): SecretKey {
        val md = MessageDigest.getInstance("SHA-256")
        val h = md.digest("meshwalkie-channel-v1:$code".toByteArray(Charsets.UTF_8))
        return SecretKeySpec(h, "AES")
    }

    private companion object {
        const val TRANSFORM = "AES/GCM/NoPadding"
        const val IV_LEN = 12
        const val TAG_BITS = 128
    }
}
