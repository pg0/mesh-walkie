## 2026-06-11

- service — add MeshBus: process-wide StateFlow bridge (peers, myHeading, pttHandler) written by service, consumed by Compose
- ui — add ArrowIcon: Canvas composable, arrow drawn via Path, rotated by Display.arrowRotation degrees
- ui — add PttButton: 120 dp circle, press-and-hold via detectTapGestures(onPress)/tryAwaitRelease, green/red visual toggle, onPtt(true/false) callbacks
- ui — add PeerListScreen + PeerRow + FreshnessDot: lazy peer list, distance+compass label from Display, freshness dot (FRESH green/AGING yellow/STALE red), PTT button docked at bottom; consumes MeshBus StateFlows via collectAsStateWithLifecycle


- audio — add OpusCodec: synchronous MediaCodec Opus encode/decode, 16 kHz mono, 20 ms / 320-sample packets, 24 kbit/s; decoder fed csd-0 (encoder codec-config) + zeroed csd-1/csd-2
- location — add LocationSource (FusedLocationProviderClient, balanced power, 5 s interval) and HeadingSource (TYPE_ROTATION_VECTOR, azimuth normalized [0,360), piped through HeadingFilter alpha=0.15)
- core — add TransportRouter: per-peer mesh-vs-server decision, 20s timeout, nowMs injected, 4 tests passing

- core — add PeerRegistry: per-peer state upsert, distance/bearing via GeoMath, Freshness thresholds (30s/120s), snapshot sorted by distance, nowMs injected (no internal clock); 7 tests passing
- core — add sealed Packet model (Position/Voice/Presence) with dedupKey, withTtl, manual equals/hashCode for Voice
- core — add PacketCodec: big-endian binary wire format, encode/decode all 3 types, truncated/garbage input throws IllegalArgumentException
- core — add PacketCodecTest: 7 tests, all passing (round-trip, dedup keys, withTtl, garbage rejection)
- core — add SeenSet: LRU bounded dedup set (capacity 500, insertion-order eviction, thread-safe)
- core — add FloodController: TTL/dedup decision matrix (new+ttl>0 deliver+forward ttl-1, new+ttl==0 deliver only, seen drop), markOwn for echo suppression
- core — add SeenSetTest (3 tests) + FloodControllerTest (5 tests), all passing
