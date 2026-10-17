#!/usr/bin/env python3
"""Check the approved TS18 marker palette against authored and packaged sources."""
import json
import re
import struct
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
        assert png[:8] == b'\x89PNG\r\n\x1a\n', folder
        width, height = struct.unpack('>II', png[16:24])
        skin = ET.parse(folder / 'symbols.xml').getroot()
        assert int(skin.find('file').get('width')) == width, folder
        assert int(skin.find('file').get('height')) == height, folder
        names = {s.get('name') for s in skin.iter('symbol')}
        assert {name[:-4] for name in PALETTE['poi_symbols']} <= names, folder

assert (ROOT / 'data/drules_in_car.bin').read_bytes() == \
       (ROOT / 'android/sdk/src/main/assets/drules_in_car.bin').read_bytes()
assert (ROOT / 'android/sdk/src/main/assets/symbols').resolve() == \
       (ROOT / 'data/symbols').resolve()
print('TS18 marker palette: 92 symbols x 2 x 6 atlases, 16 bookmarks and 15 markers verified')
