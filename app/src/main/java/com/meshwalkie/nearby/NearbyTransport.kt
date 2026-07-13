package com.meshwalkie.nearby

import android.content.Context
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import com.meshwalkie.core.Transport
import com.meshwalkie.util.L
import java.util.concurrent.CopyOnWriteArraySet

/**
 * Nearby Connections P2P_CLUSTER transport. Every phone both advertises and
 * discovers under one room-scoped service id; all discovered endpoints are
 * connected, forming the single-cluster mesh. BYTES payloads only
 * (Nearby BYTES limit is 32 KB; our largest frame is ~1 s Opus, well under).
 */
class NearbyTransport(
    context: Context,
    roomCode: String,
    private val deviceName: String
) : Transport {

    private val client: ConnectionsClient = Nearby.getConnectionsClient(context)
    private val serviceId = "com.meshwalkie.$roomCode"
    private val connected = CopyOnWriteArraySet<String>()
    @Volatile private var handler: ((ByteArray) -> Unit)? = null

    /** Fired with the current connected-link count whenever it changes. */
    @Volatile var onLinksChanged: ((Int) -> Unit)? = null

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            if (payload.type == Payload.Type.BYTES) {
                payload.asBytes()?.let { handler?.invoke(it) }
            }
        }
        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            // BYTES payloads arrive whole; nothing to do.
        }
    }

    private val lifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            // Cluster topology: accept everyone in the room. Link-level crypto
            // is Nearby's; payload E2E crypto is phase 3.
            L.i(TAG, "connection initiated by/with ${info.endpointName} ($endpointId), accepting")
            client.acceptConnection(endpointId, payloadCallback)
        }
        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            if (result.status.isSuccess) {
                connected += endpointId
                L.i(TAG, "connected to $endpointId (${connected.size} links)")
                onLinksChanged?.invoke(connected.size)
            } else {
                L.w(TAG, "connection to $endpointId failed: ${result.status}")
            }
        }
        override fun onDisconnected(endpointId: String) {
            connected -= endpointId
            L.i(TAG, "disconnected from $endpointId")
            onLinksChanged?.invoke(connected.size)
        }
    }

    private val discoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            // Symmetric advertise+discover means BOTH sides find each other and,
            // if both call requestConnection at once, the requests collide and
            // neither resolves on some GMS builds. Break the tie deterministically:
            // only the lexicographically-smaller name initiates; the other side
            // receives onConnectionInitiated and accepts.
            val theirName = info.endpointName
            L.i(TAG, "endpoint found: $theirName ($endpointId)")
            if (deviceName < theirName) {
                L.i(TAG, "initiating connection to $theirName ($deviceName < $theirName)")
                client.requestConnection(deviceName, endpointId, lifecycleCallback)
                    .addOnFailureListener { e -> L.w(TAG, "requestConnection: $e") }
            } else {
                L.i(TAG, "waiting for $theirName to initiate ($deviceName >= $theirName)")
            }
        }
        override fun onEndpointLost(endpointId: String) {
            L.i(TAG, "endpoint lost: $endpointId")
        }
    }

    fun start() {
        // setLowPower(true) keeps Nearby on BLE only, so it never auto-enables WiFi.
        client.startAdvertising(
            deviceName, serviceId, lifecycleCallback,
            AdvertisingOptions.Builder().setStrategy(Strategy.P2P_CLUSTER).setLowPower(true).build()
        ).addOnFailureListener { e -> L.e(TAG, "startAdvertising: $e") }

        client.startDiscovery(
            serviceId, discoveryCallback,
            DiscoveryOptions.Builder().setStrategy(Strategy.P2P_CLUSTER).setLowPower(true).build()
        ).addOnFailureListener { e -> L.e(TAG, "startDiscovery: $e") }
    }

    /**
     * Restart advertising + discovery without tearing down existing endpoints.
     * Nearby relies on slow internal retry timers to re-mesh after conditions
     * change (e.g. WiFi dropped, so its WiFi mediums died and it must fall back
     * to BLE). Calling this on a network change re-kicks discovery immediately,
     * so a BT fallback comes up in seconds instead of after a long wait. Safe
     * while connected - it does not drop live links (no stopAllEndpoints).
     */
    fun rediscover() {
        try { client.stopDiscovery() } catch (_: Exception) {}
        try { client.stopAdvertising() } catch (_: Exception) {}
        start()
    }

    fun stop() {
        client.stopAdvertising()
        client.stopDiscovery()
        client.stopAllEndpoints()
        connected.clear()
    }

    override fun broadcast(bytes: ByteArray) {
        val targets = connected.toList()
        if (targets.isNotEmpty()) {
            client.sendPayload(targets, Payload.fromBytes(bytes))
                .addOnFailureListener { e -> L.w(TAG, "sendPayload: $e") }
        }
    }

    override fun onReceive(handler: (ByteArray) -> Unit) {
        this.handler = handler
    }

    private companion object { const val TAG = "NearbyTransport" }
}
