"""Mutation tests for menu checks that missed InCar overlay failures."""
import importlib.util
from pathlib import Path
import unittest
import shutil
import subprocess
import tempfile
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[3]
spec = importlib.util.spec_from_file_location("controls", ROOT / "android/tools/verify_in_car_location_recording_controls.py")
controls = importlib.util.module_from_spec(spec)
spec.loader.exec_module(controls)

class MenuContractTests(unittest.TestCase):
    def test_current_contract(self):
        controls.verify_my_position()
        controls.verify_track_recording()

    def test_missing_or_reordered_detach_is_rejected(self):
        original_read = controls.read
        for replacement in ("", "toggle.setChecked(item.checked);"):
            def altered(path):
                source = original_read(path)
                if path.endswith("MenuAdapter.java"):
                    return source.replace("toggle.setOnCheckedChangeListener(null);", replacement)
                return source
            with self.subTest(replacement=replacement), patch.object(controls, "read", altered):
                with self.assertRaisesRegex(SystemExit, "detach the switch listener"):
                    controls.verify_track_recording()

    def test_incar_overlay_is_inspected(self):
        original = controls.by_id
        def missing_overlay(path, wanted):
            if "/src/inCar/" in path and wanted == "bottom_sheet_menu_item_switch":
                controls.fail("missing InCar switch")
            return original(path, wanted)
        with patch.object(controls, "by_id", missing_overlay):
            with self.assertRaisesRegex(SystemExit, "missing InCar switch"):
                controls.verify_track_recording()

    def test_production_position_age_includes_replay_and_sleep(self):
        compiler = shutil.which("g++")
        if compiler is None:
            self.skipTest("C++ compiler unavailable: native freshness NOT_RUN")
        with tempfile.TemporaryDirectory() as tmp:
            source = Path(tmp) / "freshness.cpp"
            source.write_text('''#include "current_position_freshness.hpp"
#include <cassert>
int main() {
  assert(IsFreshPositionObservation(1000000000, 1000000000));
  assert(IsFreshPositionObservation(1000000000, 11000000000));
  assert(!IsFreshPositionObservation(1000000000, 11000000001));
  assert(!IsFreshPositionObservation(0, 1000000000));
  assert(!IsFreshPositionObservation(1000000001, 1000000000));
  assert(!IsFreshPositionObservation(1000000000, -1));
  assert(!IsFreshPositionObservation(1000000000, 301000000000));
}''')
            binary = Path(tmp) / "freshness"
            subprocess.run([compiler, "-std=c++17", "-I", str(ROOT / "libs/drape_frontend"),
                            str(source), "-o", str(binary)], check=True, capture_output=True)
            subprocess.run([str(binary)], check=True)

    def test_native_owner_preserves_modes_and_consumes_pending_once(self):
        compiler = shutil.which("g++")
        if not compiler:
            self.skipTest("C++ compiler unavailable: native method isolation NOT_RUN")
        production = controls.read("libs/drape_frontend/my_position_controller.cpp")
        body = controls.method_body(production, "void MyPositionController::RecenterPreservingMode()")
        # Execute the actual owner method with listener/clock seams; full drape integration is separate.
        with tempfile.TemporaryDirectory() as tmp:
            source = Path(tmp) / "owner.cpp"
            source.write_text(r'''#include "current_position_freshness.hpp"
#include <cassert>
namespace location { enum Mode { NotFollow, Follow, FollowAndRotate, PendingPosition, NotFollowNoPosition }; }
struct Point { int value; };
struct Rect { Point Center() const { return {42}; } };
int constexpr kDoNotChangeZoom = -1;
int64_t now = 2000000000;
int64_t PositionClockNanos() { return now; }
struct Owner {
 bool m_isPositionAssigned=true, m_positionIsObsolete=false, m_recenterPending=false, routing=false, driving=false;
 int64_t m_lastRecenterObservationNanos=1000000000;
 location::Mode m_mode=location::NotFollow;
 Point m_position{7}; double m_drawDirection=0.75; Rect m_visiblePixelRect;
 int calls=0, center=0, pixel=0, zoom=0; double angle=0.25;
 bool IsNavigationStyleCameraActive() const { return routing || driving; }
 Point GetRoutingRotationPixelCenter() const { return {99}; }
 void ChangeModelView(Point p, int z) { ++calls; center=p.value; zoom=z; }
 void ChangeModelView(Point p, double a, Point px, int z) { ++calls; center=p.value; angle=a; pixel=px.value; zoom=z; }
 void recenter() {''' + body + r''' }
};
int main() {
 for (auto mode : {location::NotFollow, location::Follow, location::FollowAndRotate}) {
   Owner o; o.m_mode=mode;
   o.recenter(); o.recenter();
   assert(o.calls==2 && o.center==7 && o.zoom==-1 && o.m_mode==mode && !o.m_recenterPending);
   assert(!o.routing && !o.driving);
   if (mode==location::FollowAndRotate) assert(o.angle==0.75 && o.pixel==42);
   else assert(o.angle==0.25);
 }
 Owner routed; routed.routing=true; routed.driving=true; routed.m_mode=location::FollowAndRotate;
 routed.recenter(); assert(routed.routing && routed.driving && routed.pixel==99 && routed.m_mode==location::FollowAndRotate);
 Owner o; o.m_isPositionAssigned=false; o.m_mode=location::PendingPosition;
 for (int i=0;i<100;++i) o.recenter();
 assert(o.calls==0 && o.m_recenterPending && o.m_mode==location::PendingPosition);
 o.m_isPositionAssigned=true; now=12000000000; o.recenter();
 assert(o.calls==0 && o.m_recenterPending);
 o.m_lastRecenterObservationNanos=now;
 if (o.m_recenterPending) o.recenter();
 if (o.m_recenterPending) o.recenter();
 assert(o.calls==1 && !o.m_recenterPending && o.m_mode==location::PendingPosition);
}
''')
            # initializer_list is deliberately added by the harness, not production.
            source.write_text("#include <initializer_list>\n" + source.read_text())
            binary = Path(tmp) / "owner"
            subprocess.run([compiler, "-std=c++17", "-I", str(ROOT / "libs/drape_frontend"),
                            str(source), "-o", str(binary)], check=True, capture_output=True)
            subprocess.run([str(binary)], check=True)

    def test_production_menu_command_guard(self):
        compiler, runtime = shutil.which("javac"), shutil.which("java")
        if not compiler or not runtime:
            self.skipTest("JDK unavailable: menu command guard NOT_RUN")
        with tempfile.TemporaryDirectory() as tmp:
            source = Path(tmp) / "GuardTest.java"
            source.write_text('''import app.organicmaps.util.bottomsheet.MenuBottomSheetItem;
public class GuardTest {
 public static void main(String[] args) {
  for (boolean checked : new boolean[]{false, true}) {
   int[] calls = {0};
   MenuBottomSheetItem item = MenuBottomSheetItem.checkable(1, 2, checked, () -> calls[0]++);
   if (calls[0] != 0 || item.checked != checked) throw new AssertionError("binding dispatched");
   for (int gesture=0; gesture<100; gesture++)
     if (item.claimAction()) item.onClickListener.onClick();
   if (calls[0] != 1 || item.checked != checked) throw new AssertionError("double dispatch/optimistic state");
  }
  MenuBottomSheetItem ordinary = new MenuBottomSheetItem(1, 2, () -> {});
  if (!ordinary.claimAction() || !ordinary.claimAction()) throw new AssertionError("ordinary click changed");
 }
}''')
            production = ROOT / "android/app/src/main/java/app/organicmaps/util/bottomsheet/MenuBottomSheetItem.java"
            subprocess.run([compiler, "-d", tmp, str(production), str(source)], check=True, capture_output=True)
            subprocess.run([runtime, "-cp", tmp, "GuardTest"], check=True)

    def test_native_mode_mutation_and_settings_handoff_regressions_are_rejected(self):
        original_read = controls.read
        cases = [
            ("my_position_controller.cpp", "  m_recenterPending = true;", "  ChangeMode(location::Follow);\n  m_recenterPending = true;", "must not change intent"),
            ("MwmActivity.java", "    mLocationActivityStopped = true;", "    mLocationActivityStopped = true;\n    mInCarRecenterRecovery = false;", "settings handoff"),
        ]
        for path, before, after, expected in cases:
            def altered(name):
                source = original_read(name)
                return source.replace(before, after) if name.endswith(path) else source
            with self.subTest(path=path), patch.object(controls, "read", altered):
                with self.assertRaisesRegex(SystemExit, expected):
                    controls.verify_my_position()
