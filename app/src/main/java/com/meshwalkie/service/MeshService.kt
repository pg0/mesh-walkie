package com.meshwalkie.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.ForegroundServiceStartNotAllowedException
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
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

    @Volatile private var currentGroup = ""
    private val started = AtomicBoolean(false)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // FGS start can throw on API 31+ (e.g. START_STICKY restart after the
        // location permission was revoked -> declared location FGS type has no
        // backing permission). An uncaught throw here is fatal per project
        // policy, so swallow it and stop cleanly instead.
        try {
            startForeground(
                NOTIFICATION_ID,
                buildNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                    or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                    or ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            )
        } catch (e: Exception) {
            // ForegroundServiceStartNotAllowedException (API 31+) and
            // SecurityException are both subclasses of Exception; catch broadly.
            if (Build.VERSION.SDK_INT >= 31 && e is ForegroundServiceStartNotAllowedException) {
                Log.w(TAG, "startForeground not allowed, stopping", e)
            } else {
                Log.w(TAG, "startForeground failed, stopping", e)
            }
            stopSelf()
            return START_NOT_STICKY
        }
        startMesh()
        return START_STICKY
    }

    private fun startMesh() {
        // START_STICKY redelivers with a null intent after a process kill; guard
        // so the transport/location/heading/heartbeat are wired at most once per
        // service instance (a second wiring would leak the first).
        if (!started.compareAndSet(false, true)) return
        Settings.init(this)
        originId = DeviceId.get(this)
        bindTransport(Settings.groupCode.value)

        // Rejoin a new mesh group when the user changes the code in settings.
        scope.launch {
            Settings.groupCode.collect { code ->
                if (::transport.isInitialized && code != currentGroup) rejoin(code)
            }
        }

        locationSource.start { fix ->
            myLat = fix.latitude
            myLon = fix.longitude
            hasFix = true
            MeshBus.publishWaitingForGps(false)
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
        MeshBus.replayHandler = { voicePlayer.replayLast() }
        voicePlayer.onClipPlayed = { senderId ->
            val name = registry.nameOf(senderId) ?: senderId
            MeshBus.publishLastVoice("Last message from $name")
        }

        // presence heartbeat + freshness re-render
        scope.launch {
            while (true) {
                engine.send(
                    Packet.Presence(
                        originId, seq.incrementAndGet(), Packet.DEFAULT_TTL,
                        System.currentTimeMillis(),
                        name = Settings.displayName.value, batteryPct = 100
                    )
                )
                publishPeers()
                delay(10_000L)
            }
        }
    }

    /** Create + start a transport (and the engine bound to it) for [group]. */
    private fun bindTransport(group: String) {
        currentGroup = group
        transport = NearbyTransport(this, roomCode = group, deviceName = Settings.displayName.value)
        engine = MeshEngine(transport)
        voiceSender = VoiceSender(engine, originId, nextSeq = { seq.incrementAndGet() })

        engine.start { packet ->
            val now = System.currentTimeMillis()
            router.noteMeshSeen(packet.originId, now)   // heard on mesh -> mesh route
            registry.onPacket(packet, receivedAtMs = now)
            if (packet is Packet.Voice) voicePlayer.onVoicePacket(packet)
            publishPeers()
        }

        transport.onLinksChanged = { n ->
            MeshBus.publishLinkCount(n)
            MeshBus.publishStatus(statusText(n))
        }
        MeshBus.publishLinkCount(0)
        MeshBus.publishStatus(statusText(0))   // "Suche Geraete…" until first link
        transport.start()
    }

    /** Leave the current group and join [group]: stop old radio, bind fresh. */
    private fun rejoin(group: String) {
        MeshBus.publishStatus("Switching group…")
        transport.stop()
        bindTransport(group)
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
        val now = System.currentTimeMillis()
        // Roster (who is connected) shows regardless of GPS on either side.
        MeshBus.publishRoster(registry.roster(now))
        // Distance/bearing arrows need our own fix.
        if (!hasFix) return
        MeshBus.publishPeers(registry.snapshot(myLat, myLon, now))
    }

    private fun statusText(links: Int): String = when (links) {
        0 -> "Searching for devices…"
        1 -> "1 device connected"
        else -> "$links devices connected"
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
        MeshBus.replayHandler = null
        voicePlayer.onClipPlayed = null
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
        const val TAG = "MeshService"
    }
}
