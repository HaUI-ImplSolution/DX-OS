# SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
# SPDX-License-Identifier: Apache-2.0
"""A small MQTT 3.1.1 client on the standard library: connect with a user name and password,
subscribe at QoS 1, publish at QoS 1, and keep the connection alive.

It is used by the MQTT bridge and by examples/itot-simulator, so neither needs a package.
"""
import socket
import struct
import time

CONNECT, CONNACK, PUBLISH, PUBACK, SUBSCRIBE, SUBACK, PINGREQ, PINGRESP, DISCONNECT = 1, 2, 3, 4, 8, 9, 12, 13, 14
MAX_PACKET = 256 * 1024


class MqttError(Exception):
    pass


def _text(value):
    data = value.encode("utf-8")
    return struct.pack("!H", len(data)) + data


def _length(size):
    out = bytearray()
    while True:
        byte, size = size % 128, size // 128
        out.append(byte | (128 if size else 0))
        if not size:
            return bytes(out)


class Client:
    def __init__(self, host, port=1883, client_id="", username=None, password=None, keepalive=30, clean=False):
        self.host, self.port, self.client_id = host, port, client_id
        self.username, self.password = username, password
        self.keepalive, self.clean = keepalive, clean
        self.sock = None
        self.last_sent = 0.0
        self.packet_id = 0

    # --- wire --------------------------------------------------------------------------------

    def _send(self, kind, flags, body=b""):
        self.sock.sendall(bytes([(kind << 4) | flags]) + _length(len(body)) + body)
        self.last_sent = time.time()

    def _read(self, size):
        data = b""
        while len(data) < size:
            chunk = self.sock.recv(size - len(data))
            if not chunk:
                raise MqttError("the broker closed the connection")
            data += chunk
        return data

    def _packet(self):
        """Reads one packet, or returns None when the socket timed out before one began."""
        try:
            first = self.sock.recv(1)
        except socket.timeout:
            return None
        if not first:
            raise MqttError("the broker closed the connection")
        size, shift = 0, 0
        while True:
            byte = self._read(1)[0]
            size += (byte & 127) << shift
            if not byte & 128:
                break
            shift += 7
            if shift > 21:
                raise MqttError("malformed packet length")
        if size > MAX_PACKET:
            raise MqttError("packet too large")
        return first[0] >> 4, first[0] & 15, self._read(size)

    def _next_id(self):
        self.packet_id = self.packet_id % 65535 + 1
        return self.packet_id

    # --- session -----------------------------------------------------------------------------

    def connect(self):
        self.sock = socket.create_connection((self.host, self.port), timeout=10)
        self.sock.settimeout(max(self.keepalive / 3, 1))
        flags = (2 if self.clean else 0) | (0x80 if self.username is not None else 0) | (0x40 if self.password is not None else 0)
        body = _text("MQTT") + bytes([4, flags]) + struct.pack("!H", self.keepalive) + _text(self.client_id)
        if self.username is not None:
            body += _text(self.username)
        if self.password is not None:
            body += _text(self.password)
        self._send(CONNECT, 0, body)
        packet = self._packet()
        if packet is None or packet[0] != CONNACK or len(packet[2]) < 2:
            raise MqttError("no CONNACK from the broker")
        if packet[2][1] != 0:
            raise MqttError(f"the broker refused the connection (code {packet[2][1]})")

    def subscribe(self, topics):
        identifier = self._next_id()
        body = struct.pack("!H", identifier) + b"".join(_text(topic) + bytes([1]) for topic in topics)
        self._send(SUBSCRIBE, 2, body)

    def publish(self, topic, payload, wait=True):
        """Publishes at QoS 1 and waits for the PUBACK; messages that arrive meanwhile are lost to the caller."""
        identifier = self._next_id()
        data = payload if isinstance(payload, bytes) else payload.encode("utf-8")
        self._send(PUBLISH, 2, _text(topic) + struct.pack("!H", identifier) + data)
        while wait:
            packet = self._packet()
            if packet is None:
                raise MqttError("no PUBACK from the broker")
            if packet[0] == PUBACK and struct.unpack("!H", packet[2][:2])[0] == identifier:
                return

    def ack(self, identifier):
        self._send(PUBACK, 0, struct.pack("!H", identifier))

    def messages(self):
        """Yields (topic, payload, packet id) of each message; the caller answers with ack(packet id).
        Pings the broker while idle, and raises MqttError when the connection is lost."""
        while True:
            packet = self._packet()
            if packet is None or time.time() - self.last_sent >= self.keepalive / 2:
                self._send(PINGREQ, 0)
                if packet is None:
                    yield None
                    continue
            kind, flags, body = packet
            if kind != PUBLISH:
                continue
            size = struct.unpack("!H", body[:2])[0]
            topic = body[2:2 + size].decode("utf-8", "replace")
            rest = body[2 + size:]
            qos = (flags >> 1) & 3
            identifier = None
            if qos:
                identifier = struct.unpack("!H", rest[:2])[0]
                rest = rest[2:]
            yield topic, rest, identifier

    def close(self):
        try:
            if self.sock:
                self._send(DISCONNECT, 0)
                self.sock.close()
        except OSError:
            pass
        self.sock = None
