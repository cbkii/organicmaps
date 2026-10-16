"""Mutation tests for menu checks that missed InCar overlay failures."""
import importlib.util
from pathlib import Path
import unittest
import shutil
import subprocess
import tempfile
import re
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[3]
spec = importlib.util.spec_from_file_location("controls", ROOT / "android/tools/verify_in_car_location_recording_controls.py")
controls = importlib.util.module_from_spec(spec)
spec.loader.exec_module(controls)

class MenuContractTests(unittest.TestCase):
    def test_production_road_matcher_and_parking_departure_boundaries(self):
        compiler = shutil.which("c++") or shutil.which("g++")
        if not compiler:
            self.skipTest("C++ compiler unavailable")
        def without_local_includes(path):
            return re.sub(r'^#include "[^"\n]+"\n|^#pragma once\n', '', controls.read(path), flags=re.M)
        session = controls.read("libs/routing/routing_session_free_driving.cpp")
        start = session.index("  bool const plausiblePublicRoad =")
        predicate = session[start:session.index(";", start) + 1]
        source = r'''
#include <cassert>
namespace m2 { struct PointD { double x=0,y=0; }; }
namespace location { struct GpsInfo {
 double m_horizontalAccuracy=5,m_speed=1,m_speedAccuracy=-1,m_bearing=-1,m_bearingAccuracy=-1;
 bool HasSpeed() const { return m_speed>=0; }
 bool HasSpeedAccuracy() const { return m_speedAccuracy>=0; }
 bool HasBearing() const { return m_bearing>=0; }
 bool HasBearingAccuracy() const { return m_bearingAccuracy>=0; }
}; }
''' + without_local_includes("libs/routing/free_driving_road_snap_policy.hpp") + without_local_includes(
    "libs/routing/free_driving_road_matcher.hpp") + without_local_includes(
    "libs/routing/free_driving_road_matcher.cpp") + r'''
using namespace routing::free_driving_snap;
namespace free_driving_snap = routing::free_driving_snap;
struct Candidate { RoadMetadata m_metadata; RoadRelation m_relation=RoadRelation::Connected; bool acceptable=true; };
bool IsAcceptable(Candidate const & c,location::GpsInfo const &,bool){return c.acceptable;}
bool plausible(Candidate const * current,Candidate const * best,bool bestAcceptable,bool bestParkingAisle){
 int previousValue=1; auto previous=&previousValue; location::GpsInfo policyInfo;
''' + predicate + r'''
 return plausiblePublicRoad;
}
int main() {
 Candidate serviceRoad;
 assert(plausible(&serviceRoad,nullptr,false,false));
 serviceRoad.m_metadata.m_parkingAisle=true;
 assert(!plausible(&serviceRoad,nullptr,false,false));
 Candidate weakConnected;
 assert(plausible(nullptr,&weakConnected,true,false));
 assert(!plausible(nullptr,&weakConnected,true,true));
 assert(!HasConfirmedParkingDepartureFromRoad(true,true,false,false,true,69));
 assert(HasConfirmedParkingDepartureFromRoad(true,true,false,false,true,70));
 assert(!HasConfirmedParkingDepartureFromRoad(true,true,false,false,false,90));
 assert(HasConfirmedParkingDepartureFromRoad(true,true,false,true,false,0));
 assert(HasConfirmedParkingDepartureFromRoad(true,true,true,false,false,0));
 // Accumulated crawl displacement is useful even when each 1Hz step is below 3m.
 assert(HasTrustworthyParkingTurn(true,.6,4,false,1));
 assert(!HasTrustworthyParkingTurn(true,.6,4,true,0));
 assert(!HasTrustworthyParkingTurn(true,.2,4,false,1));
 assert(!HasTrustworthyParkingTurn(true,.6,2,false,1));
 assert(!HasTrustworthyParkingTurn(true,.6,4,false,8));
 assert(RoadDisplayEvidenceIsFresh(100,100,105));
 assert(!RoadDisplayEvidenceIsFresh(100,100,105.001));
 assert(!RoadDisplayEvidenceIsFresh(100,100,99));
 assert(!RoadDisplayEvidenceIsFresh(100,99,101));
 assert(!RoadDisplayEvidenceIsFresh(0,100,101));
 assert(RetainFreshRoadDisplay(MatchState::Road,AccuracyBand::Good,true,false,30,20));
 assert(!RetainFreshRoadDisplay(MatchState::Road,AccuracyBand::Good,false,false,30,20));
 assert(!RetainFreshRoadDisplay(MatchState::Road,AccuracyBand::Good,true,true,30,20));
 assert(!RetainFreshRoadDisplay(MatchState::Road,AccuracyBand::Good,true,false,36,20));
 assert(!RetainFreshRoadDisplay(MatchState::Road,AccuracyBand::Poor,true,false,30,20));
 AreaContext context; FreeDrivingRoadMatcher matcher; MatcherEvidence e;
 e.m_hasBestRoad=e.m_bestAcceptable=e.m_bestStrong=e.m_bestUnambiguous=true;
 e.m_accuracy=AccuracyBand::Good;e.m_candidateToken=1;e.m_deltaSeconds=1;
 e.m_relation=RoadRelation::SameDirectedEdge;
 matcher.Update(e,context);matcher.Update(e,context);
 assert(matcher.GetState()==MatchState::Road);
 context.m_insideParking=true;
 e.m_stationaryHold=true;
 for(int i=0;i<10;i++) {
  e.m_parkingReleaseEligible=HasConfirmedParkingDepartureFromRoad(true,true,false,false,false,90);
  assert(matcher.Update(e,context).m_state==MatchState::Road);
 }
 // Traffic-light stop, then a connected low-speed bend: same road authority survives.
 e.m_stationaryHold=false;e.m_motionEstablished=true;e.m_relation=RoadRelation::Connected;e.m_candidateToken=2;
 assert(matcher.Update(e,context).m_state==MatchState::Road);
 e.m_parkingReleaseEligible=HasConfirmedParkingDepartureFromRoad(true,true,false,false,true,70);
 for(int i=0;i<4;i++)matcher.Update(e,context);
 assert(matcher.GetState()==MatchState::ParkingFree);
 assert(!RetainFreshRoadDisplay(matcher.GetState(),AccuracyBand::Good,true,false,30,20));
 e.m_parkingReleaseEligible=false;e.m_allowFreeStateRoadReacquire=true;e.m_candidateToken=3;
 matcher.Update(e,context);matcher.Update(e,context);
 assert(matcher.GetState()==MatchState::Road);
}
'''
        with tempfile.TemporaryDirectory() as tmp:
            src = Path(tmp) / "roads.cpp"
            binary = Path(tmp) / "roads"
            src.write_text(source)
            result = subprocess.run([compiler, "-std=c++17", str(src), "-o", str(binary)],
                                    capture_output=True, text=True, timeout=30)
            self.assertEqual(0, result.returncode, result.stderr)
            subprocess.run([str(binary)], check=True, timeout=10)

    def test_production_ribbon_warning_surfaces_and_contrast(self):
        import xml.etree.ElementTree as ET
        runtime = shutil.which("java")
        if not runtime:
            self.skipTest("Java unavailable")
        source = controls.read("android/app/src/main/java/app/organicmaps/incar/InCarDrivingUi.java")
        render = controls.method_body(source, "private static void renderRibbon(")
        foreground = controls.method_body(source, "private static int ribbonForeground(")
        self.assertIn("renderRibbon(activity, snapshot.navigating && warning,", source)
        colours = {}
        for path in ("android/app/src/main/res/values/in_car_shared_fallbacks.xml",
                     "android/app/src/inCar/res/values/colors.xml",
                     "android/app/src/inCar/res/values/in_car_navigation_colours.xml"):
            colours.update({x.get("name"): x.text for x in ET.parse(ROOT / path).getroot() if x.tag == "color"})
        palettes = []
        for night in (False, True):
            values = dict(colours)
            if night:
                for path in ("values-night/colors.xml", "values-night/in_car_navigation_colours.xml"):
                    values.update({x.get("name"): x.text for x in ET.parse(
                        ROOT / "android/app/src/inCar/res" / path).getroot() if x.tag == "color"})
            def resolve(token):
                value = values[token].strip()
                return resolve(value[7:]) if value.startswith("@color/") else value
            names = sorted(set(re.findall(r"R\.color\.(\w+)", render)))
            palettes.append("new int[]{" + ",".join("(int)0x" + resolve(n).removeprefix("#") for n in names) + "}")
        ids = sorted(set(re.findall(r"R\.id\.(\w+)", render)) | {"in_car_nav_speed", "nav_speed_limit"})
        resources = "class R { static class id {" + "".join(
            f"static final int {n}={i};" for i, n in enumerate(ids)) + "} static class color {" + "".join(
            f"static final int {n}={i};" for i, n in enumerate(names)) + "}}"
        harness = r'''
import java.util.*;
class View {
 static final int VISIBLE=0,INVISIBLE=4;
 int background=0x12345678,visibility=VISIBLE;
 Map<Integer,View> children=new HashMap<>();
 @SuppressWarnings("unchecked") <T extends View> T findViewById(int id){return (T)children.get(id);}
 void setBackgroundColor(int c){background=c;}
 void setVisibility(int v){visibility=v;}
 int getVisibility(){return visibility;}
}
class TextView extends View { int color; void setTextColor(int c){color=c;} }
class ImageView extends View { int tint; }
class LanesView extends View { int active,inactive; void setLaneColors(int a,int b){active=a;inactive=b;} }
class MwmActivity extends View { int[] colors; }
class ColorStateList { int color; static ColorStateList valueOf(int c){var r=new ColorStateList();r.color=c;return r;} }
class ImageViewCompat { static void setImageTintList(ImageView v,ColorStateList c){v.tint=c.color;} }
class ContextCompat { static int getColor(MwmActivity a,int id){return a.colors[id];} }
class InCarSpeedDisplayPolicy {
 enum Severity { WARNING,SEVERE }
 static Severity current=Severity.SEVERE;
 static Severity severity(){return current;}
}
class ColorUtils {
 static int blendARGB(int a,int b,float f){
  int c=0; for(int shift:new int[]{0,8,16,24})c|=((int)(((a>>>shift)&255)*(1-f)+((b>>>shift)&255)*f))<<shift;
  return c;
 }
 static double channel(int n){double v=n/255.0;return v<=0.04045?v/12.92:Math.pow((v+0.055)/1.055,2.4);}
 static double luminance(int c){return .2126*channel((c>>>16)&255)+.7152*channel((c>>>8)&255)+.0722*channel(c&255);}
 static double calculateContrast(int a,int b){double x=luminance(a)+.05,y=luminance(b)+.05;return Math.max(x,y)/Math.min(x,y);}
}
''' + resources + r'''
class RibbonTest {
 static int ribbonForeground(int background){''' + foreground + r'''}
 static void renderRibbon(MwmActivity activity,boolean warning,float strength){''' + render + r'''}
 static void check(boolean ok){if(!ok)throw new AssertionError();}
 public static void main(String[] args){
  int[][] palettes={''' + ",".join(palettes) + r'''};
  for(int[] palette:palettes)for(boolean landscape:new boolean[]{false,true})for(boolean hasLanes:new boolean[]{false,true}){
   MwmActivity a=new MwmActivity(); a.colors=palette;
   for(int id=0;id<''' + str(len(ids)) + r''';id++)a.children.put(id,new View());
   for(int id:new int[]{R.id.nav_next_turn_frame,R.id.nav_next_next_turn_frame}){
    a.children.get(id).children.put(R.id.turn,new ImageView());
    a.children.get(id).children.put(R.id.distance,new TextView());
   }
   a.children.get(R.id.street_frame).children.put(R.id.street,new TextView());
   a.children.put(R.id.in_car_nav_guidance_label,new TextView());
   LanesView lanes=new LanesView(); lanes.visibility=hasLanes?View.VISIBLE:View.INVISIBLE;
   a.children.put(R.id.lanes,lanes);
   if(!landscape){a.children.remove(R.id.in_car_nav_guidance_group);a.children.remove(R.id.in_car_nav_immediate_group);}
   for(var severity:InCarSpeedDisplayPolicy.Severity.values())for(float strength:new float[]{.18f,.5f,.8f,1f}){
    InCarSpeedDisplayPolicy.current=severity;
    renderRibbon(a,false,0);
    int original=a.children.get(R.id.nav_next_next_turn_frame).background;
    renderRibbon(a,true,strength);
    View after=a.children.get(R.id.nav_next_next_turn_frame),now=a.children.get(R.id.nav_next_turn_frame);
    check(after.background!=original);
    check(lanes.background==after.background);
    TextView label=a.findViewById(R.id.in_car_nav_guidance_label);
    TextView distance=now.findViewById(R.id.distance);
    check(ColorUtils.calculateContrast(label.color,after.background)>=4.5);
    check(ColorUtils.calculateContrast(distance.color,now.background)>=4.5);
    check(ColorUtils.calculateContrast(lanes.active,lanes.background)>=4.5);
    check(a.children.get(R.id.in_car_nav_speed).background==0x12345678);
    check(a.children.get(R.id.nav_speed_limit).background==0x12345678);
    renderRibbon(a,false,0);
    check(after.background==original);
   }
  }
 }
}
'''
        with tempfile.TemporaryDirectory() as tmp:
            src = Path(tmp) / "RibbonTest.java"
            src.write_text(harness)
            color = Path(tmp) / "android/graphics/Color.java"
            color.parent.mkdir(parents=True)
            color.write_text("package android.graphics; public class Color { public static final int BLACK=0xff000000,WHITE=0xffffffff; }")
            compiler = Path(tmp) / "Compile.java"
            compiler.write_text("class Compile { public static void main(String[] a) { "
                                "System.exit(javax.tools.ToolProvider.getSystemJavaCompiler().run(null, null, null, a)); }}")
            result = subprocess.run([runtime, str(compiler), "-d", tmp, str(src), str(color)],
                                    capture_output=True, text=True, timeout=30)
            self.assertEqual(0, result.returncode, result.stderr)
            subprocess.run([runtime, "-cp", tmp, "RibbonTest"], check=True, timeout=10)

    def test_production_location_hold_keeps_metadata_and_rejects_older_fixes(self):
        compiler = shutil.which("c++") or shutil.which("g++")
        if not compiler:
            self.skipTest("C++ compiler unavailable")
        body = controls.method_body(controls.read("libs/drape_frontend/my_position_controller.cpp"),
                                    "void MyPositionController::OnLocationUpdate(")
        # Execute the production acceptance/hold prefix, before unrelated animation/rendering.
        prefix = body[:body.index("  bool const isMovingFast")]
        source = r'''
#include <cstdint>
#include <cassert>
struct Point { double x=0,y=0; };
struct Rect { double SizeX() const { return 20; } };
namespace m2 { using PointD=Point; using RectD=Rect; }
namespace mercator {
Point FromLatLon(double x,double y) { return {x,y}; }
double DistanceOnEarth(Point,Point) { return 1; }
Rect MetersToXY(double,double,double) { return {}; }
}
namespace location { struct GpsInfo {
 double m_monotonicTimestamp=20,m_timestamp=20,m_latitude=42,m_longitude=0;
 double m_horizontalAccuracy=5,m_speed=1;
 bool HasMonotonicTimestamp() const { return m_monotonicTimestamp>0; }
 bool HasSpeed() const { return true; }
}; }
namespace driving_policy { bool ShouldHoldFreeDrivingCamera(bool,bool,bool,bool,double,double) { return true; } }
constexpr double kUnknownAutoZoom=-1;
struct Owner {
 bool m_isInRouting=false,m_isDrivingView=true,m_isPositionAssigned=true;
 bool m_hasLastHeldDrivingPosition=false;
 int64_t m_lastRecenterObservationNanos=19000000000;
 Point m_position{7,0},m_lastHeldDrivingPosition;
 double m_errorRadius=0,m_horizontalAccuracy=100,m_autoScale2d=0,m_autoScale3d=0;
 double CalculateZoomBySpeed(double,bool) { return 2; }
 Point GetDrawablePosition() { return m_position; }
 double GetDrawableAzimut() { return 0; }
 void RefreshLocationFreshness(location::GpsInfo const & info) {
   m_lastRecenterObservationNanos=static_cast<int64_t>(info.m_monotonicTimestamp*1e9);
 }
 void update(location::GpsInfo const & info) {
''' + prefix + r'''
 }
};
int main() {
 Owner o; location::GpsInfo fix;
 o.update(fix);
 assert(o.m_position.x==7 && o.m_lastHeldDrivingPosition.x==42 && o.m_hasLastHeldDrivingPosition);
 assert(o.m_horizontalAccuracy==5 && o.m_errorRadius==10 && o.m_autoScale2d==4);
 fix.m_monotonicTimestamp=19; fix.m_latitude=99; fix.m_horizontalAccuracy=70;
 o.update(fix);
 assert(o.m_lastHeldDrivingPosition.x==42 && o.m_horizontalAccuracy==5 && o.m_hasLastHeldDrivingPosition);
 assert(o.m_lastRecenterObservationNanos==20000000000);
}
'''
        with tempfile.TemporaryDirectory() as tmp:
            src = Path(tmp) / "held.cpp"
            binary = Path(tmp) / "held"
            src.write_text(source)
            subprocess.run([compiler, "-D__ANDROID__", "-std=c++17", str(src), "-o", str(binary)],
                           check=True, timeout=30)
            subprocess.run([str(binary)], check=True, timeout=10)

    def test_accessibility_interaction_cancels_without_swallowing_events(self):
        runtime = shutil.which("java")
        if not runtime:
            self.skipTest("Java unavailable: accessibility cancellation NOT_RUN")
        activity = controls.read("android/app/src/main/java/app/organicmaps/MwmActivity.java")
        create = controls.method_body(activity, "protected void onSafeCreate(")
        self.assertLess(create.index("setContentView("), create.index("installInCarAccessibilityCancellation();"))
        install = controls.method_body(activity, "private void installInCarAccessibilityCancellation()")
        cancel = controls.method_body(activity, "public void cancelInCarNavigationCountdown()")
        files = {
            "android/os/Build.java": "package android.os; public class Build { public static class VERSION { public static int SDK_INT=29; } public static class VERSION_CODES { public static final int Q=29; } }",
            "android/view/View.java": r'''package android.view;
public class View { public static class AccessibilityDelegate {
 public boolean allow=true; public int calls,forwarded; public final android.view.accessibility.AccessibilityNodeProvider provider=new android.view.accessibility.AccessibilityNodeProvider();
 public boolean onRequestSendAccessibilityEvent(ViewGroup host,View child,android.view.accessibility.AccessibilityEvent e){++calls;return allow;}
 public void sendAccessibilityEvent(View v,int t){forwarded|=1;}
 public void sendAccessibilityEventUnchecked(View v,android.view.accessibility.AccessibilityEvent e){forwarded|=2;}
 public boolean dispatchPopulateAccessibilityEvent(View v,android.view.accessibility.AccessibilityEvent e){forwarded|=4;return allow;}
 public void onPopulateAccessibilityEvent(View v,android.view.accessibility.AccessibilityEvent e){forwarded|=8;}
 public void onInitializeAccessibilityEvent(View v,android.view.accessibility.AccessibilityEvent e){forwarded|=16;}
 public void onInitializeAccessibilityNodeInfo(View v,android.view.accessibility.AccessibilityNodeInfo i){forwarded|=32;}
 public android.view.accessibility.AccessibilityNodeProvider getAccessibilityNodeProvider(View v){forwarded|=64;return provider;}
 public boolean performAccessibilityAction(View v,int a,android.os.Bundle b){forwarded|=128;return allow;}
 public void addExtraDataToAccessibilityNodeInfo(View v,android.view.accessibility.AccessibilityNodeInfo i,String key,android.os.Bundle b){forwarded|=256;}
} }''',
            "android/view/ViewGroup.java": "package android.view; public class ViewGroup extends View { public AccessibilityDelegate delegate; public int getterCalls; public AccessibilityDelegate getAccessibilityDelegate(){++getterCalls;return delegate;} public void setAccessibilityDelegate(AccessibilityDelegate d){delegate=d;} }",
            "android/os/Bundle.java": "package android.os; public class Bundle {}",
            "android/view/accessibility/AccessibilityNodeInfo.java": "package android.view.accessibility; public class AccessibilityNodeInfo {}",
            "android/view/accessibility/AccessibilityNodeProvider.java": "package android.view.accessibility; public class AccessibilityNodeProvider {}",
            "androidx/annotation/NonNull.java": "package androidx.annotation; public @interface NonNull {}",
            "androidx/annotation/Nullable.java": "package androidx.annotation; public @interface Nullable {}",
            "android/view/accessibility/AccessibilityEvent.java": r'''package android.view.accessibility;
public class AccessibilityEvent {
 public static final int TYPE_VIEW_ACCESSIBILITY_FOCUSED=32768,TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED=65536,
 TYPE_VIEW_SCROLLED=4096,TYPE_WINDOW_CONTENT_CHANGED=2048,TYPE_VIEW_CLICKED=1,TYPE_VIEW_LONG_CLICKED=2;
 private final int type; public AccessibilityEvent(int t){type=t;} public int getEventType(){return type;}
}''',
            "AccessibilityTest.java": r'''import android.view.*;
import android.view.accessibility.AccessibilityEvent;
import app.organicmaps.incar.InCarNavigationCountdownPolicy;
@interface NonNull {}
class BuildConfig {static boolean IS_IN_CAR=true;}
class Countdown {final InCarNavigationCountdownPolicy policy=new InCarNavigationCountdownPolicy();boolean pending=true;
 boolean hasPendingAction(){return pending;}void cancel(){pending=false;policy.cancel();}}
class Window {ViewGroup root=new ViewGroup();View getDecorView(){return root;}}
class Host {
 Window window=new Window();Countdown mInCarNavigationCountdown=new Countdown();long mInCarInteractionGeneration;boolean destroyed;
 boolean isDestroyed(){return destroyed;}
 Window getWindow(){return window;}
 void install(){''' + install + r'''}
 void cancelInCarNavigationCountdown(){''' + cancel + r'''}
}
public class AccessibilityTest {
 static void check(boolean b){if(!b)throw new AssertionError();}
 public static void main(String[] args){
  for(boolean inCar:new boolean[]{true,false})for(int sdk:new int[]{21,29})for(boolean existing:new boolean[]{true,false}){
   for(boolean destroyed:new boolean[]{true,false})for(int type:new int[]{32768,65536,4096,2048,1,2}){
    BuildConfig.IS_IN_CAR=inCar;android.os.Build.VERSION.SDK_INT=sdk;
    Host h=new Host();h.destroyed=destroyed;View.AccessibilityDelegate original=new View.AccessibilityDelegate();original.allow=false;
    if(existing)h.window.root.delegate=original;
    h.install();boolean hooked=inCar&&sdk>=29;check(h.window.root.getterCalls==(hooked?1:0));
    h.mInCarNavigationCountdown.policy.arm("START",1,2,0);
    boolean allowed=h.window.root.delegate==null||h.window.root.delegate.onRequestSendAccessibilityEvent(h.window.root,new View(),new AccessibilityEvent(type));
    boolean cancelled=hooked&&!destroyed&&type!=2048;
    check(h.mInCarNavigationCountdown.policy.dispatch(2000,true)==!cancelled);
    check(h.mInCarInteractionGeneration==(cancelled?1:0));
    if(existing){
     check(!allowed&&original.calls==1); // Delegate forwarding/return value is unchanged.
     View.AccessibilityDelegate d=h.window.root.delegate;View v=new View();AccessibilityEvent e=new AccessibilityEvent(2048);
     d.sendAccessibilityEvent(v,1);d.sendAccessibilityEventUnchecked(v,e);check(!d.dispatchPopulateAccessibilityEvent(v,e));
     d.onPopulateAccessibilityEvent(v,e);d.onInitializeAccessibilityEvent(v,e);
     d.onInitializeAccessibilityNodeInfo(v,new android.view.accessibility.AccessibilityNodeInfo());
     check(d.getAccessibilityNodeProvider(v)==original.provider);
     check(!d.performAccessibilityAction(v,0,new android.os.Bundle()));
     d.addExtraDataToAccessibilityNodeInfo(v,new android.view.accessibility.AccessibilityNodeInfo(),"extra",new android.os.Bundle());
     check(original.forwarded==511);
    }
    if(cancelled)check(!h.mInCarNavigationCountdown.policy.arm("START",1,2,3000));
   }
  }
  BuildConfig.IS_IN_CAR=true;android.os.Build.VERSION.SDK_INT=29;
  Host h=new Host();h.install();h.mInCarNavigationCountdown.policy.arm("GO_TO",1,2,0);
  h.cancelInCarNavigationCountdown();long interaction=h.mInCarInteractionGeneration;
  h.window.root.delegate.onRequestSendAccessibilityEvent(h.window.root,new View(),new AccessibilityEvent(1));
  check(h.mInCarInteractionGeneration==interaction); // Owned click already cancelled; queued START may arm.
  check(h.mInCarNavigationCountdown.policy.arm("START",1,2,0));
  check(h.mInCarNavigationCountdown.policy.dispatch(2000,true));
 }
}'''
        }
        files["app/organicmaps/incar/InCarNavigationCountdownPolicy.java"] = controls.read(
            "android/app/src/main/java/app/organicmaps/incar/InCarNavigationCountdownPolicy.java")
        files["app/organicmaps/incar/InCarAccessibilityDelegate.java"] = controls.read(
            "android/app/src/main/java/app/organicmaps/incar/InCarAccessibilityDelegate.java")
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
            subprocess.run([runtime, str(compiler), "-d", tmp, *sources], check=True, timeout=30)
            subprocess.run([runtime, "-cp", tmp, "AccessibilityTest"], check=True, timeout=10)

    def test_incar_map_overlays_share_day_night_palette_and_warning_tint(self):
        import xml.etree.ElementTree as ET
        base = ROOT / "android/app/src/inCar/res"
        def colours(path):
            return {x.get("name"): (x.text or "").strip()
                    for x in ET.parse(base / path).getroot() if x.tag == "color"}
        day = colours("values/colors.xml")
        night = colours("values-night/colors.xml")
        for token in ("in_car_camera_rail_surface", "in_car_quick_foreground",
                      "in_car_map_control_outline", "in_car_surface_overlay",
                      "in_car_selection_accent"):
            self.assertIn(token, day)
            self.assertIn(token, night)
            self.assertNotEqual(day[token], night[token])
        self.assertEqual("@color/in_car_action_primary", day["in_car_quick_foreground"])
        self.assertEqual("@color/in_car_selection_accent", night["in_car_quick_foreground"])
        camera = controls.read("android/app/src/inCar/res/drawable/in_car_camera_rail.xml")
        quick = controls.read("android/app/src/inCar/res/drawable/in_car_quick_rail.xml")
        for expected in ("@color/in_car_camera_rail_surface", "@color/in_car_map_control_outline"):
            self.assertIn(expected, camera)
            self.assertIn(expected, quick)
        overlay = controls.read("android/app/src/main/java/app/organicmaps/incar/InCarDrivingUi.java")
        self.assertIn("group.setBackgroundColor(afterBackground)", overlay)
        self.assertIn("immediateGroup.setBackgroundColor(immediateBackground)", overlay)
        theme = controls.read("android/app/src/main/java/app/organicmaps/util/ThemeSwitcher.java")
        self.assertIn("ThemeUtils.isDarkTheme(context)", theme)
        self.assertIn("calculateMapStyle(isDarkMode)", theme)

    def test_map_dock_two_column_geometry_and_transparent_camera_cells(self):
        import xml.etree.ElementTree as ET
        base = ROOT / "android/app/src"
        ns = "{http://schemas.android.com/apk/res/android}"
        values = ET.parse(base / "main/res/values/in_car_runtime_ui.xml").getroot()
        dims = {node.get("name"): (node.text or "").strip() for node in values if node.tag == "dimen"}
        self.assertEqual("69dp", dims["in_car_map_rail_cell_size"])
        self.assertEqual("8dp", dims["in_car_map_dock_edge"])
        self.assertEqual("12dp", dims["in_car_map_dock_column_gap"])
        self.assertEqual("8dp", dims["in_car_map_dock_footer_gap"])
        self.assertEqual("77dp", dims["in_car_map_dock_browse_reserve"])
        for layout in ("layout", "layout-land"):
            path = base / f"inCar/res/{layout}/map_buttons_bottom.xml"
            footer = ET.parse(path).getroot()
            dock = next(x for x in footer if x.get(ns+"id") == "@+id/in_car_browsing_controls")
            self.assertEqual("LinearLayout", dock.tag)
            self.assertEqual("@dimen/in_car_map_dock_edge", dock.get(ns+"paddingBottom"))
            search = next(x for x in dock if x.get("layout") == "@layout/map_buttons_search_square")
            menu = next(x for x in dock if x.get("layout") == "@layout/map_buttons_menu")
            self.assertEqual("wrap_content", search.get(ns+"layout_width"))
            self.assertEqual("wrap_content", menu.get(ns+"layout_width"))
            self.assertEqual("@dimen/in_car_map_dock_column_gap", search.get(ns+"layout_marginEnd"))
            self.assertIsNone(menu.get(ns+"layout_marginEnd"))
        camera = ET.parse(base / "inCar/res/layout/map_buttons_zoom.xml").getroot()
        rail = next(x for x in camera if x.get(ns+"id") == "@+id/zoom_buttons_container")
        for child in rail:
            if child.get(ns+"id") in ("@+id/nav_zoom_in", "@+id/nav_zoom_out",
                                      "@+id/in_car_camera_view_recenter"):
                self.assertEqual("androidx.appcompat.widget.AppCompatImageButton", child.tag)
                self.assertEqual("@drawable/in_car_map_rail_cell_feedback", child.get(ns+"background"))
        policy = controls.read("android/app/src/main/java/app/organicmaps/incar/InCarMapDockPolicy.java")
        quick = controls.read("android/app/src/main/java/app/organicmaps/incar/InCarQuickDestinationsUi.java")
        controller = controls.read("android/app/src/main/java/app/organicmaps/maplayer/MapButtonsController.java")
        self.assertIn("quickBottomMarginPx", policy)
        self.assertIn("quickPhysicalOffsetPx", quick)
        self.assertIn("positionCameraFrame", controller)
        self.assertIn("InCarQuickDestinationsUi.refreshDockGeometry(activity)", controller)
        self.assertIn("public static void refreshDockGeometry(", quick)
        self.assertNotIn("cameraColumn = horizontalCamera", quick)
        self.assertNotIn("mBottomButtonsHeight + mSystemBottomInset", quick)
        visuals = controls.read("android/app/src/main/java/app/organicmaps/util/InCarVisuals.java")
        self.assertIn("footer.setLayoutDirection(left ? View.LAYOUT_DIRECTION_LTR : View.LAYOUT_DIRECTION_RTL)", visuals)
        # Compile and run the actual production pure geometry methods rather
        # than merely comparing numbers manufactured by this Python test.
        java = shutil.which("java")
        if java:
            methods = []
            for method in ("cameraBottomFor", "quickBottomFor", "quickPhysicalOffsetFor"):
                match = re.search(r"  static int " + method + r"\([^)]*\)\s*\{", policy)
                self.assertIsNotNone(match, method)
                opening = policy.index("{", match.start())
                depth, end = 1, opening + 1
                while depth:
                    depth += (policy[end] == "{") - (policy[end] == "}")
                    end += 1
                methods.append(policy[match.start():end])
            harness = ("class DockGeometryTest {\n" + "\n".join(methods) +
                       """\npublic static void main(String[] ignored) {
  for (int size : new int[] {55, 69}) {
    int edge=8, gap=12, footerGap=8, width=1280;
    assert quickPhysicalOffsetFor(edge,size,gap,false)==edge+size+gap;
    assert quickPhysicalOffsetFor(edge,size,gap,true)==edge;
    assert cameraBottomFor(size,footerGap)==size+footerGap;
    assert quickBottomFor(size,footerGap,edge,0)==size+footerGap+edge;
    assert quickBottomFor(size,footerGap,edge,size)==size+2*footerGap+edge+size;
    int rightCamera=width-edge-size;
    int leftQuick=rightCamera-size-gap;
    assert leftQuick>=0 && leftQuick+size+gap==rightCamera;
  }
}
}""")
            with tempfile.TemporaryDirectory() as tmp:
                path = Path(tmp) / "DockGeometryTest.java"
                path.write_text(harness)
                subprocess.run([java, "-ea", str(path)], check=True, timeout=30)
        driving = controls.read("android/app/src/main/java/app/organicmaps/incar/InCarDrivingUi.java")
        self.assertIn("final View zoomIn = scope.findViewById(R.id.nav_zoom_in)", driving)
        self.assertIn("final FloatingActionButton drivingView = scope.findViewById", driving)
        ids = {child.get(ns+"id") for child in rail if child.get(ns+"id")}
        self.assertEqual({"@+id/nav_zoom_in", "@+id/nav_zoom_out",
                          "@+id/in_car_camera_view_recenter"}, ids)
        follow = ET.parse(base / "inCar/res/layout/map_buttons_myposition.xml").getroot()
        self.assertEqual("androidx.appcompat.widget.AppCompatImageButton", follow.tag)
        self.assertEqual("@drawable/in_car_map_rail_cell_feedback", follow.get(ns+"background"))
        self.assertEqual("fitCenter", follow.get(ns+"scaleType"))

    def test_incar_overlay_visibility_and_occupancy_contracts(self):
        """Execute actual production state predicate and rectangle intersection."""
        quick = controls.read("android/app/src/main/java/app/organicmaps/incar/InCarQuickDestinationsUi.java")
        vis = controls.read("android/app/src/main/java/app/organicmaps/incar/InCarQuickDestinationsPolicy.java")
        occupied = controls.read("android/app/src/main/java/app/organicmaps/incar/InCarOverlayCoordinator.java")
        self.assertIn("mButtonsHidden = Boolean.TRUE.equals(hidden)", quick)
        self.assertIn("mBottomButtonsHidden = Boolean.TRUE.equals(hidden)", quick)
        self.assertIn("mOverflowDialogOpen = true", quick)
        self.assertIn("mOverflowDialogOpen = false", quick)
        self.assertIn("mImeVisible = imeVisible", quick)
        self.assertIn("!mImeVisible && (mNavigationMode || !mRoutingSheetOpen)", quick)
        self.assertIn("getShowRoutingBottomSheet().observe(", quick)
        self.assertIn("final int availablePx = Math.max(0, parentHeight - topReserved - bottomMargin)", quick)
        self.assertNotIn("Math.max(minimum, parentHeight - topReserved - bottomMargin)", quick)
        sizing = controls.read("android/app/src/main/java/app/organicmaps/incar/InCarDialogSizing.java")
        self.assertIn("InCarSettingsStore.controlsOnLeft(activity) ? Gravity.LEFT : Gravity.RIGHT", sizing)
        self.assertIn("RoutingController.get().isPlanning()", quick)
        self.assertIn("InCarOverlayCoordinator.measureQuickColumn(", quick)
        self.assertIn("InCarOverlayCoordinator.measure(mActivity, left, side, cell)", quick)
        self.assertNotIn("InCarOverlayCoordinator.measure(mActivity, left, currentActionSizePx())", quick)
        self.assertNotIn("mRoot.bringToFront()", quick)
        self.assertIn("getGlobalVisibleRect(rect)", occupied)
        self.assertIn("R.id.routing_bottom_buttons", occupied)
        self.assertIn("R.id.nav_bottom_frame", occupied)
        self.assertIn("R.id.nav_next_turn_container", occupied)
        self.assertIn("usableColumnHeight(", occupied)
        camera = controls.read("android/app/src/main/java/app/organicmaps/maplayer/MapButtonsController.java")
        self.assertIn("InCarOverlayCoordinator.usableColumnHeight(", camera)
        self.assertIn("mCameraOccupancyListener = () ->", camera)
        self.assertIn("removeOnGlobalLayoutListener(mCameraOccupancyListener)", camera)
        self.assertIn("R.id.nav_speed_limit", occupied)
        self.assertIn("R.id.in_car_nav_speed", occupied)
        self.assertIn("measureQuickColumn(", occupied)
        self.assertIn("mOccupiedRegionListener = () ->", quick)
        self.assertIn("removeOnGlobalLayoutListener(mOccupiedRegionListener)", quick)
        self.assertIn("final InCarOverlayCoordinator.Snapshot occupied = measureCandidateOccupancy()", quick)
        self.assertIn("private void showFuelChargingChoice()", quick)
        self.assertIn("mOverflowDialogOpen = true", quick)
        java = shutil.which("java")
        if not java:
            self.skipTest("Java source launcher unavailable")
        method_start = vis.index("  public static boolean shouldShowSurface(boolean inCarFlavor, boolean searchOpen, boolean placePageOpen,")
        body_start = vis.index("{", method_start)
        depth, end = 1, body_start + 1
        while depth:
            depth += (vis[end] == "{") - (vis[end] == "}")
            end += 1
        method = vis[method_start:end]
        rect_start = occupied.index("  public static boolean overlapsColumn(")
        rect_body = occupied.index("{", rect_start)
        depth, rect_end = 1, rect_body + 1
        while depth:
            depth += (occupied[rect_end] == "{") - (occupied[rect_end] == "}")
            rect_end += 1
        rect_method = occupied[rect_start:rect_end].replace("@NonNull ", "")
        cap_start = occupied.index("  public static int usableColumnHeight(")
        cap_body = occupied.index("{", cap_start)
        depth, cap_end = 1, cap_body + 1
        while depth:
            depth += (occupied[cap_end] == "{") - (occupied[cap_end] == "}")
            cap_end += 1
        cap_method = occupied[cap_start:cap_end]
        harness = """class OverlayPolicyTest {
static class Rect {int left,right; Rect(int l,int r){left=l;right=r;}}
""" + method + "\n" + rect_method + "\n" + cap_method + """
public static void main(String[] args) {
  for (int mask=0;mask<256;mask++) {
    boolean car=(mask&1)!=0,search=(mask&2)!=0,place=(mask&4)!=0;
    boolean planning=(mask&8)!=0,nav=(mask&16)!=0,buttons=(mask&32)!=0;
    boolean bottom=(mask&64)!=0,modal=(mask&128)!=0;
    boolean expected=car&&!search&&!place&&!modal&&(nav||(!planning&&!buttons&&!bottom));
    assert shouldShowSurface(car,search,place,planning,nav,buttons,bottom,modal)==expected;
  }
  assert overlapsColumn(new Rect(10,30),29,40);
  assert !overlapsColumn(new Rect(10,30),30,40);
  assert !overlapsColumn(new Rect(40,50),10,40);
  assert overlapsColumn(new Rect(0,100),10,40);
  assert usableColumnHeight(410,480,120,76)==284;
  assert usableColumnHeight(410,480,180,76)==224;
  assert usableColumnHeight(410,480,350,140)==0;
  assert usableColumnHeight(200,480,0,0)==200;
  assert usableColumnHeight(200,480,-15,-5)==200;
}
}"""
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "OverlayPolicyTest.java"
            path.write_text(harness)
            subprocess.run([java, "-ea", str(path)], check=True, timeout=30)

    def test_unknown_compiled_colour_references_report_a_validation_error(self):
        from tools.ci import verify_in_car_light_palette as palette
        dump = ROOT / "data/drules_in_car.txt"
        original_read = Path.read_text
        source = original_read(dump)
        patterns = (
            r"(type amenity-fuel\n.*?    caption primary\[[^]]*?color=)c\d+",
            r"(type amenity-fuel\n.*?    caption primary\[[^]]*?stroke=)c\d+",
            r"(type highway-primary\n.*?    path_text primary\[[^]]*?color=)c\d+",
            r"(type building-address\n.*?    caption primary\[[^]]*?color=)c\d+",
            r"(^  Route )c\d+",
        )
        for pattern in patterns:
            with self.subTest(pattern=pattern):
                malformed, count = re.subn(pattern, r"\g<1>c999999", source, count=1, flags=re.S | re.M)
                self.assertEqual(count, 1)
                def read(path, *args, **kwargs):
                    return malformed if path == dump else original_read(path, *args, **kwargs)
                with patch.object(Path, "read_text", read):
                    with self.assertRaisesRegex(palette.VerificationError, "unknown colour reference c999999"):
                        palette.verify_compiled_feature_captions(ROOT)

    def test_mixed_highway_types_keep_one_native_caption_owner(self):
        compiler = shutil.which("g++")
        if not compiler:
            self.skipTest("C++ compiler unavailable: overlay ownership NOT_RUN")
        source = controls.read("libs/drape_frontend/stylist.cpp")
        # Execute the production selection/filter block, not a parallel ownership policy.
        selection = source.split("  feature::TypesHolder const types(f);", 1)[1].split(
            "  feature::FilterRulesByRuntimeSelector", 1)[0]
        selection = "  feature::TypesHolder const types(f);" + selection
        priorities = {}
        current = ""
        for line in (ROOT / "data/drules_in_car.txt").read_text().splitlines():
            if line.startswith("type "):
                current = line[5:]
            if line.startswith(("    caption", "    path_text", "    symbol", "    shield")):
                match = re.search(r"priority=(-?\d+)", line)
                if match:
                    priorities[current] = max(priorities.get(current, -2147483648), int(match[1]))
        cases = [(priorities[road], priorities[other])
                 for road in ("highway-primary", "highway-service")
                 for other in ("railway-rail-tourism", "waterway-river")]
        harness = r'''#include <cassert>
#include <cstdint>
#include <vector>
#include <limits>
#include <utility>
namespace drule { enum Kind { line, area, caption, pathtext, symbol, shield };
struct Key { int owner; Kind m_type; bool m_hatching=false; }; using KeysT=std::vector<Key>; }
struct FeatureType { std::vector<uint32_t> types; };
namespace feature { struct TypesHolder {
 std::vector<uint32_t> ts; TypesHolder(FeatureType &f):ts(f.types){}
 size_t Size() const{return ts.size();} uint32_t front() const{return ts.front();}
 auto begin() const{return ts.begin();} auto end() const{return ts.end();} int GetGeomType() const{return 1;}
}; }
struct Object { int id=0,priority=0; int GetMaxOverlaysPriority() const{return priority;}
 void GetSuitable(int,int,drule::KeysT &keys) const {keys.push_back({id,drule::line});keys.push_back({id,drule::pathtext});} };
struct Classificator { Object objects[3]; Object const *GetObject(uint32_t t) const{return &objects[t];} };
Classificator cl; Classificator const &classif(){return cl;} Classificator const &GetOutdoorClassif(){return cl;}
struct IsHatchingTerritoryChecker {static IsHatchingTerritoryChecker Instance(){return {};}
 bool operator()(uint32_t) const{return false;} };
drule::KeysT select(FeatureType &f, uint8_t zoomLevel, bool forceOutdoorStyle) {''' + selection + r'''return keys;}
int main(){
 for(auto p:std::vector<std::pair<int,int>>{''' + ",".join("{" + str(a) + "," + str(b) + "}" for a, b in cases) + r'''}){
  cl.objects[1]={1,p.first};cl.objects[2]={2,p.second};assert(p.first>p.second);
  for(auto ts:std::vector<std::vector<uint32_t>>{{1,2},{2,1}}){
   FeatureType f{ts};auto keys=select(f,18,false);int overlays=0,lines=0;
   for(auto k:keys){if(k.m_type==drule::pathtext){++overlays;assert(k.owner==1);}else ++lines;}
   assert(overlays==1 && lines==2); // Other-type geometry survives; its caption cannot override the highway.
  }
 }
}'''
        with tempfile.TemporaryDirectory() as tmp:
            fixture = Path(tmp) / "ownership.cpp"
            binary = Path(tmp) / "ownership"
            fixture.write_text(harness)
            subprocess.run([compiler, "-std=c++17", str(fixture), "-o", str(binary)], check=True)
            subprocess.run([str(binary)], check=True)

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
   Handler.advance(0); check(progress[0]==delay && c.hasPendingAction());
   Handler.advance(delay*1000-1); check(go.clicks==0);
   Handler.advance(delay*1000+200); check(go.clicks==1 && start.clicks==0);
   Handler.advance(2*delay*1000+400); check(go.clicks==1 && start.clicks==1);
   Handler.advance(100000); check(start.clicks==1 && Handler.pending.isEmpty() && !c.hasPendingAction());
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
 void DiscardPendingAutoPerspective() {}
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
            compiled = subprocess.run([compiler, "-std=c++17", str(source), "-o", str(binary)],
                                      capture_output=True, text=True)
            self.assertEqual(0, compiled.returncode, compiled.stderr)
            subprocess.run([str(binary)], check=True)

    def test_production_recenter_consumes_only_fresh_held_driving_fix(self):
        compiler = shutil.which("c++") or shutil.which("g++")
        if not compiler:
            self.skipTest("C++ compiler unavailable: native recenter seam NOT_RUN")
        native = controls.read("libs/drape_frontend/my_position_controller.cpp")
        body = controls.method_body(native, "bool MyPositionController::RecenterPreservingMode()")
        source = r"""
#include <cstdint>
struct Point { int value=0; };
struct Rect { Point Center() const { return {}; } };
namespace location { constexpr int FollowAndRotate=3; }
constexpr int kDoNotChangeZoom=-1;
int64_t nowNanos=20000000000;
int64_t PositionClockNanos() { return nowNanos; }
""" + controls.read("libs/drape_frontend/current_position_freshness.hpp").replace("#pragma once", "") + r"""
struct Listener {
 int events=0,last=-1;
 void PositionChanged(Point p,bool) { ++events; last=p.value; }
};
class MyPositionController {
public:
 Listener * m_listener=nullptr;
 Point m_position={7},m_lastHeldDrivingPosition={42};
 bool m_isPositionAssigned=true,m_positionIsObsolete=false,m_recenterPending=false;
 bool m_hasLastHeldDrivingPosition=true;
 int64_t m_lastRecenterObservationNanos=20000000000;
 int m_mode=location::FollowAndRotate;
 double m_drawDirection=0;
 Rect m_visiblePixelRect;
 int recentered=-1;
 bool IsNavigationStyleCameraActive() const { return true; }
 Point GetRoutingRotationPixelCenter() const { return {}; }
 Point Position() const { return m_position; }
 bool IsModeHasPosition() const { return true; }
 void ChangeModelView(Point p,int) { recentered=p.value; }
 void ChangeModelView(Point p,double,Point,int) { recentered=p.value; }
 bool RecenterPreservingMode();
};
bool MyPositionController::RecenterPreservingMode() {
""" + body + r"""
}
int main() {
 Listener listener;
 MyPositionController controller;
 controller.m_listener=&listener;
 if (!controller.RecenterPreservingMode() || controller.recentered!=42 ||
     controller.m_position.value!=42 || controller.m_hasLastHeldDrivingPosition ||
     listener.events!=1 || listener.last!=42) return 1;
 MyPositionController stale;
 stale.m_listener=&listener;
 stale.m_lastRecenterObservationNanos=100;
 if (stale.RecenterPreservingMode() || stale.m_position.value!=7 ||
     !stale.m_hasLastHeldDrivingPosition || stale.recentered!=-1 ||
     listener.events!=1 || listener.last!=42) return 2;
 MyPositionController noListener;
 if (noListener.RecenterPreservingMode()) return 3;
 MyPositionController future;
 future.m_listener=&listener;
 nowNanos=19999999999;
 if (future.RecenterPreservingMode() || listener.events!=1) return 4;
 nowNanos=30000000001;
 if (future.RecenterPreservingMode() || listener.events!=1) return 5;
 nowNanos=30000000000;
 if (!future.RecenterPreservingMode() || listener.events!=2) return 6;
 return 0;
}
"""
        with tempfile.TemporaryDirectory() as tmp:
            src = Path(tmp) / "recenter.cpp"
            binary = Path(tmp) / "recenter"
            src.write_text(source)
            subprocess.run([compiler, "-std=c++17", str(src), "-o", str(binary)],
                           check=True, timeout=30)
            subprocess.run([str(binary)], check=True, timeout=10)

    def test_camera_presentation_invalidates_scene_without_fresh_location(self):
        compiler = shutil.which("g++")
        if compiler is None:
            self.skipTest("C++ compiler unavailable: camera scene invalidation NOT_RUN")
        stream_source = controls.read("libs/drape_frontend/user_event_stream.cpp")
        body = controls.method_body(stream_source, "void UserEventStream::ApplyCameraPresentation(bool perspective)")
        discard = controls.method_body(stream_source, "void UserEventStream::DiscardPendingAutoPerspective()")
        harness = r"""#include <cassert>
#include <memory>
#include <list>
#include <mutex>
struct UserEvent {
 enum class EventType { AutoPerspective, ActiveFrame };
 virtual ~UserEvent() = default;
 virtual EventType GetType() const = 0;
};
struct ActiveFrameEvent : UserEvent {
 EventType GetType() const override { return EventType::ActiveFrame; }
};
struct SetAutoPerspectiveEvent : UserEvent {
 EventType GetType() const override { return EventType::AutoPerspective; }
};
template<typename T> using drape_ptr = std::unique_ptr<T>;
template<typename T> auto make_unique_dp() { return std::make_unique<T>(); }
struct ScreenBase { bool tilted=false; void SetAutoPerspective(bool) {}
 void ApplyPerspective(double angle,double,double) { assert(angle>0); tilted=true; }
 void ResetPerspective() { tilted=false; } double GetAngleFOV() const { return 1; } };
struct Navigator { ScreenBase screen; const ScreenBase & Screen() const { return screen; }
 void SetFromScreen(const ScreenBase & s) { screen=s; } };
struct Stream { Navigator m_navigator; int events=0; bool redraw=false;
 std::mutex m_lock;
 std::list<drape_ptr<UserEvent>> m_events;
 void ResetMapPlaneAnimations() {}
 void AddEvent(std::unique_ptr<ActiveFrameEvent> event) {
  ++events; m_events.push_back(std::move(event));
 }
 void queueStaleAuto() { m_events.push_back(std::make_unique<SetAutoPerspectiveEvent>()); }
 void DiscardPendingAutoPerspective() {""" + discard + r"""}
 void apply(bool perspective) {""" + body + r"""}
 void process() { redraw=events!=0; events=0; m_events.clear(); }
};
int main() {
 Stream s;
 s.queueStaleAuto();
 s.apply(true); assert(s.m_navigator.screen.tilted && s.events==1);
 assert(s.m_events.size()==1 && s.m_events.front()->GetType()==UserEvent::EventType::ActiveFrame);
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
            compiled = subprocess.run([compiler, "-std=c++17", str(source), "-o", str(binary)],
                                      capture_output=True, text=True)
            self.assertEqual(0, compiled.returncode, compiled.stderr)
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
struct Listener { int notifications=0; void PositionChanged(Point,bool) { ++notifications; } };
int constexpr kDoNotChangeZoom = -1;
int64_t now = 2000000000;
int64_t PositionClockNanos() { return now; }
struct Owner {
 Listener listener;
 Listener * m_listener=&listener;
 bool m_hasLastHeldDrivingPosition=false;
 Point m_lastHeldDrivingPosition{7};
 Point Position() const { return m_position; }
 bool IsModeHasPosition() const { return true; }
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

class PostMergeAuditRegressionTests(unittest.TestCase):
    """Source contracts complement, but do not replace, Android/TS18 runtime tests."""

    def test_repeated_flat_perspective_sync_avoids_restarting_renderer(self):
        renderer = controls.read("libs/drape_frontend/frontend_renderer.cpp")
        body = renderer.split("void FrontendRenderer::ApplyPerspectivePermission(bool allowed)", 1)[1]
        body = body.split("bool FrontendRenderer::ToggleCameraViewAndRecenter()", 1)[0]
        self.assertIn("bool const hadManualOverride = m_manualCameraPresentation;", body)
        self.assertIn("if (changed || hadManualOverride || m_userEventStream.GetCurrentScreen().isPerspective())", body)
        self.assertIn("m_userEventStream.ApplyCameraPresentation(false);", body)
        self.assertIn("m_userEventStream.DiscardPendingAutoPerspective();", body)
        queue = controls.read("libs/drape_frontend/user_event_stream.cpp")
        self.assertIn("void UserEventStream::DiscardPendingAutoPerspective()", queue)
        self.assertIn("m_events.remove_if", queue)

    def test_place_page_redraw_cache_is_incar_only(self):
        source = controls.read("android/app/src/main/java/app/organicmaps/widget/placepage/PlacePageButtons.java")
        self.assertIn("if (BuildConfig.IS_IN_CAR && buttons.equals(mRenderedButtons)", source)
        self.assertIn("selectionGeneration == mRenderedSelectionGeneration", source)
        self.assertIn("restoreAvailable == mRenderedRestoreAvailable", source)
        self.assertIn("nativeHasRecentlyDeletedBookmark()", source)
        factory = controls.read("android/app/src/main/java/app/organicmaps/widget/placepage/PlacePageButtonFactory.java")
        self.assertIn("nativeHasRecentlyDeletedBookmark() ? R.string.restore : R.string.save", factory)

    def test_countdown_text_is_resource_format_driven(self):
        from xml.etree import ElementTree
        resources = ElementTree.fromstring(
            controls.read("android/app/src/main/res/values/in_car_control_preferences.xml"))
        strings = {item.attrib["name"]: item.text for item in resources.findall("string")}
        self.assertEqual("%1$ds", strings["in_car_navigation_countdown_seconds"])
        self.assertEqual("%1$s · %2$d", strings["in_car_navigation_start_countdown"])
        place_page = controls.read("android/app/src/main/java/app/organicmaps/widget/placepage/PlacePageButtons.java")
        start = controls.read("android/app/src/main/java/app/organicmaps/routing/RoutingBottomMenuController.java")
        self.assertIn("getString(R.string.in_car_navigation_countdown_seconds, seconds)", place_page)
        self.assertIn("mContext.getString(R.string.in_car_navigation_start_countdown,", start)

class InCarNativeCameraProductionProbeTests(unittest.TestCase):
    """Compile the actual C++ production method against minimal seam fakes."""

    def test_repeated_permission_messages_avoid_resets_and_discard_stale_events(self):
        compiler = shutil.which("c++") or shutil.which("g++")
        if not compiler:
            self.skipTest("C++ compiler unavailable: native renderer probe NOT_RUN")
        renderer = controls.read("libs/drape_frontend/frontend_renderer.cpp")
        method = renderer.split("void FrontendRenderer::ApplyPerspectivePermission(bool allowed)", 1)[1]
        method = "void FrontendRenderer::ApplyPerspectivePermission(bool allowed)" + (
            method.split("bool FrontendRenderer::ToggleCameraViewAndRecenter()", 1)[0])
        fixture = r"""
