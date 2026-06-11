# Mesh Walkie-Talkie Phase 1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A working Android walkie-talkie app where a group in a single Nearby Connections cluster sees each other as live distance+bearing arrows and talks via push-to-talk Opus clips, with the entire mesh/geo/packet core proven by JVM unit tests before any radio code exists.

**Architecture:** A pure-Kotlin core (`com.meshwalkie.core`) holds all bug-prone logic - geo math, packet codec, TTL+dedup flooding, peer registry, routing - tested on the JVM with zero Android dependencies. A `Transport` interface isolates radio; `FakeTransport` proves multi-node relay in tests, `NearbyTransport` (P2P_CLUSTER) is a thin adapter over the same interface. Android layers (location, rotation-vector heading, AudioRecord/MediaCodec-Opus audio, Compose UI, foreground service) plug into the core via callbacks and StateFlow.

**Tech Stack:** Kotlin, Jetpack Compose, Google Nearby Connections, FusedLocationProviderClient, rotation-vector sensor, Opus, Kotlin Coroutines + Flow, JUnit (JVM unit tests)

---

**Phase 1 scope guard:** single cluster only. NO server fallback, NO cross-cluster bridge, NO E2E encryption (phases 2/3). `TransportRouter` decision logic IS built and tested (it is pure core), but phase 1 wiring always sends via mesh.

**API grounding notes (context7, 2026-06-11):**
- `/google/nearby` only documents the C++ Core library (`Core::StartAdvertising`, `StartDiscovery`, `RequestConnection`/`AcceptConnection`, `SendPayload`, BYTES payloads, lifecycle callbacks initiated/accepted/disconnected). The Android GMS binding `com.google.android.gms.nearby.connection` has no context7 entry; this plan uses the documented Android API (`Nearby.getConnectionsClient`, `ConnectionsClient.startAdvertising/startDiscovery/requestConnection/acceptConnection/sendPayload`, `Strategy.P2P_CLUSTER`, `Payload.fromBytes`/`asBytes`, `ConnectionLifecycleCallback`, `EndpointDiscoveryCallback`, `PayloadCallback`) whose semantics match the C++ docs 1:1.
- `/websites/developer_android` confirmed: `LocationCallback.onLocationResult(LocationResult)` pattern, `SensorEventListener` + `SensorManager.registerListener(listener, sensor, SENSOR_DELAY_UI)` + `getSystemService(Context.SENSOR_SERVICE)`, `Service.startForeground(id, notification, foregroundServiceType)` with manifest `android:foregroundServiceType` mandatory on targetSdk 34+, `FOREGROUND_SERVICE` + typed FGS permissions, `RECORD_AUDIO` (dangerous).
- context7 had no entry for `SensorManager.getRotationMatrixFromVector`/`getOrientation` statics, `LocationRequest.Builder(priority, intervalMillis)`, `AudioRecord`/`AudioTrack`, or MediaCodec-Opus specifics; using the documented Android APIs: `MediaFormat.MIMETYPE_AUDIO_OPUS` (`"audio/opus"`), software Opus encoder available since API 29 (hence minSdk 29), Opus decoder CSD per MediaCodec docs (csd-0 = identification header, csd-1 = pre-skip ns int64, csd-2 = seek pre-roll ns int64).
- `/websites/developer_android_develop_ui_compose` confirmed: `Canvas(modifier) { ... }` with `DrawScope`, `rotate(degrees = 45F) { drawRect(...) }`, `Modifier.pointerInput(Unit) { detect*Gestures }`, `collectAsStateWithLifecycle()` requires `androidx.lifecycle:lifecycle-runtime-compose:2.10.0`.

---

## File Structure

```
mesh-walkie/                                  (repo root, .gitignore exists)
  README.md                                   project overview, build + field-test instructions
  settings.gradle.kts                         repo plugin/dependency repositories, includes :app
  build.gradle.kts                            root plugin versions (AGP, Kotlin, Compose plugin)
  gradle.properties                           AndroidX + JVM args
  app/
    build.gradle.kts                          app module: compose, deps, minSdk 29 / target 34
    src/main/AndroidManifest.xml              all permissions, MainActivity, MeshService
    src/main/java/com/meshwalkie/
      core/GeoMath.kt                         haversine distance + initial great-circle bearing
      core/Display.kt                         16-point compass label, distance format, arrow rotation
      core/Packet.kt                          sealed Packet (Position/Voice/Presence) + dedup keys
      core/PacketCodec.kt                     binary encode/decode (ByteBuffer, big-endian)
      core/SeenSet.kt                         LRU seen-set, 500 entries, insertion-order eviction
      core/FloodController.kt                 dedup + TTL decision: deliver? forward?
      core/PeerRegistry.kt                    peer state, distance/bearing snapshot, freshness
      core/TransportRouter.kt                 per-peer MESH vs SERVER decision (20 s mesh timeout)
      core/Transport.kt                       transport interface: broadcast + onReceive
      core/MeshEngine.kt                      glues Transport + FloodController; send/deliver/relay
      core/HeadingFilter.kt                   low-pass filter with 360-degree wraparound
      core/ReorderBuffer.kt                   per-clip frame reordering by frameNum
      core/VoiceFramer.kt                     groups 20 ms Opus packets into ~1 s frames, pack/unpack
      nearby/NearbyTransport.kt               Nearby Connections P2P_CLUSTER Transport impl
      location/LocationSource.kt              FusedLocationProviderClient wrapper
      location/HeadingSource.kt               rotation-vector sensor -> filtered heading degrees
      audio/OpusCodec.kt                      MediaCodec Opus encode/decode (16 kHz mono)
      audio/PttRecorder.kt                    AudioRecord PCM capture while PTT held
      audio/VoiceSender.kt                    clip -> Opus -> frames -> Voice packets
      audio/VoicePlayer.kt                    Voice packets -> reorder -> decode -> AudioTrack
      service/MeshBus.kt                      StateFlows + PTT handler bridging service and UI
      service/MeshService.kt                  foreground service owning engine + sources
      service/DeviceId.kt                     persistent random device id + display name
      ui/MainActivity.kt                      permission flow, starts service, sets Compose content
      ui/PeerListScreen.kt                    peer rows (arrow, "600 m NNW", name, freshness dot)
      ui/ArrowIcon.kt                         Canvas arrow rotated by bearing - heading
      ui/PttButton.kt                         press-and-hold button via pointerInput
    src/test/java/com/meshwalkie/core/
      SanityTest.kt                           proves the JVM test harness runs
      GeoMathTest.kt                          haversine + bearing against precomputed values
      DisplayTest.kt                          compass labels, distance strings, arrow rotation
      PacketCodecTest.kt                      round-trip all 3 packet types, reject garbage
      SeenSetTest.kt                          dedup + LRU eviction at capacity
      FloodControllerTest.kt                  deliver/forward/drop matrix incl. ttl 0
      PeerRegistryTest.kt                     update, snapshot distance/bearing, freshness aging
      TransportRouterTest.kt                  mesh-recent vs mesh-stale routing
      FakeTransport.kt                        in-memory linked transport (test sources only)
      MeshEngineTest.kt                       3-node chain hop, 4-node TTL cutoff, triangle loop dedup
      HeadingFilterTest.kt                    smoothing + 350->10 wraparound
      ReorderBufferTest.kt                    out-of-order arrival, duplicate drop
      VoiceFramerTest.kt                      grouping, flush, pack/unpack round-trip
```

Build commands run from repo root `C:/AI/context/coding/mesh-walkie` (use `gradlew.bat` on Windows where `./gradlew` is shown).

---

### Task 1: Project scaffold + test harness

**Files:**
- Create: `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`, `README.md`
- Test: `app/src/test/java/com/meshwalkie/core/SanityTest.kt`

Steps:

- [ ] 1. Write `settings.gradle.kts`:

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "mesh-walkie"
include(":app")
```

- [ ] 2. Write root `build.gradle.kts`:

```kotlin
plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
}
```

- [ ] 3. Write `gradle.properties`:

```properties
android.useAndroidX=true
org.gradle.jvmargs=-Xmx2g -Dfile.encoding=UTF-8
kotlin.code.style=official
```

- [ ] 4. Write `app/build.gradle.kts`:

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.meshwalkie"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.meshwalkie"
        minSdk = 29        // MediaCodec software Opus encoder requires API 29
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
    }

    buildFeatures { compose = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("com.google.android.gms:play-services-nearby:19.3.0")
    implementation("com.google.android.gms:play-services-location:21.3.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
}
```

- [ ] 5. Write `app/src/main/AndroidManifest.xml` with the full phase 1 permission set (service element is added in Task 15):

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <!-- Nearby Connections (API 31+) -->
    <uses-permission android:name="android.permission.BLUETOOTH_ADVERTISE" />
    <uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
    <uses-permission android:name="android.permission.BLUETOOTH_SCAN"
        android:usesPermissionFlags="neverForLocation" />
    <uses-permission android:name="android.permission.NEARBY_WIFI_DEVICES"
        android:usesPermissionFlags="neverForLocation" />
    <!-- Nearby Connections (API 30 and lower) -->
    <uses-permission android:name="android.permission.BLUETOOTH" android:maxSdkVersion="30" />
    <uses-permission android:name="android.permission.BLUETOOTH_ADMIN" android:maxSdkVersion="30" />
    <uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
    <uses-permission android:name="android.permission.CHANGE_WIFI_STATE" />

    <!-- Position packets -->
    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
    <uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />

    <!-- PTT -->
    <uses-permission android:name="android.permission.RECORD_AUDIO" />

    <!-- Screen-off operation -->
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_LOCATION" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MICROPHONE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

    <application
        android:label="Mesh Walkie"
        android:theme="@android:style/Theme.Material.Light.NoActionBar">
        <activity
            android:name=".ui.MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

- [ ] 6. Write `README.md`:

```markdown
# Mesh Walkie

Android mesh walkie-talkie. Push-to-talk voice + live peer arrows (distance,
16-point bearing) over a Google Nearby Connections P2P_CLUSTER mesh. Offline
first, no accounts, no server in phase 1.

## Build

    gradlew.bat :app:assembleDebug

## Unit tests (pure JVM core)

    gradlew.bat :app:testDebugUnitTest

## Run

Install on 2-3 phones, grant all requested permissions, walk apart, hold the
PTT button to talk. Design spec: docs/superpowers/specs/, plan: docs/superpowers/plans/.
```

- [ ] 7. Write the failing-by-absence sanity test `app/src/test/java/com/meshwalkie/core/SanityTest.kt`:

```kotlin
package com.meshwalkie.core

import org.junit.Assert.assertEquals
import org.junit.Test

class SanityTest {
    @Test
    fun harnessRuns() {
        assertEquals(4, 2 + 2)
    }
}
```

- [ ] 8. Generate the wrapper and run: `gradle wrapper --gradle-version 8.9` then `./gradlew :app:testDebugUnitTest --tests "*SanityTest*"`. Expected: `BUILD SUCCESSFUL`, 1 test passed.
- [ ] 9. Commit: `git add -A && git commit -m "chore: android project scaffold with JVM test harness"`

