# Mesh Walkie online relay

A standalone internet relay for phones that aren't on the same
Bluetooth/WiFi mesh. It's a blind ciphertext relay: no accounts, no
storage, no decryption. Every voice/text/position packet is already
AES-256-GCM encrypted client-side with a key derived from the channel
name, so the relay only ever sees ciphertext - channel privacy holds
even against whoever runs the box.

The relay is dual-protocol on a single port: raw TCP for direct
LAN/VPS use, and WebSocket for sitting behind an HTTP-only proxy like a
Cloudflare Tunnel. See "Behind a firewall: Cloudflare Tunnel" below.

## Wire protocol

### Raw TCP

Same framing the phones use to talk to each other and to a phone-hosted
relay (`HostServer.kt` / `ServerLink.kt`):

| Field | Size | Notes |
|---|---|---|
| Length | 4 bytes, big-endian signed int | number of payload bytes that follow |
| Payload | `length` bytes | AES-GCM ciphertext packet; opaque to the relay |

Rules:

| Length | Meaning |
|---|---|
| `0` | Keepalive - ignored on read, no payload follows |
| `< 0` or `> 200000` | Malformed - the sender is disconnected |
| else | A packet - relayed as-is to every other connected client |

The relay also sends a `0`-length keepalive frame to every raw client
every 20 seconds, and each client socket has a 75-second read timeout -
an idle-but-alive link survives on the keepalive, a dead one gets
dropped.

### WebSocket

The relay auto-detects the protocol per connection from the first 4
bytes: `GET ` means HTTP, anything else means raw TCP (the existing
framing above).

A WebSocket upgrade request gets a standard RFC 6455 handshake (any
path, no subprotocol). Any other HTTP request gets a plain
`200 ok` text response and the connection is closed - that's the
health check cloudflared and browsers expect.

Once upgraded: one mesh packet ciphertext is one binary WebSocket
message (fragmented messages are reassembled, capped at 200000 bytes
total same as raw). A ping gets a pong echoing its payload; text
frames and pongs are ignored; a close frame gets a close reply and the
connection drops. The relay sends an empty ping every 20 seconds to
WebSocket clients as its keepalive (raw clients still get the
`0`-length frame). Payloads relay across kinds transparently - a raw
client's packet reaches WebSocket clients and vice versa.

## Run it

```bash
# directly
PORT=51820 python relay.py

# or with Docker
docker compose up -d
```

Env vars: `PORT` (default `51820`), `MAX_CLIENTS` (default `64`, extra
connections are refused/closed).

## Test it

```bash
# two terminals with nc, from the server's perspective:
nc <host> 51820   # then paste raw bytes - won't look like much without framing

# realistically, easiest to test from the app itself:
# Settings > Internet servers > Online server > enter host[:port] > Connect,
# on two phones on different networks, same channel name.
```

For a scripted check, open two TCP sockets to the relay, write a
`[4-byte length][payload]` frame from one, and confirm the other socket
receives the same bytes; a `0000` length frame from either side should
never show up as a payload on the other end.

`python test_relay.py` runs an automated check of both protocols
in-process (no real network needed): raw-to-ws relay, ws-to-raw relay
with masked frames, fragmented ws messages, ping/pong, keepalive
suppression, and the plain HTTP health response.

## Deploy

Needs one TCP port reachable from the internet - a small VPS (this is
happy on the cheapest tier: no CPU/memory to speak of, no persistent
storage), or a home server/NAS with that port forwarded. Point the app
at `your-host:51820` (or whatever `PORT` you set).

The app side: **Settings > Internet servers > Online server**, enter
`host[:port]`, tap **Connect**. It reconnects on its own (backoff up to
30s) if the link drops.

## Behind a firewall: Cloudflare Tunnel

No public IP, no port forwarding, or the network is behind CGNAT? Run
the relay only on localhost and let a Cloudflare Tunnel carry the
WebSocket side out to the internet. Both ends of the tunnel dial
outbound only, so this works from anywhere the box has outbound HTTPS -
including a phone running Termux.

### a) Quick tunnel (no account, throwaway URL)

```bash
cloudflared tunnel --url http://localhost:51820
```

This prints a random `https://xyz.trycloudflare.com` URL. On the
phones: **Settings > Internet servers > Online server**, enter
`wss://xyz.trycloudflare.com` (with the `wss://` scheme), tap
**Connect**. No Cloudflare account needed, no port forwarding. The
catch: the URL changes every time the tunnel restarts, so this is for
testing, not a link you hand out once and forget.

### b) Named tunnel (permanent hostname)

For a stable hostname, set up a named tunnel in the Cloudflare
dashboard (or `cloudflared tunnel create`), point its public hostname
at service `http://meshwalkie-relay:51820` (the service name in
`compose.yml`), and grab the tunnel token. Run
`cloudflared` as a sidecar next to the relay in `compose.yml` - see the
commented-out block in that file. Set the token via an env var
(`.env`, never hardcoded):

```
CF_TUNNEL_TOKEN=<your tunnel token>
```

Then phones connect with `wss://your-chosen-hostname` permanently,
independent of restarts.

### c) Raw TCP still works

The tunnel only carries HTTP/WebSocket traffic. Direct raw TCP
connections (LAN, VPS with the port open, `host:port` in the app) keep
working exactly as before, on the same port - the relay tells the two
apart per-connection by peeking at the first bytes. Run both at once:
raw for anyone who can reach the box directly, `wss://` through the
tunnel for anyone who can't.
