#!/usr/bin/env python3
"""Verify the InCar 76dp preferred / 69dp compact touch-target contract."""

from __future__ import annotations

import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

PREFERRED_DP = 76
MINIMUM_DP = 69
ANDROID_NS = "http://schemas.android.com/apk/res/android"
APP_NS = "http://schemas.android.com/apk/res-auto"


class VerificationError(RuntimeError):
    """Raised when a required InCar sizing contract is not satisfied."""


def repository_root() -> Path:
    return Path(__file__).resolve().parents[2]


def read_text(path: Path) -> str:
    try:
        return path.read_text(encoding="utf-8")
    except OSError as exc:
        raise VerificationError(f"unable to read {path}: {exc}") from exc


def parse_xml(path: Path) -> ET.Element:
    try:
        return ET.parse(path).getroot()
    except (OSError, ET.ParseError) as exc:
        raise VerificationError(f"unable to parse {path}: {exc}") from exc


def resource_values(path: Path) -> dict[str, str]:
    root = parse_xml(path)
    values: dict[str, str] = {}
    for child in root:
        name = child.attrib.get("name")
        if name:
            values[name] = (child.text or "").strip()
    return values


def require_value(values: dict[str, str], name: str, expected: str, source: Path) -> None:
    actual = values.get(name)
    if actual != expected:
        raise VerificationError(f"{source}: {name} must be {expected}, found {actual!r}")


def require_text(path: Path, pattern: str, description: str) -> None:
    text = read_text(path)
    if re.search(pattern, text, re.MULTILINE | re.DOTALL) is None:
        raise VerificationError(f"{path}: missing {description}")


def reject_text(path: Path, pattern: str, description: str) -> None:
    if re.search(pattern, read_text(path), re.MULTILINE | re.DOTALL) is not None:
        raise VerificationError(f"{path}: contains forbidden {description}")


def java_method_body(path: Path, marker: str) -> str:
    text = read_text(path)
    marker_index = text.find(marker)
    if marker_index < 0:
        raise VerificationError(f"{path}: missing Java method marker {marker!r}")
    body_start = text.find("{", marker_index + len(marker))
    if body_start < 0:
        raise VerificationError(f"{path}: missing body for Java method marker {marker!r}")

    depth = 0
    for index in range(body_start, len(text)):
        char = text[index]
        if char == "{":
            depth += 1
        elif char == "}":
            depth -= 1
            if depth == 0:
                return text[body_start + 1 : index]
    raise VerificationError(f"{path}: unterminated body for Java method marker {marker!r}")


def require_method_text(path: Path, marker: str, pattern: str, description: str) -> None:
    body = java_method_body(path, marker)
    if re.search(pattern, body, re.MULTILINE | re.DOTALL) is None:
        raise VerificationError(f"{path}: {marker} missing {description}")


def resource_id(element: ET.Element, id_attr: str) -> str:
    raw_id = element.attrib.get(id_attr, "")
    for prefix in ("@+id/", "@id/"):
        if raw_id.startswith(prefix):
            return raw_id.removeprefix(prefix)
    return "<no-id>"


def require_layout_attr(path: Path, view_id: str, namespace: str, attr: str, expected: str) -> None:
    root = parse_xml(path)
    id_attr = f"{{{ANDROID_NS}}}id"
    target = None
    for element in root.iter():
        if element.attrib.get(id_attr) in (f"@+id/{view_id}", f"@id/{view_id}"):
            target = element
            break
    if target is None:
        raise VerificationError(f"{path}: missing view id {view_id}")
    actual = target.attrib.get(f"{{{namespace}}}{attr}")
    if actual != expected:
        raise VerificationError(f"{path}: {view_id} {attr} must be {expected}, found {actual!r}")


def verify_ratio() -> None:
    if MINIMUM_DP * 10 < PREFERRED_DP * 9:
        raise VerificationError("compact touch target is below 90% of the preferred target")
    if (MINIMUM_DP - 1) * 10 >= PREFERRED_DP * 9:
        raise VerificationError("compact touch target is not the integer ceiling of the 90% floor")