---

### Task 2: GeoMath - haversine distance + great-circle bearing

**Files:**
- Create: `app/src/main/java/com/meshwalkie/core/GeoMath.kt`
- Test: `app/src/test/java/com/meshwalkie/core/GeoMathTest.kt`

Steps:

- [ ] 1. Write the failing test `GeoMathTest.kt` (reference values precomputed with R=6371000):

```kotlin
package com.meshwalkie.core

import org.junit.Assert.assertEquals
import org.junit.Test

class GeoMathTest {

    @Test
    fun distanceBerlinToMunichIs504km() {
        // Berlin 52.52,13.405 -> Munich 48.137,11.575 = 504337.9 m
        val d = GeoMath.distanceMeters(52.52, 13.405, 48.137, 11.575)
        assertEquals(504337.9, d, 500.0)
    }

    @Test
    fun distanceOneDegreeLongitudeAtEquator() {
        // 2*pi*R/360 = 111194.93 m
        val d = GeoMath.distanceMeters(0.0, 0.0, 0.0, 1.0)
        assertEquals(111194.9, d, 1.0)
    }

    @Test
    fun distanceSamePointIsZero() {
        assertEquals(0.0, GeoMath.distanceMeters(52.52, 13.405, 52.52, 13.405), 0.001)
    }

    @Test
    fun bearingDueEastIs90() {
        assertEquals(90.0, GeoMath.bearingDegrees(0.0, 0.0, 0.0, 1.0), 0.001)
    }

    @Test
    fun bearingDueNorthIs0() {
        assertEquals(0.0, GeoMath.bearingDegrees(0.0, 0.0, 1.0, 0.0), 0.001)
    }

    @Test
    fun bearingBerlinToMunichIsSouthSouthWest() {
        // precomputed: 195.634 degrees
        assertEquals(195.634, GeoMath.bearingDegrees(52.52, 13.405, 48.137, 11.575), 0.01)
    }

    @Test
    fun bearingMunichToBerlinIsNorthNorthEast() {
        // precomputed: 14.224 degrees (NOT the 180-degree reverse - great circle)
        assertEquals(14.224, GeoMath.bearingDegrees(48.137, 11.575, 52.52, 13.405), 0.01)
    }

    @Test
    fun bearingIsAlwaysInZeroTo360() {
        // northwest-ish target: precomputed 309.522 degrees
        val b = GeoMath.bearingDegrees(52.52, 13.405, 52.62, 13.205)
        assertEquals(309.522, b, 0.01)
    }
}
```

- [ ] 2. Run `./gradlew :app:testDebugUnitTest --tests "*GeoMathTest*"`. Expected: FAIL - `Unresolved reference: GeoMath` (compile error counts as red).
- [ ] 3. Write `GeoMath.kt`:

```kotlin
package com.meshwalkie.core

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Pure spherical geo math. No Android imports - JVM testable. */
object GeoMath {
    private const val EARTH_RADIUS_M = 6371000.0

    /** Haversine distance in meters between two WGS84 points. */
    fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val dPhi = Math.toRadians(lat2 - lat1)
        val dLambda = Math.toRadians(lon2 - lon1)
        val a = sin(dPhi / 2) * sin(dPhi / 2) +
            cos(phi1) * cos(phi2) * sin(dLambda / 2) * sin(dLambda / 2)
        return 2 * EARTH_RADIUS_M * atan2(sqrt(a), sqrt(1 - a))
    }

    /** Initial great-circle bearing from point 1 to point 2, degrees in [0, 360). */
    fun bearingDegrees(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val dLambda = Math.toRadians(lon2 - lon1)
        val theta = atan2(
            sin(dLambda) * cos(phi2),
            cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(dLambda)
        )
        return (Math.toDegrees(theta) + 360.0) % 360.0
    }
}
```

- [ ] 4. Run `./gradlew :app:testDebugUnitTest --tests "*GeoMathTest*"`. Expected: PASS, 8 tests.
- [ ] 5. Commit: `git add -A && git commit -m "feat(core): haversine distance and great-circle bearing"`

---

### Task 3: Display math - compass label, distance string, arrow rotation

**Files:**
- Create: `app/src/main/java/com/meshwalkie/core/Display.kt`
- Test: `app/src/test/java/com/meshwalkie/core/DisplayTest.kt`

Steps:

- [ ] 1. Write the failing test `DisplayTest.kt`:

```kotlin
package com.meshwalkie.core

import org.junit.Assert.assertEquals
import org.junit.Test

class DisplayTest {

    @Test
    fun compassLabelsAtSectorCenters() {
        assertEquals("N", Display.compassLabel(0.0))
        assertEquals("NNE", Display.compassLabel(22.5))
        assertEquals("E", Display.compassLabel(90.0))
        assertEquals("SSW", Display.compassLabel(202.5))
        assertEquals("SSW", Display.compassLabel(195.634)) // Berlin -> Munich
        assertEquals("NW", Display.compassLabel(309.522))
        assertEquals("NNW", Display.compassLabel(337.5))
    }

    @Test
    fun compassLabelWrapsBackToNorth() {
        // 348.75 / 22.5 = 15.5 -> roundToInt 16 -> mod 16 = 0 -> N
        assertEquals("N", Display.compassLabel(348.75))
        assertEquals("N", Display.compassLabel(359.9))
        assertEquals("NNW", Display.compassLabel(348.74))
    }

    @Test
    fun distanceUnderOneKmInMeters() {
        assertEquals("600 m", Display.formatDistance(600.4))
        assertEquals("999 m", Display.formatDistance(999.4))
        assertEquals("0 m", Display.formatDistance(0.0))
    }

    @Test
    fun distanceFromOneKmInKilometersOneDecimal() {
        assertEquals("1.0 km", Display.formatDistance(1000.0))
        assertEquals("504.3 km", Display.formatDistance(504337.9))
    }

    @Test
    fun arrowRotationIsBearingMinusHeadingNormalized() {
        assertEquals(0f, Display.arrowRotation(90.0, 90.0), 0.001f)
        assertEquals(20f, Display.arrowRotation(10.0, 350.0), 0.001f)   // wraps up
        assertEquals(340f, Display.arrowRotation(350.0, 10.0), 0.001f)  // wraps down
        assertEquals(270f, Display.arrowRotation(180.0, 270.0), 0.001f)
    }
}
```

- [ ] 2. Run `./gradlew :app:testDebugUnitTest --tests "*DisplayTest*"`. Expected: FAIL - `Unresolved reference: Display`.
- [ ] 3. Write `Display.kt`:

```kotlin
package com.meshwalkie.core

import java.util.Locale
import kotlin.math.roundToInt

/** Pure presentation math for the peer rows. No Android imports. */
object Display {
    private val COMPASS_16 = listOf(
        "N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE",
        "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW"
    )

    /** 16-point compass label: idx = round(deg / 22.5) mod 16. */
    fun compassLabel(bearingDeg: Double): String {
        val idx = (bearingDeg / 22.5).roundToInt() % 16
        return COMPASS_16[idx]
    }

    /** <1 km in meters, >=1 km in km with one decimal. */
    fun formatDistance(meters: Double): String =
        if (meters < 1000.0) "${meters.roundToInt()} m"
        else String.format(Locale.US, "%.1f km", meters / 1000.0)

    /** On-screen arrow rotation = bearingToPeer - myHeading, normalized [0, 360). */
    fun arrowRotation(bearingToPeerDeg: Double, myHeadingDeg: Double): Float {
        val r = (bearingToPeerDeg - myHeadingDeg) % 360.0
        return ((r + 360.0) % 360.0).toFloat()
    }
}
```

- [ ] 4. Run `./gradlew :app:testDebugUnitTest --tests "*DisplayTest*"`. Expected: PASS, 5 tests.
- [ ] 5. Commit: `git add -A && git commit -m "feat(core): compass label, distance format, arrow rotation"`

---

### Task 4: Packet model + binary codec

**Files:**
- Create: `app/src/main/java/com/meshwalkie/core/Packet.kt`, `app/src/main/java/com/meshwalkie/core/PacketCodec.kt`
- Test: `app/src/test/java/com/meshwalkie/core/PacketCodecTest.kt`

Wire format (big-endian, ByteBuffer default):
`[type 1B][originIdLen 1B][originId UTF-8][seqNum 4B][ttl 1B][timestampMs 8B][body]`
Bodies - POSITION: `lat 8B double, lon 8B double, heading 4B float`. VOICE: `clipId 4B, frameNum 4B, isLast 1B, dataLen 2B, data`. PRESENCE: `nameLen 1B, name UTF-8, batteryPct 1B`.

Steps:

- [ ] 1. Write the failing test `PacketCodecTest.kt`:

```kotlin
package com.meshwalkie.core

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PacketCodecTest {

    @Test
    fun positionRoundTrip() {
        val p = Packet.Position(
            originId = "a1b2c3d4", seqNum = 42, ttl = 4, timestampMs = 1765432100123L,
            lat = 52.52, lon = 13.405, headingDeg = 195.5f
        )
        val decoded = PacketCodec.decode(PacketCodec.encode(p))
        assertEquals(p, decoded)
    }

    @Test
    fun voiceRoundTrip() {
        val opus = byteArrayOf(1, 2, 3, 4, 5, -1, 0, 127)
        val p = Packet.Voice(
            originId = "a1b2c3d4", seqNum = 7, ttl = 4, timestampMs = 1765432100456L,
            clipId = 3, frameNum = 12, isLast = true, opusData = opus
        )
        val decoded = PacketCodec.decode(PacketCodec.encode(p)) as Packet.Voice
        assertEquals(p.originId, decoded.originId)
        assertEquals(p.seqNum, decoded.seqNum)
        assertEquals(p.ttl, decoded.ttl)
        assertEquals(p.timestampMs, decoded.timestampMs)
        assertEquals(3, decoded.clipId)
        assertEquals(12, decoded.frameNum)
        assertEquals(true, decoded.isLast)
        assertArrayEquals(opus, decoded.opusData)
    }

    @Test
    fun presenceRoundTrip() {
        val p = Packet.Presence(
            originId = "a1b2c3d4", seqNum = 1, ttl = 4, timestampMs = 1765432100789L,
            name = "Patrick", batteryPct = 87
        )
        assertEquals(p, PacketCodec.decode(PacketCodec.encode(p)))
    }

    @Test
    fun dedupKeyIsOriginAndSeqForPositionAndPresence() {
        val p = Packet.Position("aa", 5, 4, 0L, 0.0, 0.0, 0f)
        assertEquals("aa:5", p.dedupKey)
    }

    @Test
    fun dedupKeyIsOriginClipFrameForVoice() {
        val v = Packet.Voice("aa", 9, 4, 0L, clipId = 2, frameNum = 3,
            isLast = false, opusData = byteArrayOf(1))
        assertEquals("aa:v:2:3", v.dedupKey)
    }

    @Test
    fun withTtlCopiesEveryType() {
        val p = Packet.Position("aa", 5, 4, 0L, 1.0, 2.0, 3f).withTtl(3)
        assertEquals(3, p.ttl)
        assertEquals("aa", p.originId)
    }

    @Test
    fun garbageThrows() {
        assertThrows(IllegalArgumentException::class.java) {
            PacketCodec.decode(byteArrayOf(99, 0, 0))
        }
    }
}
```

