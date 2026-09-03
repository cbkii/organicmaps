import datetime
import re
import unittest

from maps_generator.generator.exceptions import ParseError
from maps_generator.generator import statistics


class TestStatistics(unittest.TestCase):
    def test_process_stat_aggregates_requested_fields(self):
        config = [
            (re.compile(r"^highway"), "len", "Road length"),
            (re.compile(r"^highway"), "cnt_names", "Named roads"),
            (re.compile(r"^park"), "area", "Park area"),
            (re.compile(r"^poi"), "cnt", "POIs"),
        ]
        stats = [
            {"name": "highway-primary", "cnt": 2, "len": 12.5, "area": 0.0, "names": 1},
            {"name": "highway-secondary", "cnt": 3, "len": 7.5, "area": 0.0, "names": 2},
            {"name": "park", "cnt": 1, "len": 0.0, "area": 42.0, "names": 1},
            {"name": "poi-cafe", "cnt": 4, "len": 0.0, "area": 0.0, "names": 4},
        ]

        result = statistics.process_stat(config, stats)

        self.assertEqual(result[str(config[0][0]) + "len"], 20.0)
        self.assertEqual(result[str(config[1][0]) + "cnt_names"], 3)
        self.assertEqual(result[str(config[2][0]) + "area"], 42.0)
        self.assertEqual(result[str(config[3][0]) + "cnt"], 4)

    def test_format_res_returns_units_and_rejects_unknown_type(self):
        self.assertEqual(statistics.format_res(1.5, "len"), (1.5, "m"))
        self.assertEqual(statistics.format_res(2.5, "area"), (2.5, "m²"))
        self.assertEqual(statistics.format_res(3, "cnt"), (3, "pc"))
        self.assertEqual(statistics.format_res(4, "cnt_names"), (4, "pc"))
        with self.assertRaisesRegex(ParseError, "Unknown type unknown"):
            statistics.format_res(0, "unknown")

    def test_parse_time_supports_days_fractional_seconds_and_invalid_input(self):
        self.assertEqual(statistics.parse_time("1:02:03"), datetime.timedelta(hours=1, minutes=2, seconds=3))
        self.assertEqual(
            statistics.parse_time("2 days, 3:04:05.123456"),
            datetime.timedelta(days=2, hours=3, minutes=4, seconds=5, microseconds=123456),
        )
        self.assertEqual(statistics.parse_time("45.5"), datetime.timedelta(seconds=45, microseconds=500000))
        self.assertIsNone(statistics.parse_time("not-a-duration"))

    def test_diff_handles_relative_changes_and_zero_baseline(self):
        old = {
            "roads": {"quantity": 100.0, "unit": "m"},
            "pois": {"quantity": 0.0, "unit": "pc"},
            "empty": {"quantity": 0.0, "unit": "pc"},
        }
        new = {
            "roads": {"quantity": 125.0, "unit": "m"},
            "pois": {"quantity": 5.0, "unit": "pc"},
            "empty": {"quantity": 0.0, "unit": "pc"},
        }

        self.assertEqual(
            statistics.diff(new, old),
            [
                ("roads", 100.0, 125.0, 25, 25.0, "m"),
                ("pois", 0.0, 5.0, 100, 5.0, "pc"),
                ("empty", 0.0, 0.0, 0, 0.0, "pc"),
            ],
        )

    def test_diff_rejects_different_dictionary_lengths(self):
        with self.assertRaises(AssertionError):
            statistics.diff({"one": {"quantity": 1, "unit": "pc"}}, {})


if __name__ == "__main__":
    unittest.main()
