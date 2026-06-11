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