def verify_resources(root: Path) -> None:
    runtime_path = root / "android/app/src/main/res/values/in_car_runtime_ui.xml"
    visuals_path = root / "android/app/src/main/res/values/in_car_visuals.xml"
    automotive_path = root / "android/app/src/main/res/values/in_car_automotive_ui.xml"
    override_path = root / "android/app/src/inCar/res/values/in_car_layout.xml"
    runtime_extra_path = root / "android/app/src/inCar/res/values/in_car_runtime_layout_extra.xml"

    runtime = resource_values(runtime_path)
    visuals = resource_values(visuals_path)
    automotive = resource_values(automotive_path)
    overrides = resource_values(override_path)
    runtime_extra = resource_values(runtime_extra_path)

    require_value(runtime, "in_car_touch_target_preferred", f"{PREFERRED_DP}dp", runtime_path)
    require_value(runtime, "in_car_touch_target_min", f"{MINIMUM_DP}dp", runtime_path)
    require_value(runtime, "in_car_runtime_button_size", "@dimen/in_car_touch_target_preferred", runtime_path)
    require_value(runtime, "in_car_runtime_row_min_height", "@dimen/in_car_touch_target_preferred", runtime_path)
    require_value(runtime, "in_car_place_page_action_min_height", "@dimen/in_car_touch_target_preferred", runtime_path)
    require_value(runtime, "in_car_place_page_save_width", "@dimen/in_car_touch_target_preferred", runtime_path)
    require_value(runtime, "in_car_place_page_other_width", "@dimen/in_car_touch_target_preferred", runtime_path)
    require_value(runtime, "in_car_quick_marker_touch_radius", "38dp", runtime_path)
    require_text(
        runtime_path,
        r'<item\s+name="in_car_driving_view_button"\s+type="id"\s*/>',
        "main-source Driving View id declaration for all app flavours",
    )
    require_value(automotive, "in_car_action_menu_width", "300dp", automotive_path)
    require_value(runtime_extra, "in_car_search_result_meta_width", "112dp", runtime_extra_path)

    preferred_visuals = (
        "in_car_map_button_size",
        "in_car_button_min_touch_target",
        "in_car_routing_action_button_size",
        "in_car_routing_toolbar_cell_height",
        "in_car_routing_close_button_size",
        "in_car_nav_button_height",
        "in_car_place_page_close_button_size",
    )
    for name in preferred_visuals:
        require_value(visuals, name, "@dimen/in_car_touch_target_preferred", visuals_path)

    compact_visuals = (
        "in_car_compact_map_button_size",
        "in_car_compact_button_min_touch_target",
        "in_car_compact_routing_action_button_size",
        "in_car_compact_routing_toolbar_cell_height",
        "in_car_compact_close_button_size",
        "in_car_compact_nav_button_height",
    )
    for name in compact_visuals:
        require_value(visuals, name, "@dimen/in_car_touch_target_min", visuals_path)

    require_value(automotive, "in_car_map_primary_button_size", "@dimen/in_car_runtime_button_size", automotive_path)
    require_value(automotive, "in_car_map_zoom_gap", "10dp", automotive_path)

    preferred_overrides = (
        "map_button_size",
        "primary_button_min_height",
        "menu_line_height",
        "menu_list_item_height",
        "nav_button_height",
        "nav_street_height",
        "routing_action_button_size",
        "routing_toolbar_cell_height",
    )
    for name in preferred_overrides:
        require_value(overrides, name, "@dimen/in_car_touch_target_preferred", override_path)
    require_value(overrides, "routing_bottom_buttons_max_height", "96dp", override_path)


def verify_search_toolbar(root: Path) -> None:
    path = root / "android/app/src/inCar/res/layout/toolbar_search_controls_sheet.xml"
    minimum = "@dimen/in_car_touch_target_min"
    preferred = "@dimen/in_car_touch_target_preferred"

    for view_id in ("in_car_search_mode", "back", "close_search"):
        require_layout_attr(path, view_id, ANDROID_NS, "layout_width", minimum)
        require_layout_attr(path, view_id, ANDROID_NS, "layout_height", minimum)
        require_layout_attr(path, view_id, ANDROID_NS, "minWidth", minimum)
        require_layout_attr(path, view_id, ANDROID_NS, "minHeight", minimum)

    require_layout_attr(path, "search_container", ANDROID_NS, "layout_height", preferred)
    require_layout_attr(path, "query_input_layout", ANDROID_NS, "minHeight", preferred)
    require_layout_attr(path, "query_input_layout", APP_NS, "endIconMinSize", minimum)
    require_layout_attr(path, "query", ANDROID_NS, "minHeight", preferred)


