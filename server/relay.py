#!/usr/bin/env python3
"""Standalone internet relay for Mesh Walkie.

Dual-protocol on a single port, auto-detected per connection from the
first 4 bytes read:

  Raw TCP - frames of [4-byte big-endian length][payload]. Payloads are
  AES-GCM ciphertext (per-channel key, client-side) - this relay never
  decrypts, it just fans each frame out to every OTHER connected client.
  A frame with length == 0 is a keepalive: ignored on read, sent
  periodically on write, so idle links survive NAT/firewall timeouts and
  dead ones get caught by the read timeout instead of hanging around
  forever.

  WebSocket - for use behind a Cloudflare Tunnel (or any HTTP-only
  proxy) that won't carry raw TCP. A connection whose first 4 bytes are
  b"GET " is treated as HTTP: a WebSocket upgrade gets the RFC 6455
  handshake and is then framed as WS binary messages (one mesh packet
  ciphertext == one binary message, fragmentation supported); any other
  HTTP request gets a plain 200 "ok" text response and is closed (health
  check for cloudflared/browsers). Payloads relay identically across
  both kinds - a raw client's packet reaches WS clients as a WS binary
  frame and vice versa.

Env vars:
  PORT         listen port (default 51820)
  MAX_CLIENTS  max simultaneous clients, refused beyond this (default 64)
"""
import base64
import hashlib
import os
import signal
import socket
import struct
import threading
import time

PORT = int(os.environ.get("PORT", "51820"))
MAX_CLIENTS = int(os.environ.get("MAX_CLIENTS", "64"))
MAX_FRAME = 200_000
SOCKET_TIMEOUT = 75.0
KEEPALIVE_INTERVAL = 20.0
MAX_HTTP_REQUEST = 16 * 1024

WS_MAGIC = b"258EAFA5-E914-47DA-95CA-C5AB0DC85B11"

clients = {}          # socket -> (threading.Lock, kind) ; kind is None until handshake, then "raw" or "ws"
clients_lock = threading.Lock()
running = True
listener = None


def log(msg):
    ts = time.strftime("%Y-%m-%d %H:%M:%S")
    print(f"[{ts}] {msg}", flush=True)


def client_count():
    with clients_lock:
        return len(clients)


def recv_exact(sock, n):
    buf = bytearray()
    while len(buf) < n:
        try:
            chunk = sock.recv(n - len(buf))
        except OSError:
            return None
        if not chunk:
            return None
        buf.extend(chunk)
    return bytes(buf)


def set_kind(sock, kind):
    with clients_lock:
        entry = clients.get(sock)
        if entry is not None:
            lock, _ = entry
            clients[sock] = (lock, kind)


def pack_ws_frame(opcode, payload=b""):
    """Build one unmasked server->client WS frame (FIN=1) for opcode/payload."""
    length = len(payload)
    b0 = 0x80 | (opcode & 0x0F)
    if length < 126:
        header = bytes([b0, length])
    elif length < 65536:
        header = bytes([b0, 126]) + struct.pack(">H", length)
    else:
        header = bytes([b0, 127]) + struct.pack(">Q", length)
    return header + payload


def build_frame(kind, payload):
    """Build one outbound frame for a client of the given kind from a bare payload."""
    if kind == "raw":
        return struct.pack(">i", len(payload)) + payload
    return pack_ws_frame(0x2, payload)


def broadcast(sender_sock, payload: bytes):
    """Relay one bare payload to every other client, framed for its own kind."""
    with clients_lock:
        targets = [(s, lock, kind) for s, (lock, kind) in clients.items()
                   if s is not sender_sock and kind is not None]
    dead = []
    for sock, lock, kind in targets:
        frame = build_frame(kind, payload)
        try:
            with lock:
                sock.sendall(frame)
        except OSError:
            dead.append(sock)
    for sock in dead:
        disconnect(sock)


def disconnect(sock):
    with clients_lock:
        existed = clients.pop(sock, None) is not None
    if not existed:
        return
    try:
        sock.close()
    except OSError:
        pass
    log(f"client disconnected ({client_count()} total)")


