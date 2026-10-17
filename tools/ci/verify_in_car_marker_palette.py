#!/usr/bin/env python3
"""Check the approved TS18 marker palette against authored and packaged sources."""
import json
import re
import struct
import zlib
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
PALETTE = json.loads((ROOT / 'data/styles/in_car/TS18_marker_palette_light_dark.json').read_text())
MARKERS = {
    'Selected object': 'Selection', 'Location arrow': 'Arrow3D',
    'Search default': 'SearchmarkDefault', 'Search pending': 'SearchmarkPreparing',
    'Search unavailable': 'SearchmarkNotAvailable',
    'Unavailable selected': 'SearchmarkSelectedNotAvailable',
    'Rating good': 'RatingGood', 'Rating bad': 'RatingBad',
    'Rating unrated': 'RatingNone', 'Speed camera': 'SpeedCameraMarkBg',
    'Route via point': 'RouteMarkInterBg',
    'Transit transfer outer': 'TransitTransferOuterMarker',
    'Transit transfer inner': 'TransitTransferInnerMarker',
    'Transit stop inner': 'TransitStopInnerMarker',
    'Hotel price text': 'HotelPriceText',
}
assert len(PALETTE['poi_symbols']) == 92
assert len(PALETTE['bookmarks']) == 16
assert set(PALETTE['mapcss_markers']) == set(MARKERS)


def rgba_rows(png):
    """Decode the checked-in 8-bit RGBA atlas with PNG's five row filters."""
    assert png[:8] == b'\x89PNG\r\n\x1a\n'
    offset, compressed, width, height = 8, bytearray(), None, None
    while offset < len(png):
        length = struct.unpack_from('>I', png, offset)[0]
        kind = png[offset + 4:offset + 8]
        data = png[offset + 8:offset + 8 + length]
        if kind == b'IHDR':
            width, height, depth, colour, compression, filtering, interlace = struct.unpack('>IIBBBBB', data)
            assert (depth, colour, compression, filtering, interlace) == (8, 6, 0, 0, 0)
        elif kind == b'IDAT':
            compressed.extend(data)
        offset += length + 12
    assert width and height
    raw = zlib.decompress(compressed)
    stride, pos, previous, rows = width * 4, 0, bytearray(width * 4), []
    for _ in range(height):
        method = raw[pos]
        row = bytearray(raw[pos + 1:pos + 1 + stride])
        pos += stride + 1
        assert method in (0, 1, 2, 3, 4)
        if method:
            for i in range(stride):
                left = row[i - 4] if i >= 4 else 0
                above = previous[i]
                upper_left = previous[i - 4] if i >= 4 else 0
                if method == 1:
                    predictor = left
                elif method == 2:
                    predictor = above
                elif method == 3:
                    predictor = (left + above) // 2
                else:
                    p = left + above - upper_left
                    distances = (abs(p - left), abs(p - above), abs(p - upper_left))
                    predictor = (left, above, upper_left)[distances.index(min(distances))]
                row[i] = (row[i] + predictor) & 255
        rows.append(row)
        previous = row
    assert pos == len(raw)
    return width, height, rows


def has_palette_pixels(rows, symbol, colour):
    target = bytes.fromhex(colour.lstrip('#'))[:3]
    matches = 0
    for y in range(int(symbol['minY']), int(symbol['maxY'])):
        row = rows[y]
        for x in range(int(symbol['minX']), int(symbol['maxX'])):
            pixel = row[4 * x:4 * x + 4]
            if pixel[3] >= 40 and max(abs(pixel[i] - target[i]) for i in range(3)) <= 25:
                matches += 1
                if matches >= 2:
                    return True
    return False


for variant in ('light', 'dark'):
    style = (ROOT / f'data/styles/in_car/{variant}/style.mapcss').read_text()
    for key, item in PALETTE['bookmarks'].items():
        assert re.search(r'\bBookmark' + key + r'-color:\s*' + item[variant] + r'\s*;', style), (variant, key)
    for key, item in PALETTE['mapcss_markers'].items():
        assert re.search(r'\b' + MARKERS[key] + r'-color:\s*' + item[variant] + r'\s*;', style), (variant, key)
    for name, item in PALETTE['poi_symbols'].items():
        svg = ROOT / f'data/styles/default/{variant}/symbols/{name}'
        assert svg.is_file(), svg
        ET.parse(svg)
        assert item[variant].lower() in svg.read_text().lower(), (variant, name)

    for density in ('mdpi', 'hdpi', 'xhdpi', '6plus', 'xxhdpi', 'xxxhdpi'):
        folder = ROOT / f'data/symbols/{density}/{variant}'
        png = (folder / 'symbols.png').read_bytes()
        width, height, rows = rgba_rows(png)
        skin = ET.parse(folder / 'symbols.xml').getroot()
        assert int(skin.find('file').get('width')) == width, folder
        assert int(skin.find('file').get('height')) == height, folder
        symbols = {s.get('name'): s.attrib for s in skin.iter('symbol')}
        names = set(symbols)
        assert {name[:-4] for name in PALETTE['poi_symbols']} <= names, folder
        for name, item in PALETTE['poi_symbols'].items():
            assert has_palette_pixels(rows, symbols[name[:-4]], item[variant]), (folder, name)

assert (ROOT / 'data/drules_in_car.bin').read_bytes() == \
       (ROOT / 'android/sdk/src/main/assets/drules_in_car.bin').read_bytes()
assert (ROOT / 'android/sdk/src/main/assets/symbols').resolve() == \
       (ROOT / 'data/symbols').resolve()
print('TS18 marker palette: 92 symbols x 2 x 6 atlases, 16 bookmarks and 15 markers verified')
