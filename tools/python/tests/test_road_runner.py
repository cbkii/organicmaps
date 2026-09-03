import sys
import unittest
from pathlib import Path
from unittest import mock


sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import road_runner  # noqa: E402


class TestGetWayIds(unittest.TestCase):
    def test_returns_way_ids_and_builds_expected_request(self):
        response = object()
        with mock.patch.object(road_runner, "urlopen", return_value=response) as open_url, \
             mock.patch.object(road_runner.json, "load", return_value={"way_ids": [101, 202]}) as load_json:
            result = road_runner.get_way_ids((1.5, 2.5), (3.5, 4.5), "router.example:8080")

        self.assertEqual(result, [101, 202])
        open_url.assert_called_once_with(
            "http://router.example:8080/wayid?z=18&loc=1.5,2.5&loc=3.5,4.5"
        )
        load_json.assert_called_once_with(response)

    def test_missing_way_ids_returns_empty_list(self):
        with mock.patch.object(road_runner, "urlopen", return_value=object()), \
             mock.patch.object(road_runner.json, "load", return_value={"status": "ok"}):
            self.assertEqual(road_runner.get_way_ids((0, 0), (1, 1), "localhost"), [])


if __name__ == "__main__":
    unittest.main()