- [ ] 2. Run `./gradlew :app:testDebugUnitTest --tests "*PacketCodecTest*"`. Expected: FAIL - unresolved references.
- [ ] 3. Write `Packet.kt`:

```kotlin
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

    companion object {
        const val DEFAULT_TTL = 4
    }
}
```

- [ ] 4. Write `PacketCodec.kt`:

```kotlin
package com.meshwalkie.core

import java.nio.ByteBuffer

/** Binary wire codec. Big-endian. Identical format on every transport. */
object PacketCodec {
    private const val TYPE_POSITION: Byte = 1
    private const val TYPE_VOICE: Byte = 2
    private const val TYPE_PRESENCE: Byte = 3

    fun encode(p: Packet): ByteArray {
        val originBytes = p.originId.toByteArray(Charsets.UTF_8)
        require(originBytes.size <= 255) { "originId too long" }
        val header = 1 + 1 + originBytes.size + 4 + 1 + 8
        val buf: ByteBuffer = when (p) {
            is Packet.Position -> ByteBuffer.allocate(header + 8 + 8 + 4).also {
                it.put(TYPE_POSITION); putHeader(it, p, originBytes)
                it.putDouble(p.lat); it.putDouble(p.lon); it.putFloat(p.headingDeg)
            }
            is Packet.Voice -> {
                require(p.opusData.size <= 65535) { "voice frame too large" }
                ByteBuffer.allocate(header + 4 + 4 + 1 + 2 + p.opusData.size).also {
                    it.put(TYPE_VOICE); putHeader(it, p, originBytes)
                    it.putInt(p.clipId); it.putInt(p.frameNum)
                    it.put(if (p.isLast) 1 else 0)
                    it.putShort(p.opusData.size.toShort()); it.put(p.opusData)
                }
            }
            is Packet.Presence -> {
                val nameBytes = p.name.toByteArray(Charsets.UTF_8)
                require(nameBytes.size <= 255) { "name too long" }
                ByteBuffer.allocate(header + 1 + nameBytes.size + 1).also {
                    it.put(TYPE_PRESENCE); putHeader(it, p, originBytes)
                    it.put(nameBytes.size.toByte()); it.put(nameBytes)
                    it.put(p.batteryPct.toByte())
                }
            }
        }
        return buf.array()
    }

    fun decode(bytes: ByteArray): Packet {
        try {
            val buf = ByteBuffer.wrap(bytes)
            val type = buf.get()
            val originLen = buf.get().toInt() and 0xFF
            val originId = String(ByteArray(originLen).also { buf.get(it) }, Charsets.UTF_8)
            val seqNum = buf.getInt()
            val ttl = buf.get().toInt()
            val ts = buf.getLong()
            return when (type) {
                TYPE_POSITION -> Packet.Position(
                    originId, seqNum, ttl, ts,
                    lat = buf.getDouble(), lon = buf.getDouble(), headingDeg = buf.getFloat()
                )
                TYPE_VOICE -> {
                    val clipId = buf.getInt()
                    val frameNum = buf.getInt()
                    val isLast = buf.get() == 1.toByte()
                    val len = buf.getShort().toInt() and 0xFFFF
                    val data = ByteArray(len).also { buf.get(it) }
                    Packet.Voice(originId, seqNum, ttl, ts, clipId, frameNum, isLast, data)
                }
                TYPE_PRESENCE -> {
                    val nameLen = buf.get().toInt() and 0xFF
                    val name = String(ByteArray(nameLen).also { buf.get(it) }, Charsets.UTF_8)
                    Packet.Presence(originId, seqNum, ttl, ts, name, buf.get().toInt())
                }
                else -> throw IllegalArgumentException("unknown packet type $type")
            }
        } catch (e: java.nio.BufferUnderflowException) {
            throw IllegalArgumentException("truncated packet", e)
        }
    }

    private fun putHeader(buf: ByteBuffer, p: Packet, originBytes: ByteArray) {
        buf.put(originBytes.size.toByte()); buf.put(originBytes)
        buf.putInt(p.seqNum); buf.put(p.ttl.toByte()); buf.putLong(p.timestampMs)
    }
}
```

- [ ] 5. Run `./gradlew :app:testDebugUnitTest --tests "*PacketCodecTest*"`. Expected: PASS, 7 tests.
- [ ] 6. Commit: `git add -A && git commit -m "feat(core): packet model and binary wire codec"`

---

### Task 5: SeenSet (LRU) + FloodController (TTL/dedup decision)

**Files:**
- Create: `app/src/main/java/com/meshwalkie/core/SeenSet.kt`, `app/src/main/java/com/meshwalkie/core/FloodController.kt`
- Test: `app/src/test/java/com/meshwalkie/core/SeenSetTest.kt`, `app/src/test/java/com/meshwalkie/core/FloodControllerTest.kt`

Steps:

- [ ] 1. Write the failing test `SeenSetTest.kt`:

```kotlin
package com.meshwalkie.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SeenSetTest {

    @Test
    fun firstSightingIsNewSecondIsNot() {
        val seen = SeenSet(capacity = 500)
        assertTrue(seen.checkAndAdd("a:1"))
        assertFalse(seen.checkAndAdd("a:1"))
        assertTrue(seen.checkAndAdd("a:2"))
    }

    @Test
    fun evictsOldestAtCapacity() {
        val seen = SeenSet(capacity = 3)
        seen.checkAndAdd("k1"); seen.checkAndAdd("k2"); seen.checkAndAdd("k3")
        seen.checkAndAdd("k4")               // evicts k1
        assertEquals(3, seen.size)
        assertTrue(seen.checkAndAdd("k1"))   // k1 forgotten -> counts as new again
        assertFalse(seen.checkAndAdd("k4"))  // k4 still remembered
    }

    @Test
    fun defaultCapacityIs500() {
        val seen = SeenSet()
        repeat(500) { assertTrue(seen.checkAndAdd("k$it")) }
        assertEquals(500, seen.size)
        seen.checkAndAdd("k500")
        assertEquals(500, seen.size)         // bounded memory
        assertTrue(seen.checkAndAdd("k0"))   // oldest evicted
    }
}
```

- [ ] 2. Run `./gradlew :app:testDebugUnitTest --tests "*SeenSetTest*"`. Expected: FAIL - `Unresolved reference: SeenSet`.
- [ ] 3. Write `SeenSet.kt`:

```kotlin
package com.meshwalkie.core

/**
 * Bounded dedup memory: LRU ~500 entries keyed by Packet.dedupKey,
 * insertion-order eviction (oldest first). Thread-safe via synchronization
 * because transports deliver from binder/radio threads.
 */
class SeenSet(private val capacity: Int = 500) {
    private val map = object : LinkedHashMap<String, Unit>(capacity, 0.75f, false) {
        // Kotlin override of java.util.LinkedHashMap's protected eviction hook
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Unit>?): Boolean =
            size > capacity
    }

    /** @return true if the key was NOT seen before (i.e. packet is new). */
    @Synchronized
    fun checkAndAdd(key: String): Boolean {
        if (map.containsKey(key)) return false
        map[key] = Unit
        return true
    }

    val size: Int @Synchronized get() = map.size
}
```

- [ ] 4. Run `./gradlew :app:testDebugUnitTest --tests "*SeenSetTest*"`. Expected: PASS, 3 tests.
- [ ] 5. Write the failing test `FloodControllerTest.kt`:

```kotlin
package com.meshwalkie.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FloodControllerTest {

    private fun position(seq: Int, ttl: Int) =
        Packet.Position("origin1", seq, ttl, 1000L, 52.52, 13.405, 0f)

    @Test
    fun newPacketIsDeliveredAndForwardedWithTtlMinusOne() {
        val flood = FloodController()
        val result = flood.onReceive(position(seq = 1, ttl = 4))
        assertTrue(result.deliver)
        assertEquals(3, result.forward!!.ttl)
        assertEquals("origin1", result.forward!!.originId)
    }

    @Test
    fun duplicateIsDroppedEntirely() {
        val flood = FloodController()
        flood.onReceive(position(seq = 1, ttl = 4))
        val second = flood.onReceive(position(seq = 1, ttl = 4))
        assertFalse(second.deliver)
        assertNull(second.forward)
    }

    @Test
    fun ttlZeroIsDeliveredButNotForwarded() {
        val flood = FloodController()
        val result = flood.onReceive(position(seq = 2, ttl = 0))
        assertTrue(result.deliver)
        assertNull(result.forward)
    }

    @Test
    fun ownPacketMarkedSoEchoIsDropped() {
        val flood = FloodController()
        val mine = position(seq = 3, ttl = 4)
        flood.markOwn(mine)
        val echo = flood.onReceive(mine.withTtl(3)) // echo back from a neighbor
        assertFalse(echo.deliver)
        assertNull(echo.forward)
    }

    @Test
    fun voiceFramesDedupByClipAndFrameNotSeq() {
        val flood = FloodController()
        val f0 = Packet.Voice("o", 10, 4, 0L, clipId = 1, frameNum = 0, isLast = false, opusData = byteArrayOf(1))
        val f0again = Packet.Voice("o", 99, 4, 0L, clipId = 1, frameNum = 0, isLast = false, opusData = byteArrayOf(1))
        assertTrue(flood.onReceive(f0).deliver)
        assertFalse(flood.onReceive(f0again).deliver) // same clip+frame, different seq -> dup
    }
}
```

- [ ] 6. Run `./gradlew :app:testDebugUnitTest --tests "*FloodControllerTest*"`. Expected: FAIL - `Unresolved reference: FloodController`.
- [ ] 7. Write `FloodController.kt`:

```kotlin
package com.meshwalkie.core

/**
 * Controlled flooding: dedup via SeenSet, TTL decrement.
 * Seen -> drop. New + ttl>0 -> deliver and forward ttl-1.
 * New + ttl==0 -> deliver only.
 */
class FloodController(private val seen: SeenSet = SeenSet()) {

    data class Result(val deliver: Boolean, val forward: Packet?) {
        companion object { val DROP = Result(deliver = false, forward = null) }
    }

    fun onReceive(packet: Packet): Result {
        if (!seen.checkAndAdd(packet.dedupKey)) return Result.DROP
        val forward = if (packet.ttl > 0) packet.withTtl(packet.ttl - 1) else null
        return Result(deliver = true, forward = forward)
    }

    /** Mark a locally originated packet so echoes from neighbors are dropped. */
    fun markOwn(packet: Packet) {
        seen.checkAndAdd(packet.dedupKey)
    }
}
```

- [ ] 8. Run `./gradlew :app:testDebugUnitTest --tests "*FloodControllerTest*"`. Expected: PASS, 5 tests.
- [ ] 9. Commit: `git add -A && git commit -m "feat(core): LRU seen-set and TTL/dedup flood controller"`