def handle_http(sock, first_bytes):
    """Read the rest of an HTTP request (first_bytes == b"GET " already
    consumed from the socket). Replies and returns True if this became a
    WebSocket connection (caller should move to the WS loop), False if it
    was a plain HTTP request (already replied to; caller should close)."""
    request = bytearray(first_bytes)
    while b"\r\n\r\n" not in request:
        if len(request) >= MAX_HTTP_REQUEST:
            return False
        chunk = sock.recv(4096)
        if not chunk:
            return False
        request.extend(chunk)
    if len(request) > MAX_HTTP_REQUEST:
        return False

    head = bytes(request).split(b"\r\n\r\n", 1)[0]
    lines = head.split(b"\r\n")
    headers = {}
    for line in lines[1:]:
        if b":" in line:
            k, _, v = line.partition(b":")
            headers[k.strip().lower()] = v.strip()

    upgrade = headers.get(b"upgrade", b"").lower()
    ws_key = headers.get(b"sec-websocket-key")

    if upgrade == b"websocket" and ws_key:
        accept = base64.b64encode(hashlib.sha1(ws_key + WS_MAGIC).digest())
        response = (
            b"HTTP/1.1 101 Switching Protocols\r\n"
            b"Upgrade: websocket\r\n"
            b"Connection: Upgrade\r\n"
            b"Sec-WebSocket-Accept: " + accept + b"\r\n\r\n"
        )
        try:
            sock.sendall(response)
        except OSError:
            return False
        return True

    body = b"ok"
    response = (
        b"HTTP/1.1 200 OK\r\n"
        b"Content-Type: text/plain\r\n"
        b"Content-Length: " + str(len(body)).encode() + b"\r\n"
        b"Connection: close\r\n\r\n" + body
    )
    try:
        sock.sendall(response)
    except OSError:
        pass
    return False


def recv_ws_frame(sock):
    """Read one raw WS frame from the client. Returns (fin, opcode, payload)
    or None on EOF/error/oversize."""
    head = recv_exact(sock, 2)
    if head is None:
        return None
    b1, b2 = head[0], head[1]
    fin = bool(b1 & 0x80)
    opcode = b1 & 0x0F
    masked = bool(b2 & 0x80)
    length = b2 & 0x7F

    if length == 126:
        ext = recv_exact(sock, 2)
        if ext is None:
            return None
        (length,) = struct.unpack(">H", ext)
    elif length == 127:
        ext = recv_exact(sock, 8)
        if ext is None:
            return None
        (length,) = struct.unpack(">Q", ext)

    if length > MAX_FRAME:
        return None

    mask_key = None
    if masked:
        mask_key = recv_exact(sock, 4)
        if mask_key is None:
            return None

    payload = recv_exact(sock, length)
    if payload is None:
        return None
    if masked:
        payload = bytes(b ^ mask_key[i % 4] for i, b in enumerate(payload))

    return fin, opcode, payload


def raw_loop(sock, header):
    """Raw-protocol read loop. `header` is the already-read first 4-byte
    length header of the first frame."""
    while running:
        if header is None:
            header = recv_exact(sock, 4)
            if header is None:
                break
        (length,) = struct.unpack(">i", header)
        header = None
        if length == 0:
            continue   # keepalive, not a packet
        if length < 0 or length > MAX_FRAME:
            break
        payload = recv_exact(sock, length)
        if payload is None:
            break
        broadcast(sock, payload)


