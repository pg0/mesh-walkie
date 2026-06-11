package com.meshwalkie.audio

/**
 * Shared playback routing flag. The service flips [earpiece] from the proximity
 * sensor (phone at the ear -> route voice to the earpiece, not the loudspeaker,
 * for use in loud places like concerts). Both players read it when building their
 * AudioTrack so the right output and usage are chosen.
 */
object AudioRoute {
    @Volatile var earpiece = false
}