---

### Task 6: PeerRegistry - state, distance/bearing snapshot, freshness

**Files:**
- Create: `app/src/main/java/com/meshwalkie/core/PeerRegistry.kt`
- Test: `app/src/test/java/com/meshwalkie/core/PeerRegistryTest.kt`

Steps:

- [ ] 1. Write the failing test `PeerRegistryTest.kt`:

```kotlin
package com.meshwalkie.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PeerRegistryTest {

    private val now = 1_000_000L

    private fun bobAtMunich(ts: Long) =
        Packet.Position("bob", 1, 4, ts, lat = 48.137, lon = 11.575, headingDeg = 90f)

    @Test
    fun positionPacketCreatesPeerWithDistanceAndBearing() {
        val reg = PeerRegistry()
        reg.onPacket(bobAtMunich(now), receivedAtMs = now)
        val views = reg.snapshot(myLat = 52.52, myLon = 13.405, nowMs = now)
        assertEquals(1, views.size)
        val bob = views[0]
        assertEquals("bob", bob.id)
        assertEquals(504337.9, bob.distanceMeters, 500.0)   // Berlin -> Munich
        assertEquals(195.634, bob.bearingDeg, 0.01)          // SSW
        assertEquals(Freshness.FRESH, bob.freshness)
    }

    @Test
    fun presencePacketSetsName() {
        val reg = PeerRegistry()
        reg.onPacket(bobAtMunich(now), receivedAtMs = now)
        reg.onPacket(Packet.Presence("bob", 2, 4, now, name = "Bob K", batteryPct = 80), receivedAtMs = now)
        assertEquals("Bob K", reg.snapshot(52.52, 13.405, now)[0].name)
    }

    @Test
    fun nameDefaultsToIdUntilPresenceArrives() {
        val reg = PeerRegistry()
        reg.onPacket(bobAtMunich(now), receivedAtMs = now)
        assertEquals("bob", reg.snapshot(52.52, 13.405, now)[0].name)
    }

    @Test
    fun freshnessAges() {
        val reg = PeerRegistry()
        reg.onPacket(bobAtMunich(now), receivedAtMs = now)
        assertEquals(Freshness.FRESH, reg.snapshot(52.52, 13.405, now + 29_999)[0].freshness)
        assertEquals(Freshness.AGING, reg.snapshot(52.52, 13.405, now + 30_000)[0].freshness)
        assertEquals(Freshness.AGING, reg.snapshot(52.52, 13.405, now + 119_999)[0].freshness)
        assertEquals(Freshness.STALE, reg.snapshot(52.52, 13.405, now + 120_000)[0].freshness)
    }

    @Test
    fun newerPositionWins() {
        val reg = PeerRegistry()
        reg.onPacket(bobAtMunich(now), receivedAtMs = now)
        reg.onPacket(
            Packet.Position("bob", 5, 4, now + 5000, lat = 52.62, lon = 13.205, headingDeg = 0f),
            receivedAtMs = now + 5000
        )
        val bob = reg.snapshot(52.52, 13.405, now + 5000)[0]
        assertEquals(17502.7, bob.distanceMeters, 50.0)  // 52.52,13.405 -> 52.62,13.205
        assertEquals(309.522, bob.bearingDeg, 0.01)       // NW
    }

    @Test
    fun voicePacketsRefreshLastSeenButNotPosition() {
        val reg = PeerRegistry()
        reg.onPacket(bobAtMunich(now), receivedAtMs = now)
        reg.onPacket(
            Packet.Voice("bob", 9, 4, now + 100_000, 1, 0, false, byteArrayOf(1)),
            receivedAtMs = now + 100_000
        )
        val bob = reg.snapshot(52.52, 13.405, now + 100_000)[0]
        assertEquals(Freshness.FRESH, bob.freshness)
        assertEquals(504337.9, bob.distanceMeters, 500.0) // position unchanged
    }

    @Test
    fun snapshotSortedByDistance() {
        val reg = PeerRegistry()
        reg.onPacket(bobAtMunich(now), receivedAtMs = now)
        reg.onPacket(
            Packet.Position("near", 1, 4, now, lat = 52.62, lon = 13.205, headingDeg = 0f),
            receivedAtMs = now
        )
        val views = reg.snapshot(52.52, 13.405, now)
        assertEquals(listOf("near", "bob"), views.map { it.id })
        assertTrue(views[0].distanceMeters < views[1].distanceMeters)
    }
}
```

- [ ] 2. Run `./gradlew :app:testDebugUnitTest --tests "*PeerRegistryTest*"`. Expected: FAIL - unresolved references.
- [ ] 3. Write `PeerRegistry.kt`:

```kotlin
package com.meshwalkie.core

/** Freshness dot: green <30 s, yellow <2 min, red stale. */
enum class Freshness { FRESH, AGING, STALE }

/** What the UI renders per peer. */
data class PeerView(
    val id: String,
    val name: String,
    val distanceMeters: Double,
    val bearingDeg: Double,
    val freshness: Freshness
)

/**
 * Who exists, where, how fresh. Fed by delivered packets,
 * queried by the UI with my current location.
 */
class PeerRegistry {
    private data class PeerState(
        var name: String? = null,
        var lat: Double? = null,
        var lon: Double? = null,
        var positionTimestampMs: Long = 0L,
        var lastSeenMs: Long = 0L
    )

    private val peers = LinkedHashMap<String, PeerState>()

    companion object {
        const val FRESH_MS = 30_000L
        const val AGING_MS = 120_000L
    }

    @Synchronized
    fun onPacket(packet: Packet, receivedAtMs: Long) {
        val state = peers.getOrPut(packet.originId) { PeerState() }
        state.lastSeenMs = maxOf(state.lastSeenMs, receivedAtMs)
        when (packet) {
            is Packet.Position -> if (packet.timestampMs >= state.positionTimestampMs) {
                state.lat = packet.lat
                state.lon = packet.lon
                state.positionTimestampMs = packet.timestampMs
            }
            is Packet.Presence -> state.name = packet.name
            is Packet.Voice -> Unit // lastSeen already refreshed
        }
    }

    /** Peers with a known position, nearest first. */
    @Synchronized
    fun snapshot(myLat: Double, myLon: Double, nowMs: Long): List<PeerView> =
        peers.mapNotNull { (id, s) ->
            val lat = s.lat ?: return@mapNotNull null
            val lon = s.lon ?: return@mapNotNull null
            val age = nowMs - s.lastSeenMs
            PeerView(
                id = id,
                name = s.name ?: id,
                distanceMeters = GeoMath.distanceMeters(myLat, myLon, lat, lon),
                bearingDeg = GeoMath.bearingDegrees(myLat, myLon, lat, lon),
                freshness = when {
                    age < FRESH_MS -> Freshness.FRESH
                    age < AGING_MS -> Freshness.AGING
                    else -> Freshness.STALE
                }
            )
        }.sortedBy { it.distanceMeters }
}
```

- [ ] 4. Run `./gradlew :app:testDebugUnitTest --tests "*PeerRegistryTest*"`. Expected: PASS, 7 tests.
- [ ] 5. Commit: `git add -A && git commit -m "feat(core): peer registry with distance, bearing, freshness"`

---

### Task 7: TransportRouter - per-peer mesh-vs-server decision

Phase 1 wiring only uses MESH, but the decision logic is pure core and gets tested now so phase 2 plugs in without touching tested code.

**Files:**
- Create: `app/src/main/java/com/meshwalkie/core/TransportRouter.kt`
- Test: `app/src/test/java/com/meshwalkie/core/TransportRouterTest.kt`

Steps:

- [ ] 1. Write the failing test `TransportRouterTest.kt`:

```kotlin
package com.meshwalkie.core

import org.junit.Assert.assertEquals
import org.junit.Test

class TransportRouterTest {

    @Test
    fun peerHeardOnMeshRecentlyRoutesMesh() {
        val router = TransportRouter()
        router.noteMeshSeen("bob", nowMs = 1_000L)
        assertEquals(Route.MESH, router.routeFor("bob", nowMs = 20_999L)) // 19.999 s ago
    }

    @Test
    fun peerSilentOnMeshOver20sRoutesServer() {
        val router = TransportRouter()
        router.noteMeshSeen("bob", nowMs = 1_000L)
        assertEquals(Route.SERVER, router.routeFor("bob", nowMs = 21_001L)) // 20.001 s ago
    }

    @Test
    fun unknownPeerRoutesServer() {
        assertEquals(Route.SERVER, TransportRouter().routeFor("ghost", nowMs = 0L))
    }

    @Test
    fun decisionIsPerPeer() {
        val router = TransportRouter()
        router.noteMeshSeen("near", nowMs = 100_000L)
        router.noteMeshSeen("far", nowMs = 10_000L)
        assertEquals(Route.MESH, router.routeFor("near", nowMs = 105_000L))
        assertEquals(Route.SERVER, router.routeFor("far", nowMs = 105_000L))
    }
}
```

- [ ] 2. Run `./gradlew :app:testDebugUnitTest --tests "*TransportRouterTest*"`. Expected: FAIL - unresolved references.
- [ ] 3. Write `TransportRouter.kt`:

```kotlin
package com.meshwalkie.core

enum class Route { MESH, SERVER }

/**
 * Mesh always preferred, per-peer: heard on mesh within [meshTimeoutMs] -> MESH,
 * otherwise SERVER. Phase 1 has no ServerTransport; callers treat SERVER as
 * "unreachable for direct addressing" and still flood the mesh.
 */
class TransportRouter(private val meshTimeoutMs: Long = 20_000L) {
    private val meshLastSeen = HashMap<String, Long>()

    @Synchronized
    fun noteMeshSeen(peerId: String, nowMs: Long) {
        meshLastSeen[peerId] = nowMs
    }

    @Synchronized
    fun routeFor(peerId: String, nowMs: Long): Route {
        val last = meshLastSeen[peerId] ?: return Route.SERVER
        return if (nowMs - last <= meshTimeoutMs) Route.MESH else Route.SERVER
    }
}
```

- [ ] 4. Run `./gradlew :app:testDebugUnitTest --tests "*TransportRouterTest*"`. Expected: PASS, 4 tests.
- [ ] 5. Commit: `git add -A && git commit -m "feat(core): per-peer transport router, mesh preferred"`

---

### Task 8: Transport interface + MeshEngine + fake-node relay tests

The heart of the mesh, proven without radios: 3+ fake nodes chained, packets hop, dedup kills loops, TTL caps reach.

**Files:**
- Create: `app/src/main/java/com/meshwalkie/core/Transport.kt`, `app/src/main/java/com/meshwalkie/core/MeshEngine.kt`
- Test: `app/src/test/java/com/meshwalkie/core/FakeTransport.kt`, `app/src/test/java/com/meshwalkie/core/MeshEngineTest.kt`

Steps:

- [ ] 1. Write `Transport.kt` (interface first - it has no behavior to test):

