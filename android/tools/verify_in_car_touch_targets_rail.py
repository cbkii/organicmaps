#!/usr/bin/env python3
"""Run the established InCar touch-target contract with the grouped driver surfaces."""

from __future__ import annotations

import verify_in_car_touch_targets as base


def verify_camera_control_rail(root):
    zoom_layout = root / "android/app/src/inCar/res/layout/map_buttons_zoom.xml"
    overlay_layout = root / "android/app/src/main/res/layout/in_car_driving_overlay.xml"
    zoom_root = base.parse_xml(zoom_layout)
    id_attr = f"{{{base.ANDROID_NS}}}id"
    visibility_attr = f"{{{base.ANDROID_NS}}}visibility"

    if zoom_root.attrib.get(id_attr) != "@+id/in_car_camera_controls_rail":
        raise base.VerificationError(f"{zoom_layout}: camera controls must have a dedicated stable rail root")

    direct_ids = [base.resource_id(child, id_attr) for child in zoom_root]
    expected_direct_ids = ["in_car_driving_view_button", "zoom_buttons_container", "track_recording_status"]
    if direct_ids != expected_direct_ids:
        raise base.VerificationError(
            f"{zoom_layout}: camera rail direct children must be {expected_direct_ids}; found {direct_ids}"
        )

    driving_view = zoom_root[0]
    if driving_view.attrib.get(visibility_attr) != "gone":
        raise base.VerificationError(
            f"{zoom_layout}: legacy Driving View compatibility owner must remain hidden in InCar"
        )

    zoom_container = None
    for element in zoom_root.iter():
        if element.attrib.get(id_attr) in ("@+id/zoom_buttons_container", "@id/zoom_buttons_container"):
            zoom_container = element
            break
    if zoom_container is None:
        raise base.VerificationError(f"{zoom_layout}: missing grouped zoom/My Position container")

    interactive_ids = [
        base.resource_id(child, id_attr)
        for child in zoom_container
        if base.resource_id(child, id_attr) != "<no-id>"
    ]
    if interactive_ids != ["nav_zoom_in", "nav_zoom_out"]:
        raise base.VerificationError(
            f"{zoom_layout}: grouped rail must retain zoom in/out controller ids; found {interactive_ids}"
        )

    divider_count = sum(1 for child in zoom_container if child.tag == "View")
    if divider_count != 2:
        raise base.VerificationError(f"{zoom_layout}: grouped rail must contain exactly two visual dividers")

    my_position_includes = [
        child
        for child in zoom_container
        if child.tag == "include" and child.attrib.get("layout") == "@layout/map_buttons_myposition"
    ]
    if len(my_position_includes) != 1:
        raise base.VerificationError(
            f"{zoom_layout}: grouped rail must contain exactly one My Position control include"
        )

    for view_id in ("in_car_driving_view_button", "nav_zoom_in", "nav_zoom_out"):
        base.require_layout_attr(
            zoom_layout,
            view_id,
            base.APP_NS,
            "fabCustomSize",
            "@dimen/in_car_map_primary_button_size",
        )
    base.require_layout_attr(
        zoom_layout,
        "in_car_driving_view_button",
        base.APP_NS,
        "srcCompat",
        "@drawable/ic_in_car_driving_view",
    )

    overlay = base.parse_xml(overlay_layout)
    for element in overlay.iter():
        if element.attrib.get(id_attr) in ("@+id/in_car_driving_view_button", "@id/in_car_driving_view_button"):
            raise base.VerificationError(f"{overlay_layout}: Driving View button must not be duplicated in the overlay")


