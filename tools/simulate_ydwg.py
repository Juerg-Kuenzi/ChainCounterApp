#!/usr/bin/env python3
"""Simuliert die YDWG-02 mit dem Abgassensor: sendet alle 2 s PGN 130312 und 130316 im RAW-Format.

Die Temperatur steigt von --start um --step pro Wert bis --max und faellt dann wieder,
damit sich der Alarm der App ein- und ausschalten laesst. Nur Python-Standardbibliothek.
Beispiel: python3 tools/simulate_ydwg.py --start 40 --step 2 --max 60
"""
import argparse
import socket
import time

SOURCE_ADDRESS = 0x74
EXHAUST_GAS = 14


def frame(pgn: int, data: bytes) -> str:
    can_id = (5 << 26) | (pgn << 8) | SOURCE_ADDRESS
    stamp = time.strftime("%H:%M:%S") + f".{int(time.time() * 1000) % 1000:03d}"
    return f"{stamp} R {can_id:08X} " + " ".join(f"{b:02X}" for b in data)


def packet(celsius: float) -> str:
    kelvin = celsius + 273.15
    standard = bytes([0xFF, 0, EXHAUST_GAS]) + round(kelvin * 100).to_bytes(2, "little") + b"\xff\xff\xff"
    extended = bytes([0xFF, 0, EXHAUST_GAS]) + round(kelvin * 1000).to_bytes(3, "little") + b"\xff\xff"
    return frame(130312, standard) + "\r\n" + frame(130316, extended) + "\r\n"


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--port", type=int, default=1457)
    parser.add_argument("--target", default="255.255.255.255")
    parser.add_argument("--start", type=float, default=40.0)
    parser.add_argument("--step", type=float, default=2.0)
    parser.add_argument("--max", type=float, default=60.0)
    parser.add_argument("--count", type=int, help="nach so vielen Werten beenden")
    args = parser.parse_args()

    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    sock.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)
    value, step, sent = args.start, args.step, 0
    while args.count is None or sent < args.count:
        sock.sendto(packet(value).encode("ascii"), (args.target, args.port))
        print(f"Abgas {value:.1f} °C", flush=True)
        sent += 1
        if value + step > args.max or value + step < args.start:
            step = -step
        value += step
        time.sleep(2)


if __name__ == "__main__":
    main()
