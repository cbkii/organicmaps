#!/usr/bin/env python3
"""Run the established InCar touch-target contract with the grouped driver surfaces."""

from __future__ import annotations

import re

import verify_in_car_contrast as contrast

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
    expected_direct_ids = ["in_car_driving_view_button", "zoom_buttons_container"]
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
        (base.ANDROID_NS, "gravity"): "center_vertical",
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
    base.require_layout_attr(layout, "lanes", base.ANDROID_NS, "layout_gravity", "center_vertical")
    lanes_drawable = root / "android/sdk/widgets/lanes/src/main/java/app/organicmaps/sdk/widgets/lanes/LanesDrawable.java"
    fitting = base.java_method_body(lanes_drawable, "public void setBounds(")
    if re.search(r"\bm(?:Width|Height)\s*=", fitting):
        raise base.VerificationError(f"{lanes_drawable}: repeated lane fitting must retain intrinsic dimensions")

    values_path = root / "android/app/src/inCar/res/values/in_car_layout.xml"
    values = base.resource_values(values_path)
    for name, expected in (
        ("in_car_nav_ribbon_height", "80dp"),
        ("in_car_nav_manoeuvre_width", "196dp"),
        ("in_car_nav_instruction_height", "72dp"),
        ("in_car_nav_lanes_height", "72dp"),
        ("in_car_nav_instruction_text_size", "29sp"),
        ("in_car_nav_distance_text_size", "30sp"),
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
        "private void updateNavigationHeaderMetrics(",
        r"R\.dimen\.nav_menu_height",
        "fixed active-navigation footer height authority",
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

    quick_ui = root / "android/app/src/main/java/app/organicmaps/incar/InCarQuickDestinationsUi.java"
    base.require_method_text(
        quick_ui,
        "private int navigationFooterHeightPx(",
        r"if \(mNavigationMode\)[\s\S]*?R\.dimen\.nav_menu_height[\s\S]*?return mBottomButtonsHeight",
        "navigation rail must reserve the route footer rather than a positive legacy map-buttons height",
    )

    view_model = root / "android/app/src/main/java/app/organicmaps/maplayer/MapButtonsViewModel.kt"
    base.require_method_text(
        view_model,
        "fun setBottomButtonsHeight(",
        r"navigationFooterOwnsHeight[\s\S]*?LayoutMode\.navigation[\s\S]*?height <= 0f",
        "legacy map-buttons zero height must not erase the active InCar footer clearance",
    )


def verify_navigation_refinements(root):
    layout = root / "android/app/src/inCar/res/layout-land/layout_nav_top.xml"
    parsed = base.parse_xml(layout)
    android = lambda name: f"{{{base.ANDROID_NS}}}{name}"
    by_id = {base.resource_id(e, android("id")): e for e in parsed.iter()}
    values_path = root / "android/app/src/inCar/res/values/in_car_layout.xml"
    values = base.resource_values(values_path)
    strings = {e.attrib["name"]: e.text for e in base.parse_xml(root / "android/app/src/main/res/values/in_car_shared_fallbacks.xml").findall("string")}
    for stage in ("after", "lanes"):
        base.require_value(strings, f"in_car_nav_{stage}", stage.upper(), layout)
    for element in parsed.iter():
        text = element.attrib.get(android("text"), "").strip()
        resolved = strings.get(text.removeprefix("@string/"), text) if text.startswith("@string/") else text
        if text in ("@string/in_car_nav_now", "@string/in_car_nav_next") or resolved.upper() in ("NOW", "NEXT"):
            raise base.VerificationError("immediate guidance must not retain NOW/NEXT captions")
    if "in_car_nav_now" in by_id or "in_car_nav_next" in by_id:
        raise base.VerificationError("immediate guidance must not retain NOW/NEXT caption ids")
    base.require_layout_attr(layout, "in_car_nav_guidance_label", base.ANDROID_NS, "text", "@string/in_car_nav_after")
    base.require_layout_attr(layout, "in_car_nav_guidance_label", base.ANDROID_NS, "layout_height", "@dimen/in_car_nav_label_height")
    base.require_value(values, "in_car_nav_guidance_label_width", "60dp", values_path)
    group = by_id["in_car_nav_immediate_group"]
    if not {"nav_next_turn_frame", "street_frame"}.issubset({base.resource_id(e, android("id")) for e in list(group)}):
        raise base.VerificationError("manoeuvre and road must share one immediate guidance group")
    base.require_layout_attr(layout, "in_car_nav_immediate_group", base.ANDROID_NS, "background", "@color/in_car_nav_immediate_background")
    if android("alpha") in by_id["street_frame"].attrib:
        raise base.VerificationError("immediate road must retain full foreground strength")
    for view in ("nav_next_next_turn_frame", "in_car_nav_guidance_label"):
        base.require_layout_attr(layout, view, base.ANDROID_NS, "alpha", "0.70")
    base.require_layout_attr(layout, "nav_next_turn_container", base.ANDROID_NS, "layoutDirection", "ltr")
    base.require_layout_attr(layout, "street_frame", base.ANDROID_NS, "layout_width", "0dp")
    base.require_layout_attr(layout, "street_frame", base.ANDROID_NS, "layout_weight", "1")
    now = float(values["in_car_nav_now_icon_size"][:-2])
    after = float(values["in_car_nav_after_icon_size"][:-2])
    distance = float(values["in_car_nav_distance_text_size"][:-2])
    road = float(values["in_car_nav_instruction_text_size"][:-2])
    if not (now == 72 and 0.92 <= after / now < 1 and 0.95 <= road / distance < 1):
        raise base.VerificationError("navigation hierarchy must use only modest optical size steps")
    base.require_value(values, "in_car_nav_icon_gap", "12dp", values_path)
    for element in parsed.iter("ImageView"):
        if base.resource_id(element, android("id")) == "turn":
            for edge in ("Left", "Right"):
                if element.attrib.get(android(f"layout_margin{edge}")) != "@dimen/in_car_nav_icon_gap":
                    raise base.VerificationError("manoeuvre icons need 12dp on both horizontal edges")
            if any(android(f"layout_margin{edge}") in element.attrib for edge in ("Top", "Bottom")):
                raise base.VerificationError("manoeuvre icons must not consume extra vertical margins")
    ribbon = by_id["nav_next_turn_container"]
    sections = list(ribbon)
    if not sections or not any(base.resource_id(element, android("id")) == "in_car_nav_speed"
                               for element in sections[-1].iter()):
        raise base.VerificationError("speed must remain in the last physical-right ribbon section")
    controller = root / "android/app/src/main/java/app/organicmaps/routing/NavigationController.java"
    base.require_method_text(controller, "private void updateGuidanceLabel(",
                             r"hasLanes \? R\.string\.in_car_nav_lanes : R\.string\.in_car_nav_after",
                             "LANES caption must replace AFTER")
    base.require_method_text(controller, "private void updateGuidanceLabel(",
                             r"label\.setAlpha\(hasLanes \? 1\.0f : 0\.70f\)",
                             "LANES must reset AFTER alpha in both directions")
    base.require_method_text(controller, "private void updateVehicle(",
                             r"showNextNextTurn = info\.hasNextNextTurn\(\) && \(!isInCarLandscape\(\) \|\| !hasLanes\)",
                             "complete lanes must take precedence over AFTER")

    footer = root / "android/app/src/inCar/res/layout-land/layout_nav_bottom.xml"
    for edge in ("Top", "Bottom", "Right"):
        base.require_layout_attr(footer, "stop", base.ANDROID_NS, f"layout_margin{edge}",
                                 "@dimen/in_car_nav_end_outer_gap")
    base.require_layout_attr(footer, "stop", base.ANDROID_NS, "background", "@drawable/in_car_navigation_end")
    base.require_layout_attr(footer, "stop", base.ANDROID_NS, "textColor", "@android:color/white")
    base.require_layout_attr(footer, "stop", base.ANDROID_NS, "padding", "@dimen/margin_base")
    base.require_layout_attr(footer, "stop", base.ANDROID_NS, "minHeight", "@dimen/in_car_touch_target_min")
    height = float(values["in_car_nav_end_height"][:-2])
    gap = float(values["in_car_nav_end_outer_gap"][:-2])
    if height < 69 or height + 2 * gap != 76:
        raise base.VerificationError("END touch target and equal inset must fit the 76dp footer")
    drawable = base.parse_xml(root / "android/app/src/inCar/res/drawable/in_car_navigation_end.xml")
    if drawable.find(".//solid").attrib.get(android("color")) != "#FF000000":
        raise base.VerificationError("END must have a true-black fill")

    owner = root / "android/app/src/main/java/app/organicmaps/maplayer/MapButtonsController.java"
    base.require_method_text(owner, "static boolean shouldSuppressLegacyButton(",
                             r"inCar && mode == LayoutMode\.navigation.*MapButtons\.search.*MapButtons\.bookmarks",
                             "only duplicate legacy controls must be suppressed in InCar navigation")
    for path, method in ((owner, "public void showButton("),
                         (root / "android/app/src/main/java/app/organicmaps/maplayer/SearchWheel.java", "public void show(")):
        for token in ("setClickable(!suppressed)", "setFocusable(!suppressed)",
                      "IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS"):
            base.require_method_text(path, method, re.escape(token), "hidden duplicates must leave all input traversal")
    wheel = root / "android/app/src/main/java/app/organicmaps/maplayer/SearchWheel.java"
    base.require_method_text(wheel, "private void refreshSearchVisibility(", r"isSuppressed\(\)[\s\S]*?show\(false\)",
                             "search refresh must honour duplicate suppression")


def verify_warning_foreground_contrast(root):
    fallbacks = contrast.read_colors(root / "android/app/src/main/res/values/in_car_shared_fallbacks.xml")
    for night in (False, True):
        palette = {**fallbacks, **contrast.build_palette(root, night)}
        normal = contrast.resolve_color("bg_cards", palette)
        red = contrast.resolve_color("in_car_nav_warning", palette)
        foreground = contrast.resolve_color("in_car_nav_foreground", palette)
        immediate = contrast.resolve_color("in_car_nav_immediate_background", palette)
        light = contrast.resolve_color("in_car_nav_immediate_foreground", palette)
        for strength in (0.0, 0.44, 0.47, 0.50):
            for stage, normal_background, ink, alpha in (("IMMEDIATE", immediate, light, 1.0),
                                                        ("AFTER", normal, foreground, 0.70),
                                                        ("LANES", normal, foreground, 1.0)):
                background = contrast.composite(contrast.Rgba(red.red, red.green, red.blue, strength), normal_background)
                faded = contrast.Rgba(ink.red, ink.green, ink.blue, alpha)
                ratio = contrast.contrast_ratio(faded, background)
                if ratio < 4.5:
                    raise base.VerificationError(f"{stage} warning foreground contrast is only {ratio:.2f}:1")
    renderer = root / "android/app/src/main/java/app/organicmaps/incar/InCarDrivingUi.java"
    body = base.java_method_body(renderer, "private static void renderRibbon(")
    if "immediateGroup.setBackgroundColor(warning ? ColorUtils.blendARGB" not in body:
        raise base.VerificationError("near-black immediate group must participate in warning blending")
    if "Color.WHITE" in body:
        raise base.VerificationError("half-strength warning must retain theme-appropriate foregrounds")


def verify_navigation_quick_actions(root):
    nav_buttons = root / "android/app/src/main/res/layout/in_car_map_buttons_layout_navigation.xml"
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
    base.require_method_text(
        quick_ui,
        "private void showOverflowChoice(",
        r"styleSettingsFooter[\s\S]*?applyNavigationOverflowBounds",
        "navigation overflow must fit between ribbon and footer",
    )

    sizing = root / "android/app/src/main/java/app/organicmaps/incar/InCarDialogSizing.java"
    base.require_method_text(
        sizing,
        "public static void applyNavigationOverflowBounds(",
        r"bottomControlsHeightPx[\s\S]*?headerHeightPx[\s\S]*?Gravity\.RIGHT \| Gravity\.BOTTOM",
        "navigation overflow bounds and physical-right placement",
    )
    base.require_method_text(
        sizing,
        "private static int[] usableWindowSize(",
        r"getDecorView\(\)[\s\S]*?decor\.getWidth\(\)[\s\S]*?decor\.getHeight\(\)",
        "dialog sizing must prefer the measured current task window",
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
    for token in ("SPEED_WARNING_ENTER_FACTOR = 1.05", "SPEED_WARNING_CLEAR_FACTOR = 1.03",
                  "SPEED_BOUNDARY_TOLERANCE_MPS", "sSpeedLimitMps", "updateSpeedLimit(", "resetSpeeding()"):
        if token not in text:
            raise base.VerificationError(f"{policy}: missing authoritative overspeed policy token {token!r}")

    controller = root / "android/app/src/main/java/app/organicmaps/routing/NavigationController.java"
    base.require_method_text(
        controller,
        "private void updateSpeedLimit(",
        r"BuildConfig\.IS_IN_CAR[\s\S]*?InCarDrivingUi\.updateNavigationSpeedLimit",
        "speed-limit sign must update only the shared InCar limit authority",
    )
    base.reject_text(
        controller,
        r"BuildConfig\.IS_IN_CAR[\s\S]{0,350}InCarSpeedDisplayPolicy\.isSpeeding",
        "route controller must not feed a competing speed sample into the overspeed hysteresis",
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
    base.require_value(fallback_values, "in_car_nav_ribbon_height", "80dp", fallback_path)

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
    verify_navigation_refinements(root)
    verify_warning_foreground_contrast(root)
    verify_navigation_quick_actions(root)
    verify_speed_warning_policy(root)


base.verify_camera_control_rail = verify_camera_control_rail
base.verify_code = verify_code

if __name__ == "__main__":
    raise SystemExit(base.main())
