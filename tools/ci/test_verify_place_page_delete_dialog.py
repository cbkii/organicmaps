#!/usr/bin/env python3
"""Mutation regressions for captured track-delete identity and selection dismissal."""

import unittest

import verify_place_page_delete_dialog as verifier


class TrackDeleteContractTest(unittest.TestCase):
    def setUp(self):
        self.source = verifier.SOURCE.read_text(encoding="utf-8")

    def test_current_contract(self):
        self.assertEqual([], verifier.verify_sources(self.source))

    def test_guard_whitespace_is_harmless(self):
        changed = self.source.replace(
            "if (!sameTrack)\n      dismissAlertDialog();",
            "if (  !sameTrack  )\n\n        dismissAlertDialog ( );")
        # Keep the method call spelling while varying guard/newline indentation.
        changed = changed.replace("dismissAlertDialog ( );", "dismissAlertDialog();")
        self.assertNotEqual(self.source, changed)
        self.assertEqual([], verifier.verify_sources(changed))
        braced = self.source.replace("if (!sameTrack)\n      dismissAlertDialog();",
                                    "if (!sameTrack) { dismissAlertDialog(); }")
        self.assertNotEqual(self.source, braced)
        self.assertEqual([], verifier.verify_sources(braced))

    def test_mutations_reject_wrong_track_or_lifecycle(self):
        mutations = (
            ("deleteTrack(trackId)", "deleteTrack(track.getTrackId())"),
            ("currentTrack.getTrackId() == trackId", "currentTrack.getTrackId() != trackId"),
            ("previousTrack.getTrackId() == newTrack.getTrackId()", "previousTrack == newTrack"),
            ("if (!sameTrack)", "if (sameTrack)"),
        )
        for original, replacement in mutations:
            with self.subTest(original=original):
                changed = self.source.replace(original, replacement, 1)
                self.assertNotEqual(self.source, changed)
                self.assertTrue(verifier.verify_sources(changed))

    def test_comment_cannot_supply_missing_delete(self):
        changed = self.source.replace(
            "BookmarkManager.INSTANCE.deleteTrack(trackId);",
            "// BookmarkManager.INSTANCE.deleteTrack(trackId);\n"
            "BookmarkManager.INSTANCE.deleteTrack(-1);", 1)
        self.assertNotEqual(self.source, changed)
        self.assertTrue(verifier.verify_sources(changed))


if __name__ == "__main__":
    unittest.main()