def verify_action_menus(root: Path) -> None:
    action_menu = root / "android/app/src/main/java/app/organicmaps/incar/InCarActionMenu.java"
    nav_menu = root / "android/app/src/main/java/app/organicmaps/widget/menu/NavMenu.java"
    route_plan = root / "android/app/src/main/java/app/organicmaps/routing/RoutingPlanFragment.java"

    require_text(action_menu, r"ListPopupWindow", "automotive action list")
    require_text(action_menu, r"popup\.setAnchorView\(anchor\)", "anchored automotive action list")
    require_text(
        action_menu,
        r"R\.dimen\.in_car_runtime_row_min_height[\s\S]*?view\.setMinimumHeight\(minHeight\)",
        "preferred automotive action-row minimum enforcement",
    )
    require_text(action_menu, r"R\.dimen\.in_car_action_menu_width", "bounded automotive action-menu width")

    for path in (nav_menu, route_plan):
        reject_text(path, r"\bPopupMenu\b", "platform-sized PopupMenu on a driver-facing InCar surface")
        require_text(path, r"InCarActionMenu\.show\(", "automotive-sized InCarActionMenu use")


def verify_start_end_controls(root: Path) -> None:
    start = root / "android/app/src/inCar/res/layout/routing_start_button.xml"
    nav = root / "android/app/src/inCar/res/layout/layout_nav_bottom.xml"
    strings = root / "android/app/src/inCar/res/values/strings_runtime_overrides.xml"

    require_layout_attr(start, "start", ANDROID_NS, "layout_width", "0dp")
    require_layout_attr(start, "start", ANDROID_NS, "layout_weight", "1")
    require_layout_attr(start, "start", ANDROID_NS, "minHeight", "@dimen/in_car_touch_target_preferred")
    require_text(
        start,
        r'<Space\b(?=[^>]*android:layout_width="0dp")(?=[^>]*android:layout_weight="1")[^>]*>',
        "equal spacer for half-width START",
    )

    require_layout_attr(nav, "stop", ANDROID_NS, "layout_height", "@dimen/nav_button_height")
    require_layout_attr(nav, "stop", ANDROID_NS, "minWidth", "@dimen/in_car_nav_stop_min_width")
    require_layout_attr(nav, "stop", ANDROID_NS, "padding", "@dimen/margin_base")
    require_layout_attr(nav, "stop", ANDROID_NS, "text", "@string/in_car_end_navigation")
    require_text(strings, r'<string\s+name="in_car_end_navigation">END</string>', "compact END label")


def verify_camera_control_rail(root: Path) -> None:
    zoom_layout = root / "android/app/src/inCar/res/layout/map_buttons_zoom.xml"
    overlay_layout = root / "android/app/src/main/res/layout/in_car_driving_overlay.xml"
    zoom_root = parse_xml(zoom_layout)
    id_attr = f"{{{ANDROID_NS}}}id"

    if zoom_root.attrib.get(id_attr) != "@+id/in_car_camera_controls_rail":
        raise VerificationError(f"{zoom_layout}: camera controls must have a dedicated stable rail root")

    direct_ids = [resource_id(child, id_attr) for child in zoom_root]
    expected_direct_ids = ["in_car_driving_view_button", "zoom_buttons_container", "track_recording_status"]
    if direct_ids != expected_direct_ids:
        raise VerificationError(
            f"{zoom_layout}: camera rail direct children must be {expected_direct_ids}; found {direct_ids}"
        )

    zoom_container = None
    for element in zoom_root.iter():
        if element.attrib.get(id_attr) in ("@+id/zoom_buttons_container", "@id/zoom_buttons_container"):
            zoom_container = element
            break
    if zoom_container is None:
        raise VerificationError(f"{zoom_layout}: missing independent zoom_buttons_container")

    nested_zoom_ids = [resource_id(child, id_attr) for child in zoom_container]
    if nested_zoom_ids != ["nav_zoom_in", "nav_zoom_out"]:
        raise VerificationError(
            f"{zoom_layout}: +/- visibility container must contain only zoom in/out; found {nested_zoom_ids}"
        )

    for view_id in ("in_car_driving_view_button", "nav_zoom_in", "nav_zoom_out"):
        require_layout_attr(
            zoom_layout,
            view_id,
            APP_NS,
            "fabCustomSize",
            "@dimen/in_car_map_primary_button_size",
        )
    for view_id in ("in_car_driving_view_button", "nav_zoom_in"):
        require_layout_attr(
            zoom_layout,
            view_id,
            ANDROID_NS,
            "layout_marginBottom",
            "@dimen/in_car_map_zoom_gap",
        )
    require_layout_attr(
        zoom_layout,
        "in_car_driving_view_button",
        APP_NS,
        "srcCompat",
        "@drawable/ic_in_car_driving_view",
    )

    overlay = parse_xml(overlay_layout)
    for element in overlay.iter():
        if element.attrib.get(id_attr) in ("@+id/in_car_driving_view_button", "@id/in_car_driving_view_button"):
            raise VerificationError(f"{overlay_layout}: Driving View button must live in the zoom rail, not the overlay")


