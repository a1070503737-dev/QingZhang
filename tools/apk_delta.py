"""Build QZAPK001 patches from identical ZIP payloads in two signed APKs."""

import mmap
from pathlib import Path
import struct
from zipfile import BadZipFile, ZipFile


MAGIC = b"QZAPK001"
BUFFER_SIZE = 64 * 1024
LOCAL_HEADER = struct.Struct("<4s22xHH")


def payloads(apk: Path, data: mmap.mmap) -> dict:
    entries = {}
    with ZipFile(apk) as archive:
        for entry in archive.infolist():
            if entry.compress_size == 0:
                continue
            header = entry.header_offset
            if not 0 <= header <= len(data) - LOCAL_HEADER.size:
                raise BadZipFile("APK local header is outside the archive")
            signature, name_size, extra_size = LOCAL_HEADER.unpack_from(data, header)
            offset = header + LOCAL_HEADER.size + name_size + extra_size
            if signature != b"PK\x03\x04" or offset + entry.compress_size > len(data):
                raise BadZipFile("APK compressed entry exceeds the archive")
            entries[entry.filename] = (offset, entry.compress_size, entry.CRC)
    return entries


def write_patch(base: Path, target: Path, patch: Path) -> bool:
    """Leave a patch beside the APK when it saves bytes. The APKs must be verified first."""
    partial = patch.with_name(patch.name + ".part")
    try:
        with base.open("rb") as base_file, target.open("rb") as target_file:
            with mmap.mmap(base_file.fileno(), 0, access=mmap.ACCESS_READ) as old:
                with mmap.mmap(target_file.fileno(), 0, access=mmap.ACCESS_READ) as new:
                    write_records(base, target, old, new, partial)
        if partial.stat().st_size >= target.stat().st_size:
            return False
        partial.replace(patch)
        return True
    finally:
        partial.unlink(missing_ok=True)


def write_records(base: Path, target: Path, old: mmap.mmap, new: mmap.mmap, patch: Path) -> None:
    old_entries = payloads(base, old)
    new_entries = sorted(payloads(target, new).items(), key=lambda item: item[1][0])
    with patch.open("wb") as output:
        output.write(MAGIC)
        cursor = 0
        for name, (offset, length, crc) in new_entries:
            prior = old_entries.get(name)
            if prior is None or prior[1:] != (length, crc) or offset < cursor:
                continue
            old_offset = prior[0]
            if any(
                old[old_offset + start : old_offset + min(start + BUFFER_SIZE, length)]
                != new[offset + start : offset + min(start + BUFFER_SIZE, length)]
                for start in range(0, length, BUFFER_SIZE)
            ):
                continue
            write_literal(output, new, cursor, offset)
            output.write(struct.pack(">ii", -length, old_offset))
            cursor = offset + length
        write_literal(output, new, cursor, len(new))


def write_literal(output, data: mmap.mmap, start: int, end: int) -> None:
    if start < end:
        output.write(struct.pack(">i", end - start))
        for offset in range(start, end, BUFFER_SIZE):
            output.write(data[offset : min(offset + BUFFER_SIZE, end)])
