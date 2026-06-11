# Mesh Walkie-Talkie - Design Spec

Date: 2026-06-11
Status: Approved (design), pending implementation plan

## Summary

Native Android app. Digital walkie-talkie for a group in the field. Devices
reach each other over a Bluetooth/WiFi peer-to-peer mesh first; a server relay
is the fallback only for peers out of mesh reach. Each peer shows on screen as
a rotating arrow with distance and 16-point compass bearing (e.g. `arrow ->
600 m NNW`). Voice is push-to-talk, store-and-forward.

## Goals

- Talk to a group with no cell/wifi infrastructure (hike, festival, event, field
  ops) via phone-to-phone mesh.
- See every peer's live position relative to me: distance + bearing arrow.
- When a peer is beyond mesh reach but both sides have internet, relay their
  voice AND position through a thin server, transparently.
- Offline-first. Server is a special-case fallback, never the primary path.

## Non-goals (YAGNI)

- No real-time live voice streaming. PTT clips only (multi-hop BLE bandwidth
  cannot sustain a live stream).
- No browser/PWA build. Web cannot do BLE mesh, background audio, or reliable
  compass. Native Android only.
- No accounts. Rooms are a shared code/QR ("group key").
- iOS, maps, message history, file transfer - out of scope for v1.
- End-to-end payload encryption is designed-for but deferred to phase 2.

## Platform decision

| Capability | Browser PWA | Native Android |
|---|---|---|
| GPS / distance / bearing | yes | yes |
| Voice via server | yes | yes |
| BLE mesh | NO (Web BT central-only, no advertise/peripheral/mesh) | yes |
| Background / screen-off | NO (tab killed) | yes (foreground service) |
| Reliable compass heading | flaky | yes |

BLE mesh is primary -> browser is impossible -> native Android.

## Transport decision

Mesh path: **Google Nearby Connections API** (`P2P_CLUSTER`).
- Auto-combines BLE + Bluetooth + WiFi-Direct, handles handshake + link crypto.
- Free, official, supported.
- Gives direct links to nearby phones (a cluster). Multi-hop relay is built on
  top by this app (see Mesh relay).

Rejected alternatives:
- Custom BLE GATT flooding mesh (Briar/Bridgefy-style): full control but high
  effort, very low bandwidth.
- Bridgefy SDK: closed-source, paid tiers, built for text not voice.

Voice mode: **push-to-talk, store-and-forward** (record clip -> compress ->
ship over mesh -> play on arrival). Real-time multi-hop stream fights physics.

## Architecture

Five isolated modules, each one job, communicating through defined interfaces.

```
UI layer (Jetpack Compose)
  - Radar/arrow screen, PTT button, peer list
        |
PeerRegistry  (who exists, where, fresh?)
  - merges GPS from mesh + server
  - computes distance + bearing per peer
    |                         |
MeshTransport            ServerTransport
Nearby Connections       WebSocket fallback
BLE+WiFi P2P + relay     (out-of-mesh peers)
    |                         |
    +-----------+-------------+
                |  both speak one Packet format
Codec/Audio (PTT record -> Opus -> play)
Location    (FusedLocation + rotation-vector compass)
```

**TransportRouter rule:** peer heard on mesh -> use mesh. Peer not on mesh but
seen via server -> use server. Mesh always preferred. Decision is per-peer.

### Packet format (identical on both transports)

Every packet carries `(originId, seqNum, ttl, timestamp)`.

- `POSITION` - id, lat, lon, heading, timestamp. Tiny. Flooded often.
- `VOICE` - id, clipId, frameNum, Opus bytes, timestamp. Store-and-forward.
- `PRESENCE` - id, name, battery, lastSeen. Heartbeat.

## Mesh relay + hop logic

Nearby gives direct links only; multi-hop is this app's layer.

**Controlled flooding with dedup + TTL:**
- Receive packet -> check `seen` set keyed `(originId, seqNum)` (for voice,
  `(originId, clipId, frameNum)`).
  - Seen -> drop (kills loops).
  - New -> process locally + re-broadcast to all other links with `ttl-1`.
  - `ttl == 0` -> process, do not forward.
- `seen` = LRU ~500 entries, evicts oldest. Bounded memory.

**TTL defaults:** POSITION/PRESENCE ttl 4; VOICE ttl 4 + rate-limited.

**Topology:** `P2P_CLUSTER` - all phones in range connect, equal, no master. A
phone in range of two clusters bridges them automatically by relaying.

**Voice flow control:**
- Clips chunked into ~1s frames so a long transmission streams through hops
  instead of waiting for the whole clip.
