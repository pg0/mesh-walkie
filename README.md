# Mesh Walkie

## What is this?

Mesh Walkie is a walkie-talkie app for Android phones.

You know how a walkie-talkie lets you press a button and talk to your friend far
away? This app turns your phone into one. No SIM card needed. No internet needed.
No paying for anything.

## How does it work?

Phones talk to each other straight through the air using Bluetooth, a tiny radio
that is already inside every phone. Your words hop from phone to phone like
passing a note around a classroom.

That last part is the cool trick. If your friend is too far away for your phone
to reach, but someone else is standing in the middle, your message hops through
that middle phone to reach your friend. The more friends you have, the further
your voice can travel. That is what "mesh" means: everybody helps pass the
message along.

## What can it do?

- Hold the big button and talk. Everyone in your group hears you.
- See a little arrow for each friend that points where they are and how far away,
  like "600 m" to the north.
- A map and a radar screen so you can see your whole group.
- Send quick text messages like "OK" or "On my way" without typing.
- Drop a pin on the map to say "meet here".
- A live mode that streams your voice the whole time, so you can even use a phone
  as a baby monitor.
- Works when there is no phone signal at all, like in the mountains, at a big
  festival, or deep in a forest.

## Why is it neat?

Normal phone calls and chat apps stop working when there is no signal. This one
keeps working because the phones talk to each other directly. It is like having
your own little radio network that you carry in your pocket.

## Is it private?

Yes. Your group picks a secret channel name, and only phones that know that name
can hear you. Messages are scrambled so other people cannot listen in.

---

## For developers

Native Android (Kotlin, Jetpack Compose, foreground service). A pure-Kotlin core
(`com.meshwalkie.core`) is JVM-unit-tested; the Android layer wraps Google Nearby
Connections (BLE mesh) and an optional WiFi/LAN relay fallback.

- Voice: AMR-WB (MediaCodec) with an IMA ADPCM fallback, TTL + dedup flood mesh.
- Live mode: continuous chunked streaming with optional speech gating.
- Map: osmdroid (OpenStreetMap, no API key). Encryption: AES-256-GCM channel key.

### Build

    gradlew.bat :app:assembleDebug

### Unit tests (pure JVM core)

    gradlew.bat :app:testDebugUnitTest

### Run

Install on 2 to 3 phones, grant all requested permissions, walk apart, hold the
PTT button to talk. Radio behaviour cannot be unit-tested; verify on hardware
(same room discovery, arrows, PTT, multi-hop relay, freshness, screen-off).

A signed release build needs a `keystore.properties` at the repo root pointing at
your own keystore. That file and all `*.jks` keys are gitignored and never
committed.
