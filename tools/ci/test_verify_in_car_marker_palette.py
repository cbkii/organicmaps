"""Exercise the palette verifier's XML boundary without loading generated assets."""
import ast
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


if __name__ == '__main__':
    unittest.main()
