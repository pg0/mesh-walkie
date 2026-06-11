package com.meshwalkie.core

/**
 * Every packet carries (originId, seqNum, ttl, timestamp).
 * Dedup key: (originId, seqNum) - for voice (originId, clipId, frameNum).
 */
sealed class Packet {
    abstract val originId: String
    abstract val seqNum: Int
    abstract val ttl: Int
    abstract val timestampMs: Long

    abstract val dedupKey: String
    abstract fun withTtl(newTtl: Int): Packet

    data class Position(
        override val originId: String,
        override val seqNum: Int,
        override val ttl: Int,
        override val timestampMs: Long,
        val lat: Double,
        val lon: Double,
        val headingDeg: Float
    ) : Packet() {
        override val dedupKey get() = "$originId:$seqNum"
        override fun withTtl(newTtl: Int) = copy(ttl = newTtl)
    }

    data class Voice(
        override val originId: String,
        override val seqNum: Int,
        override val ttl: Int,
        override val timestampMs: Long,
        val clipId: Int,
        val frameNum: Int,
        val isLast: Boolean,
        val opusData: ByteArray
    ) : Packet() {
        override val dedupKey get() = "$originId:v:$clipId:$frameNum"
        override fun withTtl(newTtl: Int) = copy(ttl = newTtl)

        // ByteArray field needs manual equals/hashCode
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Voice) return false
            return originId == other.originId && seqNum == other.seqNum &&
                ttl == other.ttl && timestampMs == other.timestampMs &&
                clipId == other.clipId && frameNum == other.frameNum &&
                isLast == other.isLast && opusData.contentEquals(other.opusData)
        }
        override fun hashCode(): Int = dedupKey.hashCode()
    }

    data class Presence(
        override val originId: String,
        override val seqNum: Int,
        override val ttl: Int,
        override val timestampMs: Long,
        val name: String,
        val batteryPct: Int
    ) : Packet() {
        override val dedupKey get() = "$originId:$seqNum"
        override fun withTtl(newTtl: Int) = copy(ttl = newTtl)
    }

    /** Quick-text / short chat message flooded to the channel. */
    data class Text(
        override val originId: String,
        override val seqNum: Int,
        override val ttl: Int,
        override val timestampMs: Long,
        val senderName: String,
        val text: String
    ) : Packet() {
        override val dedupKey get() = "$originId:$seqNum"
        override fun withTtl(newTtl: Int) = copy(ttl = newTtl)
    }

    /** A named, fixed point dropped by someone (rally point, "car here"). */
    data class Waypoint(
        override val originId: String,
        override val seqNum: Int,
        override val ttl: Int,
        override val timestampMs: Long,
        val senderName: String,
        val lat: Double,
        val lon: Double,
        val label: String
    ) : Packet() {
        override val dedupKey get() = "$originId:$seqNum"
        override fun withTtl(newTtl: Int) = copy(ttl = newTtl)
    }

    /** Ping/pong for round-trip latency (signal proxy). replyTo="" = ping; else = pong to that id. */
    data class Ping(
        override val originId: String,
        override val seqNum: Int,
        override val ttl: Int,
        override val timestampMs: Long,
        val nonce: Int,
        val replyTo: String
    ) : Packet() {
        override val dedupKey get() = "$originId:$seqNum"
        override fun withTtl(newTtl: Int) = copy(ttl = newTtl)
    }

    /** Announces an in-app relay host reachable over the internet at [ip]:[port]. */
    data class Host(
        override val originId: String,
        override val seqNum: Int,
        override val ttl: Int,
        override val timestampMs: Long,
        val name: String,
        val ip: String,
        val port: Int
    ) : Packet() {
        override val dedupKey get() = "$originId:$seqNum"
        override fun withTtl(newTtl: Int) = copy(ttl = newTtl)
    }

    /** Delivery receipt: [originId] heard the clip (refOriginId, refClipId). */
    data class Ack(
        override val originId: String,
        override val seqNum: Int,
        override val ttl: Int,
        override val timestampMs: Long,
        val refOriginId: String,
        val refClipId: Int
    ) : Packet() {
        override val dedupKey get() = "$originId:$seqNum"
        override fun withTtl(newTtl: Int) = copy(ttl = newTtl)
    }

    companion object {
        const val DEFAULT_TTL = 4
    }
}
