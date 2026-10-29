#!/usr/bin/env python3
"""Validate the exact Sonic the Hedgehog Genesis REV00 ROM expected by this port lab."""
import argparse
import hashlib
import pathlib
import struct
import sys
import zlib

EXPECTED_SHA256 = "46160baa06362c711c9f1a5017cb7371026444936c8af5e93a78996cf32ff2a6"
EXPECTED_CRC32 = 0xF9394E97
EXPECTED_SIZE = 0x80000

def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("rom", type=pathlib.Path)
    args = ap.parse_args()
    try:
        data = args.rom.read_bytes()
    except OSError as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        return 2
    if len(data) < 0x200:
        print("FAIL: file is too small")
        return 1
    header_ok = data[0x100:0x110] == b"SEGA MEGA DRIVE "
    serial = data[0x180:0x18E].decode("ascii", "replace")
    checksum = struct.unpack_from(">H", data, 0x18E)[0]
    rom_end = struct.unpack_from(">I", data, 0x1A4)[0]
    sha = hashlib.sha256(data).hexdigest()
    crc = zlib.crc32(data) & 0xFFFFFFFF
    ok = (len(data) == EXPECTED_SIZE and header_ok and serial == "GM 00001009-00"
          and checksum == 0x264A and rom_end == 0x0007FFFF
          and sha == EXPECTED_SHA256 and crc == EXPECTED_CRC32)
    print(f"File size: {len(data)}")
    print(f"Header: {'OK' if header_ok else 'INVALID'}")
    print(f"Serial: {serial}")
    print(f"Checksum: 0x{checksum:04X}")
    print(f"SHA256: {sha}")
    print(f"CRC32: 0x{crc:08X}")
    print("PASS: exact Sonic 1 REV00 ROM" if ok else "FAIL: ROM does not match target")
    return 0 if ok else 1

if __name__ == "__main__":
    raise SystemExit(main())
