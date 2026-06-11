package com.meshwalkie.nearby

import android.content.Context
import android.util.Log
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
            client.acceptConnection(endpointId, payloadCallback)
        }
        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            if (result.status.isSuccess) {
                connected += endpointId
                Log.i(TAG, "connected to $endpointId (${connected.size} links)")
            }
        }
        override fun onDisconnected(endpointId: String) {
            connected -= endpointId
            Log.i(TAG, "disconnected from $endpointId")
        }
    }

    private val discoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            // Both sides may request simultaneously; Nearby resolves the race,
            // the loser's request fails with STATUS_ALREADY_CONNECTED - ignore.
            client.requestConnection(deviceName, endpointId, lifecycleCallback)
                .addOnFailureListener { e -> Log.w(TAG, "requestConnection: $e") }
        }
        override fun onEndpointLost(endpointId: String) = Unit
    }

    fun start() {
        client.startAdvertising(
            deviceName, serviceId, lifecycleCallback,
            AdvertisingOptions.Builder().setStrategy(Strategy.P2P_CLUSTER).build()
        ).addOnFailureListener { e -> Log.e(TAG, "startAdvertising: $e") }

        client.startDiscovery(
            serviceId, discoveryCallback,
            DiscoveryOptions.Builder().setStrategy(Strategy.P2P_CLUSTER).build()
        ).addOnFailureListener { e -> Log.e(TAG, "startDiscovery: $e") }
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
                .addOnFailureListener { e -> Log.w(TAG, "sendPayload: $e") }
        }
    }

    override fun onReceive(handler: (ByteArray) -> Unit) {
        this.handler = handler
    }

    private companion object { const val TAG = "NearbyTransport" }
}