def verify_navigation_ribbon(root):
    layout = root / "android/app/src/inCar/res/layout-land/layout_nav_top.xml"
    layout_root = base.parse_xml(layout)
    id_attr = f"{{{base.ANDROID_NS}}}id"

    ribbon = None
    for element in layout_root.iter():
        if element.attrib.get(id_attr) in ("@+id/nav_next_turn_container", "@id/nav_next_turn_container"):
            ribbon = element
            break
    if ribbon is None:
        raise base.VerificationError(f"{layout}: missing unified nav_next_turn_container ribbon")

    expected_ribbon_attrs = {
        (base.ANDROID_NS, "layout_width"): "0dp",
        (base.ANDROID_NS, "layout_height"): "@dimen/in_car_nav_ribbon_height",
        (base.ANDROID_NS, "gravity"): "top",
        (base.APP_NS, "layout_constraintLeft_toLeftOf"): "parent",
        (base.APP_NS, "layout_constraintRight_toRightOf"): "parent",
        (base.APP_NS, "layout_constraintTop_toTopOf"): "parent",
    }
    for (namespace, attr), expected in expected_ribbon_attrs.items():
        actual = ribbon.attrib.get(f"{{{namespace}}}{attr}")
        if actual != expected:
            raise base.VerificationError(
                f"{layout}: navigation ribbon {attr} must be {expected}, found {actual!r}"
            )

    ribbon_ids = {
        base.resource_id(element, id_attr)
        for element in ribbon.iter()
        if base.resource_id(element, id_attr) != "<no-id>"
    }
    required_ids = {
        "nav_next_turn_frame",
        "distance",
        "nav_next_next_turn_frame",
        "street_frame",
        "street",
        "lanes",
        "nav_speed_limit",
        "in_car_nav_speed",
    }
    missing = sorted(required_ids - ribbon_ids)
    if missing:
        raise base.VerificationError(f"{layout}: driver guidance escaped the unified ribbon: {missing}")

    base.reject_text(layout, r'layout_marginTop="-40dp"', "legacy overlapping phone-style turn card")
    base.require_layout_attr(
        layout, "street", base.ANDROID_NS, "textSize", "@dimen/in_car_nav_instruction_text_size"
    )
    base.require_layout_attr(
        layout, "street_frame", base.ANDROID_NS, "layout_height", "@dimen/in_car_nav_instruction_height"
    )
    base.require_layout_attr(
        layout, "distance", base.ANDROID_NS, "textSize", "@dimen/in_car_nav_distance_text_size"
    )
    base.require_layout_attr(
        layout, "lanes", base.ANDROID_NS, "layout_height", "@dimen/in_car_nav_lanes_height"
    )
    base.require_layout_attr(layout, "lanes", base.ANDROID_NS, "layout_gravity", "top")
    base.require_layout_attr(layout, "lanes", base.APP_NS, "lanesAlignTop", "true")

    values_path = root / "android/app/src/inCar/res/values/in_car_layout.xml"
    values = base.resource_values(values_path)
    for name, expected in (
        ("in_car_nav_ribbon_height", "112dp"),
        ("in_car_nav_manoeuvre_width", "184dp"),
        ("in_car_nav_instruction_height", "48dp"),
        ("in_car_nav_lanes_height", "40dp"),
        ("in_car_nav_instruction_text_size", "24sp"),
        ("in_car_nav_distance_text_size", "24sp"),
    ):
        base.require_value(values, name, expected, values_path)

    controller = root / "android/app/src/main/java/app/organicmaps/routing/NavigationController.java"
    base.require_method_text(
        controller,
        "private boolean isInCarLandscape(",
        r"BuildConfig\.IS_IN_CAR[\s\S]*?Configuration\.ORIENTATION_LANDSCAPE",
        "InCar landscape gate",
    )
    base.require_method_text(
        controller,
        "private int computeNavContentHeight(",
        r"isInCarLandscape\(\)[\s\S]*?R\.dimen\.in_car_nav_ribbon_height",
        "fixed ribbon height authority",
    )
    base.require_method_text(
        controller,
        "private void updateStreetView(",
        r"isInCarLandscape\(\)[\s\S]*?computeNavContentHeight\(\)",
        "map-control clearance for the whole ribbon",
    )
    base.reject_text(
        controller,
        r"mNextTurnContainer\.addOnLayoutChangeListener",
        "conditional ribbon child height must not drive map clearance",
    )


def verify_navigation_quick_actions(root):
    nav_buttons = root / "android/app/src/inCar/res/layout/map_buttons_layout_navigation.xml"
    base.require_layout_attr(nav_buttons, "map_buttons_inner_left", base.ANDROID_NS, "visibility", "gone")

    quick_ui = root / "android/app/src/main/java/app/organicmaps/incar/InCarQuickDestinationsUi.java"
    base.require_method_text(
        quick_ui,
        "private void collectNavigationActions(",
        r"in_car_quick_fuel[\s\S]*?category_toilet[\s\S]*?R\.string\.search[\s\S]*?in_car_quick_places",
        "active-navigation Fuel/Toilets/Search/Places action contract",
    )
    base.require_method_text(
        quick_ui,
        "private void renderNavigationActionLayout(",
        r"mDirectActions[\s\S]*?ensureMoreButton\(\)",
        "active-navigation fixed quick rail",
    )
    base.require_method_text(
        quick_ui,
        "private void showOverflowChoice(",
        r"InCarChoiceAdapter\.withIcons[\s\S]*?setNeutralButton\(\s*R\.string\.settings",
        "icon-labelled overflow with fixed Settings footer",
    )

    adapter = root / "android/app/src/main/java/app/organicmaps/incar/InCarChoiceAdapter.java"
    base.require_method_text(
        adapter,
        "public static InCarChoiceAdapter withIcons(",
        r"iconResIds[\s\S]*?InCarChoiceAdapter",
        "icon-aware InCar overflow rows",
    )


