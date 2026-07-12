# Mesh Walkie online relay

A standalone internet relay for phones that aren't on the same
Bluetooth/WiFi mesh. It's a blind ciphertext relay: no accounts, no
storage, no decryption. Every voice/text/position packet is already
AES-256-GCM encrypted client-side with a key derived from the channel
name, so the relay only ever sees ciphertext - channel privacy holds
even against whoever runs the box.

## Wire protocol

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

The relay also sends a `0`-length keepalive frame to every client every
20 seconds, and each client socket has a 75-second read timeout - an
idle-but-alive link survives on the keepalive, a dead one gets dropped.

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

## Deploy

Needs one TCP port reachable from the internet - a small VPS (this is
happy on the cheapest tier: no CPU/memory to speak of, no persistent
storage), or a home server/NAS with that port forwarded. Point the app
at `your-host:51820` (or whatever `PORT` you set).

The app side: **Settings > Internet servers > Online server**, enter
`host[:port]`, tap **Connect**. It reconnects on its own (backoff up to
30s) if the link drops.
