package com.meshwalkie.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.ForegroundServiceStartNotAllowedException
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import com.meshwalkie.util.L
import com.meshwalkie.audio.PttRecorder
import com.meshwalkie.audio.VadRecorder
import com.meshwalkie.audio.VoicePlayer
import com.meshwalkie.audio.VoiceSender
import com.meshwalkie.core.ChannelCrypto
import com.meshwalkie.core.CompositeTransport
import com.meshwalkie.core.MeshEngine
import com.meshwalkie.core.Packet
import com.meshwalkie.core.PeerRegistry
import com.meshwalkie.core.TransportRouter
import com.meshwalkie.core.WaypointStore
import com.meshwalkie.location.HeadingSource
import com.meshwalkie.location.LocationSource
import com.meshwalkie.nearby.NearbyTransport
import com.meshwalkie.net.HostServer
import com.meshwalkie.net.NetUtil
import com.meshwalkie.net.ServerLink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
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
    private lateinit var composite: CompositeTransport
    private lateinit var engine: MeshEngine
    private lateinit var voiceSender: VoiceSender
    private var hostServer: HostServer? = null
    private var serverLink: ServerLink? = null
    private val registry = PeerRegistry()
    private val router = TransportRouter()
    private val waypointStore = WaypointStore()
    private val locationSource by lazy { LocationSource(this) }
    private val headingSource by lazy { HeadingSource(this) }
    private val voicePlayer = VoicePlayer()
    private val recorder = PttRecorder()
    private val vad = VadRecorder()
    private var vadJob: Job? = null

    @Volatile private var myLat = 0.0
    @Volatile private var myLon = 0.0
    @Volatile private var myHeading = 0f
    @Volatile private var hasFix = false

    @Volatile private var currentGroup = ""
    @Volatile private var currentLinks = 0
    @Volatile private var everConnected = false   // have we ever had a peer this session?
    @Volatile private var lastSentClipId = -1
    private val acksByClip = HashMap<Int, MutableSet<String>>()   // refClipId -> ackers
    private val outbox = ArrayDeque<ShortArray>()   // voice clips recorded while offline
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
                L.w(TAG, "startForeground not allowed, stopping", e)
            } else {
                L.w(TAG, "startForeground failed, stopping", e)
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
            MeshBus.publishMyLocation(fix.latitude, fix.longitude)
            MeshBus.publishMySpeed(if (fix.hasSpeed()) fix.speed.toDouble() else 0.0)
            // Privacy: only broadcast my position when GPS sharing is enabled.
            if (Settings.gpsEnabled.value) {
                engine.send(
                    Packet.Position(
                        originId, seq.incrementAndGet(), Packet.DEFAULT_TTL,
                        System.currentTimeMillis(), fix.latitude, fix.longitude, myHeading,
                        speedMps = if (fix.hasSpeed()) fix.speed else 0f,
                        courseDeg = if (fix.hasBearing()) fix.bearing else -1f
                    )
                )
            }
            publishPeers()
        }

        headingSource.start { deg ->
            myHeading = deg
            MeshBus.publishHeading(deg)
        }

        // Voice-activated transmit: (re)start the VAD loop when enabled or when
        // the sensitivity changes; stop it when disabled.
        scope.launch {
            combine(Settings.vadEnabled, Settings.vadSensitivity) { en, sens -> en to sens }
                .collect { (enabled, sens) ->
                    vad.stop()
                    vadJob?.join()
                    vadJob = null
                    if (enabled) {
                        val threshold = vad.thresholdFor(sens)
                        vadJob = scope.launch(Dispatchers.IO) {
                            vad.run(threshold, micSource()) { pcm -> emitClip(pcm) }
                        }
                    }
                }
        }

        // Mute-all also silences received voice playback.
        scope.launch { Settings.muteSounds.collect { on -> voicePlayer.muted = on } }

        // Route mic/audio to a Bluetooth headset when the setting is on.
        scope.launch { Settings.btHeadset.collect { on -> applyHeadsetRouting(on) } }

        // Internet fallback: host the relay (toggle); joining is explicit per-host.
        scope.launch { Settings.internetHost.collect { on -> if (on) startHost() else stopHost() } }
        scope.launch {
            Settings.internetClient.collect { on ->
                if (on && serverLink == null) {
                    MeshBus.hosts.value.lastOrNull()?.let { joinHost(it.ip, it.port) }
                } else if (!on) {
                    disconnectClient()
                }
            }
        }
        MeshBus.joinHandler = { ip, port -> joinHost(ip, port) }
        MeshBus.leaveHostHandler = { disconnectClient() }

        MeshBus.pttHandler = { pressed -> onPtt(pressed) }
        MeshBus.replayHandler = { voicePlayer.replayLast() }
        MeshBus.sendTextHandler = { text -> sendText(text) }
        MeshBus.dropWaypointHandler = { label -> dropWaypoint(label) }
        MeshBus.removeWaypointHandler = { id -> waypointStore.remove(id); publishPeers() }
        MeshBus.dropWaypointAtHandler = { lat, lon, label -> dropWaypointAt(lat, lon, label) }
        MeshBus.startCountdownHandler = { label, sec -> startCountdown(label, sec) }
        voicePlayer.onClipPlayed = { senderId, clipId ->
            val name = registry.nameOf(senderId) ?: senderId
            MeshBus.publishLastVoice("Last message from $name")
            // send a delivery receipt back to the sender
            engine.send(
                Packet.Ack(
                    originId, seq.incrementAndGet(), Packet.DEFAULT_TTL,
                    System.currentTimeMillis(), refOriginId = senderId, refClipId = clipId
                )
            )
        }

        // presence heartbeat + freshness re-render
        scope.launch {
            while (true) {
                engine.send(
                    Packet.Presence(
                        originId, seq.incrementAndGet(), Packet.DEFAULT_TTL,
                        System.currentTimeMillis(),
                        name = Settings.displayName.value, batteryPct = batteryPct()
                    )
                )
                hostServer?.let {
                    NetUtil.bestHostAddress()?.let { ip -> announceHost(ip) }
                    MeshBus.publishHostClientCount(it.clientCount)
                }
                publishPeers()
                delay(10_000L)
            }
        }
    }

    /** Create + start a transport (and the engine bound to it) for [group]. */
    private fun bindTransport(group: String) {
        currentGroup = group
        transport = NearbyTransport(this, roomCode = group, deviceName = Settings.displayName.value)
        composite = CompositeTransport(ChannelCrypto(group))   // channel-keyed AES-GCM
        composite.add(transport)            // BLE mesh; internet server links added on demand
        engine = MeshEngine(composite)
        voiceSender = VoiceSender(engine, originId, nextSeq = { seq.incrementAndGet() })

        engine.start { packet ->
            val now = System.currentTimeMillis()
            router.noteMeshSeen(packet.originId, now)   // heard on mesh -> mesh route
            registry.onPacket(packet, receivedAtMs = now)
            if (packet is Packet.Voice) voicePlayer.onVoicePacket(packet)
            if (packet is Packet.Text) {
                MeshBus.publishText("${packet.senderName}: ${packet.text}")
                if (Settings.textSound.value) beep()
            }
            if (packet is Packet.Waypoint) {
                waypointStore.add(packet.dedupKey, packet.senderName, packet.lat, packet.lon, packet.label)
                beep()
            }
            if (packet is Packet.Timer) {
                MeshBus.publishCountdown(packet.label to (System.currentTimeMillis() + packet.durationSec * 1000L))
                beep()
            }
            if (packet is Packet.Host) onHostAnnounced(packet)
            if (packet is Packet.Ack && packet.refOriginId == originId) {
                val ackers = acksByClip.getOrPut(packet.refClipId) { mutableSetOf() }
                ackers.add(packet.originId)
                if (packet.refClipId == lastSentClipId) {
                    MeshBus.publishSentStatus("Heard by ${ackers.size}")
                }
            }
            publishPeers()
        }

        transport.onLinksChanged = { n ->
            val was = currentLinks
            currentLinks = n
            if (n > 0) everConnected = true
            MeshBus.publishLinkCount(n)
            MeshBus.publishStatus(statusText(n))
            if (was == 0 && n > 0) flushOutbox()   // reconnected: deliver queued clips
            if (n < was && Settings.offlineSound.value) offlineBeep()   // a device dropped
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

    private fun sendText(text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        engine.send(
            Packet.Text(
                originId, seq.incrementAndGet(), Packet.DEFAULT_TTL,
                System.currentTimeMillis(), Settings.displayName.value, clean
            )
        )
        MeshBus.publishText("You: $clean")
    }

    private val tone by lazy {
        android.media.ToneGenerator(AudioManager.STREAM_MUSIC, 100)
    }

    /** Audible alert when a quick-text arrives (not for voice). */
    private fun beep() {
        if (Settings.muteSounds.value) return
        try {
            tone.startTone(android.media.ToneGenerator.TONE_PROP_BEEP2, 250)
        } catch (_: Exception) {
        }
    }

    /** Distinct alert when a connected device drops off the mesh. */
    private fun offlineBeep() {
        if (Settings.muteSounds.value) return
        try {
            tone.startTone(android.media.ToneGenerator.TONE_SUP_ERROR, 400)
        } catch (_: Exception) {
        }
    }

    /** Mic source: route to the BT headset when enabled (API 31+), else phone mic. */
    private fun micSource(): Int =
        if (Settings.btHeadset.value && Build.VERSION.SDK_INT >= 31)
            MediaRecorder.AudioSource.VOICE_COMMUNICATION
        else MediaRecorder.AudioSource.MIC

    private fun applyHeadsetRouting(on: Boolean) {
        if (Build.VERSION.SDK_INT < 31) return
        val am = getSystemService(AUDIO_SERVICE) as AudioManager
        if (on) {
            am.mode = AudioManager.MODE_IN_COMMUNICATION
            val dev = am.availableCommunicationDevices.firstOrNull {
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                    it.type == AudioDeviceInfo.TYPE_BLE_HEADSET
            }
            if (dev != null) am.setCommunicationDevice(dev)
        } else {
            am.clearCommunicationDevice()
            am.mode = AudioManager.MODE_NORMAL
        }
    }

    private fun batteryPct(): Int {
        val bm = getSystemService(BATTERY_SERVICE) as android.os.BatteryManager
        return bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
            .coerceIn(0, 100)
    }

    private fun onPtt(pressed: Boolean) {
        // VAD owns the mic when enabled; ignore manual PTT to avoid two AudioRecords.
        if (Settings.vadEnabled.value) return
        if (pressed) {
            if (pttHeld.getAndSet(true)) return
            scope.launch(Dispatchers.IO) {
                val pcm = recorder.record(isHeld = { pttHeld.get() }, audioSource = micSource())
                emitClip(pcm)
            }
        } else {
            pttHeld.set(false)
        }
    }

    /**
     * Send a recorded clip now; if peers dropped (we were connected, now 0),
     * queue it for reconnect. If we have NEVER connected this session, do not
     * queue - there is nobody to talk to and a future joiner should not get a
     * backlog of old clips.
     */
    private fun emitClip(pcm: ShortArray) {
        if (pcm.isEmpty()) return
        if (currentLinks > 0) {
            lastSentClipId = voiceSender.sendClip(pcm, System.currentTimeMillis())
            MeshBus.publishSentStatus("Sent - heard by 0")
        } else if (everConnected) {
            synchronized(outbox) {
                outbox.addLast(pcm)
                while (outbox.size > 5) outbox.removeFirst()
            }
            MeshBus.publishStatus("Voice queued - peer offline, sends on reconnect")
        } else {
            MeshBus.publishStatus("No peers connected - not sent")
        }
    }

    private fun flushOutbox() {
        val pending = synchronized(outbox) { val l = outbox.toList(); outbox.clear(); l }
        pending.forEach { lastSentClipId = voiceSender.sendClip(it, System.currentTimeMillis()) }
    }

    private fun publishPeers() {
        val now = System.currentTimeMillis()
        // Roster (who is connected) shows regardless of GPS on either side.
        MeshBus.publishRoster(registry.roster(now))
        // Distance/bearing arrows need our own fix.
        if (!hasFix) return
        MeshBus.publishPeers(registry.snapshot(myLat, myLon, now))
        MeshBus.publishWaypoints(waypointStore.snapshot(myLat, myLon))
    }

    private fun startCountdown(label: String, seconds: Int) {
        if (seconds <= 0) return
        engine.send(
            Packet.Timer(
                originId, seq.incrementAndGet(), Packet.DEFAULT_TTL,
                System.currentTimeMillis(), label.ifBlank { "Regroup" }, seconds
            )
        )
        MeshBus.publishCountdown(label.ifBlank { "Regroup" } to (System.currentTimeMillis() + seconds * 1000L))
    }

    private fun onHostAnnounced(p: Packet.Host) {
        if (p.originId == originId) return
        MeshBus.addHost(HostInfo(p.originId, p.name, p.ip, p.port))
        val client = Settings.internetClient.value
        val hosting = Settings.internetHost.value
        L.i(TAG, "host announced: ${p.name} [${p.ip}]:${p.port} - client=$client hosting=$hosting linked=${serverLink != null}")
        // Client mode: auto-join the first host that shows up.
        if (client && serverLink == null && !hosting) {
            joinHost(p.ip, p.port)
        }
    }

    private fun startHost() {
        if (hostServer != null) return
        val ip = NetUtil.bestHostAddress()
        if (ip == null) {
            MeshBus.publishStatus("No network address - cannot host")
            return
        }
        val hs = HostServer(NetUtil.DEFAULT_PORT)
        hs.start()
        composite.add(hs)
        hostServer = hs
        MeshBus.publishMyHostIp(ip)
        MeshBus.publishStatus("Hosting at [$ip]:${NetUtil.DEFAULT_PORT}")
        announceHost(ip)
    }

    private fun announceHost(ip: String) {
        if (!::engine.isInitialized) return
        engine.send(
            Packet.Host(
                originId, seq.incrementAndGet(), Packet.DEFAULT_TTL,
                System.currentTimeMillis(), Settings.displayName.value, ip, NetUtil.DEFAULT_PORT
            )
        )
    }

    private fun stopHost() {
        hostServer?.let { composite.remove(it); it.stop() }
        hostServer = null
        MeshBus.publishMyHostIp(null)
        MeshBus.publishStatus(statusText(currentLinks))
    }

    private fun joinHost(ip: String, port: Int) {
        L.i(TAG, "joinHost [$ip]:$port")
        disconnectClient()
        val link = ServerLink(ip, port)
        link.onState = { c ->
            MeshBus.publishJoinedServer(c)
            if (!c) {
                // failed or dropped: free the slot so auto-join can retry
                composite.remove(link)
                if (serverLink === link) serverLink = null
            }
            MeshBus.publishStatus(if (c) "Joined internet host $ip" else statusText(currentLinks))
        }
        link.connect()
        composite.add(link)
        serverLink = link
    }

    private fun disconnectClient() {
        serverLink?.let { composite.remove(it); it.close() }
        serverLink = null
        MeshBus.publishJoinedServer(false)
        MeshBus.publishStatus(statusText(currentLinks))
    }

    private fun dropWaypoint(label: String) {
        if (!hasFix) return
        dropWaypointAt(myLat, myLon, label)
    }

    private fun dropWaypointAt(lat: Double, lon: Double, label: String) {
        val clean = label.trim().ifEmpty {
            java.time.LocalDateTime.now().withNano(0).toString()   // ISO datetime
        }
        val p = Packet.Waypoint(
            originId, seq.incrementAndGet(), Packet.DEFAULT_TTL,
            System.currentTimeMillis(), Settings.displayName.value, lat, lon, clean
        )
        engine.send(p)
        waypointStore.add(p.dedupKey, p.senderName, lat, lon, clean)
        publishPeers()
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
        MeshBus.sendTextHandler = null
        MeshBus.dropWaypointHandler = null
        MeshBus.removeWaypointHandler = null
        MeshBus.dropWaypointAtHandler = null
        MeshBus.startCountdownHandler = null
        MeshBus.joinHandler = null
        MeshBus.leaveHostHandler = null
        voicePlayer.onClipPlayed = null
        pttHeld.set(false)
        vad.stop()
        stopHost()
        disconnectClient()
        applyHeadsetRouting(false)
        locationSource.stop()
        headingSource.stop()
        transport.stop()
        try { tone.release() } catch (_: Exception) {}
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