def verify_speed_warning_policy(root):
    policy = root / "android/app/src/main/java/app/organicmaps/incar/InCarSpeedDisplayPolicy.java"
    text = base.read_text(policy)
    for token in ("SPEED_WARNING_ENTER_FACTOR = 1.05", "SPEED_WARNING_CLEAR_FACTOR = 1.03", "resetSpeeding()"):
        if token not in text:
            raise base.VerificationError(f"{policy}: missing authoritative overspeed policy token {token!r}")

    controller = root / "android/app/src/main/java/app/organicmaps/routing/NavigationController.java"
    base.require_method_text(
        controller,
        "private void updateSpeedLimit(",
        r"BuildConfig\.IS_IN_CAR[\s\S]*?InCarSpeedDisplayPolicy\.isSpeeding",
        "speed-limit sign must share the InCar overspeed authority",
    )


def verify_shared_resource_contract(root):
    required = (
        root / "android/app/src/main/res/layout/in_car_action_menu_item.xml",
        root / "android/app/src/main/res/drawable/in_car_search_row_even.xml",
        root / "android/app/src/main/res/drawable/in_car_search_row_odd.xml",
        root / "android/app/src/main/res/drawable-night/in_car_search_row_even.xml",
        root / "android/app/src/main/res/drawable-night/in_car_search_row_odd.xml",
        root / "android/app/src/main/res/values/in_car_shared_fallbacks.xml",
    )
    missing = [str(path) for path in required if not path.is_file()]
    if missing:
        raise base.VerificationError(f"shared src/main InCar resource fallbacks missing: {missing}")

    fallback_path = root / "android/app/src/main/res/values/in_car_shared_fallbacks.xml"
    fallback_text = base.read_text(fallback_path)
    if 'name="in_car_selection_foreground"' not in fallback_text:
        raise base.VerificationError("shared src/main InCar colour fallback is missing selection foreground")
    fallback_values = base.resource_values(fallback_path)
    base.require_value(fallback_values, "in_car_nav_ribbon_height", "112dp", fallback_path)

    theme_path = root / "android/app/src/inCar/res/values/in_car_dialog_theme.xml"
    theme_root = base.parse_xml(theme_path)
    alert_styles = [style for style in theme_root.findall("style") if style.attrib.get("name") == "MwmTheme.AlertDialog"]
    if len(alert_styles) != 1:
        raise base.VerificationError(f"{theme_path}: expected exactly one MwmTheme.AlertDialog style")

    alert_items = {}
    for item in alert_styles[0].findall("item"):
        name = item.attrib.get("name")
        if name:
            alert_items[name] = (item.text or "").strip()

    expected_fraction = "@fraction/in_car_compact_dialog_width_fraction"
    for attr in ("windowFixedWidthMajor", "windowFixedWidthMinor"):
        if alert_items.get(attr) != expected_fraction:
            raise base.VerificationError(
                f"{theme_path}: MwmTheme.AlertDialog must set {attr} to {expected_fraction}"
            )

    for attr in ("android:windowFixedWidthMajor", "android:windowFixedWidthMinor"):
        if attr in alert_items:
            raise base.VerificationError(
                f"{theme_path}: MwmTheme.AlertDialog must not use framework-private width attribute: {attr}"
            )


original_verify_code = base.verify_code


def verify_code(root):
    original_verify_code(root)
    verify_shared_resource_contract(root)
    verify_navigation_ribbon(root)
    verify_navigation_quick_actions(root)
    verify_speed_warning_policy(root)


base.verify_camera_control_rail = verify_camera_control_rail
base.verify_code = verify_code

if __name__ == "__main__":
    raise SystemExit(base.main())
