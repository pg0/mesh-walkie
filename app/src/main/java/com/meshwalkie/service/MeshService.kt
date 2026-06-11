package com.meshwalkie.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import com.meshwalkie.audio.PttRecorder
import com.meshwalkie.audio.VoicePlayer
import com.meshwalkie.audio.VoiceSender
import com.meshwalkie.core.MeshEngine
import com.meshwalkie.core.Packet
import com.meshwalkie.core.PeerRegistry
import com.meshwalkie.core.TransportRouter
import com.meshwalkie.location.HeadingSource
import com.meshwalkie.location.LocationSource
import com.meshwalkie.nearby.NearbyTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Foreground service = the app survives screen-off (spec platform decision).
 * Owns every runtime component; UI only sees MeshBus.
 */
class MeshService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val seq = AtomicInteger(0)
    private val pttHeld = AtomicBoolean(false)

    private lateinit var originId: String
    private lateinit var transport: NearbyTransport
    private lateinit var engine: MeshEngine
    private lateinit var voiceSender: VoiceSender
    private val registry = PeerRegistry()
    private val router = TransportRouter()
    private val locationSource by lazy { LocationSource(this) }
    private val headingSource by lazy { HeadingSource(this) }
    private val voicePlayer = VoicePlayer()
    private val recorder = PttRecorder()

    @Volatile private var myLat = 0.0
    @Volatile private var myLon = 0.0
    @Volatile private var myHeading = 0f
    @Volatile private var hasFix = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(
            NOTIFICATION_ID,
            buildNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                or ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        )
        startMesh()
        return START_STICKY
    }

    private fun startMesh() {
        originId = DeviceId.get(this)
        transport = NearbyTransport(this, roomCode = "field1", deviceName = DeviceId.displayName(this))
        engine = MeshEngine(transport)
        voiceSender = VoiceSender(engine, originId, nextSeq = { seq.incrementAndGet() })

        engine.start { packet ->
            val now = System.currentTimeMillis()
            router.noteMeshSeen(packet.originId, now)   // heard on mesh -> mesh route
            registry.onPacket(packet, receivedAtMs = now)
            if (packet is Packet.Voice) voicePlayer.onVoicePacket(packet)
            publishPeers()
        }
        transport.start()

        locationSource.start { fix ->
            myLat = fix.latitude
            myLon = fix.longitude
            hasFix = true
            engine.send(
                Packet.Position(
                    originId, seq.incrementAndGet(), Packet.DEFAULT_TTL,
                    System.currentTimeMillis(), fix.latitude, fix.longitude, myHeading
                )
            )
            publishPeers()
        }

        headingSource.start { deg ->
            myHeading = deg
            MeshBus.publishHeading(deg)
        }

        MeshBus.pttHandler = { pressed -> onPtt(pressed) }

        // presence heartbeat + freshness re-render
        scope.launch {
            while (true) {
                engine.send(
                    Packet.Presence(
                        originId, seq.incrementAndGet(), Packet.DEFAULT_TTL,
                        System.currentTimeMillis(),
                        name = DeviceId.displayName(this@MeshService), batteryPct = 100
                    )
                )
                publishPeers()
                delay(10_000L)
            }
        }
    }

    private fun onPtt(pressed: Boolean) {
        if (pressed) {
            if (pttHeld.getAndSet(true)) return
            scope.launch(Dispatchers.IO) {
                val pcm = recorder.record(isHeld = { pttHeld.get() })
                voiceSender.sendClip(pcm, System.currentTimeMillis())
            }
        } else {
            pttHeld.set(false)
        }
    }

    private fun publishPeers() {
        if (!hasFix) return
        MeshBus.publishPeers(registry.snapshot(myLat, myLon, System.currentTimeMillis()))
    }

    private fun buildNotification(): Notification {
        val channel = NotificationChannel(
            CHANNEL_ID, "Mesh active", NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Mesh Walkie")
            .setContentText("Mesh active - sharing position, listening for voice")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .build()
    }

    override fun onDestroy() {
        MeshBus.pttHandler = null
        pttHeld.set(false)
        locationSource.stop()
        headingSource.stop()
        transport.stop()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private companion object {
        const val NOTIFICATION_ID = 1
        const val CHANNEL_ID = "mesh"
    }
}
