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
