"""Minimal Source RCON client (stdlib only).

Implements just enough of the Valve Source RCON protocol to authenticate and
run server commands against a Minecraft dedicated server. No third-party deps.
"""

from __future__ import annotations

import socket
import struct

SERVERDATA_AUTH = 3
SERVERDATA_AUTH_RESPONSE = 2
SERVERDATA_EXECCOMMAND = 2
SERVERDATA_RESPONSE_VALUE = 0


class RconError(Exception):
    pass


class RconClient:
    def __init__(self, host: str, port: int, password: str, timeout: float = 10.0):
        self.host = host
        self.port = port
        self.password = password
        self.timeout = timeout
        self._sock: socket.socket | None = None
        self._req_id = 0

    # -- low level ---------------------------------------------------------
    def _send(self, req_type: int, body: str) -> int:
        assert self._sock is not None
        self._req_id += 1
        req_id = self._req_id
        payload = struct.pack("<ii", req_id, req_type) + body.encode("utf-8") + b"\x00\x00"
        packet = struct.pack("<i", len(payload)) + payload
        self._sock.sendall(packet)
        return req_id

    def _recv_packet(self) -> tuple[int, int, str]:
        assert self._sock is not None
        raw_len = self._read_exactly(4)
        (length,) = struct.unpack("<i", raw_len)
        data = self._read_exactly(length)
        req_id, resp_type = struct.unpack("<ii", data[:8])
        body = data[8:-2].decode("utf-8", errors="replace")
        return req_id, resp_type, body

    def _read_exactly(self, n: int) -> bytes:
        assert self._sock is not None
        buf = b""
        while len(buf) < n:
            chunk = self._sock.recv(n - len(buf))
            if not chunk:
                raise RconError("connection closed by server")
            buf += chunk
        return buf

    # -- public ------------------------------------------------------------
    def connect(self) -> None:
        self._sock = socket.create_connection((self.host, self.port), timeout=self.timeout)
        self._sock.settimeout(self.timeout)
        req_id = self._send(SERVERDATA_AUTH, self.password)
        resp_id, resp_type, _ = self._recv_packet()
        # Some servers send an empty RESPONSE_VALUE before the AUTH_RESPONSE.
        if resp_type == SERVERDATA_RESPONSE_VALUE:
            resp_id, resp_type, _ = self._recv_packet()
        if resp_id == -1 or resp_id != req_id:
            raise RconError("RCON authentication failed (bad password?)")

    def command(self, cmd: str) -> str:
        if self._sock is None:
            raise RconError("not connected")
        req_id = self._send(SERVERDATA_EXECCOMMAND, cmd)
        # Read the (possibly multi-packet) response for our request id.
        out = []
        while True:
            resp_id, _, body = self._recv_packet()
            if resp_id != req_id:
                continue
            out.append(body)
            # Minecraft answers a single command with one packet; break once we
            # have any body and nothing more is immediately pending.
            if len(body) < 4000:
                break
        return "".join(out)

    def close(self) -> None:
        if self._sock is not None:
            try:
                self._sock.close()
            finally:
                self._sock = None

    def __enter__(self) -> "RconClient":
        self.connect()
        return self

    def __exit__(self, *exc) -> None:
        self.close()