```kotlin
package com.meshwalkie.core

/**
 * A broadcast medium. NearbyTransport (radio) and FakeTransport (tests)
 * both implement this; MeshEngine only ever sees this interface.
 */
interface Transport {
    /** Send bytes to every directly connected link. */
    fun broadcast(bytes: ByteArray)

    /** Register the single receive handler. Called once by MeshEngine. */
    fun onReceive(handler: (ByteArray) -> Unit)
}
```

- [ ] 2. Write the test-double `FakeTransport.kt` in the TEST source set:

```kotlin
package com.meshwalkie.core

/**
 * In-memory transport. Linked fakes deliver synchronously - a broadcast
 * recursively triggers neighbor handlers, so loops are real and only
 * dedup/TTL stop them. That is exactly what we want to prove.
 */
class FakeTransport : Transport {
    private val links = mutableListOf<FakeTransport>()
    private var handler: ((ByteArray) -> Unit)? = null

    override fun broadcast(bytes: ByteArray) {
        links.forEach { it.handler?.invoke(bytes) }
    }

    override fun onReceive(handler: (ByteArray) -> Unit) {
        this.handler = handler
    }

    companion object {
        fun link(a: FakeTransport, b: FakeTransport) {
            a.links += b
            b.links += a
        }
    }
}
```

- [ ] 3. Write the failing test `MeshEngineTest.kt`:

```kotlin
package com.meshwalkie.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MeshEngineTest {

    private class Node(val id: String) {
        val transport = FakeTransport()
        val engine = MeshEngine(transport)
        val delivered = mutableListOf<Packet>()
        init { engine.start { delivered += it } }
    }

    private fun position(origin: String, seq: Int, ttl: Int) =
        Packet.Position(origin, seq, ttl, 0L, 52.52, 13.405, 0f)

    @Test
    fun packetHopsAcrossThreeChainedNodes() {
        // A - B - C : A and C are NOT linked, C only reachable via B's relay
        val a = Node("A"); val b = Node("B"); val c = Node("C")
        FakeTransport.link(a.transport, b.transport)
        FakeTransport.link(b.transport, c.transport)

        a.engine.send(position("A", seq = 1, ttl = 4))

        assertEquals(1, b.delivered.size)
        assertEquals(1, c.delivered.size)              // hopped through B
        assertEquals("A", c.delivered[0].originId)
        // sender broadcasts ttl 4 unmodified; B delivers ttl 4, forwards ttl 3
        assertEquals(3, c.delivered[0].ttl)
    }

    @Test
    fun dedupDeliversExactlyOnceInATriangleLoop() {
        // A - B, B - C, C - A : full loop, flooding must not storm
        val a = Node("A"); val b = Node("B"); val c = Node("C")
        FakeTransport.link(a.transport, b.transport)
        FakeTransport.link(b.transport, c.transport)
        FakeTransport.link(c.transport, a.transport)

        a.engine.send(position("A", seq = 1, ttl = 4))

        assertEquals(1, b.delivered.size)   // exactly once despite two paths
        assertEquals(1, c.delivered.size)
        assertEquals(0, a.delivered.size)   // own echo dropped, never self-delivered
    }

    @Test
    fun ttlStopsForwardingAtTheEdge() {
        // A - B - C - D chain, ttl=1: B receives ttl1 + forwards ttl0,
        // C receives ttl0 + delivers but does NOT forward, D hears nothing.
        val a = Node("A"); val b = Node("B"); val c = Node("C"); val d = Node("D")
        FakeTransport.link(a.transport, b.transport)
        FakeTransport.link(b.transport, c.transport)
        FakeTransport.link(c.transport, d.transport)

        a.engine.send(position("A", seq = 1, ttl = 1))

        assertEquals(1, b.delivered.size)
        assertEquals(1, c.delivered.size)
        assertEquals(0, c.delivered[0].ttl)
        assertTrue(d.delivered.isEmpty())
    }

    @Test
    fun voiceFramesRelayAndDedupByClipFrame() {
        val a = Node("A"); val b = Node("B"); val c = Node("C")
        FakeTransport.link(a.transport, b.transport)
        FakeTransport.link(b.transport, c.transport)

        val frame = Packet.Voice("A", 1, 4, 0L, clipId = 7, frameNum = 0,
            isLast = false, opusData = byteArrayOf(10, 20, 30))
        a.engine.send(frame)
        a.engine.send(frame.copy(seqNum = 2))  // re-send same clip+frame -> dup at receivers

        assertEquals(1, c.delivered.size)
        val v = c.delivered[0] as Packet.Voice
        assertEquals(7, v.clipId)
        assertTrue(byteArrayOf(10, 20, 30).contentEquals(v.opusData))
    }

    @Test
    fun corruptBytesAreIgnoredNotFatal() {
        val a = Node("A"); val b = Node("B")
        FakeTransport.link(a.transport, b.transport)
        a.transport.broadcast(byteArrayOf(99, 1, 2))   // garbage straight onto the wire
        assertTrue(b.delivered.isEmpty())              // no crash, no delivery
    }
}
```

- [ ] 4. Run `./gradlew :app:testDebugUnitTest --tests "*MeshEngineTest*"`. Expected: FAIL - `Unresolved reference: MeshEngine`.
- [ ] 5. Write `MeshEngine.kt`:

```kotlin
package com.meshwalkie.core

/**
 * Glues one Transport to the flood logic.
 * send(): mark own (kills echoes) + broadcast.
 * receive: decode -> flood decision -> deliver locally and/or re-broadcast ttl-1.
 */
class MeshEngine(
    private val transport: Transport,
    private val flood: FloodController = FloodController()
) {
    private var onDeliver: (Packet) -> Unit = {}

    fun start(onDeliver: (Packet) -> Unit) {
        this.onDeliver = onDeliver
        transport.onReceive { bytes ->
            val packet = try {
                PacketCodec.decode(bytes)
            } catch (e: IllegalArgumentException) {
                return@onReceive // corrupt frame off the radio - drop silently
            }
            val result = flood.onReceive(packet)
            if (result.deliver) onDeliver(packet)
            result.forward?.let { transport.broadcast(PacketCodec.encode(it)) }
        }
    }

    fun send(packet: Packet) {
        flood.markOwn(packet)
        transport.broadcast(PacketCodec.encode(packet))
    }
}
```

- [ ] 6. Run `./gradlew :app:testDebugUnitTest --tests "*MeshEngineTest*"`. Expected: PASS, 5 tests. This is the spec's "test mesh relay with 3+ fake nodes wired in a chain: verify a packet hops, dedups, respects TTL" - done with zero radio code.
- [ ] 7. Run the whole core suite once: `./gradlew :app:testDebugUnitTest`. Expected: PASS, all tests green.
- [ ] 8. Commit: `git add -A && git commit -m "feat(core): transport interface and mesh engine with fake-node relay proof"`

---

### Task 9: Pure stream helpers - HeadingFilter, ReorderBuffer, VoiceFramer

Three small JVM-tested classes the Android layers will consume unchanged.

**Files:**
- Create: `app/src/main/java/com/meshwalkie/core/HeadingFilter.kt`, `app/src/main/java/com/meshwalkie/core/ReorderBuffer.kt`, `app/src/main/java/com/meshwalkie/core/VoiceFramer.kt`
- Test: `app/src/test/java/com/meshwalkie/core/HeadingFilterTest.kt`, `app/src/test/java/com/meshwalkie/core/ReorderBufferTest.kt`, `app/src/test/java/com/meshwalkie/core/VoiceFramerTest.kt`

Steps:

- [ ] 1. Write the failing test `HeadingFilterTest.kt`:

```kotlin
package com.meshwalkie.core

import org.junit.Assert.assertEquals
import org.junit.Test

class HeadingFilterTest {

    @Test
    fun firstSampleIsReturnedAsIs() {
        assertEquals(123f, HeadingFilter(alpha = 0.5f).update(123f), 0.001f)
    }

    @Test
    fun smoothsTowardNewValue() {
        val f = HeadingFilter(alpha = 0.5f)
        f.update(0f)
        assertEquals(5f, f.update(10f), 0.001f)     // 0 + 0.5 * 10
        assertEquals(7.5f, f.update(10f), 0.001f)   // 5 + 0.5 * 5
    }

    @Test
    fun wrapsAcrossNorthUpward() {
        val f = HeadingFilter(alpha = 0.5f)
        f.update(350f)
        // shortest path 350 -> 10 is +20 degrees, NOT -340
        assertEquals(0f, f.update(10f), 0.001f)     // 350 + 0.5 * 20 = 360 -> 0
    }

    @Test
    fun wrapsAcrossNorthDownward() {
        val f = HeadingFilter(alpha = 0.5f)
        f.update(10f)
        assertEquals(0f, f.update(350f), 0.001f)    // 10 + 0.5 * (-20) = 0
    }

    @Test
    fun outputAlwaysInZeroTo360() {
        val f = HeadingFilter(alpha = 1.0f)
        f.update(359f)
        val v = f.update(2f)
        assertEquals(2f, v, 0.001f)
    }
}
```

- [ ] 2. Run `./gradlew :app:testDebugUnitTest --tests "*HeadingFilterTest*"`. Expected: FAIL. Then write `HeadingFilter.kt`:

```kotlin
package com.meshwalkie.core

/**
 * Exponential low-pass for compass heading with 360-degree wraparound,
 * so the arrow glides instead of vibrating. alpha in (0,1]: higher = snappier.
 */
class HeadingFilter(private val alpha: Float = 0.15f) {
    private var filtered: Float? = null

    fun update(rawDeg: Float): Float {
        val current = filtered
        val next = if (current == null) {
            normalize(rawDeg)
        } else {
            // shortest signed angular difference in (-180, 180]
            val delta = ((rawDeg - current + 540f) % 360f) - 180f
            normalize(current + alpha * delta)
        }
        filtered = next
        return next
    }

    private fun normalize(deg: Float): Float = ((deg % 360f) + 360f) % 360f
}
```

- [ ] 3. Run `./gradlew :app:testDebugUnitTest --tests "*HeadingFilterTest*"`. Expected: PASS, 5 tests.
- [ ] 4. Write the failing test `ReorderBufferTest.kt`:

```kotlin
package com.meshwalkie.core

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReorderBufferTest {

    @Test
    fun inOrderFramesPassStraightThrough() {
        val buf = ReorderBuffer()
        assertEquals(1, buf.offer(0, byteArrayOf(0)).size)
        assertEquals(1, buf.offer(1, byteArrayOf(1)).size)
    }

    @Test
    fun outOfOrderFrameIsHeldUntilGapFills() {
        val buf = ReorderBuffer()
        assertTrue(buf.offer(1, byteArrayOf(1)).isEmpty())       // hole at 0
        val released = buf.offer(0, byteArrayOf(0))               // fills hole
        assertEquals(2, released.size)
        assertArrayEquals(byteArrayOf(0), released[0])
        assertArrayEquals(byteArrayOf(1), released[1])
    }

    @Test
    fun scrambledClipComesOutOrdered() {
        val buf = ReorderBuffer()
        val out = mutableListOf<ByteArray>()
        listOf(2, 0, 3, 1).forEach { n -> out += buf.offer(n, byteArrayOf(n.toByte())) }
        assertEquals(listOf<Byte>(0, 1, 2, 3), out.map { it[0] })
    }

    @Test
    fun duplicatesAndAncientFramesAreDropped() {
        val buf = ReorderBuffer()
        buf.offer(0, byteArrayOf(0))
        assertTrue(buf.offer(0, byteArrayOf(0)).isEmpty())   // already played
        buf.offer(2, byteArrayOf(2))                          // held
        assertTrue(buf.offer(2, byteArrayOf(2)).isEmpty())   // duplicate of held
    }
}
```

