"""Assembles a Windows icon (.ico) from PNG files, without any dependency (D-185).

Each PNG is stored as it is (PNG entries in an icon file, read by Windows Vista and later).
Usage: python -I tools/icons/make_ico.py <out.ico> <in-16.png> <in-32.png> ...
"""
import struct
import sys


def png_size(data: bytes) -> tuple[int, int]:
    if data[:8] != b"\x89PNG\r\n\x1a\n" or data[12:16] != b"IHDR":
        raise ValueError("not a PNG file")
    return struct.unpack(">II", data[16:24])


def main(out: str, inputs: list[str]) -> None:
    images = []
    for path in inputs:
        with open(path, "rb") as f:
            data = f.read()
        width, height = png_size(data)
        if width > 256 or height > 256:
            raise ValueError(f"{path}: an icon entry is 256 pixels at most")
        images.append((width, height, data))
    header = struct.pack("<HHH", 0, 1, len(images))
    offset = len(header) + 16 * len(images)
    entries, blobs = b"", b""
    for width, height, data in images:
        entries += struct.pack("<BBBBHHII", width % 256, height % 256, 0, 0, 1, 32, len(data), offset + len(blobs))
        blobs += data
    with open(out, "wb") as f:
        f.write(header + entries + blobs)


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2:])