def verify_driving_view_lifecycle(root: Path) -> None:
    driving_ui = root / "android/app/src/main/java/app/organicmaps/incar/InCarDrivingUi.java"

    require_method_text(
        driving_ui,
        "public static void attach(",
        r"registerFragmentLifecycleCallbacks\(observed\.mapButtonsCallbacks,\s*true\)",
        "recursive MapButtons fragment lifecycle registration",
    )
    require_method_text(
        driving_ui,
        "public static void attach(",
        r"onFragmentViewCreated.*?fragment instanceof MapButtonsController.*?"
        r"bindDrivingViewButton\(activity,\s*observed,\s*view,\s*fragment\)",
        "MapButtons view-created rebinding",
    )
    require_method_text(
        driving_ui,
        "public static void attach(",
        r"onFragmentViewDestroyed.*?fragment instanceof MapButtonsController\s*&&\s*"
        r"observed\.mapButtonsFragment\s*==\s*fragment.*?clearDrivingViewButton\(observed\)",
        "owner-matched MapButtons view-destroy cleanup",
    )
    require_method_text(
        driving_ui,
        "private static void bindDrivingViewButton(",
        r"R\.id\.in_car_driving_view_button.*?R\.id\.nav_zoom_in.*?"
        r"findMapButtonsOwner\(activity\.getSupportFragmentManager\(\),\s*drivingView\).*?"
        r"binding\.mapButtonsFragment\s*=\s*resolvedOwner",
        "fallback binding ownership resolution",
    )
    require_method_text(
        driving_ui,
        "private static void bindDrivingViewButton(",
        r"drivingView\.setOnClickListener\(v\s*->\s*binding\.controller\.onDrivingViewButtonPressed\(\)\).*?"
        r"zoomIn\.addOnLayoutChangeListener\(binding\.zoomSizeListener\)",
        "Driving View action and zoom-size listener wiring",
    )
    require_method_text(
        driving_ui,
        "private static void syncDrivingViewButtonSize(",
        r"binding\.zoomIn\.getWidth\(\).*?binding\.zoomIn\.getHeight\(\).*?"
        r"binding\.drivingView\.setCustomSize\(size\).*?"
        r"binding\.drivingView\.setMinimumWidth\(binding\.zoomIn\.getMinimumWidth\(\)\).*?"
        r"binding\.drivingView\.setMinimumHeight\(binding\.zoomIn\.getMinimumHeight\(\)\)",
        "zoom-in size authority propagation",
    )
    require_method_text(
        driving_ui,
        "public static void release(",
        r"unregisterFragmentLifecycleCallbacks\(binding\.mapButtonsCallbacks\).*?clearDrivingViewButton\(binding\)",
        "lifecycle callback unregistration and view cleanup",
    )


