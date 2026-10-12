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
    def test_production_countdown_dispatch_and_interruption_boundaries(self):
        runtime = shutil.which("java")
        if not runtime:
            self.skipTest("Java unavailable: countdown coordinator NOT_RUN")
        # Compile the whole production coordinator/policy. Only Android clock, queue, view and
        # lifecycle services are fixtures: advancing the clock runs callbacks without sleeping.
        files = {
            "android/os/Looper.java": "package android.os; public class Looper { public static Looper getMainLooper(){return new Looper();} }",
            "android/os/SystemClock.java": "package android.os; public class SystemClock { public static long now; public static long elapsedRealtime(){return now;} }",
            "android/os/Handler.java": r'''package android.os; import java.util.*;
public class Handler {
 public static final Map<Runnable,Long> pending=new LinkedHashMap<>();
 public Handler(Looper l) {} public void removeCallbacks(Runnable r){pending.remove(r);}
 public void post(Runnable r){postDelayed(r,0);} public void postDelayed(Runnable r,long delay){pending.put(r,SystemClock.now+delay);}
 public static void advance(long now){SystemClock.now=now;
  for(int bounded=0; bounded<100; ++bounded){Runnable ready=null;
   for(var e:pending.entrySet()) if(e.getValue()<=now){ready=e.getKey();break;}
   if(ready==null)return; pending.remove(ready); ready.run();
  } throw new AssertionError("unbounded callbacks");
 }
}''',
            "android/os/PowerManager.java": "package android.os; public class PowerManager { public boolean interactive=true; public boolean isInteractive(){return interactive;} }",
            "android/content/Context.java": "package android.content; public class Context { public static final String POWER_SERVICE=\"power\"; }",
            "android/graphics/Rect.java": "package android.graphics; public class Rect { public int w,h; public int width(){return w;} public int height(){return h;} }",
            "android/view/View.java": r'''package android.view; import android.graphics.Rect;
public class View {
 public boolean shown=true, enabled=true; public int w=100,h=60,clicks; public Runnable click=()->{};
 public boolean isShown(){return shown;} public boolean isEnabled(){return enabled;}
 public boolean getGlobalVisibleRect(Rect r){r.w=w;r.h=h;return shown;}
 public int getWidth(){return w;} public int getHeight(){return h;} public View getRootView(){return this;}
 public void performClick(){++clicks;click.run();}
}''',
            "androidx/lifecycle/ViewModel.java": "package androidx.lifecycle; public class ViewModel {}",
            "androidx/lifecycle/ViewModelProvider.java": r'''package androidx.lifecycle;
public class ViewModelProvider { public ViewModelProvider(Object o){} public <T> T get(Class<T> c){try{return c.getDeclaredConstructor().newInstance();}catch(Exception e){throw new RuntimeException(e);}} }''',
            "androidx/lifecycle/Lifecycle.java": r'''package androidx.lifecycle;
public class Lifecycle { public enum State { CREATED, RESUMED; public boolean isAtLeast(State s){return ordinal()>=s.ordinal();} }
 public State state=State.RESUMED; public State getCurrentState(){return state;} }''',
            "app/organicmaps/MwmActivity.java": r'''package app.organicmaps;
public class MwmActivity {
 public boolean focus=true,touch=false; public final androidx.lifecycle.Lifecycle life=new androidx.lifecycle.Lifecycle();
 public final android.os.PowerManager power=new android.os.PowerManager();
 public static class Fragments { public boolean saved; public boolean isStateSaved(){return saved;} }
 public final Fragments fragments=new Fragments();
 public boolean hasWindowFocus(){return focus;} public boolean isInCarTouchActive(){return touch;}
 public androidx.lifecycle.Lifecycle getLifecycle(){return life;} public Object getSystemService(String s){return power;}
 public Fragments getSupportFragmentManager(){return fragments;}
}''',
            "app/organicmaps/incar/InCarSettingsStore.java": "package app.organicmaps.incar; public class InCarSettingsStore { public static int delay=10; public static int getAutomaticNavigationActionDelaySeconds(Object o){return delay;} }",
            "CountdownTest.java": r'''import android.os.*; import android.view.View;
import app.organicmaps.*; import app.organicmaps.incar.*;
public class CountdownTest {
 static void check(boolean b){if(!b)throw new AssertionError();}
 public static void main(String[] args){
  for(int delay:new int[]{2,3,5,10,15,20,30}){
   Handler.pending.clear(); SystemClock.now=0; InCarSettingsStore.delay=delay;
   MwmActivity a=new MwmActivity(); View go=new View(), start=new View();
   InCarNavigationCountdown c=new InCarNavigationCountdown(a); final int[] progress={-1};
   go.click=()->c.arm("START",1,start,()->true,s->progress[0]=s);
   c.arm("GO_TO",1,go,()->true,s->progress[0]=s);
   Handler.advance(0); check(progress[0]==delay);
   Handler.advance(delay*1000-1); check(go.clicks==0);
   Handler.advance(delay*1000+200); check(go.clicks==1 && start.clicks==0);
   Handler.advance(2*delay*1000+400); check(go.clicks==1 && start.clicks==1);
   Handler.advance(100000); check(start.clicks==1 && Handler.pending.isEmpty());
   c.arm("GO_TO",1,go,()->true,s->{}); Handler.advance(100000); check(go.clicks==1);
  }
  // Every interruption cancels dispatch and consumes the generation; a new one can still arm.
  for(int interruption=0;interruption<8;++interruption){
   Handler.pending.clear(); SystemClock.now=0; InCarSettingsStore.delay=2;
   MwmActivity a=new MwmActivity(); View v=new View(); boolean[] eligible={true};
   InCarNavigationCountdown c=new InCarNavigationCountdown(a);
   c.arm("START",1,v,()->eligible[0],s->{}); Handler.advance(0);
   switch(interruption){case 0:a.focus=false;break;case 1:a.life.state=androidx.lifecycle.Lifecycle.State.CREATED;break;
    case 2:a.power.interactive=false;break;case 3:v.w++;break;case 4:v.shown=false;break;
    case 5:v.enabled=false;break;case 6:eligible[0]=false;break;case 7:c.cancel();break;}
   Handler.advance(2200); check(v.clicks==0 && Handler.pending.isEmpty());
   a.focus=true;a.life.state=androidx.lifecycle.Lifecycle.State.RESUMED;a.power.interactive=true;
   v.shown=true;v.enabled=true;eligible[0]=true;
   c.arm("START",1,v,()->true,s->{}); Handler.advance(5000);check(v.clicks==0);
   c.arm("START",2,v,()->true,s->{}); Handler.advance(5000);Handler.advance(7200);check(v.clicks==1);
  }
  Handler.pending.clear(); SystemClock.now=0; MwmActivity a=new MwmActivity(); View v=new View();
  InCarNavigationCountdown c=new InCarNavigationCountdown(a); a.touch=true;
  c.arm("GO_TO",1,v,()->true,s->{});Handler.advance(0);Handler.advance(1000);check(v.clicks==0);
  a.touch=false;Handler.advance(1500);Handler.advance(3400);check(v.clicks==0);
  Handler.advance(3700);check(v.clicks==1); // Full delay starts only after selection touch completes.
 }
}'''
        }
        for name in ("InCarNavigationCountdown", "InCarNavigationCountdownPolicy"):
            files[f"app/organicmaps/incar/{name}.java"] = controls.read(
                f"android/app/src/main/java/app/organicmaps/incar/{name}.java")
        with tempfile.TemporaryDirectory() as tmp:
            sources = []
            for path, content in files.items():
                target = Path(tmp) / path
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_text(content)
                sources.append(str(target))
            compiler = Path(tmp) / "Compile.java"
            compiler.write_text("class Compile { public static void main(String[] a) { "
                                "System.exit(javax.tools.ToolProvider.getSystemJavaCompiler().run(null, null, null, a)); }}")
            subprocess.run([runtime, str(compiler), "-d", tmp, *sources], check=True)
            subprocess.run([runtime, "-cp", tmp, "CountdownTest"], check=True)

    def test_incar_saved_places_cannot_be_restored_by_visibility_updates(self):
        runtime = shutil.which("java")
        if not runtime:
            self.skipTest("Java unavailable: production visibility dispatch NOT_RUN")
        production = controls.read("android/app/src/main/java/app/organicmaps/maplayer/MapButtonsController.java")
        policy = controls.method_body(production, "static boolean shouldSuppressLegacyButton(")
        dispatch = controls.method_body(production, "public void showButton(boolean show, MapButtons button)")
        # Execute production dispatch, including focus/accessibility setters, against a small View seam.
        harness = r'''import java.util.*;
class View {
 static final int IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS=4, IMPORTANT_FOR_ACCESSIBILITY_AUTO=0;
 boolean visible=true, clickable=true, focusable=true; int accessibility;
 void setClickable(boolean v) { clickable=v; } void setFocusable(boolean v) { focusable=v; }
 void setImportantForAccessibility(int v) { accessibility=v; }
}
class FloatingActionButton extends View {}
class UiUtils { static void showIf(boolean show, View v) { v.visible=show; } }
class BuildConfig { static boolean IS_IN_CAR=true; }
class Config { static boolean showZoomButtons() { return true; } }
class TrackRecorder { static boolean nativeIsTrackRecordingEnabled() { return false; } }
class Value { LayoutMode mode; LayoutMode getValue() { return mode; } }
class Model { Value value=new Value(); Value getLayoutMode() { return value; } }
enum LayoutMode { regular, planning, navigation }
enum MapButtons { zoom, toggleMapLayer, myPosition, search, bookmarks, cameraViewRecenter, menu, trackRecordingStatus }
class Position { void showButton(boolean show) {} }
class Wheel { void show(boolean show) {} }
class Controller {
 Map<MapButtons, View> mButtonsMap=new HashMap<>(); Model mMapButtonsViewModel=new Model();
 View mToggleMapLayerButton; Position mNavMyPosition; Wheel mSearchWheel=new Wheel();
 boolean isInNavigationMode() { return false; } void animateIconBlinking(boolean active, FloatingActionButton v) {}
 static boolean shouldSuppressLegacyButton(boolean inCar, LayoutMode mode, MapButtons button) {''' + policy + r'''}
 void showButton(boolean show, MapButtons button) {''' + dispatch + r'''}
}
public class VisibilityTest {
 static void check(boolean condition) { if (!condition) throw new AssertionError(); }
 public static void main(String[] args) {
  for (boolean inCar : new boolean[]{true, false}) {
   BuildConfig.IS_IN_CAR=inCar;
   // A fresh controller represents initial inflation and each fragment replacement.
   for (int replacement=0; replacement<3; ++replacement) {
    Controller c=new Controller(); View places=new View(), search=new View(), menu=new View();
    c.mButtonsMap.put(MapButtons.bookmarks,places); c.mButtonsMap.put(MapButtons.search,search);
    c.mButtonsMap.put(MapButtons.menu,menu);
    for (LayoutMode mode : new LayoutMode[]{null, LayoutMode.regular, LayoutMode.planning,
          LayoutMode.navigation, LayoutMode.regular}) {
     c.mMapButtonsViewModel.value.mode=mode;
     // Repeat requests model initial setup, LiveData delivery, and visibility/layout refresh.
     for (int refresh=0; refresh<3; ++refresh) {
      c.showButton(false,MapButtons.bookmarks); c.showButton(true,MapButtons.bookmarks);
      c.showButton(true,MapButtons.search); c.showButton(true,MapButtons.menu);
      check(places.visible==!inCar);
      check(places.clickable==!inCar && places.focusable==!inCar);
      check(places.accessibility==(inCar?4:0));
      check(search.visible==!(inCar && mode==LayoutMode.navigation)); check(menu.visible);
     }
    }
   }
  }
 }
}'''
        with tempfile.TemporaryDirectory() as tmp:
            source = Path(tmp) / "VisibilityTest.java"
            source.write_text(harness)
            compiler = Path(tmp) / "Compile.java"
            compiler.write_text("class Compile { public static void main(String[] a) { "
                                "System.exit(javax.tools.ToolProvider.getSystemJavaCompiler().run(null, null, null, a)); }}")
            subprocess.run([runtime, str(compiler), "-d", tmp, str(source)], check=True)
            subprocess.run([runtime, "-cp", tmp, "VisibilityTest"], check=True)

    def test_shared_camera_symbol_and_safe_activity_teardown(self):
        import xml.etree.ElementTree as ET
        resources = ET.parse(ROOT / "android/app/src/main/res/values/in_car_shared_fallbacks.xml").getroot()
        self.assertTrue(any(item.tag == "item" and item.get("type") == "id"
                            and item.get("name") == "in_car_camera_view_recenter" for item in resources))
        activity = controls.read("android/app/src/main/java/app/organicmaps/MwmActivity.java")
        base = controls.read("android/app/src/main/java/app/organicmaps/base/BaseMwmFragmentActivity.java")
        self.assertIn("protected final void onDestroy()", base)
        self.assertNotIn("protected void onDestroy()", activity)
        teardown = controls.method_body(activity, "protected void onSafeDestroy()")
        self.assertIn("cancelInCarNavigationCountdown();", teardown)
        self.assertIn("super.onSafeDestroy();", teardown)

    def test_current_contract(self):
        controls.verify_my_position()
        controls.verify_track_recording()

    def test_renderer_camera_toggle_uses_actual_presentation_and_recenter_result(self):
        compiler = shutil.which("g++")
        if compiler is None:
            self.skipTest("C++ compiler unavailable: native camera NOT_RUN")
        production = controls.read("libs/drape_frontend/frontend_renderer.cpp")
        body = controls.method_body(production, "bool FrontendRenderer::ToggleCameraViewAndRecenter()")
        permission = controls.method_body(production, "void FrontendRenderer::ApplyPerspectivePermission(bool allowed)")
        session = controls.method_body(controls.read("libs/drape_frontend/frontend_renderer.hpp"),
                                       "void SetDrivingView(bool enabled, bool autoReturn, bool recenter)")
        harness = r"""#include <cassert>
#include <memory>
struct SetAutoPerspectiveEvent { bool p; explicit SetAutoPerspectiveEvent(bool v):p(v){} };
template<typename T, typename... Args> auto make_unique_dp(Args&&... args) { return std::make_unique<T>(std::forward<Args>(args)...); }
struct Screen { bool tilted=false; bool isPerspective() const { return tilted; } };
struct Stream { Screen screen; const Screen & GetCurrentScreen() const { return screen; }
 void ApplyCameraPresentation(bool p) { screen.tilted=p; } };
struct Position { bool routing=false, fresh=true; int calls=0, mode=3;
 bool IsInRouting() const { return routing; }
 void SetDrivingView(bool, bool, bool) {}
 void EnablePerspectiveInRouting(bool) {}
 bool RecenterPreservingMode() { ++calls; return fresh; } };
struct Renderer {
 Stream m_userEventStream; Position position; Position *m_myPositionController=&position;
 bool m_enablePerspectiveInNavigation=true, m_manualCameraPresentation=false, m_cameraDrivingSession=false;
 void AddUserEvent(std::unique_ptr<SetAutoPerspectiveEvent> e) { m_userEventStream.ApplyCameraPresentation(e->p); }
 void DisablePerspective() { m_userEventStream.ApplyCameraPresentation(false); }
 void driving(bool enabled, bool autoReturn, bool recenter) {""" + session + r"""}
 void permit(bool allowed) {""" + permission + r"""}
 bool toggle() {""" + body + r"""}
};
int main() {
 Renderer r;
 assert(r.toggle() && r.m_userEventStream.screen.tilted && r.position.calls==1);
 assert(r.toggle() && !r.m_userEventStream.screen.tilted && r.position.calls==2);
 assert(r.position.mode==3 && r.m_manualCameraPresentation);
 r.position.fresh=false;
 assert(!r.toggle() && r.m_userEventStream.screen.tilted && r.position.calls==3);
 r.position.routing=true; r.m_enablePerspectiveInNavigation=false;
 assert(!r.toggle() && !r.m_userEventStream.screen.tilted && r.position.mode==3);
 assert(!r.toggle() && !r.m_userEventStream.screen.tilted);
 Renderer session;
 session.driving(true, true, false);
 assert(session.m_userEventStream.screen.tilted);
 session.toggle(); assert(!session.m_userEventStream.screen.tilted && session.m_manualCameraPresentation);
 session.driving(true, true, false);
 assert(!session.m_userEventStream.screen.tilted && session.m_manualCameraPresentation);
 session.driving(false, true, false);
 assert(!session.m_manualCameraPresentation && !session.m_userEventStream.screen.tilted);
 session.driving(true, true, false); assert(session.m_userEventStream.screen.tilted);
 session.position.routing=true;
 session.toggle(); assert(!session.m_userEventStream.screen.tilted);
 session.permit(true); assert(!session.m_userEventStream.screen.tilted && session.m_manualCameraPresentation);
 session.toggle(); assert(session.m_userEventStream.screen.tilted);
 session.permit(false); assert(!session.m_userEventStream.screen.tilted && !session.m_manualCameraPresentation);
 session.permit(false); assert(!session.m_userEventStream.screen.tilted);
 session.permit(true); assert(session.m_userEventStream.screen.tilted);
}
"""
        with tempfile.TemporaryDirectory() as tmp:
            source = Path(tmp) / "camera.cpp"
            binary = Path(tmp) / "camera"
            source.write_text(harness)
            subprocess.run([compiler, "-std=c++17", str(source), "-o", str(binary)], check=True, capture_output=True)
            subprocess.run([str(binary)], check=True)

    def test_camera_presentation_invalidates_scene_without_fresh_location(self):
        compiler = shutil.which("g++")
        if compiler is None:
            self.skipTest("C++ compiler unavailable: camera scene invalidation NOT_RUN")
        body = controls.method_body(controls.read("libs/drape_frontend/user_event_stream.cpp"),
                                    "void UserEventStream::ApplyCameraPresentation(bool perspective)")
        harness = r"""#include <cassert>
#include <memory>
struct ActiveFrameEvent {};
template<typename T> auto make_unique_dp() { return std::make_unique<T>(); }
struct ScreenBase { bool tilted=false; void SetAutoPerspective(bool) {}
 void ApplyPerspective(double angle,double,double) { assert(angle>0); tilted=true; }
 void ResetPerspective() { tilted=false; } double GetAngleFOV() const { return 1; } };
struct Navigator { ScreenBase screen; const ScreenBase & Screen() const { return screen; }
 void SetFromScreen(const ScreenBase & s) { screen=s; } };
struct Stream { Navigator m_navigator; int events=0; bool redraw=false;
 void ResetMapPlaneAnimations() {} void AddEvent(std::unique_ptr<ActiveFrameEvent>) { ++events; }
 void apply(bool perspective) {""" + body + r"""}
 void process() { redraw=events!=0; events=0; }
};
int main() {
 Stream s;
 s.apply(true); assert(s.m_navigator.screen.tilted && s.events==1);
 s.process(); assert(s.redraw); // No location/recenter event exists: presentation alone dirtied it.
 s.apply(false); assert(!s.m_navigator.screen.tilted && s.events==1);
 s.process(); assert(s.redraw); // Permission-disallow flatten is also rendered.
 s.process(); assert(!s.redraw); // No perpetual redraw loop.
}
"""
        with tempfile.TemporaryDirectory() as tmp:
            source = Path(tmp) / "camera_scene.cpp"
            binary = Path(tmp) / "camera_scene"
            source.write_text(harness)
            subprocess.run([compiler, "-std=c++17", str(source), "-o", str(binary)], check=True, capture_output=True)
            subprocess.run([str(binary)], check=True)

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

    def test_recording_switch_applies_requested_state(self):
        runtime = shutil.which("java")
        if not runtime:
            self.skipTest("Java unavailable: recording command isolation NOT_RUN")
        probe = subprocess.run([runtime, "--list-modules"], capture_output=True, text=True)
        if "jdk.compiler@" not in probe.stdout:
            self.skipTest("jdk.compiler unavailable: recording command isolation NOT_RUN")
        activity = controls.read("android/app/src/main/java/app/organicmaps/MwmActivity.java")
        command = controls.method_body(activity, "public void onTrackRecordingSwitchChanged(boolean enabled)")
        with tempfile.TemporaryDirectory() as tmp:
            source = Path(tmp) / "RecordingTest.java"
            source.write_text('''class Map { static boolean engine=true; static boolean isEngineCreated() { return engine; } }
class TrackRecorder { static boolean enabled; static boolean nativeIsTrackRecordingEnabled() { return enabled; } }
class Activity {
 int starts, saves;
 void startTrackRecording() { starts++; TrackRecorder.enabled=true; }
 void onTrackRecordingSaved() { saves++; TrackRecorder.enabled=false; }
 void onTrackRecordingSwitchChanged(boolean enabled) {''' + command + ''' }
}
public class RecordingTest {
 public static void main(String[] args) {
  Activity a=new Activity();
  a.onTrackRecordingSwitchChanged(false);
  if (a.starts!=0 || a.saves!=0) throw new AssertionError("stale ON snapshot restarted stopped recorder");
  a.onTrackRecordingSwitchChanged(true); a.onTrackRecordingSwitchChanged(true);
  if (a.starts!=1 || !TrackRecorder.enabled) throw new AssertionError("start request not idempotent");
  a.onTrackRecordingSwitchChanged(false); a.onTrackRecordingSwitchChanged(false);
  if (a.saves!=1 || TrackRecorder.enabled) throw new AssertionError("stop request not idempotent");
  Map.engine=false; a.onTrackRecordingSwitchChanged(true);
  if (a.starts!=1) throw new AssertionError("started without engine");
 }
}''')
            compiler = Path(tmp) / "Compile.java"
            compiler.write_text("class Compile { public static void main(String[] a) { "
                                "System.exit(javax.tools.ToolProvider.getSystemJavaCompiler().run(null, null, null, a)); }}")
            subprocess.run([runtime, str(compiler), "-d", tmp, str(source)], check=True, capture_output=True)
            subprocess.run([runtime, "-cp", tmp, "RecordingTest"], check=True)

    def test_recording_forwarding_removal_is_rejected(self):
        original_read = controls.read
        def altered(path):
            source = original_read(path)
            if path.endswith("MenuBottomSheetFragment.java"):
                source = source.replace("activity.onTrackRecordingSwitchChanged(enabled);", "")
            return source
        with patch.object(controls, "read", altered):
            with self.assertRaisesRegex(SystemExit, "still reach the Activity"):
                controls.verify_track_recording()
