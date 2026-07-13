#!/usr/bin/env python3
"""Stdlib-only smoke tests for the dual-protocol relay (server/relay.py).

Starts relay.handle_client's accept loop in-process on a free localhost
port (no subprocess, no real listener/main() needed - we just reuse the
relay module's connection-handling functions against sockets we open
ourselves), then drives raw TCP and WebSocket clients against it to
check the protocol contract:

  1. raw client -> ws client relay
  2. ws client (masked frames) -> raw client relay
  3. fragmented ws message is reassembled and relayed
  4. ping gets a pong; a raw length==0 keepalive is never relayed
  5. a plain HTTP GET gets a 200 "ok" response

Run with: python server/test_relay.py
"""
import base64
import hashlib
import os
import socket
import struct
import sys
import threading

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import relay


# ---------------------------------------------------------------------------
# server bootstrap
# ---------------------------------------------------------------------------

def start_server():
    """Bind a real listener on a free localhost port and run relay's own
    accept/handle_client loop against it, in background daemon threads."""
    listener = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    listener.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    listener.bind(("127.0.0.1", 0))
    listener.listen(128)
    port = listener.getsockname()[1]

    relay.running = True
    relay.listener = listener

    def accept_loop():
        while relay.running:
            try:
                conn, addr = listener.accept()
            except OSError:
                break
            threading.Thread(target=relay.handle_client, args=(conn, addr), daemon=True).start()

    threading.Thread(target=accept_loop, daemon=True).start()
    return port


def connect(port, timeout=5):
    sock = socket.create_connection(("127.0.0.1", port), timeout=timeout)
    sock.settimeout(timeout)
    return sock


# ---------------------------------------------------------------------------
# raw client helpers
# ---------------------------------------------------------------------------

def raw_send(sock, payload: bytes):
    sock.sendall(struct.pack(">i", len(payload)) + payload)


def raw_register(sock):
    """A raw client only gets registered with kind='raw' once the server has
    read its first 4 bytes. Send a length==0 keepalive to register without
    sending a real packet (it must not be relayed anywhere)."""
    raw_send(sock, b"")


def raw_recv(sock, timeout=3):
    """Read one raw frame. Returns (length, payload), or None on timeout/EOF."""
    sock.settimeout(timeout)
    header = relay.recv_exact(sock, 4)
    if header is None:
        return None
    (length,) = struct.unpack(">i", header)
    if length == 0:
        return (0, b"")
    payload = relay.recv_exact(sock, length)
    if payload is None:
        return None
    return (length, payload)


# ---------------------------------------------------------------------------
# ws client helpers
# ---------------------------------------------------------------------------

def recv_http_response(sock, timeout=5):
    sock.settimeout(timeout)
    data = b""
    while b"\r\n\r\n" not in data:
        chunk = sock.recv(4096)
        if not chunk:
            break
        data += chunk
    head, _, rest = data.partition(b"\r\n\r\n")
    content_length = None
    for line in head.split(b"\r\n")[1:]:
        if line.lower().startswith(b"content-length:"):
            content_length = int(line.split(b":", 1)[1].strip())
    if content_length is not None:
        while len(rest) < content_length:
            chunk = sock.recv(4096)
            if not chunk:
                break
            rest += chunk
    return head + b"\r\n\r\n" + rest


def ws_handshake(sock, path="/"):
    key = base64.b64encode(os.urandom(16))
    request = (
        f"GET {path} HTTP/1.1\r\n"
        f"Host: 127.0.0.1\r\n"
        f"Upgrade: websocket\r\n"
        f"Connection: Upgrade\r\n"
        f"Sec-WebSocket-Key: {key.decode()}\r\n"
        f"Sec-WebSocket-Version: 13\r\n"
        f"\r\n"
    ).encode()
    sock.sendall(request)
    resp = recv_http_response(sock)
    status_line = resp.split(b"\r\n", 1)[0]
    assert b"101" in status_line, f"expected 101 Switching Protocols, got {status_line!r}"
    expected_accept = base64.b64encode(hashlib.sha1(key + relay.WS_MAGIC).digest())
    assert expected_accept in resp, f"Sec-WebSocket-Accept mismatch in {resp!r}"


def mask(payload: bytes, mask_key: bytes) -> bytes:
    return bytes(b ^ mask_key[i % 4] for i, b in enumerate(payload))


def pack_client_ws_frame(opcode, payload=b"", fin=True, masked=True):
    b0 = (0x80 if fin else 0x00) | (opcode & 0x0F)
    length = len(payload)
    mask_bit = 0x80 if masked else 0x00
    if length < 126:
        header = bytes([b0, mask_bit | length])
    elif length < 65536:
        header = bytes([b0, mask_bit | 126]) + struct.pack(">H", length)
    else:
        header = bytes([b0, mask_bit | 127]) + struct.pack(">Q", length)
    if masked:
        mask_key = os.urandom(4)
        return header + mask_key + mask(payload, mask_key)
    return header + payload


