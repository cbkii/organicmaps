"""Exercise the palette verifier's XML boundary without loading generated assets."""
import ast
import json
from pathlib import Path
import re
import tempfile
import unittest
import xml.etree.ElementTree as ET

SOURCE = Path(__file__).with_name('verify_in_car_marker_palette.py')
module = ast.parse(SOURCE.read_text())
namespace = {'re': re, 'ET': ET}
exec(compile(ast.Module(body=[node for node in module.body
                             if isinstance(node, ast.FunctionDef) and node.name == 'parse_xml'],
                        type_ignores=[]), str(SOURCE), 'exec'), namespace)


class PaletteXmlTests(unittest.TestCase):
    def parse(self, data):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'symbol.xml'
            path.write_bytes(data)
            return namespace['parse_xml'](path)

    def test_generator_name_only_doctype_is_accepted(self):
        self.assertEqual('root', self.parse(b'<!DOCTYPE skin>\n<root/>').tag)
        self.assertEqual('svg', self.parse(b'<svg><path fill="#aabbcc"/></svg>').tag)

    def test_external_entities_and_internal_subsets_are_rejected(self):
        for data in (b'<!DOCTYPE svg SYSTEM "file:///etc/passwd"><svg/>',
                     b'<!DOCTYPE skin [<!ENTITY x "expanded">]><root>&x;</root>',
                     b'<!DOCTYPE skin><!DOCTYPE svg><svg/>'):
            with self.subTest(data=data), self.assertRaises(SystemExit):
                self.parse(data)

    def test_non_utf8_declarations_cannot_bypass_boundary(self):
        with self.assertRaises(UnicodeError):
            self.parse('<!DOCTYPE svg [<!ENTITY x "expanded">]><svg/>'.encode('utf-16'))

    def test_no_palette_checks_disappear_under_optimisation(self):
        self.assertFalse(any(isinstance(node, ast.Assert) for node in ast.walk(module)))



class InCarMarkerContrastTests(unittest.TestCase):
    """Cheap, source-only checks for editable named-colour marker tokens."""

    ROOT = Path(__file__).resolve().parents[2]

    @staticmethod
    def luminance(rgb):
        channels = [int(rgb[index:index + 2], 16) / 255 for index in (1, 3, 5)]
        linear = [c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4
                  for c in channels]
        return sum(component * weight for component, weight
                   in zip(linear, (0.2126, 0.7152, 0.0722)))

    @classmethod
    def contrast(cls, a, b):
        x, y = cls.luminance(a), cls.luminance(b)
        return (max(x, y) + 0.05) / (min(x, y) + 0.05)

    def test_proposed_bookmark_fills_have_mode_appropriate_icon_contrast(self):
        data = json.loads((self.ROOT / 'data/styles/in_car/TS18_marker_palette_light_dark.json').read_text())
        self.assertEqual(16, len(data['bookmarks']))
        for name, palette in data['bookmarks'].items():
            with self.subTest(bookmark=name, mode='light'):
                self.assertGreaterEqual(self.contrast('#FFFFFF', palette['light']), 7.5)
            with self.subTest(bookmark=name, mode='dark'):
                self.assertGreaterEqual(self.contrast('#000000', palette['dark']), 8.0)

    def test_camera_red_and_native_compound_colours_are_mode_consistent(self):
        for mode, detail in (('light', '#FFFFFF'), ('dark', '#000000')):
            with self.subTest(mode=mode):
                content = (self.ROOT / f'data/styles/in_car/{mode}/style.mapcss').read_text()
                def token(name):
                    values = re.findall(r'(?m)^\s*' + re.escape(name)
                                        + r'-color:\s*(#[0-9A-Fa-f]{6})\s*;', content)
                    self.assertEqual(1, len(values), name)
                    return values[0].upper()
                self.assertEqual('#FF0000', token('SpeedCameraMarkBg'))
                self.assertEqual(detail, token('SpeedCameraMarkText'))
                self.assertEqual(detail, token('SpeedCameraMarkOutline'))
                self.assertEqual(detail, token('RouteMarkInterText'))
                self.assertEqual(detail, token('RouteMarkInterOutline'))
                self.assertGreaterEqual(
                    self.contrast(detail, token('RouteMarkInterBg')), 7.5 if mode == 'light' else 8.0)

    def test_poi_sprites_are_outside_the_runtime_colour_contract(self):
        data = json.loads((self.ROOT / 'data/styles/in_car/TS18_marker_palette_light_dark.json').read_text())
        self.assertEqual(92, len(data['poi_symbols']))
        self.assertEqual('#FF0000', data['mapcss_markers']['Speed camera']['light'])
        self.assertEqual('#FF0000', data['mapcss_markers']['Speed camera']['dark'])


if __name__ == '__main__':
    unittest.main()