- [ ] 5. Run `./gradlew :app:testDebugUnitTest --tests "*ReorderBufferTest*"`. Expected: FAIL. Then write `ReorderBuffer.kt`:

```kotlin
package com.meshwalkie.core

/**
 * Per-clip frame reordering: voice frames may arrive out of order over
 * multi-path flooding. offer() returns every frame now ready to play, in order.
 */
class ReorderBuffer {
    private var nextFrame = 0
    private val pending = sortedMapOf<Int, ByteArray>()

    fun offer(frameNum: Int, data: ByteArray): List<ByteArray> {
        if (frameNum < nextFrame || pending.containsKey(frameNum)) return emptyList()
        pending[frameNum] = data
        val ready = mutableListOf<ByteArray>()
        while (pending.containsKey(nextFrame)) {
            ready += pending.remove(nextFrame)!!
            nextFrame++
        }
        return ready
    }
}
```

- [ ] 6. Run `./gradlew :app:testDebugUnitTest --tests "*ReorderBufferTest*"`. Expected: PASS, 4 tests.
- [ ] 7. Write the failing test `VoiceFramerTest.kt`:

```kotlin
package com.meshwalkie.core

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class VoiceFramerTest {

    // 3 x 20 ms packets per frame for short tests
    private fun framer() = VoiceFramer(frameDurationMs = 60, packetDurationMs = 20)

    @Test
    fun emitsFrameWhenDurationReached() {
        val f = framer()
        assertNull(f.add(byteArrayOf(1)))
        assertNull(f.add(byteArrayOf(2, 2)))
        val frame = f.add(byteArrayOf(3, 3, 3))
        assertNotNull(frame)
    }

    @Test
    fun flushReturnsRemainderOrNull() {
        val f = framer()
        f.add(byteArrayOf(1))
        assertNotNull(f.flush())
        assertNull(f.flush())   // empty now
    }

    @Test
    fun unpackRestoresOriginalPackets() {
        val f = framer()
        f.add(byteArrayOf(1))
        f.add(byteArrayOf(2, 2))
        val frame = f.add(byteArrayOf(3, 3, 3))!!
        val packets = VoiceFramer.unpack(frame)
        assertEquals(3, packets.size)
        assertArrayEquals(byteArrayOf(1), packets[0])
        assertArrayEquals(byteArrayOf(2, 2), packets[1])
        assertArrayEquals(byteArrayOf(3, 3, 3), packets[2])
    }

    @Test
    fun defaultIsOneSecondFrames() {
        val f = VoiceFramer() // 1000 ms / 20 ms = 50 packets per frame
        repeat(49) { assertNull(f.add(byteArrayOf(0))) }
        assertNotNull(f.add(byteArrayOf(0)))
    }
}
```

- [ ] 8. Run `./gradlew :app:testDebugUnitTest --tests "*VoiceFramerTest*"`. Expected: FAIL. Then write `VoiceFramer.kt`:

```kotlin
package com.meshwalkie.core

import java.nio.ByteBuffer

/**
 * Groups 20 ms Opus packets into ~1 s mesh frames so long clips stream
 * through hops instead of waiting for the whole clip.
 * Frame layout: repeated [len 2B big-endian][opus packet bytes].
 */
class VoiceFramer(
    frameDurationMs: Int = 1000,
    packetDurationMs: Int = 20
) {
    private val packetsPerFrame = frameDurationMs / packetDurationMs
    private val pending = mutableListOf<ByteArray>()

    /** @return a packed frame when full, else null. */
    fun add(opusPacket: ByteArray): ByteArray? {
        pending += opusPacket
        return if (pending.size >= packetsPerFrame) packAndClear() else null
    }

    /** @return the final partial frame, or null if nothing pending. */
    fun flush(): ByteArray? = if (pending.isEmpty()) null else packAndClear()

    private fun packAndClear(): ByteArray {
        val size = pending.sumOf { 2 + it.size }
        val buf = ByteBuffer.allocate(size)
        pending.forEach { buf.putShort(it.size.toShort()); buf.put(it) }
        pending.clear()
        return buf.array()
    }

    companion object {
        fun unpack(frame: ByteArray): List<ByteArray> {
            val buf = ByteBuffer.wrap(frame)
            val packets = mutableListOf<ByteArray>()
            while (buf.remaining() >= 2) {
                val len = buf.getShort().toInt() and 0xFFFF
                packets += ByteArray(len).also { buf.get(it) }
            }
            return packets
        }
    }
}
```

- [ ] 9. Run `./gradlew :app:testDebugUnitTest --tests "*VoiceFramerTest*"` then the full suite `./gradlew :app:testDebugUnitTest`. Expected: PASS (4 new tests; everything green). The pure core is now complete and fully tested.
- [ ] 10. Commit: `git add -A && git commit -m "feat(core): heading low-pass, frame reorder buffer, voice framer"`

---

### Task 10: NearbyTransport - real radio behind the tested interface

> context7 note: `/google/nearby` documents only the C++ Core API. The Android binding used here (`com.google.android.gms.nearby.connection.*` from `play-services-nearby:19.3.0`) has no context7 entry; method names below follow the documented Android API, whose flow matches the C++ docs fetched above (advertise + discover -> requestConnection -> onConnectionInitiated -> acceptConnection -> payload callbacks).

**Files:**
- Create: `app/src/main/java/com/meshwalkie/nearby/NearbyTransport.kt`, `app/src/main/java/com/meshwalkie/service/DeviceId.kt`

Radio cannot be JVM-tested (spec: "Radio can't be unit-tested"); the verification step is compilation + the Task 15 field test. All mesh logic this class feeds was already proven in Task 8.

Steps:

- [ ] 1. Write `DeviceId.kt`:

```kotlin
package com.meshwalkie.service

import android.content.Context
import android.os.Build

/** Stable random id + human name, persisted across launches. */
object DeviceId {
    private const val PREFS = "meshwalkie"
    private const val KEY_ID = "device_id"

    fun get(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getString(KEY_ID, null)?.let { return it }
        val id = List(8) { "0123456789abcdef".random() }.joinToString("")
        prefs.edit().putString(KEY_ID, id).apply()
        return id
    }

    fun displayName(context: Context): String = "${Build.MODEL}-${get(context).take(4)}"
}
```

- [ ] 2. Write `NearbyTransport.kt`:

```kotlin
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
    private var handler: ((ByteArray) -> Unit)? = null

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
```

- [ ] 3. Verify it compiles: `./gradlew :app:assembleDebug`. Expected: `BUILD SUCCESSFUL`.
- [ ] 4. Run the core suite to prove nothing regressed: `./gradlew :app:testDebugUnitTest`. Expected: PASS.
- [ ] 5. Commit: `git add -A && git commit -m "feat(nearby): P2P_CLUSTER transport over Nearby Connections"`

---

### Task 11: Location + heading sources

> context7 grounding: `LocationCallback.onLocationResult(LocationResult)` confirmed; `SensorManager` acquisition via `getSystemService(Context.SENSOR_SERVICE)`, `registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)` and `unregisterListener` confirmed. context7 had no entry for `LocationRequest.Builder` or the `getRotationMatrixFromVector`/`getOrientation` statics; using the documented Android APIs: `LocationRequest.Builder(int priority, long intervalMillis).build()`, `Priority.PRIORITY_BALANCED_POWER_ACCURACY`, `SensorManager.getRotationMatrixFromVector(float[9], event.values)`, `SensorManager.getOrientation(float[9], float[3])` (orientation[0] = azimuth radians).

**Files:**
- Create: `app/src/main/java/com/meshwalkie/location/LocationSource.kt`, `app/src/main/java/com/meshwalkie/location/HeadingSource.kt`

The angle math (filtering, wraparound) was tested in Task 9; these classes are thin sensor adapters.

Steps:

- [ ] 1. Write `LocationSource.kt`:

```kotlin
package com.meshwalkie.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

/** FusedLocationProviderClient wrapper. Spec: balanced power, each fix -> POSITION packet. */
class LocationSource(context: Context) {

    private val client = LocationServices.getFusedLocationProviderClient(context)
    private var callback: LocationCallback? = null

    /** Caller (MeshService) holds ACCESS_FINE_LOCATION before calling. */
    @SuppressLint("MissingPermission")
    fun start(intervalMs: Long = 5_000L, onFix: (Location) -> Unit) {
        val request = LocationRequest.Builder(
            Priority.PRIORITY_BALANCED_POWER_ACCURACY, intervalMs
        ).build()
        val cb = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let(onFix)
            }
        }
        callback = cb
        client.requestLocationUpdates(request, cb, Looper.getMainLooper())
    }

    fun stop() {
        callback?.let { client.removeLocationUpdates(it) }
        callback = null
    }
}
```

- [ ] 2. Write `HeadingSource.kt`:

```kotlin
package com.meshwalkie.location

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.meshwalkie.core.HeadingFilter

/**
 * My heading = direction the phone faces, from TYPE_ROTATION_VECTOR
 * (fused magnetometer + accelerometer + gyro), low-pass filtered (Task 9)
 * so the arrow glides instead of vibrating.
 */
class HeadingSource(context: Context) : SensorEventListener {

    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val sensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val filter = HeadingFilter(alpha = 0.15f)
    private val rotationMatrix = FloatArray(9)
    private val orientation = FloatArray(3)
    private var onHeading: (Float) -> Unit = {}

    fun start(onHeading: (Float) -> Unit) {
        this.onHeading = onHeading
        sensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    fun stop() = sensorManager.unregisterListener(this)

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ROTATION_VECTOR) return
        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
        SensorManager.getOrientation(rotationMatrix, orientation)
        val azimuthDeg = Math.toDegrees(orientation[0].toDouble()).toFloat()
        onHeading(filter.update(((azimuthDeg % 360f) + 360f) % 360f))
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
```

- [ ] 3. Verify: `./gradlew :app:assembleDebug`. Expected: `BUILD SUCCESSFUL`.
- [ ] 4. Commit: `git add -A && git commit -m "feat(location): fused location source and filtered rotation-vector heading"`

---

### Task 12: OpusCodec - MediaCodec encode/decode at 16 kHz mono

> context7 note: no entry for MediaCodec Opus usage; using the documented Android MediaCodec API. Facts relied on: `MediaFormat.MIMETYPE_AUDIO_OPUS` = `"audio/opus"`; software Opus encoder (`c2.android.opus.encoder`) available since API 29 (our minSdk); MediaCodec CSD table requires the Opus decoder to get csd-0 = identification header (the encoder emits it as its codec-config output buffer), csd-1 = 8-byte pre-skip in nanoseconds, csd-2 = 8-byte seek pre-roll in nanoseconds (both native-order int64, zeros are accepted for raw packet streaming).

