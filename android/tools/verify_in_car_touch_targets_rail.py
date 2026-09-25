#!/usr/bin/env python3
"""Run the established InCar touch-target contract with the grouped camera-rail structure."""

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

    fallback_text = base.read_text(root / "android/app/src/main/res/values/in_car_shared_fallbacks.xml")
    if 'name="in_car_selection_foreground"' not in fallback_text:
        raise base.VerificationError("shared src/main InCar colour fallback is missing selection foreground")

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


base.verify_camera_control_rail = verify_camera_control_rail
base.verify_code = verify_code

if __name__ == "__main__":
    raise SystemExit(base.main())
