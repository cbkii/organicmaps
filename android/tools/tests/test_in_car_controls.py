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
        body = controls.method_body(production, "bool MyPositionController::RecenterPreservingMode()")
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
 bool m_listener=true;
 bool m_isPositionAssigned=true, m_positionIsObsolete=false, m_recenterPending=false, routing=false, driving=false;
 int64_t m_lastRecenterObservationNanos=1000000000;
 location::Mode m_mode=location::NotFollow;
 Point m_position{7}; double m_drawDirection=0.75; Rect m_visiblePixelRect;
 int calls=0, center=0, pixel=0, zoom=0; double angle=0.25;
 bool IsNavigationStyleCameraActive() const { return routing || driving; }
 Point GetRoutingRotationPixelCenter() const { return {99}; }
 void ChangeModelView(Point p, int z) { ++calls; center=p.value; zoom=z; }
 void ChangeModelView(Point p, double a, Point px, int z) { ++calls; center=p.value; angle=a; pixel=px.value; zoom=z; }
 bool recenter() {''' + body + r''' }
};
int main() {
 for (auto mode : {location::NotFollow, location::Follow, location::FollowAndRotate}) {
   Owner o; o.m_mode=mode;
   assert(o.recenter()); assert(o.recenter());
   assert(o.calls==2 && o.center==7 && o.zoom==-1 && o.m_mode==mode && !o.m_recenterPending);
   assert(!o.routing && !o.driving);
   if (mode==location::FollowAndRotate) assert(o.angle==0.75 && o.pixel==42);
   else assert(o.angle==0.25);
   now=12000000000;
   assert(!o.recenter() && o.calls==2 && o.m_recenterPending && o.m_mode==mode);
   o.m_lastRecenterObservationNanos=now;
   if (o.m_recenterPending) assert(o.recenter());
   if (o.m_recenterPending) o.recenter();
   assert(o.calls==3 && !o.m_recenterPending && o.m_mode==mode);
   now=2000000000;
 }
 Owner routed; routed.routing=true; routed.driving=true; routed.m_mode=location::FollowAndRotate;
 routed.recenter(); assert(routed.routing && routed.driving && routed.pixel==99 && routed.m_mode==location::FollowAndRotate);
 Owner o; o.m_isPositionAssigned=false; o.m_mode=location::PendingPosition;
 for (int i=0;i<100;++i) assert(!o.recenter());
 assert(o.calls==0 && o.m_recenterPending && o.m_mode==location::PendingPosition);
 o.m_isPositionAssigned=true; now=12000000000; assert(!o.recenter());
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
        if not runtime:
            self.skipTest("Java unavailable: menu command guard NOT_RUN")
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
            if compiler:
                compile_command = [compiler, "-d", tmp, str(production), str(source)]
            else:
                # This environment exposes jdk.compiler through java but no javac executable.
                probe = subprocess.run([runtime, "--list-modules"], capture_output=True, text=True)
                if "jdk.compiler@" not in probe.stdout:
                    self.skipTest("jdk.compiler unavailable: menu command guard NOT_RUN")
                bridge = Path(tmp) / "Compile.java"
                bridge.write_text("class Compile { public static void main(String[] a) { "
                                  "System.exit(javax.tools.ToolProvider.getSystemJavaCompiler().run(null, null, null, a)); }}")
                compile_command = [runtime, str(bridge), "-d", tmp, str(production), str(source)]
            subprocess.run(compile_command, check=True, capture_output=True)
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

    def test_qualified_mobile_layout_selection_is_rejected(self):
        original_read = controls.read
        def altered(path):
            source = original_read(path)
            if path.endswith("MapButtonsController.java"):
                source = source.replace("layout = R.layout.in_car_map_buttons_layout_regular",
                                        "layout = R.layout.map_buttons_layout_regular")
            return source
        with patch.object(controls, "read", altered):
            with self.assertRaisesRegex(SystemExit, "dedicated regular layout"):
                controls.verify_track_recording()

    def test_restore_comment_spoof_is_rejected(self):
        original_read = controls.read
        def altered(path):
            source = original_read(path)
            if path.endswith("MwmActivity.java"):
                source = source.replace("    mInCarRecenterRecovery = BuildConfig.IS_IN_CAR && savedInstanceState.getBoolean",
                                        "    // mInCarRecenterRecovery = BuildConfig.IS_IN_CAR && savedInstanceState.getBoolean")
            return source
        with patch.object(controls, "read", altered):
            with self.assertRaisesRegex(SystemExit, "configuration recreation"):
                controls.verify_my_position()

    def test_qualified_incar_resource_cannot_restore_recording(self):
        original_root = controls.ROOT
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            for mode in ("regular", "navigation"):
                relative = f"android/app/src/main/res/layout/in_car_map_buttons_layout_{mode}.xml"
                target = root / relative
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_text((original_root / relative).read_text())
            variant = root / "android/app/src/inCar/res/layout-land/in_car_map_buttons_layout_regular.xml"
            variant.parent.mkdir(parents=True)
            variant.write_text('<include layout="@layout/map_status_track_recording" />')
            with patch.object(controls, "ROOT", root), patch.object(controls, "read", lambda path: (original_root/path).read_text()):
                with self.assertRaisesRegex(SystemExit, "qualified/flavour variants"):
                    controls.verify_track_recording()

    def test_effective_incar_layouts_have_one_my_position_and_no_recording(self):
        import xml.etree.ElementTree as ET
        def ids(name, visiting=()):
            self.assertNotIn(name, visiting, "layout include cycle")
            choices = [ROOT / f"android/app/src/{flavour}/res/layout/{name}.xml" for flavour in ("inCar", "main")]
            path = next((p for p in choices if p.exists()), None)
            self.assertIsNotNone(path, name)
            result = []
            for element in ET.parse(path).getroot().iter():
                value = element.attrib.get(f"{{{controls.ANDROID_NS}}}id", "")
                if value:
                    result.append(value.split("/")[-1])
                include = element.attrib.get("layout", "")
                if element.tag == "include" and include.startswith("@layout/"):
                    result += ids(include.split("/")[-1], visiting + (name,))
            return result
        for mode in ("regular", "navigation"):
            actual = ids(f"in_car_map_buttons_layout_{mode}")
            self.assertEqual(1, actual.count("my_position"), actual)
            self.assertNotIn("track_recording_status", actual)

    def test_successful_native_dispatch_cannot_start_recovery(self):
        original_read = controls.read
        def altered(path):
            source = original_read(path)
            if path.endswith("LocationState.cpp"):
                source = source.replace("      if (dispatched)\n        return;", "      if (dispatched) {}")
            return source
        with patch.object(controls, "read", altered):
            with self.assertRaisesRegex(SystemExit, "gate Android recovery"):
                controls.verify_my_position()

    def test_application_recovery_rebinds_across_activity_gap(self):
        runtime = shutil.which("java")
        if not runtime:
            self.skipTest("Java unavailable: recovery delivery isolation NOT_RUN")
        probe = subprocess.run([runtime, "--list-modules"], capture_output=True, text=True)
        if "jdk.compiler@" not in probe.stdout:
            self.skipTest("jdk.compiler unavailable: recovery delivery isolation NOT_RUN")
        application = controls.read("android/app/src/main/java/app/organicmaps/MwmApplication.java")
        request = controls.method_body(application, "void requestInCarRecenterRecovery()")
        retry = controls.method_body(application, "private void retryPendingInCarRecenterRecovery()")
        with tempfile.TemporaryDirectory() as tmp:
            source = Path(tmp) / "RelayTest.java"
            source.write_text('''class BuildConfig { static final boolean IS_IN_CAR=true; }
class MwmActivity { boolean ready; int deliveries;
 boolean recoverInCarRecenterLocation() { deliveries++; return ready; }
}
class Relay {
 boolean mPendingInCarRecenterRecovery; Object current;
 Object getTopActivity() { return current; }
 void requestInCarRecenterRecovery() {''' + request + ''' }
 void retryPendingInCarRecenterRecovery() {''' + retry + ''' }
}
public class RelayTest {
 public static void main(String[] args) {
  Relay app=new Relay(); app.requestInCarRecenterRecovery();
  if (!app.mPendingInCarRecenterRecovery) throw new AssertionError("lost between Activities");
  MwmActivity old=new MwmActivity(); app.current=old; app.retryPendingInCarRecenterRecovery();
  if (!app.mPendingInCarRecenterRecovery) throw new AssertionError("lost while stopped/rendering unavailable");
  app.current=new Object(); app.retryPendingInCarRecenterRecovery();
  if (!app.mPendingInCarRecenterRecovery) throw new AssertionError("stolen by another Activity");
  MwmActivity replacement=new MwmActivity(); replacement.ready=true; app.current=replacement;
  app.retryPendingInCarRecenterRecovery(); app.retryPendingInCarRecenterRecovery();
  if (app.mPendingInCarRecenterRecovery || replacement.deliveries!=1 || old.deliveries!=1)
   throw new AssertionError("recovery not rebound/consumed once");
 }
}''')
            compiler = Path(tmp) / "Compile.java"
            compiler.write_text("class Compile { public static void main(String[] a) { "
                                "System.exit(javax.tools.ToolProvider.getSystemJavaCompiler().run(null, null, null, a)); }}")
            subprocess.run([runtime, str(compiler), "-d", tmp, str(source)], check=True, capture_output=True)
            subprocess.run([runtime, "-cp", tmp, "RelayTest"], check=True)
