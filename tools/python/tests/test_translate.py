import os
import sys
import unittest
from pathlib import Path
from unittest import mock


sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import translate  # noqa: E402


class TestGetApiKey(unittest.TestCase):
    def test_free_key_takes_precedence(self):
        with mock.patch.dict(
            os.environ,
            {"DEEPL_FREE_API_KEY": "free-key", "DEEPL_API_KEY": "paid-key"},
            clear=True,
        ):
            self.assertEqual(translate.get_api_key(), "free-key")

    def test_paid_key_is_fallback(self):
        with mock.patch.dict(os.environ, {"DEEPL_API_KEY": "paid-key"}, clear=True):
            self.assertEqual(translate.get_api_key(), "paid-key")

    def test_missing_key_exits_with_error(self):
        with mock.patch.dict(os.environ, {}, clear=True), mock.patch("builtins.print") as output:
            with self.assertRaises(SystemExit) as raised:
                translate.get_api_key()

        self.assertEqual(raised.exception.code, 1)
        output.assert_called_once_with(
            "Error: DEEPL_FREE_API_KEY or DEEPL_API_KEY env variables are not set"
        )


if __name__ == "__main__":
    unittest.main()
