"""Generate a tight, square, transparent PNG from the browser-tab favicon artwork.

Source:  public/static/logo.png   (300x300, 8-bit palette + tRNS transparency)
Output:  public/static/logo-mark.png (260x260, RGBA, artwork centered)

The source has uneven padding (artwork bbox is roughly x=26..276, y=29..242),
so embedding it directly in the UI makes the mark sit slightly high. This
script crops to the non-transparent bounding box and re-centers it on a square
canvas while preserving the original colors and per-pixel alpha (anti-aliasing).

Run:  python scripts/make-logo-mark.py
Only the Python standard library is required.
"""

import os
import struct
import zlib

BASE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(BASE, "public", "static", "logo.png")
DST = os.path.join(BASE, "public", "static", "logo-mark.png")
SIZE = 260  # square canvas, plenty of resolution for 2x/3x displays


def read_chunks(data):
    pos = 8
    assert data[:8] == b"\x89PNG\r\n\x1a\n", "not a PNG file"
    while pos < len(data):
        length = struct.unpack(">I", data[pos : pos + 4])[0]
        kind = data[pos + 4 : pos + 8]
        yield kind, data[pos + 8 : pos + 8 + length]
        pos += 12 + length


def png_chunk(kind, payload):
    return (
        struct.pack(">I", len(payload))
        + kind
        + payload
        + struct.pack(">I", zlib.crc32(kind + payload) & 0xFFFFFFFF)
    )


def main():
    with open(SRC, "rb") as fh:
        chunks = list(read_chunks(fh.read()))

    w, h, depth, color_type, _, _, interlace = struct.unpack(
        ">IIBBBBB", next(d for k, d in chunks if k == b"IHDR")
    )
    assert (depth, color_type, interlace) == (8, 3, 0), "expected 8-bit non-interlaced palette PNG"

    palette = next(d for k, d in chunks if k == b"PLTE")
    transparency = next((d for k, d in chunks if k == b"tRNS"), b"")
    raw = zlib.decompress(b"".join(d for k, d in chunks if k == b"IDAT"))
    assert len(raw) == h * (w + 1), "unexpected scanline length"
    # This file uses filter type 0 on every scanline; keep the assertion so a
    # future re-export of the artwork fails loudly instead of producing noise.
    assert all(raw[y * (w + 1)] == 0 for y in range(h)), "unexpected PNG filter type"
    rows = [raw[y * (w + 1) + 1 : (y + 1) * (w + 1)] for y in range(h)]

    def alpha_of(index):
        return transparency[index] if index < len(transparency) else 255

    min_x, min_y, max_x, max_y = w, h, -1, -1
    for y in range(h):
        row = rows[y]
        for x in range(w):
            if alpha_of(row[x]) > 8:
                if x < min_x:
                    min_x = x
                if x > max_x:
                    max_x = x
                if y < min_y:
                    min_y = y
                if y > max_y:
                    max_y = y
    assert max_x >= min_x and max_y >= min_y, "no visible artwork found"

    box_w, box_h = max_x - min_x + 1, max_y - min_y + 1
    off_x, off_y = (SIZE - box_w) // 2, (SIZE - box_h) // 2
    assert off_x >= 0 and off_y >= 0, "artwork does not fit the output canvas"

    pixels = bytearray(SIZE * SIZE * 4)
    for y in range(box_h):
        row = rows[min_y + y]
        for x in range(box_w):
            idx = row[min_x + x]
            alpha = alpha_of(idx)
            if alpha == 0:
                continue
            r, g, b = palette[idx * 3 : idx * 3 + 3]
            out = ((off_y + y) * SIZE + off_x + x) * 4
            pixels[out : out + 4] = bytes((r, g, b, alpha))

    scanlines = b"".join(
        b"\x00" + bytes(pixels[y * SIZE * 4 : (y + 1) * SIZE * 4]) for y in range(SIZE)
    )
    png = (
        b"\x89PNG\r\n\x1a\n"
        + png_chunk(b"IHDR", struct.pack(">IIBBBBB", SIZE, SIZE, 8, 6, 0, 0, 0))
        + png_chunk(b"IDAT", zlib.compress(scanlines, 9))
        + png_chunk(b"IEND", b"")
    )
    with open(DST, "wb") as fh:
        fh.write(png)
    print(f"wrote {DST} ({SIZE}x{SIZE}, artwork bbox {box_w}x{box_h}, {len(png)} bytes)")


if __name__ == "__main__":
    main()