def ws_loop(sock, lock):
    """WebSocket-protocol read loop, run after a successful upgrade."""
    fragments = None       # bytearray accumulating a fragmented message, or None
    frag_opcode = None

    while running:
        frame = recv_ws_frame(sock)
        if frame is None:
            break
        fin, opcode, payload = frame

        if opcode == 0x8:  # close
            try:
                with lock:
                    sock.sendall(pack_ws_frame(0x8))
            except OSError:
                pass
            break
        elif opcode == 0x9:  # ping -> pong
            try:
                with lock:
                    sock.sendall(pack_ws_frame(0xA, payload))
            except OSError:
                break
        elif opcode == 0xA:  # pong -> ignore
            continue
        elif opcode == 0x0:  # continuation
            if fragments is None:
                break   # continuation with no start frame - protocol error
            fragments.extend(payload)
            if len(fragments) > MAX_FRAME:
                break
            if fin:
                if frag_opcode == 0x2:
                    broadcast(sock, bytes(fragments))
                fragments = None
                frag_opcode = None
        elif opcode == 0x1:  # text
            if not fin:
                fragments = bytearray()
                frag_opcode = 0x1
            # complete or fragmented text: ignored per contract
        elif opcode == 0x2:  # binary
            if fin:
                broadcast(sock, payload)
            else:
                fragments = bytearray(payload)
                frag_opcode = 0x2
                if len(fragments) > MAX_FRAME:
                    break
        # unknown opcodes are ignored


def handle_client(sock, addr):
    with clients_lock:
        if len(clients) >= MAX_CLIENTS:
            log(f"refused {addr}: at MAX_CLIENTS={MAX_CLIENTS}")
            try:
                sock.close()
            except OSError:
                pass
            return
        clients[sock] = (threading.Lock(), None)
    try:
        sock.settimeout(SOCKET_TIMEOUT)
    except OSError:
        pass
    log(f"client connected: {addr} ({client_count()} total)")

    try:
        header = recv_exact(sock, 4)
        if header is None:
            return
        if header == b"GET ":
            if handle_http(sock, header):
                with clients_lock:
                    lock, _ = clients[sock]
                set_kind(sock, "ws")
                ws_loop(sock, lock)
            # else: plain HTTP request already answered, connection closes
        else:
            set_kind(sock, "raw")
            raw_loop(sock, header)
    except OSError:
        pass
    finally:
        disconnect(sock)


def keepalive_loop():
    raw_frame = struct.pack(">i", 0)
    ws_ping = pack_ws_frame(0x9)
    while running:
        time.sleep(KEEPALIVE_INTERVAL)
        if not running:
            break
        with clients_lock:
            targets = [(s, lock, kind) for s, (lock, kind) in clients.items() if kind is not None]
        dead = []
        for sock, lock, kind in targets:
            frame = raw_frame if kind == "raw" else ws_ping
            try:
                with lock:
                    sock.sendall(frame)
            except OSError:
                dead.append(sock)
        for sock in dead:
            disconnect(sock)


def make_listener():
    """Dual-stack (IPv6 + IPv4-mapped) listener; falls back to plain IPv4
    if the host has no IPv6 stack (e.g. some container networks)."""
    try:
        sock = socket.socket(socket.AF_INET6, socket.SOCK_STREAM)
        sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        if hasattr(socket, "IPV6_V6ONLY"):
            sock.setsockopt(socket.IPPROTO_IPV6, socket.IPV6_V6ONLY, 0)
        sock.bind(("::", PORT))
        return sock
    except OSError as e:
        log(f"IPv6 dual-stack bind failed ({e}), falling back to IPv4")
        sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        sock.bind(("0.0.0.0", PORT))
        return sock


def shutdown(*_args):
    global running
    running = False
    log("shutting down")
    with clients_lock:
        socks = list(clients.keys())
    for sock in socks:
        try:
            sock.close()
        except OSError:
            pass
    if listener is not None:
        try:
            listener.close()
        except OSError:
            pass


def main():
    global listener
    listener = make_listener()
    listener.listen(128)
    log(f"meshwalkie relay listening on port {PORT} (max_clients={MAX_CLIENTS})")

    signal.signal(signal.SIGTERM, shutdown)
    signal.signal(signal.SIGINT, shutdown)

    threading.Thread(target=keepalive_loop, daemon=True).start()

    while running:
        try:
            sock, addr = listener.accept()
        except OSError:
            break
        threading.Thread(target=handle_client, args=(sock, addr), daemon=True).start()


if __name__ == "__main__":
    main()
