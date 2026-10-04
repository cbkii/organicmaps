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
    require(button.attrib.get(f"{{{APP_NS}}}rippleColor") == "?attr/colorControlHighlight",
            f"{layout}: My Position must retain transient pressed feedback")
    require(button.attrib.get(f"{{{APP_NS}}}fabCustomSize") == "@dimen/in_car_map_primary_button_size",
            f"{layout}: icon-only My Position must retain the automotive hit target")

    java = read("android/app/src/main/java/app/organicmaps/widget/menu/MyPositionButton.java")
    activity = read("android/app/src/main/java/app/organicmaps/MwmActivity.java")
    click = method_body(activity, "case myPosition ->")
    incar = method_body(click, "if (BuildConfig.IS_IN_CAR)")
    require("LocationState.nativeRecenterToCurrentPosition()" in incar,
            "InCar click must request native recenter")
    for forbidden in ("nativeSwitchToNextMode", "setExplicitLocationOff", ".recenter()"):
        require(forbidden not in incar, f"InCar click must not change intent via {forbidden}")
    recovery = method_body(activity, "private void restartLocationAfterAvailabilityConfirmed(")
    explicit = method_body(recovery, "if (BuildConfig.IS_IN_CAR && mInCarRecenterRecovery)")
    require("resumeLocationInForeground(true)" in explicit, "recenter recovery must reuse existing provider acquisition")
    for forbidden in ("nativeSwitchToNextMode", "setExplicitLocationOff", ".recenter()"):
        require(forbidden not in explicit, f"recenter acquisition must preserve intent: {forbidden}")
    stopped = method_body(activity, "protected void onStop()")
    require("mInCarRecenterRecovery = false" not in stopped,
            "settings handoff must retain bounded recenter recovery context")
    require("savedInstanceState.getBoolean(IN_CAR_RECENTER_RECOVERY" in activity,
            "configuration recreation must retain Android recovery context")
    render = method_body(java, "private void updateInCar()")
    require("R.drawable.ic_not_follow" in render, "InCar My Position must use one stable crosshair icon")
    require("setSelected(false)" in render and "clearAnimation()" in render,
            "InCar My Position must not render mode-selected/pending state")

    bridge = read("android/sdk/src/main/cpp/app/organicmaps/sdk/LocationState.cpp")
    jni = method_body(bridge, "Java_app_organicmaps_sdk_location_LocationState_nativeRecenterToCurrentPosition")
    require("RecenterMyPositionPreservingMode()" in jni, "JNI must dispatch to native camera owner")
    require("CurrentPositionState" not in bridge and "g_currentPosition" not in bridge,
            "JNI must not maintain a second provider position cache")
    controller = read("libs/drape_frontend/my_position_controller.cpp")
    recenter = method_body(controller, "void MyPositionController::RecenterPreservingMode()")
    for token in ("m_isPositionAssigned", "IsFreshPositionObservation", "m_recenterPending = true",
                  "m_recenterPending = false", "m_position", "m_drawDirection", "GetRoutingRotationPixelCenter()",
                  "m_visiblePixelRect.Center()", "kDoNotChangeZoom"):
        require(token in recenter, f"native recenter owner missing {token}")
    for forbidden in ("ChangeMode", "NextMode", "SetDrivingView"):
        require(re.search(r"\b" + forbidden + r"\s*\(", recenter) is None,
                f"native recenter must not change intent via {forbidden}")
    fresh = method_body(controller, "void MyPositionController::RefreshLocationFreshness(")
    require("if (m_recenterPending)" in fresh and "RecenterPreservingMode()" in fresh,
            "accepted fresh native position must consume a pending request")


def verify_track_recording() -> None:
    zoom = "android/app/src/inCar/res/layout/map_buttons_zoom.xml"
    require("track_recording_status" not in read(zoom), "InCar must remove the recording map FAB")
    controller = read("android/app/src/main/java/app/organicmaps/maplayer/MapButtonsController.java")
    create = method_body(controller, "public View onCreateView(")
    owning_layouts = method_body(create, "if (BuildConfig.IS_IN_CAR)")
    for mode in ("regular", "navigation"):
        name = f"in_car_map_buttons_layout_{mode}"
        require(f"layout = R.layout.{name}" in owning_layouts,
                f"InCar must select its dedicated {mode} layout before Android qualifier matching")
        root = ET.parse(ROOT / f"android/app/src/main/res/layout/{name}.xml").getroot()
        require(all(element.attrib.get("layout") != "@layout/map_status_track_recording" for element in root.iter()),
                f"{name}: InCar must not include the shared recording map FAB")
        require(not list((ROOT / "android/app/src/main/res").glob(f"layout-*/{name}.xml")),
                f"{name}: qualified variants require explicit recording-free verification")
    prefs = read("android/app/src/main/res/xml/prefs_in_car.xml")
    require("pref_in_car_show_track_recording_button" not in prefs,
            "InCar Settings must not expose a Show Track Recording map-button preference")
    settings = read("android/app/src/main/java/app/organicmaps/incar/InCarSettingsStore.java")
    require("KEY_SHOW_TRACK_RECORDING_BUTTON" not in settings,
            "obsolete Track Recording map-button preference storage must stay removed")
    require("isShowTrackRecordingButton" not in settings,
            "obsolete map recording visibility accessor must be removed")
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
    action = method_body(adapter, "private void onMenuItemClick(")
    require("if (!item.claimAction())" in action, "menu action must guard duplicate gestures across rebinds")
    bind = method_body(adapter, "public void onBindViewHolder(")
    detach_at = bind.find("toggle.setOnCheckedChangeListener(null)")
    checked_at = bind.find("toggle.setChecked(item.checked)")
    require(0 <= detach_at < checked_at,
            "menu binding must detach the switch listener before applying observed state")
    require("if (checked != item.checked)" in bind,
            "menu switch must not dispatch merely because RecyclerView rebound the row")
    require("v -> toggle.setChecked(!toggle.isChecked())" in bind,
            "row and switch must converge on one switch command path")

    require("toggle.setContentDescription(viewHolder.getTitleTextView().getText())" in bind,
            "the switch must carry its observed row action as an accessible name")

    row = by_id("android/app/src/inCar/res/layout/bottom_sheet_menu_item.xml", "bottom_sheet_menu_item_switch")
    require(row.attrib.get(f"{{{ANDROID_NS}}}visibility") == "gone",
            "ordinary menu rows must not gain a visible switch")
    require(row.attrib.get(f"{{{APP_NS}}}showText") == "true",
            "Track Recording switch must expose explicit ON/OFF text")


if __name__ == "__main__":
    verify_my_position()
    verify_track_recording()
    print("InCar location/recording controls verification PASSED")