def verify_camera_rail_movement(root: Path) -> None:
    controller = root / "android/app/src/main/java/app/organicmaps/maplayer/MapButtonsController.java"

    require_method_text(
        controller,
        "public View onCreateView(",
        r"final View zoomFrame\s*=\s*mFrame\.findViewById\(R\.id\.zoom_buttons_container\).*?"
        r"mZoomMovementFrame\s*=\s*zoomFrame.*?BuildConfig\.IS_IN_CAR\s*&&\s*"
        r"zoomFrame\.getParent\(\) instanceof View parent.*?mZoomMovementFrame\s*=\s*parent.*?"
        r"mButtonsMap\.put\(MapButtons\.zoom,\s*zoomFrame\)",
        "separate InCar movement rail and +/- visibility authority",
    )
    require_method_text(
        controller,
        "private void updateButtonsVisibility(",
        r"mZoomMovementFrame\s*!=\s*null\s*&&\s*mZoomMovementFrame\s*!=\s*"
        r"mButtonsMap\.get\(MapButtons\.zoom\).*?mZoomMovementFrame\.getParent\(\)\s*==\s*parent.*?"
        r"UiUtils\.showIf\(getViewTopOffset\(translation,\s*mZoomMovementFrame\)\s*>=\s*-140,\s*"
        r"mZoomMovementFrame\)",
        "rail-level movement clipping",
    )
    require_method_text(
        controller,
        "public void showButton(",
        r"case zoom:\s*UiUtils\.showIf\(show\s*&&\s*Config\.showZoomButtons\(\),\s*buttonView\)",
        "independent +/- visibility setting authority",
    )


def verify_code(root: Path) -> None:
    quick_policy = root / "android/app/src/main/java/app/organicmaps/incar/InCarQuickDestinationsLayoutPolicy.java"
    choice_adapter = root / "android/app/src/main/java/app/organicmaps/incar/InCarChoiceAdapter.java"
    dialog_sizing = root / "android/app/src/main/java/app/organicmaps/incar/InCarDialogSizing.java"
    settings_fragment = root / "android/app/src/main/java/app/organicmaps/settings/InCarSettingsFragment.java"
    routing_layouts = (
        root / "android/app/src/main/res/layout/routing_bottom_sheet.xml",
        root / "android/app/src/main/res/layout-land/routing_bottom_sheet.xml",
    )

    require_text(
        quick_policy,
        rf"PREFERRED_ACTION_SIZE_DP\s*=\s*{PREFERRED_DP}\s*;",
        f"{PREFERRED_DP}dp Quick Destination preferred target",
    )
    require_text(
        quick_policy,
        rf"MIN_ACTION_SIZE_DP\s*=\s*{MINIMUM_DP}\s*;",
        f"{MINIMUM_DP}dp Quick Destination compact floor",
    )
    require_text(
        choice_adapter,
        r"R\.dimen\.in_car_runtime_row_min_height",
        "resource-backed InCar choice-row minimum",
    )
    require_text(
        choice_adapter,
        r"simple_list_item_single_choice",
        "selected-state layout for single-choice InCar rows",
    )
    require_text(
        dialog_sizing,
        r"R\.dimen\.in_car_touch_target_preferred",
        "preferred touch-target enforcement for InCar dialog controls",
    )
    require_text(
        dialog_sizing,
        r"view\.isClickable\(\).*view\.isLongClickable\(\).*view instanceof EditText",
        "interactive dialog-control traversal",
    )
    require_text(
        settings_fragment,
        r"void\s+onDisplayPreferenceDialog\(",
        "InCar list-preference dialog override",
    )
    require_text(
        settings_fragment,
        r"InCarChoiceAdapter\.singleChoice\(",
        "automotive-sized single-choice InCar preference rows",
    )

    for layout in routing_layouts:
        text = read_text(layout)
        if 'app:fabCustomSize="40dp"' in text:
            raise VerificationError(f"{layout}: route actions still hard-code a 40dp touch target")
        if text.count('app:fabCustomSize="@dimen/routing_action_button_size"') != 4:
            raise VerificationError(f"{layout}: expected four resource-sized routing action FABs")


def main() -> int:
    try:
        root = repository_root()
        verify_ratio()
        verify_resources(root)
        verify_search_toolbar(root)
        verify_action_menus(root)
        verify_start_end_controls(root)
        verify_camera_control_rail(root)
        verify_driving_view_lifecycle(root)
        verify_camera_rail_movement(root)
        verify_code(root)
    except VerificationError as exc:
        print(f"FAILED: {exc}", file=sys.stderr)
        return 1

    print(
        f"SUCCESS: InCar touch targets prefer {PREFERRED_DP}dp and compact no lower than {MINIMUM_DP}dp."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
