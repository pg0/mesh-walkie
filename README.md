<div align="center">

# Mesh Walkie

### Turn your phone into a walkie-talkie. No signal, no SIM, no internet.

![Android](https://img.shields.io/badge/Android-10%2B-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-7F52FF?logo=kotlin&logoColor=white)
![Release](https://img.shields.io/github/v/release/pg0/mesh-walkie?color=blue)
![Works offline](https://img.shields.io/badge/works-offline-success)
![No account](https://img.shields.io/badge/no-account-success)

</div>

---

> Phones talk to each other **directly over Bluetooth**. Your voice hops from
> phone to phone, so the more people in the group, the further it reaches.
> Mountains, festivals, deep forest - anywhere with no signal.

## What it does

|  |  |
|---|---|
| **Talk** | Hold the button, your whole group hears you |
| **Live** | Stream your voice non-stop (works as a baby monitor) |
| **Find** | An arrow and distance to each friend, like `600 m N` |
| **See** | Map and radar of the whole group |
| **Type** | One-tap quick texts like `OK` or `On my way` |
| **Pin** | Drop a `meet here` marker on the map |
| **Reach** | Out of mesh range? An optional online relay bridges the group over the internet |

## How it works

**1.** Pick a shared channel name (your group's secret word)
**2.** Walk apart - phones link over Bluetooth on their own
**3.** Talk. Out of range? Your voice hops through phones in between.

## Private by design

Only phones with your channel name can hear you. Voice is encrypted
(AES-256-GCM). No accounts, no servers, nothing leaves the group.

## Get it

<div align="center">

### [Download the latest APK](https://github.com/pg0/mesh-walkie/releases/latest)

Allow *install from unknown sources*, then open and grant permissions.

</div>

---

<details>
<summary><b>For developers</b></summary>

<br>

Native Android (Kotlin, Jetpack Compose, foreground service). A pure-Kotlin core
(`com.meshwalkie.core`) is JVM-unit-tested; the Android layer wraps Google Nearby
Connections (BLE mesh) with an optional WiFi/LAN relay fallback.

| Area | Tech |
|---|---|
| Transport | Google Nearby Connections (P2P_CLUSTER, BLE), TTL + dedup flood |
| Voice | AMR-WB (MediaCodec), IMA ADPCM fallback, live chunk streaming |
| Crypto | AES-256-GCM channel key |
| Map | osmdroid (OpenStreetMap, no API key) |
| UI | Jetpack Compose |

```bash
# Build
gradlew.bat :app:assembleDebug

# Unit tests (pure JVM core)
gradlew.bat :app:testDebugUnitTest
```

Radio behaviour cannot be unit-tested; verify on 2-3 phones (same-room
discovery, arrows, PTT, multi-hop relay, freshness, screen-off). A signed
release needs a `keystore.properties` at the repo root pointing at your own
keystore; that file and all `*.jks` keys are gitignored and never committed.

**Online relay** - phones off the same mesh reach each other through a
standalone internet relay (blind ciphertext forwarding, no accounts, same
length-framed wire protocol as the phone-hosted relay). See
[`server/README.md`](server/README.md) for the protocol spec, how to run it
(plain Python or Docker), and deployment notes.

</details>
