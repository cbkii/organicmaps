"""Guard the measured NOW layout against restoring the fixed line cap."""
from pathlib import Path
import sys
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT / "android/tools"))
import verify_in_car_touch_targets_rail as ribbon


class NavigationRibbonFitTest(unittest.TestCase):
    def test_current_navigation_fit_contract(self):
        ribbon.verify_navigation_refinements(ROOT)

    def test_fixed_road_line_cap_is_rejected(self):
        path = ROOT / "android/app/src/inCar/res/layout-land/layout_nav_top.xml"
        original_parse = ribbon.base.parse_xml

        def with_line_cap(source):
            parsed = original_parse(source)
            if source == path:
                for node in parsed.iter():
                    if ribbon.base.resource_id(node, f"{{{ribbon.base.ANDROID_NS}}}id") == "street":
                        node.set(f"{{{ribbon.base.ANDROID_NS}}}maxLines", "3")
            return parsed

        with patch.object(ribbon.base, "parse_xml", with_line_cap):
            with self.assertRaisesRegex(ribbon.base.VerificationError, "fixed line cap"):
                ribbon.verify_navigation_refinements(ROOT)


if __name__ == "__main__":
    unittest.main()