- Backpressure: saturated link -> POSITION gets priority, VOICE queued.
  Position must never starve; the arrow is the core feature.
- Voice frames dedup'd by `clipId+frameNum`.

**Range reality:** per hop ~10-80m BLE, up to ~100m+ when Nearby picks
WiFi-Direct. 4 hops ~= 300-400m field, more in open terrain.

## GPS, distance, bearing, arrow

**Location source:** `FusedLocationProviderClient`, balanced power. Each fix ->
POSITION packet -> flood mesh (+ server if connected).

**Two angles kept separate:**
1. Bearing to peer - compass direction from me to peer, pure lat/lon math,
   independent of phone orientation.
2. My heading - direction phone/body faces now, from rotation-vector sensor
   (magnetometer + accelerometer).

**Arrow rotation on screen = `bearingToPeer - myHeading`.**

**Distance - haversine:**
```
R = 6371000 m
a = sin^2(dLat/2) + cos(lat1)cos(lat2)sin^2(dLon/2)
d = 2R * atan2(sqrt(a), sqrt(1-a))
```

**Bearing - initial great-circle:**
```
theta = atan2( sin(dLon)*cos(lat2),
               cos(lat1)sin(lat2) - sin(lat1)cos(lat2)cos(dLon) )
bearing = (theta*180/pi + 360) mod 360
```

**16-point compass label:** `idx = round(deg / 22.5) mod 16` ->
`[N, NNE, NE, ENE, E, ESE, SE, SSE, S, SSW, SW, WSW, W, WNW, NW, NNW]`.

**Per-peer display:** rotated arrow + `"600 m NNW"` + name + freshness dot.
- Freshness dot: green <30s, yellow <2min, red stale. Stale position is a lie.
- Distance: <1km in m, >=1km in km.
- Heading low-pass filtered so the arrow glides, not vibrates.

## Server fallback

**Trigger:** peer not seen on mesh for >20s but I have internet.

**Server = thin dumb relay.**
- WebSocket, one persistent conn per phone when internet exists. Node `ws` or
  Go. Hosted on odin.
- Holds `roomId -> set of connected phones`. Forwards any packet from one to all
  others in the room. Same Packet format; server never parses voice, relays
  bytes.
- No accounts. Room = shared code/QR (group key). Join room = subscribe.

**Cross-transport dedup (critical):** mesh + server share one global `seen`
set keyed on `(originId, seqNum)`. A peer on both paths is heard once. Mesh copy
wins (arrives first), server copy dropped.

**Bridge behavior:** a phone with both internet and mesh links auto-relays
mesh<->server (TTL + dedup prevent storms). This is how an out-of-reach peer's
GPS reaches the mesh group: their packet -> server -> bridge phone -> mesh ->
me. I see their arrow even kilometers away.

**Privacy:** server sees coords in v1. Room key should encrypt payload
end-to-end in phase 2 (peers share key, server relays ciphertext). Design leaves
room for it.

## Data flow

**Position:**
```
GPS fix -> POSITION packet -> [mesh flood] + [server if connected]
  -> peers receive -> dedup -> PeerRegistry.update(id, lat, lon, ts)
  -> UI recomputes distance + bearing each frame from my loc + my heading
```

**Voice (PTT):**
```
hold button -> record -> Opus encode -> chunk into frames
  -> each frame = VOICE packet -> mesh flood + server
peer: receive frames -> dedup -> reorder by frameNum -> Opus decode -> play
release -> end-of-clip marker
```

## Testing strategy

- **Pure logic, unit tested on JVM (no Android), the bug-prone core:**
  haversine, bearing, 16-point compass, TTL/dedup flood logic, packet
  encode/decode, TransportRouter decision.
- **Transport behind an interface + fake:** `Transport` interface
  (send/receive packets). Real `NearbyTransport`, `ServerTransport`, plus
  `FakeTransport`. Test mesh relay with 3+ fake nodes wired in a chain: verify a
  packet hops, dedups, respects TTL. No radios needed.
- **Server:** unit test room forward + dedup with fake sockets.
- **Manual field test:** 3 phones, walk apart, verify hop + arrow. Radio can't
  be unit-tested.

## Stack

- Android: Kotlin, Jetpack Compose, Nearby Connections API,
  FusedLocationProviderClient, rotation-vector sensor, Opus codec, Kotlin
  Coroutines + Flow, foreground service for background operation.
- Server: Node + `ws` (or Go) on odin.

## Phasing

1. Core logic (pure, tested) + single-cluster mesh + arrow UI + PTT.
2. Multi-hop relay + bridge + server fallback.
3. End-to-end payload encryption.