#include <cassert>
#include <memory>
#include <utility>

struct SetAutoPerspectiveEvent { explicit SetAutoPerspectiveEvent(bool) {} };
template <typename T, typename... A>
std::unique_ptr<T> make_unique_dp(A&&... a) {
  return std::make_unique<T>(std::forward<A>(a)...);
}
struct PositionController {
  bool routing = true;
  int updates = 0;
  bool IsInRouting() const { return routing; }
  void EnablePerspectiveInRouting(bool) { ++updates; }
};
struct Screen { bool perspective = false; bool isPerspective() const { return perspective; } };
struct UserEvents {
  Screen screen;
  int applied = 0;
  int purged = 0;
  Screen const & GetCurrentScreen() const { return screen; }
  void DiscardPendingAutoPerspective() { ++purged; }
  void ApplyCameraPresentation(bool perspective) {
    DiscardPendingAutoPerspective();
    ++applied; screen.perspective = perspective;
  }
};
struct FrontendRenderer {
  bool m_enablePerspectiveInNavigation = false;
  bool m_manualCameraPresentation = false;
  PositionController position;
  PositionController * m_myPositionController = &position;
  UserEvents m_userEventStream;
  int queued = 0;
  void AddUserEvent(std::unique_ptr<SetAutoPerspectiveEvent>) { ++queued; }
  void ApplyPerspectivePermission(bool allowed);
};
""" + method + r"""
int main() {
  FrontendRenderer renderer;
  renderer.ApplyPerspectivePermission(false);
  renderer.ApplyPerspectivePermission(false);
  assert(renderer.m_userEventStream.applied == 0);
  assert(renderer.m_userEventStream.purged == 2);
  assert(renderer.position.updates == 0);
  renderer.m_userEventStream.screen.perspective = true;
  renderer.ApplyPerspectivePermission(false);
  assert(renderer.m_userEventStream.applied == 1);
  assert(renderer.m_userEventStream.purged == 3);
  assert(!renderer.m_userEventStream.screen.perspective);
  renderer.ApplyPerspectivePermission(false);
  assert(renderer.m_userEventStream.applied == 1);
  assert(renderer.m_userEventStream.purged == 4);
  renderer.m_manualCameraPresentation = true;
  renderer.ApplyPerspectivePermission(false);
  assert(!renderer.m_manualCameraPresentation);
  assert(renderer.m_userEventStream.applied == 2);
  renderer.m_enablePerspectiveInNavigation = true;
  renderer.ApplyPerspectivePermission(false);
  assert(renderer.position.updates == 1);
  assert(renderer.m_userEventStream.applied == 3);
  renderer.ApplyPerspectivePermission(false);
  assert(renderer.m_userEventStream.applied == 3);
  renderer.ApplyPerspectivePermission(true);
  assert(renderer.position.updates == 2);
  assert(renderer.queued == 1);
  renderer.ApplyPerspectivePermission(true);
  assert(renderer.queued == 1);
}
"""
        with tempfile.TemporaryDirectory() as tmp:
            source = Path(tmp) / "probe.cpp"
            executable = Path(tmp) / "probe"
            source.write_text(fixture)
            subprocess.run([compiler, "-std=c++17", "-Wall", "-Wextra", "-Werror",
                            str(source), "-o", str(executable)], check=True, capture_output=True, text=True)
            subprocess.run([str(executable)], check=True, capture_output=True, text=True)
