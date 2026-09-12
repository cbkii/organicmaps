#!/usr/bin/env python3
"""Focused parser-safety tests for verify_in_car_compact_chrome.py."""

from __future__ import annotations

import importlib.util
import tempfile
import unittest
from pathlib import Path


_MODULE_PATH = Path(__file__).with_name("verify_in_car_compact_chrome.py")
_SPEC = importlib.util.spec_from_file_location("verify_in_car_compact_chrome", _MODULE_PATH)
assert _SPEC and _SPEC.loader
_VERIFY = importlib.util.module_from_spec(_SPEC)
_SPEC.loader.exec_module(_VERIFY)


class XmlParserSafetyTest(unittest.TestCase):
    def parse_values(self, xml: str) -> dict[str, str]:
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "values.xml"
            path.write_text(xml, encoding="utf-8")
            return _VERIFY.resource_values(path)

    def assert_rejected(self, xml: str) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "values.xml"
            path.write_text(xml, encoding="utf-8")
            with self.assertRaises(_VERIFY.VerificationError):
                _VERIFY.resource_values(path)

    def test_normal_android_xml_is_accepted(self) -> None:
        self.assertEqual(self.parse_values("<resources><dimen name=\"size\">1dp</dimen></resources>"), {"size": "1dp"})

    def test_doctype_is_rejected_before_elementtree(self) -> None:
        self.assert_rejected("<!DOCTYPE resources><resources/>")

    def test_entity_declaration_is_rejected(self) -> None:
        self.assert_rejected("<!ENTITY value 'unsafe'><resources><string name=\"x\">&value;</string></resources>")

    def test_external_entity_payload_is_rejected(self) -> None:
        self.assert_rejected(
            "<!DOCTYPE resources [<!ENTITY ext SYSTEM 'file:///etc/passwd'>]>"
            "<resources><string name=\"x\">&ext;</string></resources>"
        )


if __name__ == "__main__":
    unittest.main()
