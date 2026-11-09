#!/usr/bin/env python3
"""Simuliert den ChainCounter: sendet 1x pro Sekunde $ISLWND und verarbeitet $ISLWNS.

PC und Telefon muessen im selben WLAN sein. Nur Python-Standardbibliothek.
Beispiel: python3 tools/simulate_device.py --port 1458
"""
import argparse
import re
import socket
import time


def checksum(body: str) -> int:
    value = 0
    for char in body:
        value ^= ord(char)
    return value


def sentence(body: str) -> str:
    return f"${body}*{checksum(body):02X}"


RESTART_SECONDS = 8


class Device:
    def __init__(self) -> None:
        self.turns = 0
        self.factor = 0.4
        self.debounce = 10
        self.direction = 0
        self.started = time.monotonic()
        self.silent_until = 0.0

    def tx(self) -> str:
        runtime = int(time.monotonic() - self.started)
        length = self.turns * self.factor
        return sentence(f"ISLWND,{length:.1f},{self.direction},24.1,1,{runtime},{runtime}")

    def handle(self, raw: str) -> str:
        match = re.fullmatch(r"\$([^*]*)\*([0-9A-Fa-f]{2})", raw)
        if not match:
            return "FEHLER Format"
        body, given = match.groups()
        if checksum(body) != int(given, 16):
            return "FEHLER Checksumme"
        fields = body.split(",")
        if fields[0] != "ISLWNS":
            return "ignoriert"
        command, args = fields[1], fields[2:]
        if command == "C" and not args:
            self.turns = 0
        elif command == "C" and len(args) == 1 and re.fullmatch(r"-?\d+", args[0]):
            self.turns = round(int(args[0]) / self.factor)
        elif command == "R" and not args:
            self.silent_until = time.monotonic() + RESTART_SECONDS
            self.started = self.silent_until
            return f"ok: Neustart, {RESTART_SECONDS} s keine Daten"
        elif command == "S" and len(args) == 1 and args[0].isdigit() and 1 <= int(args[0]) <= 1000:
            self.debounce = int(args[0])
        elif command == "K" and len(args) == 1 and 0.001 <= float(args[0]) <= 100:
            self.factor = float(args[0])
        else:
            return "FEHLER Kommando"
        return f"ok: turns={self.turns} factor={self.factor} debounce={self.debounce}"


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--port", type=int, default=1458)
    parser.add_argument("--target", default="255.255.255.255", help="Ziel der $ISLWND-Saetze")
    parser.add_argument("--target-port", type=int, help="Zielport, Standard: --port")
    parser.add_argument("--seconds", type=float, help="nach dieser Zeit beenden")
    args = parser.parse_args()

    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    sock.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)
    sock.bind(("", args.port))
    sock.settimeout(0.1)

    device = Device()
    begin = time.monotonic()
    next_tx = begin
    while args.seconds is None or time.monotonic() - begin < args.seconds:
        now = time.monotonic()
        if now >= next_tx:
            if now >= device.silent_until:
                sock.sendto(device.tx().encode("ascii"), (args.target, args.target_port or args.port))
            next_tx += 1
        try:
            data, sender = sock.recvfrom(512)
        except socket.timeout:
            continue
        text = data.decode("ascii", "replace")
        if not text.startswith("$ISLWND"):
            print(f"{sender[0]}: {text} -> {device.handle(text)}", flush=True)


if __name__ == "__main__":
    main()
