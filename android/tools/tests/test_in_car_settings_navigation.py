"""Static InCar navigation-contract guard; instrumented touch checks remain separate."""
from pathlib import Path
import re
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[3]
RES = ROOT / "android/app/src/main/res"
ANDROID_KEY = "{http://schemas.android.com/apk/res/android}key"
FRAGMENT = ROOT / "android/app/src/main/java/app/organicmaps/settings"
MAP_ACTIVITY = ROOT / "android/app/src/main/java/app/organicmaps/MwmActivity.java"

SECTIONS = ["navigation", "driving", "map_display", "places", "offline", "controls", "privacy"]
CATEGORIES = {
    "navigation": ["@string/pref_3d", "@string/pref_auto_zoom",
                   "@string/prefs_routing", "in_car_navigation_action_delay",
                   "in_car_route_resume_minutes", "@string/pref_tts_screen",
                   "@string/pref_in_car_navigation_fallback_mode", "in_car_speed_edge_warning"],
    "driving": ["@string/pref_in_car_driving_view_mode", "@string/pref_in_car_auto_return_driving_view",
                "@string/pref_auto_follow_location_on_launch", "@string/pref_in_car_startup_map_view",
                "in_car_compact_startup", "in_car_location_access",
                "@string/pref_in_car_location_disabled_warning",
                "@string/pref_in_car_open_location_settings", "in_car_location_diagnostics"],
    "map_display": ["@string/pref_map_appearance", "@string/pref_in_car_optimised_visuals",
                    "in_car_map_layers", "@string/pref_bg_tiles_screen",
                    "@string/pref_show_downloaded_regions", "@string/pref_map_locale",
                    "@string/pref_transliteration", "@string/pref_large_fonts_size",
                    "@string/pref_bookmarks_text_placement", "@string/pref_munits"],
    "places": ["@string/pref_in_car_quick_destinations",
               "@string/pref_track_recording_auto_resume"],
    "offline": ["@string/pref_autodownload", "@string/pref_use_mobile_data",
                "@string/pref_in_car_map_age_warning", "@string/pref_storage"],
    "controls": ["in_car_control_position", "@string/pref_power_management",
                 "@string/pref_in_car_budget_rendering", "@string/pref_3d_buildings",
                 "@string/pref_keep_screen_on", "@string/pref_show_on_lock_screen"],
    "privacy": ["@string/pref_search_history", "@string/pref_play_services",
                "@string/pref_enable_logging", "@string/pref_help"],
}
NONINTERACTIVE = {"@string/pref_osm_profile", "@string/pref_in_car_settings",
                  "@string/pref_auto_night_in_navigation",
                  "@string/pref_emulate_bad_external_storage",
                  "@string/pref_show_zoom_buttons"}

class InCarSettingsNavigationTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.layout = ET.parse(RES / "xml/prefs_in_car_home.xml").getroot()
        cls.main = ET.parse(RES / "xml/prefs_main.xml").getroot()
        cls.vehicle = ET.parse(RES / "xml/prefs_in_car.xml").getroot()

    def test_seven_stable_root_screens(self):
        screens = list(self.layout)
        self.assertEqual([x.attrib[ANDROID_KEY] for x in screens],
                         [f"in_car_section_{name}" for name in SECTIONS])
        for screen in screens:
            self.assertTrue(screen.tag.endswith("PreferenceScreen"))

    def test_each_option_has_one_owner_and_existing_key(self):
        keys = []
        for section, screen in zip(SECTIONS, self.layout):
            found = []
            for category in screen:
                self.assertTrue(category.tag.endswith("PreferenceCategory"))
                for item in category:
                    found.append(item.attrib[ANDROID_KEY])
            self.assertEqual(found, CATEGORIES[section], section)
            keys.extend(found)
        self.assertEqual(len(keys), 43)
        self.assertEqual(len(keys), len(set(keys)))
        originals = {x.attrib[ANDROID_KEY] for root in (self.main, self.vehicle)
                     for x in root.iter() if ANDROID_KEY in x.attrib}
        self.assertTrue(set(keys).issubset(originals))
        self.assertTrue(NONINTERACTIVE.isdisjoint(keys))

    def test_vehicle_screen_reuses_existing_backend_not_mobile(self):
        activity = (FRAGMENT / "SettingsActivity.java").read_text()
        owner = (FRAGMENT / "InCarSettingsFragment.java").read_text()
        settings = (FRAGMENT / "SettingsPrefsFragment.java").read_text()
        self.assertIn("BuildConfig.IS_IN_CAR ? InCarSettingsFragment.class : SettingsPrefsFragment.class", activity)
        self.assertIn("stackFragment(getFragmentClass()", activity)
        self.assertIn("extends SettingsPrefsFragment", owner)
        self.assertIn("R.xml.prefs_in_car_home", owner)
        self.assertIn("if (findPreference(getString(R.string.pref_3d_buildings)) != null)", settings)

    def test_original_automotive_dimensions_unchanged(self):
        source = (RES / "xml/prefs_in_car_home.xml").read_text()
        self.assertNotRegex(source, r"(?:minHeight|minWidth|layout_width|layout_height)=.*(?:[0-9]+dp)")
        self.assertIn("in_car_section_controls", source)

    def test_menu_single_track_toggle_and_more_actions(self):
        java = MAP_ACTIVITY.read_text()
        main = java.split("if (id.equals(MAIN_MENU_ID))", 1)[1].split(
            "if (id.equals(ADVANCED_MENU_ID))", 1)[0]
        car = main.split("if (BuildConfig.IS_IN_CAR)", 1)[1].split("return items;", 1)[0]
        self.assertIn("R.string.bookmarks", car)
        self.assertIn("R.string.download_maps", car)
        self.assertIn("R.string.layers_title", car)
        self.assertIn("R.string.settings", car)
        self.assertIn("R.string.in_car_more_actions", car)
        self.assertNotIn("R.string.share_my_location", car)
        self.assertNotIn("R.string.placepage_add_place_button", car)
        more = java.split("if (id.equals(ADVANCED_MENU_ID))", 1)[1].split(
            "mDonatesUrl = Utils.getDonateUrl", 1)[0]
        self.assertIn("R.string.share_my_location", more)
        self.assertIn("R.string.placepage_add_place_button", more)
        self.assertNotIn("onTrackRecordingOptionSelected", more)

if __name__ == "__main__":
    unittest.main()