**Files:**
- Create: `app/src/main/java/com/meshwalkie/audio/OpusCodec.kt`

MediaCodec needs a device; verification is compile + the Task 13 loopback check on hardware.

Steps:

- [ ] 1. Write `OpusCodec.kt`:

```kotlin
package com.meshwalkie.audio

import android.media.MediaCodec
import android.media.MediaFormat
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Synchronous MediaCodec Opus wrapper. 16 kHz mono, 20 ms packets
 * (320 samples) - matches VoiceFramer's packetDurationMs = 20.
 */
class OpusCodec {

    data class Encoded(val config: ByteArray, val packets: List<ByteArray>)

    /** PCM16 -> Opus packets + the codec-config (identification header). */
    fun encode(pcm: ShortArray): Encoded {
        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_OPUS)
        val format = MediaFormat.createAudioFormat(
            MediaFormat.MIMETYPE_AUDIO_OPUS, SAMPLE_RATE, CHANNELS
        ).apply { setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE) }
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()

        var config = ByteArray(0)
        val packets = mutableListOf<ByteArray>()
        var inOffset = 0
        var inputDone = false

        try {
            while (true) {
                if (!inputDone) {
                    val inIdx = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (inIdx >= 0) {
                        val inBuf = codec.getInputBuffer(inIdx)!!
                        val samples = minOf(FRAME_SAMPLES, pcm.size - inOffset)
                        if (samples <= 0) {
                            codec.queueInputBuffer(
                                inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            inputDone = true
                        } else {
                            inBuf.order(ByteOrder.LITTLE_ENDIAN)
                            for (i in 0 until samples) inBuf.putShort(pcm[inOffset + i])
                            inOffset += samples
                            codec.queueInputBuffer(inIdx, 0, samples * 2, 0, 0)
                        }
                    }
                }
                val info = MediaCodec.BufferInfo()
                val outIdx = codec.dequeueOutputBuffer(info, TIMEOUT_US)
                if (outIdx >= 0) {
                    val out = ByteArray(info.size)
                    codec.getOutputBuffer(outIdx)!!.get(out)
                    codec.releaseOutputBuffer(outIdx, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                        config = out
                    } else if (info.size > 0) {
                        packets += out
                    }
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                }
            }
        } finally {
            codec.stop(); codec.release()
        }
        return Encoded(config, packets)
    }

    /** Opus packets -> PCM16. config = csd-0 captured by encode(). */
    fun decode(config: ByteArray, packets: List<ByteArray>): ShortArray {
        val codec = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_AUDIO_OPUS)
        val zeros64 = ByteBuffer.allocate(8).order(ByteOrder.nativeOrder()).putLong(0L)
            .apply { flip() }
        val format = MediaFormat.createAudioFormat(
            MediaFormat.MIMETYPE_AUDIO_OPUS, SAMPLE_RATE, CHANNELS
        ).apply {
            setByteBuffer("csd-0", ByteBuffer.wrap(config))
            setByteBuffer("csd-1", zeros64.duplicate())  // pre-skip ns = 0
            setByteBuffer("csd-2", zeros64.duplicate())  // seek pre-roll ns = 0
        }
        codec.configure(format, null, null, 0)
        codec.start()

        val pcmOut = mutableListOf<Short>()
        var packetIdx = 0
        var inputDone = false

        try {
            while (true) {
                if (!inputDone) {
                    val inIdx = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (inIdx >= 0) {
                        if (packetIdx >= packets.size) {
                            codec.queueInputBuffer(
                                inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            inputDone = true
                        } else {
                            val packet = packets[packetIdx++]
                            codec.getInputBuffer(inIdx)!!.put(packet)
                            codec.queueInputBuffer(inIdx, 0, packet.size, 0, 0)
                        }
                    }
                }
                val info = MediaCodec.BufferInfo()
                val outIdx = codec.dequeueOutputBuffer(info, TIMEOUT_US)
                if (outIdx >= 0) {
                    val buf = codec.getOutputBuffer(outIdx)!!.order(ByteOrder.LITTLE_ENDIAN)
                    repeat(info.size / 2) { pcmOut += buf.short }
                    codec.releaseOutputBuffer(outIdx, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                }
            }
        } finally {
            codec.stop(); codec.release()
        }
        return pcmOut.toShortArray()
    }

    companion object {
        const val SAMPLE_RATE = 16_000
        const val CHANNELS = 1
        const val FRAME_SAMPLES = 320      // 20 ms at 16 kHz
        const val BIT_RATE = 24_000
        private const val TIMEOUT_US = 10_000L
    }
}
```

- [ ] 2. Verify: `./gradlew :app:assembleDebug`. Expected: `BUILD SUCCESSFUL`.
- [ ] 3. Commit: `git add -A && git commit -m "feat(audio): MediaCodec opus encode/decode, 16 kHz mono 20 ms packets"`

---

### Task 13: PTT pipeline - record, frame, send; receive, reorder, play

**Files:**
- Create: `app/src/main/java/com/meshwalkie/audio/PttRecorder.kt`, `app/src/main/java/com/meshwalkie/audio/VoiceSender.kt`, `app/src/main/java/com/meshwalkie/audio/VoicePlayer.kt`

Flow per spec: hold -> record -> Opus encode -> chunk into ~1 s frames -> each frame = VOICE packet -> flood. Receive: dedup (MeshEngine) -> reorder by frameNum (Task 9) -> decode -> play. Frame 0 of every clip carries the encoder's codec-config so any receiver can configure its decoder.

Steps:

- [ ] 1. Write `PttRecorder.kt`:

```kotlin
package com.meshwalkie.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder

/**
 * Captures PCM16 mono 16 kHz from the mic while PTT is held.
 * Blocking - run on a background thread/dispatcher.
 */
class PttRecorder {

    /** Caller (MeshService) holds RECORD_AUDIO before calling. */
    @SuppressLint("MissingPermission")
    fun record(isHeld: () -> Boolean, maxMs: Int = 15_000): ShortArray {
        val minBuf = AudioRecord.getMinBufferSize(
            OpusCodec.SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            OpusCodec.SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minBuf, OpusCodec.FRAME_SAMPLES * 2 * 4)
        )
        val maxSamples = OpusCodec.SAMPLE_RATE * maxMs / 1000
        val pcm = ArrayList<Short>(maxSamples)
        val chunk = ShortArray(OpusCodec.FRAME_SAMPLES)
        try {
            recorder.startRecording()
            while (isHeld() && pcm.size < maxSamples) {
                val read = recorder.read(chunk, 0, chunk.size)
                for (i in 0 until read) pcm += chunk[i]
            }
        } finally {
            recorder.stop()
            recorder.release()
        }
        return pcm.toShortArray()
    }
}
```

- [ ] 2. Write `VoiceSender.kt`:

```kotlin
package com.meshwalkie.audio

import com.meshwalkie.core.MeshEngine
import com.meshwalkie.core.Packet
import com.meshwalkie.core.VoiceFramer

/**
 * Clip -> Opus -> 1 s frames -> Voice packets onto the mesh.
 * Frame 0 = codec config alone; frames 1..n = packed Opus; last flagged isLast.
 */
class VoiceSender(
    private val engine: MeshEngine,
    private val originId: String,
    private val nextSeq: () -> Int,
    private val codec: OpusCodec = OpusCodec()
) {
    private var clipCounter = 0

    fun sendClip(pcm: ShortArray, nowMs: Long) {
        if (pcm.isEmpty()) return
        val clipId = clipCounter++
        val encoded = codec.encode(pcm)

        val frames = mutableListOf<ByteArray>()
        val framer = VoiceFramer() // 1 s frames of 20 ms packets
        encoded.packets.forEach { packet -> framer.add(packet)?.let { frames += it } }
        framer.flush()?.let { frames += it }

        var frameNum = 0
        fun emit(data: ByteArray, isLast: Boolean) {
            engine.send(
                Packet.Voice(
                    originId = originId, seqNum = nextSeq(), ttl = Packet.DEFAULT_TTL,
                    timestampMs = nowMs, clipId = clipId, frameNum = frameNum++,
                    isLast = isLast, opusData = data
                )
            )
        }
        emit(encoded.config, isLast = frames.isEmpty())
        frames.forEachIndexed { i, frame -> emit(frame, isLast = i == frames.lastIndex) }
    }
}
```

- [ ] 3. Write `VoicePlayer.kt`:

```kotlin
package com.meshwalkie.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.meshwalkie.core.Packet
import com.meshwalkie.core.ReorderBuffer
import com.meshwalkie.core.VoiceFramer
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Receives delivered Voice packets (already dedup'd by MeshEngine),
 * reorders per clip, decodes once the clip completes, plays via AudioTrack.
 * Store-and-forward per spec - playback starts at end-of-clip.
 */
class VoicePlayer(private val codec: OpusCodec = OpusCodec()) {

    private class ClipState {
        val reorder = ReorderBuffer()
        var config: ByteArray? = null
        val packets = mutableListOf<ByteArray>()
        var lastFrameSeen = false
    }

    private val clips = HashMap<String, ClipState>()

    /** Feed every delivered Packet.Voice here. Plays when the clip is complete. */
    @Synchronized
    fun onVoicePacket(v: Packet.Voice) {
        val key = "${v.originId}:${v.clipId}"
        val clip = clips.getOrPut(key) { ClipState() }
        if (v.isLast) clip.lastFrameSeen = true
        for (frame in clip.reorder.offer(v.frameNum, v.opusData)) {
            if (clip.config == null) clip.config = frame          // frame 0 = codec config
            else clip.packets += VoiceFramer.unpack(frame)
        }
        if (clip.lastFrameSeen && clip.config != null) {
            val config = clip.config!!
            val packets = clip.packets.toList()
            clips.remove(key)
            Thread { play(codec.decode(config, packets)) }.start()
        }
    }

    private fun play(pcm: ShortArray) {
        if (pcm.isEmpty()) return
        val bytes = ByteBuffer.allocate(pcm.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        pcm.forEach { bytes.putShort(it) }
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(OpusCodec.SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(pcm.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        track.write(bytes.array(), 0, bytes.capacity())
        track.play()
        // MODE_STATIC: release after playback duration
        Thread.sleep(pcm.size * 1000L / OpusCodec.SAMPLE_RATE + 200L)
        track.release()
    }
}
```

- [ ] 4. Verify: `./gradlew :app:assembleDebug` and `./gradlew :app:testDebugUnitTest`. Expected: both `BUILD SUCCESSFUL`, tests green (the reorder/framer logic these classes lean on is Task 9 tested).
- [ ] 5. Commit: `git add -A && git commit -m "feat(audio): ptt recorder, voice sender, store-and-forward player"`

---

### Task 14: Compose UI - peer rows, rotating arrow, PTT button

