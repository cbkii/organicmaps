#!/usr/bin/env python3
"""Pin the direct-display InCar My Position and Track Recording control contracts."""

from __future__ import annotations

from pathlib import Path
import re
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
ANDROID_NS = "http://schemas.android.com/apk/res/android"
APP_NS = "http://schemas.android.com/apk/res-auto"


def fail(message: str) -> None:
    raise SystemExit(f"ERROR: {message}")


def require(condition: bool, message: str) -> None:
    if not condition:
        fail(message)


def read(relative: str) -> str:
    return (ROOT / relative).read_text(encoding="utf-8")


def method_body(source: str, anchor: str) -> str:
    start = source.find(anchor)
    if start < 0:
        fail(f"missing source anchor: {anchor}")
    opening = source.find("{", start + len(anchor))
    if opening < 0:
        fail(f"missing method body after: {anchor}")
    depth = 0
    for index in range(opening, len(source)):
        char = source[index]
        if char == "{":
            depth += 1
        elif char == "}":
            depth -= 1
            if depth == 0:
                return source[opening + 1 : index]
    fail(f"unterminated method body after: {anchor}")


def by_id(path: str, wanted: str) -> ET.Element:
    root = ET.parse(ROOT / path).getroot()
    attr = f"{{{ANDROID_NS}}}id"
    for element in root.iter():
        value = element.attrib.get(attr, "")
        if value in (f"@+id/{wanted}", f"@id/{wanted}"):
            return element
    fail(f"{path}: missing @{wanted}")


def verify_my_position() -> None:
    layout = "android/app/src/inCar/res/layout/map_buttons_myposition.xml"
    button = by_id(layout, "my_position")
    require(button.attrib.get(f"{{{APP_NS}}}backgroundTint") == "@android:color/transparent",
            f"{layout}: My Position must have no visible button background")
    require(button.attrib.get(f"{{{APP_NS}}}rippleColor") == "@android:color/transparent",
            f"{layout}: My Position ripple must not recreate visible button chrome")
    require(button.attrib.get(f"{{{APP_NS}}}fabCustomSize") == "@dimen/in_car_map_primary_button_size",
            f"{layout}: icon-only My Position must retain the automotive hit target")

    java = read("android/app/src/main/java/app/organicmaps/widget/menu/MyPositionButton.java")
    click = method_body(java, "private void onInCarClick(")
    for token in ("LocationState.nativeRecenterToCurrentPosition()",
                  "LocationState.getMode() == LocationState.NOT_FOLLOW_NO_POSITION"):
        require(token in click, f"MyPositionButton InCar click contract missing {token}")
    for forbidden in ("nativeSwitchToNextMode", "setExplicitLocationOff", "setDrivingViewEnabled"):
        require(forbidden not in click, f"MyPositionButton InCar click must not change location mode via {forbidden}")
    render = method_body(java, "private void updateInCar()")
    require("R.drawable.ic_not_follow" in render, "InCar My Position must use one stable crosshair icon")
    require("setSelected(false)" in render and "clearAnimation()" in render,
            "InCar My Position must not render mode-selected/pending state")

    bridge = read("android/sdk/src/main/cpp/app/organicmaps/sdk/LocationState.cpp")
    recenter = method_body(bridge, "bool RecenterToCurrentPosition()")
    require("SetModelViewCenter" in recenter and "kDoNotChangeZoom" in recenter,
            "native InCar recenter must reuse the existing mode-neutral camera-centre authority")
    for forbidden in ("SwitchMyPositionNextMode", "SetDrivingView", "ChangeMode"):
        require(forbidden not in recenter, f"native recenter must not mutate My Position mode via {forbidden}")
    jni = method_body(
        bridge, "Java_app_organicmaps_sdk_location_LocationState_nativeRecenterToCurrentPosition")
    require("m_recenterPending = true" in jni,
            "a missing fresh fix must retain exactly one pending recenter request")


def verify_track_recording() -> None:
    zoom = "android/app/src/inCar/res/layout/map_buttons_zoom.xml"
    status = by_id(zoom, "track_recording_status")
    require(status.attrib.get(f"{{{ANDROID_NS}}}visibility") == "gone",
            f"{zoom}: Track Recording compatibility view must remain hidden")
    require(status.attrib.get(f"{{{ANDROID_NS}}}importantForAccessibility") == "no",
            f"{zoom}: hidden Track Recording view must not remain an accessibility action")

    prefs = read("android/app/src/main/res/xml/prefs_in_car.xml")
    require("pref_in_car_show_track_recording_button" not in prefs,
            "InCar Settings must not expose a Show Track Recording map-button preference")
    settings = read("android/app/src/main/java/app/organicmaps/incar/InCarSettingsStore.java")
    require("KEY_SHOW_TRACK_RECORDING_BUTTON" not in settings,
            "obsolete Track Recording map-button preference storage must stay removed")
    show = method_body(settings, "public static boolean isShowTrackRecordingButton(")
    require(re.search(r"\breturn\s+false\s*;", show) is not None,
            "shared MapButtons compatibility seam must permanently hide Track Recording in InCar")

    fragment = read("android/app/src/main/java/app/organicmaps/util/bottomsheet/MenuBottomSheetFragment.java")
    adapt = method_body(fragment, "private void adaptInCarMenu(")
    for token in ("MAIN_MENU_ID", "ADVANCED_MENU_ID", "MenuBottomSheetItem.checkable(",
                  "R.string.track_recording_title", "R.drawable.ic_track_recording_off", "item.iconRes == 0"):
        require(token in adapt, f"InCar main-menu Track Recording/icon contract missing {token}")
    toggle = method_body(fragment, "private void toggleInCarTrackRecording()")
    require("TrackRecorder.nativeIsTrackRecordingEnabled()" in toggle,
            "Track Recording switch must observe the actual native recorder state at action time")
    require("activity.onTrackRecordingSaved()" in toggle,
            "Track Recording ON -> OFF must use the existing save-and-stop authority")
    require("MapButtonsController.MapButtons.trackRecordingStatus" in toggle,
            "Track Recording OFF -> ON must reuse the existing start/permission authority")

    item = read("android/app/src/main/java/app/organicmaps/util/bottomsheet/MenuBottomSheetItem.java")
    require("public final boolean checkable" in item and "public final boolean checked" in item,
            "menu model must carry immutable checkable state")
    adapter = read("android/app/src/main/java/app/organicmaps/util/bottomsheet/MenuAdapter.java")
    bind = method_body(adapter, "public void onBindViewHolder(")
    require(bind.find("toggle.setOnCheckedChangeListener(null)") < bind.find("toggle.setChecked(item.checked)"),
            "menu binding must detach the switch listener before applying observed state")
    require("if (checked != item.checked)" in bind,
            "menu switch must not dispatch merely because RecyclerView rebound the row")
    require("v -> toggle.setChecked(!toggle.isChecked())" in bind,
            "row and switch must converge on one switch command path")

    row = by_id("android/app/src/main/res/layout/bottom_sheet_menu_item.xml", "bottom_sheet_menu_item_switch")
    require(row.attrib.get(f"{{{ANDROID_NS}}}visibility") == "gone",
            "ordinary menu rows must not gain a visible switch")
    require(row.attrib.get(f"{{{APP_NS}}}showText") == "true",
            "Track Recording switch must expose explicit ON/OFF text")


if __name__ == "__main__":
    verify_my_position()
    verify_track_recording()
    print("InCar location/recording controls verification PASSED")
