## 2026-07-13

- service — BT mesh is now the primary path, internet relay an explicit quiet backup: status headline reflects BLE links and only falls back to an "internet backup" line when no device is nearby; main-screen indicator reworded to "Internet backup active". Both transports stay always-on (required for BT<->internet bridging).
- nearby/service — fast BLE re-establish on network change: a ConnectivityManager callback re-kicks Nearby advertising+discovery (NearbyTransport.rediscover, debounced 2.5s) when a network appears/drops, so a BT fallback comes up in seconds instead of waiting on Nearby's slow internal retry (measured ~4s to reconnect after a WiFi toggle). Adds ACCESS_NETWORK_STATE.
- build — bump to v0.7.2 (versionCode 10)

- net — online server speaks WebSocket alongside raw TCP; app accepts wss://host[/path] (TLS + hostname verify) so the relay can sit behind a Cloudflare Tunnel with no port forwarding (WsWire.kt, ServerLink dual-mode, NetUtil.parseServerAddr, relay.py auto-detects GET/raw on one port)
- core — CompositeTransport implements RoutedTransport: each child tagged Route.MESH/SERVER, tag flows through MeshEngine onSeen (pre-dedup) to TransportRouter (tracks both links, mesh preferred while fresh)
- ui — per-peer link badge 📶 BLE (with hop count) vs 🌐 via internet in roster + arrow rows; fixes internet peers falsely showing "direct (near)" since the relay leaves TTL untouched
- audio — auto level mic (on by default): device AutomaticGainControl on the capture session (CaptureAgc) plus a pure-Kotlin brick-wall PeakLimiter on the PCM (anti-clip, envelope persists across live chunks); Settings toggle "Auto level mic (AGC + limiter)"
- test — +12 tests (TransportRouter route semantics, CompositeTransport routing/decrypt, PeakLimiter ceiling/quiet/envelope); 114 total green
- build — bump to v0.7.1 (versionCode 9)

## 2026-07-12

- build - bump to v0.5.0 (versionCode 5)
- core - add AppTheme enum (FIELD/CORRUPTION/RADIO/DARK/NIGHT), FIELD default
- ui - add Theme.kt: themeColorScheme/themeShapes/themeTypography/themeIsLight per AppTheme, replacing the two hardcoded dark/night color schemes in MainActivity
- service - Settings: replace darkMode/nightMode booleans with a single theme StateFlow (setTheme), persisted as the enum name; migrates old dark_mode/night_mode prefs to the nearest theme on first read
- ui - MainActivity: MaterialTheme now takes theme-derived colors/shapes/typography; status/nav bar color and icon-contrast (isAppearanceLightStatusBars/NavigationBars) now follow the selected theme via SideEffect instead of a hardcoded black
- ui - PttButton: derives night flag from theme == AppTheme.NIGHT instead of a separate nightMode flow
- ui - SettingsScreen: dark-mode/night-mode switches replaced with a "Theme" section (RadioButton per theme)
- ui - add ThemedControls.kt: LocalAppTheme + AppButton/AppOutlinedButton/AppTextButton/SectionHeader/AppDivider, each rendering per-theme shape/border/padding instead of stock M3 pill controls that ignored MaterialTheme.shapes
- ui - MainActivity: wraps content in CompositionLocalProvider(LocalAppTheme provides theme) so ThemedControls can read the active theme
- ui - swap Button/OutlinedButton/TextButton for AppButton/AppOutlinedButton/AppTextButton across SettingsScreen, PeerListScreen, QuickTextWheel, MapScreen, ServerDialog (dialog action slots keep stock TextButton by convention)
- ui - SettingsScreen: "Theme"/"Voice quality"/"Fallback via WiFi" labels replaced with SectionHeader; AppDivider inserted between the GPS-through-earpiece toggle rows so the list reads as distinct rows
- ui - PttButton: knob shape/border now theme-aware via LocalAppTheme (FIELD = ring on circle, CORRUPTION = ring on square, RADIO/DARK/NIGHT unchanged), idle/held/maxed color logic untouched
- build - bump to v0.5.1 (versionCode 6)

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