> context7 grounding: `Canvas(modifier) { }` DrawScope, `rotate(degrees = ...) { draw... }` transform, `Modifier.pointerInput(Unit) { detect*Gestures }`, `collectAsStateWithLifecycle()` from `androidx.lifecycle:lifecycle-runtime-compose:2.10.0` - all confirmed.

**Files:**
- Create: `app/src/main/java/com/meshwalkie/service/MeshBus.kt`, `app/src/main/java/com/meshwalkie/ui/ArrowIcon.kt`, `app/src/main/java/com/meshwalkie/ui/PttButton.kt`, `app/src/main/java/com/meshwalkie/ui/PeerListScreen.kt`

Steps:

- [ ] 1. Write `MeshBus.kt` - the service/UI bridge:

```kotlin
package com.meshwalkie.service

import com.meshwalkie.core.PeerView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Process-wide bridge: MeshService writes, Compose reads.
 * pttHandler is set by the service; UI calls it with pressed=true/false.
 */
object MeshBus {
    private val _peers = MutableStateFlow<List<PeerView>>(emptyList())
    val peers: StateFlow<List<PeerView>> = _peers

    private val _myHeading = MutableStateFlow(0f)
    val myHeading: StateFlow<Float> = _myHeading

    @Volatile var pttHandler: ((pressed: Boolean) -> Unit)? = null

    fun publishPeers(views: List<PeerView>) { _peers.value = views }
    fun publishHeading(deg: Float) { _myHeading.value = deg }
}
```

- [ ] 2. Write `ArrowIcon.kt`:

```kotlin
package com.meshwalkie.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp

/** Arrow pointing up at rotationDeg = 0; rotated by bearingToPeer - myHeading. */
@Composable
fun ArrowIcon(rotationDeg: Float, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(40.dp)) {
        rotate(degrees = rotationDeg) {
            val w = size.width
            val h = size.height
            val path = Path().apply {
                moveTo(w * 0.5f, h * 0.08f)   // tip
                lineTo(w * 0.82f, h * 0.85f)  // right tail
                lineTo(w * 0.5f, h * 0.65f)   // notch
                lineTo(w * 0.18f, h * 0.85f)  // left tail
                close()
            }
            drawPath(path, color = Color(0xFF1565C0))
            drawCircle(
                color = Color(0x331565C0),
                radius = w * 0.5f,
                center = Offset(w * 0.5f, h * 0.5f)
            )
        }
    }
}
```

- [ ] 3. Write `PttButton.kt`:

```kotlin
package com.meshwalkie.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

/** Press-and-hold: onPtt(true) on press, onPtt(false) on release/cancel. */
@Composable
fun PttButton(onPtt: (pressed: Boolean) -> Unit, modifier: Modifier = Modifier) {
    var held by remember { mutableStateOf(false) }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(120.dp)
            .background(
                color = if (held) Color(0xFFD32F2F) else Color(0xFF388E3C),
                shape = CircleShape
            )
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        held = true
                        onPtt(true)
                        tryAwaitRelease()   // suspends until finger lifts or cancels
                        held = false
                        onPtt(false)
                    }
                )
            }
    ) {
        Text(
            text = if (held) "ON AIR" else "HOLD TO TALK",
            color = Color.White,
            style = MaterialTheme.typography.labelLarge
        )
    }
}
```

- [ ] 4. Write `PeerListScreen.kt`:

```kotlin
package com.meshwalkie.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meshwalkie.core.Display
import com.meshwalkie.core.Freshness
import com.meshwalkie.core.PeerView
import com.meshwalkie.service.MeshBus

@Composable
fun PeerListScreen() {
    val peers by MeshBus.peers.collectAsStateWithLifecycle()
    val heading by MeshBus.myHeading.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Mesh Walkie", style = MaterialTheme.typography.headlineSmall)
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(peers, key = { it.id }) { peer ->
                PeerRow(peer = peer, myHeadingDeg = heading)
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            PttButton(onPtt = { pressed -> MeshBus.pttHandler?.invoke(pressed) })
        }
    }
}

@Composable
fun PeerRow(peer: PeerView, myHeadingDeg: Float) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
    ) {
        // arrow rotation = bearingToPeer - myHeading (tested in Task 3)
        ArrowIcon(rotationDeg = Display.arrowRotation(peer.bearingDeg, myHeadingDeg.toDouble()))
        Spacer(modifier = Modifier.size(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                // e.g. "600 m NNW"
                text = "${Display.formatDistance(peer.distanceMeters)} ${Display.compassLabel(peer.bearingDeg)}",
                style = MaterialTheme.typography.titleMedium
            )
            Text(peer.name, style = MaterialTheme.typography.bodyMedium)
        }
        FreshnessDot(peer.freshness)
    }
}

@Composable
fun FreshnessDot(freshness: Freshness) {
    val color = when (freshness) {
        Freshness.FRESH -> Color(0xFF2E7D32)  // green <30 s
        Freshness.AGING -> Color(0xFFF9A825)  // yellow <2 min
        Freshness.STALE -> Color(0xFFC62828)  // red - stale position is a lie
    }
    Box(modifier = Modifier.size(14.dp).background(color, CircleShape))
}
```

- [ ] 5. Verify: `./gradlew :app:assembleDebug`. Expected: `BUILD SUCCESSFUL`.
- [ ] 6. Commit: `git add -A && git commit -m "feat(ui): peer list with rotating arrows, freshness dots, ptt button"`

---

### Task 15: Foreground service, permission flow, full wiring, field test

> context7 grounding: `startForeground(int id, Notification notification, int foregroundServiceType)` requires the type to be a subset of the manifest `android:foregroundServiceType`; targetSdk 34+ throws `MissingForegroundServiceTypeException` without it, and `SecurityException` without the matching `FOREGROUND_SERVICE_*` permission (all declared in Task 1). `startForegroundService(Intent)` promises a `startForeground` call.

**Files:**
- Create: `app/src/main/java/com/meshwalkie/service/MeshService.kt`, `app/src/main/java/com/meshwalkie/ui/MainActivity.kt`
- Modify: `app/src/main/AndroidManifest.xml` (add service element), `README.md` (field test checklist)

Steps:

- [ ] 1. Add the service element inside `<application>` in `AndroidManifest.xml`:

```xml
<service
    android:name=".service.MeshService"
    android:exported="false"
    android:foregroundServiceType="location|microphone|connectedDevice" />
```

- [ ] 2. Write `MeshService.kt`:

```kotlin
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
```

- [ ] 3. Write `MainActivity.kt`:

```kotlin
package com.meshwalkie.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import com.meshwalkie.service.MeshService

class MainActivity : ComponentActivity() {

    private val requestPermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
            if (grants.values.all { it }) startMeshService()
            // denied -> UI still renders; peer list stays empty until granted
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { PeerListScreen() } }
        ensurePermissionsThenStart()
    }

    private fun ensurePermissionsThenStart() {
        val needed = buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= 31) {
                add(Manifest.permission.BLUETOOTH_ADVERTISE)
                add(Manifest.permission.BLUETOOTH_CONNECT)
                add(Manifest.permission.BLUETOOTH_SCAN)
            }
            if (Build.VERSION.SDK_INT >= 33) {
                add(Manifest.permission.NEARBY_WIFI_DEVICES)
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        val missing = needed.filter {
            checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) startMeshService()
        else requestPermissions.launch(missing.toTypedArray())
    }

    private fun startMeshService() {
        // FGS with microphone type must start while app is in foreground - we are.
        startForegroundService(Intent(this, MeshService::class.java))
    }
}
```

- [ ] 4. Full verification: `./gradlew :app:testDebugUnitTest` (Expected: PASS, all core tests) then `./gradlew :app:assembleDebug` (Expected: `BUILD SUCCESSFUL`, APK at `app/build/outputs/apk/debug/app-debug.apk`).
- [ ] 5. Append the manual field test checklist to `README.md`:

```markdown
## Manual field test (3 phones)

Radio cannot be unit-tested. Verify on hardware:

1. Install app-debug.apk on 3 phones (A, B, C), grant all permissions.
2. Same room: each phone lists the 2 others within ~30 s, green dots.
3. Arrow check: lay phone A flat, rotate your body - arrows counter-rotate
   and keep pointing at the physical peers. Label sanity: peer to your
   north shows N-ish labels.
4. PTT: hold on A, speak, release - B and C play the clip once (no double
   playback = dedup works).
5. Hop test: walk A and C apart until they lose direct link (~80 m+,
   B halfway). A's PTT must still reach C via B (relay), and C must still
   see A's arrow update (POSITION relayed).
6. Freshness: kill the app on A - on B, A's dot goes yellow after 30 s,
   red after 2 min.
7. Screen-off: lock phone B, talk from A - B still plays voice
   (foreground service holds mesh + audio alive).
```

- [ ] 6. Commit: `git add -A && git commit -m "feat(app): foreground mesh service, permission flow, end-to-end wiring"`

---

## Spec coverage map (phase 1)

| Spec requirement | Task |
|---|---|
| Haversine distance | 2 |
| Great-circle bearing | 2 |
| 16-point compass label | 3 |
| Distance format m/km | 3 |
| Arrow = bearingToPeer - myHeading | 3 (math), 14 (render) |
| Packet format (originId, seqNum, ttl, timestamp), 3 types | 4 |
| Controlled flooding: dedup (originId,seqNum / clipId+frameNum), TTL, LRU 500 | 5 |
| PeerRegistry: update, distance+bearing, freshness green/yellow/red | 6 |
| TransportRouter per-peer, mesh preferred, 20 s window | 7 |
| Transport interface + FakeTransport, 3+ chained nodes hop/dedup/TTL | 8 |
| Heading low-pass (arrow glides) | 9 (filter), 11 (sensor) |
| Voice frames ~1 s, reorder by frameNum | 9 (pure), 13 (pipeline) |
| Nearby Connections P2P_CLUSTER single cluster | 10 |
| FusedLocation balanced power -> POSITION flood | 11 (source), 15 (wiring) |
| Rotation-vector heading | 11 |
| PTT record -> Opus -> frames -> VOICE; receive -> dedup -> reorder -> decode -> play | 12, 13 |
| Per-peer row: arrow + "600 m NNW" + name + freshness dot | 14 |
| Foreground service, screen-off operation | 15 |
| Runtime permissions (location, mic, BT, nearby-wifi) | 1 (manifest), 15 (request) |
| Excluded: server fallback, cross-cluster bridge, E2E crypto | phases 2/3 - not planned here |

Deliberate phase 1 simplifications (documented, not hidden):
- Voice clip is sent on PTT release (frames still chunked ~1 s so they relay
  independently); live frame-by-frame send during recording is a phase 2 polish.
- Backpressure (POSITION priority over VOICE on saturated links) is not
  implemented; single-cluster BYTES payloads at these rates do not saturate.
  Becomes relevant with multi-hop bridging in phase 2.
- Room code is fixed to "field1"; QR/group-key entry UI is phase 2.
- batteryPct in PRESENCE is hardcoded 100; BatteryManager wiring is trivial
  and deferred.
