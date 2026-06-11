## 2026-06-11

- core — add TransportRouter: per-peer mesh-vs-server decision, 20s timeout, nowMs injected, 4 tests passing

- core — add PeerRegistry: per-peer state upsert, distance/bearing via GeoMath, Freshness thresholds (30s/120s), snapshot sorted by distance, nowMs injected (no internal clock); 7 tests passing
- core — add sealed Packet model (Position/Voice/Presence) with dedupKey, withTtl, manual equals/hashCode for Voice
- core — add PacketCodec: big-endian binary wire format, encode/decode all 3 types, truncated/garbage input throws IllegalArgumentException
- core — add PacketCodecTest: 7 tests, all passing (round-trip, dedup keys, withTtl, garbage rejection)
- core — add SeenSet: LRU bounded dedup set (capacity 500, insertion-order eviction, thread-safe)
- core — add FloodController: TTL/dedup decision matrix (new+ttl>0 deliver+forward ttl-1, new+ttl==0 deliver only, seen drop), markOwn for echo suppression
- core — add SeenSetTest (3 tests) + FloodControllerTest (5 tests), all passing