def ws_recv(sock, timeout=3):
    sock.settimeout(timeout)
    return relay.recv_ws_frame(sock)  # (fin, opcode, payload) or None


# ---------------------------------------------------------------------------
# tests
# ---------------------------------------------------------------------------

def test_raw_to_ws(port):
    ws = connect(port)
    ws_handshake(ws)
    raw = connect(port)

    payload = b"hello from raw client, mesh packet ciphertext blob"
    raw_send(raw, payload)

    frame = ws_recv(ws)
    assert frame is not None, "ws client got nothing"
    fin, opcode, data = frame
    assert fin is True, "expected FIN=1"
    assert opcode == 0x2, f"expected binary opcode 0x2, got {opcode:#x}"
    assert data == payload, f"payload mismatch: {data!r} != {payload!r}"

    raw.close()
    ws.close()
    print("PASS: raw -> ws relay")


def test_ws_to_raw(port):
    raw = connect(port)
    raw_register(raw)
    ws = connect(port)
    ws_handshake(ws)

    payload = b"hello from masked ws client"
    ws.sendall(pack_client_ws_frame(0x2, payload, fin=True, masked=True))

    result = raw_recv(raw)
    assert result is not None, "raw client got nothing"
    length, data = result
    assert length == len(payload), f"length mismatch: {length} != {len(payload)}"
    assert data == payload, f"payload mismatch: {data!r} != {payload!r}"

    raw.close()
    ws.close()
    print("PASS: ws (masked) -> raw relay")


def test_ws_fragmented(port):
    raw = connect(port)
    raw_register(raw)
    ws = connect(port)
    ws_handshake(ws)

    part1 = b"first fragment;"
    part2 = b"second fragment tail"
    ws.sendall(pack_client_ws_frame(0x2, part1, fin=False, masked=True))
    ws.sendall(pack_client_ws_frame(0x0, part2, fin=True, masked=True))

    result = raw_recv(raw)
    assert result is not None, "raw client got nothing for fragmented ws message"
    length, data = result
    expected = part1 + part2
    assert data == expected, f"reassembled payload mismatch: {data!r} != {expected!r}"

    raw.close()
    ws.close()
    print("PASS: fragmented ws message reassembled and relayed")


def test_ping_pong_and_keepalive_not_relayed(port):
    # ping -> pong
    ws = connect(port)
    ws_handshake(ws)
    ping_payload = b"hb"
    ws.sendall(pack_client_ws_frame(0x9, ping_payload, fin=True, masked=True))
    frame = ws_recv(ws)
    assert frame is not None, "no pong received"
    fin, opcode, data = frame
    assert opcode == 0xA, f"expected pong opcode 0xA, got {opcode:#x}"
    assert data == ping_payload, f"pong payload mismatch: {data!r} != {ping_payload!r}"
    ws.close()
    print("PASS: ping gets pong")

    # raw length==0 keepalive is never relayed
    a = connect(port)
    b = connect(port)
    raw_register(a)
    raw_register(b)
    raw_send(a, b"")  # a second keepalive, post-registration
    result = raw_recv(b, timeout=1)
    assert result is None, f"keepalive was relayed to other client: {result!r}"
    a.close()
    b.close()
    print("PASS: raw length==0 keepalive not relayed")


def test_plain_http(port):
    sock = connect(port)
    sock.sendall(b"GET /health HTTP/1.1\r\nHost: 127.0.0.1\r\nConnection: close\r\n\r\n")
    resp = recv_http_response(sock)
    status_line = resp.split(b"\r\n", 1)[0]
    assert b"200" in status_line, f"expected 200 OK, got {status_line!r}"
    assert resp.rstrip(b"\r\n").endswith(b"ok"), f"expected body 'ok', got {resp!r}"
    sock.close()
    print("PASS: plain HTTP GET returns 200 ok")


def main():
    port = start_server()
    print(f"test relay listening on 127.0.0.1:{port}")

    test_raw_to_ws(port)
    test_ws_to_raw(port)
    test_ws_fragmented(port)
    test_ping_pong_and_keepalive_not_relayed(port)
    test_plain_http(port)

    relay.running = False
    try:
        relay.listener.close()
    except OSError:
        pass

    print("ALL TESTS PASSED")
    sys.stdout.flush()
    # Daemon accept/handler threads may still be winding down; skip normal
    # interpreter finalization so a stray thread doesn't race the stdout
    # lock during shutdown and turn a passing run into a nonzero exit.
    os._exit(0)


if __name__ == "__main__":
    main()
