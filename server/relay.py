#!/usr/bin/env python3
"""Standalone internet relay for Mesh Walkie.

Speaks the exact wire format the app already uses between phones: TCP
frames of [4-byte big-endian length][payload]. Payloads are AES-GCM
ciphertext (per-channel key, client-side) - this relay never decrypts,
it just fans each frame out to every OTHER connected client. A frame
with length == 0 is a keepalive: ignored on read, sent periodically on
write, so idle links survive NAT/firewall timeouts and dead ones get
caught by the read timeout instead of hanging around forever.

Env vars:
  PORT         listen port (default 51820)
  MAX_CLIENTS  max simultaneous clients, refused beyond this (default 64)
"""
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

clients = {}          # socket -> threading.Lock (per-client write lock)
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


def broadcast(sender_sock, frame: bytes):
    """Relay one already-framed [length][payload] blob to every other client."""
    with clients_lock:
        targets = [(s, lock) for s, lock in clients.items() if s is not sender_sock]
    dead = []
    for sock, lock in targets:
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


def handle_client(sock, addr):
    with clients_lock:
        if len(clients) >= MAX_CLIENTS:
            log(f"refused {addr}: at MAX_CLIENTS={MAX_CLIENTS}")
            try:
                sock.close()
            except OSError:
                pass
            return
        clients[sock] = threading.Lock()
    try:
        sock.settimeout(SOCKET_TIMEOUT)
    except OSError:
        pass
    log(f"client connected: {addr} ({client_count()} total)")

    try:
        while running:
            header = recv_exact(sock, 4)
            if header is None:
                break
            (length,) = struct.unpack(">i", header)
            if length == 0:
                continue   # keepalive, not a packet
            if length < 0 or length > MAX_FRAME:
                break
            payload = recv_exact(sock, length)
            if payload is None:
                break
            broadcast(sock, header + payload)
    except OSError:
        pass
    finally:
        disconnect(sock)


def keepalive_loop():
    frame = struct.pack(">i", 0)
    while running:
        time.sleep(KEEPALIVE_INTERVAL)
        if not running:
            break
        with clients_lock:
            targets = list(clients.items())
        dead = []
        for sock, lock in targets:
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
