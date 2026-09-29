#!/usr/bin/env python3
"""Verify the destructive track-delete confirmation stays bound to one track identity."""

from __future__ import annotations

from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / "android/app/src/main/java/app/organicmaps/widget/placepage/PlacePageController.java"


def method_body(text: str, signature: str) -> str:
    start = text.find(signature)
    if start < 0:
        raise ValueError(f"missing method: {signature}")
    brace = text.find("{", start)
    if brace < 0:
        raise ValueError(f"missing method body: {signature}")
    depth = 0
    for index in range(brace, len(text)):
        if text[index] == "{":
            depth += 1
        elif text[index] == "}":
            depth -= 1
            if depth == 0:
                return text[brace + 1 : index]
    raise ValueError(f"unterminated method body: {signature}")


def main() -> int:
    text = SOURCE.read_text(encoding="utf-8")
    errors: list[str] = []
    try:
        show = method_body(text, "void showTrackDeleteAlertDialog()")
        dismiss = method_body(text, "void dismissAlertDialog()")
        changed = method_body(text, "public void onChanged(@Nullable MapObject mapObject)")
        destroyed = method_body(text, "public void onDestroyView()")
    except ValueError as error:
        print(f"ERROR: {error}", file=sys.stderr)
        return 1

    required_show = (
        "final long trackId = track.getTrackId();",
        "BookmarkManager.INSTANCE.deleteTrack(trackId)",
        "if (mAlertDialog == dialog)",
        "mAlertDialog = null;",
    )
    for token in required_show:
        if token not in show:
            errors.append(f"showTrackDeleteAlertDialog missing identity/lifecycle contract: {token}")

    if re.search(r"if\s*\(mAlertDialog\s*!=\s*null\)[\s\S]*?mAlertDialog\.show\(\)", show):
        errors.append("showTrackDeleteAlertDialog must not reuse a dialog carrying an earlier captured track id")

    captured_id = show.find("final long trackId = track.getTrackId();")
    positive = show.find("BookmarkManager.INSTANCE.deleteTrack(trackId)")
    if captured_id >= 0 and positive >= 0 and positive < captured_id:
        errors.append("delete confirmation must consume the track id captured before the dialog is built")

    clear = dismiss.find("mAlertDialog = null;")
    dismiss_call = dismiss.find("alertDialog.dismiss();")
    if clear < 0 or dismiss_call < 0 or clear > dismiss_call:
        errors.append("dismissAlertDialog must clear the retained dialog before dismissing the old instance")

    # A new selection invalidates any outstanding confirmation. The same track can keep
    # its current dialog, while view destruction always dismisses it.
    if "previousTrack.getTrackId() == newTrack.getTrackId()" not in changed or "if (!sameTrack)\n      dismissAlertDialog();" not in changed:
        errors.append("Place Page selection must dismiss a confirmation when track identity changes")
    if "mAlertDialog = null;" in changed or "showTrackDeleteAlertDialog();" in changed:
        errors.append("Place Page updates must not orphan or recreate an existing confirmation")
    if "dismissAlertDialog();" not in destroyed:
        errors.append("Place Page view destruction must dismiss its confirmation")

    if errors:
        print("Place Page track-delete dialog verification FAILED:", file=sys.stderr)
        for error in errors:
            print(f"- {error}", file=sys.stderr)
        return 1

    print("Place Page track-delete dialog verification PASSED")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
