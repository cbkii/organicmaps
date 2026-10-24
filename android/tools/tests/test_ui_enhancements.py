"""Mutation coverage for the executable automotive UI gate."""
import importlib.util
from pathlib import Path
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[3]
spec = importlib.util.spec_from_file_location("touch_targets", ROOT / "android/tools/verify_in_car_touch_targets.py")
verifier = importlib.util.module_from_spec(spec)
spec.loader.exec_module(verifier)


class UiEnhancementGateTest(unittest.TestCase):
    def test_current_contract(self):
        verifier.verify_settings_and_speed_edge(ROOT)
        verifier.verify_resources(ROOT)

    def test_invalid_sizing_and_banner_contracts_are_rejected(self):
        mutations = [
            ("android/app/src/main/res/values/in_car_runtime_ui.xml", "76dp", "69dp", verifier.verify_resources),
            ("android/app/src/main/res/layout/in_car_speed_warning_overlay.xml", 'android:alpha="0.5"', 'android:alpha="1.0"', verifier.verify_settings_and_speed_edge),
            ("android/app/src/main/java/app/organicmaps/settings/BaseXmlSettingsFragment.java", "if (!BuildConfig.IS_IN_CAR)", "if (false)", verifier.verify_settings_and_speed_edge),
            ("android/app/src/main/java/app/organicmaps/settings/InCarPreferenceRowPolicy.java", "instanceof PreferenceCategory", "instanceof Preference", verifier.verify_settings_and_speed_edge),
        ]
        for name, old, new, check in mutations:
            with self.subTest(name=name), tempfile.TemporaryDirectory() as directory:
                root = Path(directory)
                # Symlink unchanged files; each mutation owns only a new local copy.
                for folder in ("android/app/src/main/res/values", "android/app/src/inCar/res/values",
                               "android/app/src/inCar/res/layout", "android/app/src/main/res/layout",
                               "android/app/src/main/java/app/organicmaps/settings", "android/app/src/main/java/app/organicmaps/incar"):
                    target = root / folder
                    target.mkdir(parents=True, exist_ok=True)
                    for source in (ROOT / folder).iterdir():
                        if source.is_file():
                            (target / source.name).symlink_to(source)
                path = root / name
                content = path.read_text()
                self.assertIn(old, content)
                path.unlink()
                path.write_text(content.replace(old, new))
                with self.assertRaises(verifier.VerificationError):
                    check(root)
        verifier.verify_settings_and_speed_edge(ROOT)
