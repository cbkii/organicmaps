"""Run the complete geometry coordinator with controlled clock, View queue and Surface boundaries.

This exercises scheduling/ownership and bounded repairs, not EGL, pixels or a TS18 driver.
"""
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[3]


class SurfacePresentationRecoveryTest(unittest.TestCase):
    def test_converged_dimensions_still_require_current_frame_and_recovery_is_bounded(self):
        if not shutil.which('java'):
            self.skipTest('JDK unavailable')
        files = {
            'androidx/annotation/NonNull.java': 'package androidx.annotation;public @interface NonNull {}',
            'androidx/annotation/Nullable.java': 'package androidx.annotation;public @interface Nullable {}',
            'androidx/annotation/UiThread.java': 'package androidx.annotation;public @interface UiThread {}',
            'androidx/annotation/VisibleForTesting.java': 'package androidx.annotation;public @interface VisibleForTesting {}',
            'android/R.java': 'package android;public class R {public static class id {public static int content=1;}}',
            'android/os/SystemClock.java': 'package android.os;public class SystemClock {public static long now;public static long uptimeMillis(){return now;}}',
            'android/os/Build.java': 'package android.os;public class Build {public static class VERSION {public static int SDK_INT=29;}public static class VERSION_CODES {public static int N=24;}}',
            'android/content/Intent.java': 'package android.content;public class Intent {public java.util.Set<String> getCategories(){return null;}public String getAction(){return null;}public Object getComponent(){return null;}public int getFlags(){return 0;}}',
            'android/content/res/Configuration.java': 'package android.content.res;public class Configuration {public int screenWidthDp=800,screenHeightDp=480,orientation=2;}',
            'android/content/res/Resources.java': 'package android.content.res;public class Resources {public Configuration getConfiguration(){return new Configuration();}}',
            'android/view/Surface.java': 'package android.view;public class Surface {public boolean valid=true;public boolean isValid(){return valid;}}',
            'android/view/SurfaceHolder.java': '''package android.view;public interface SurfaceHolder {
interface Callback {void surfaceCreated(SurfaceHolder h);void surfaceChanged(SurfaceHolder h,int f,int w,int v);void surfaceDestroyed(SurfaceHolder h);}
void addCallback(Callback c);void removeCallback(Callback c);Surface getSurface();}''',
            'android/view/View.java': '''package android.view;
public class View {
public interface OnLayoutChangeListener {void onLayoutChange(View v,int l,int t,int r,int b,int ol,int ot,int or,int ob);}
record Task(Runnable work,long at){} static final java.util.List<Task> tasks=new java.util.ArrayList<>();
public static void clear(){tasks.clear();android.os.SystemClock.now=0;}
public static void drain(long until){int n=0;while(true){Task t=tasks.stream().min(java.util.Comparator.comparingLong(Task::at)).orElse(null);if(t==null||t.at()>until)break;tasks.remove(t);android.os.SystemClock.now=t.at();t.work().run();if(++n>500)throw new AssertionError("unbounded callbacks");}android.os.SystemClock.now=until;}
public boolean postOnAnimation(Runnable r){return postDelayed(r,16);}public boolean postDelayed(Runnable r,long d){tasks.add(new Task(r,android.os.SystemClock.now+d));return true;}
public boolean removeCallbacks(Runnable r){return tasks.removeIf(t->t.work()==r);}
public int getWidth(){return 800;}public int getHeight(){return 480;}
public void addOnLayoutChangeListener(OnLayoutChangeListener l){}public void removeOnLayoutChangeListener(OnLayoutChangeListener l){}
public void requestLayout(){}public boolean isAttachedToWindow(){return true;}
}''',
            'androidx/lifecycle/Lifecycle.java': 'package androidx.lifecycle;public class Lifecycle {public enum State {CREATED,STARTED,RESUMED;public boolean isAtLeast(State s){return ordinal()>=s.ordinal();}}public State state=State.RESUMED;public State getCurrentState(){return state;}}',
            'androidx/fragment/app/FragmentActivity.java': '''package androidx.fragment.app;
public class FragmentActivity {
public android.view.View content=new android.view.View();public app.organicmaps.sdk.MapView map=new app.organicmaps.sdk.MapView();public androidx.lifecycle.Lifecycle life=new androidx.lifecycle.Lifecycle();public boolean destroyed;
@SuppressWarnings("unchecked") public <T extends android.view.View>T findViewById(int id){return (T)(id==app.organicmaps.R.id.map?map:content);}
public boolean isFinishing(){return false;}public boolean isDestroyed(){return destroyed;}public int getTaskId(){return 42;}
public androidx.lifecycle.Lifecycle getLifecycle(){return life;}public android.content.res.Resources getResources(){return new android.content.res.Resources();}
public android.content.Intent getIntent(){return null;}public boolean isInMultiWindowMode(){return false;}public boolean isInPictureInPictureMode(){return false;}
}''',
            'androidx/core/graphics/Insets.java': 'package androidx.core.graphics;public class Insets {public static final Insets NONE=new Insets();public int left,top,right,bottom;}',
            'androidx/core/view/ViewCompat.java': 'package androidx.core.view;public class ViewCompat {public static WindowInsetsCompat getRootWindowInsets(android.view.View v){return null;}}',
            'androidx/core/view/WindowInsetsCompat.java': 'package androidx.core.view;public class WindowInsetsCompat {public static class Type {public static int systemBars(){return 0;}public static int statusBars(){return 0;}public static int navigationBars(){return 0;}}public androidx.core.graphics.Insets getInsets(int t){return androidx.core.graphics.Insets.NONE;}public boolean isVisible(int t){return false;}}',
            'app/organicmaps/BuildConfig.java': 'package app.organicmaps;public class BuildConfig {public static boolean IS_IN_CAR=true;}',
            'app/organicmaps/R.java': 'package app.organicmaps;public class R {public static class id {public static int map=2,map_container=3;}}',
            'app/organicmaps/MwmActivity.java': 'package app.organicmaps;public class MwmActivity extends androidx.fragment.app.FragmentActivity {}',
            'app/organicmaps/util/InCarVisuals.java': 'package app.organicmaps.util;public class InCarVisuals {public enum TransitionReason {RESUME,WINDOW_FOCUS,CONFIGURATION}public static void reconcile(Object a,TransitionReason r){}public static void release(Object a){}}',
            'app/organicmaps/incar/InCarCompactStartupProvider.java': 'package app.organicmaps.incar;public class InCarCompactStartupProvider {public static void changed(Object a){}}',
            'app/organicmaps/sdk/util/log/Logger.java': 'package app.organicmaps.sdk.util.log;public class Logger {public static void d(String t,String s){}public static void w(String t,String s){}public static void i(String t,String s){}}',
            'app/organicmaps/sdk/MapView.java': '''package app.organicmaps.sdk;
public class MapView extends android.view.View {
public int reapply,reattach;public boolean safe=true;public android.view.Surface surface=new android.view.Surface();
public int getSurfaceFrameWidth(){return 800;}public int getSurfaceFrameHeight(){return 480;}
public int getLastAppliedSurfaceWidth(){return 800;}public int getLastAppliedSurfaceHeight(){return 480;}
public boolean isSurfaceAttachmentRecoveryAllowed(){return safe;}
public boolean requestSurfaceSizeFromLayout(){throw new AssertionError("converged surface resized");}
public boolean reapplyCurrentSurfaceSize(){reapply++;return true;}public boolean recoverSurfaceAttachment(){reattach++;return true;}
public android.view.SurfaceHolder getHolder(){return new android.view.SurfaceHolder(){public void addCallback(Callback c){}public void removeCallback(Callback c){}public android.view.Surface getSurface(){return surface;}};}
}''',
            'Harness.java': '''import app.organicmaps.util.*;import app.organicmaps.incar.*;import app.organicmaps.MwmActivity;import android.view.View;
class Harness {
static final InCarCompactStartupState state=InCarCompactStartupState.INSTANCE;
static MwmActivity start(boolean observed){View.clear();MwmActivity a=new MwmActivity();int id=System.identityHashCode(a);state.created(id,42);state.nativeReady(id);state.attached(id,true);if(observed)state.presentationRequired(id);InCarWindowGeometryCoordinator.reconcile(a,InCarVisuals.TransitionReason.RESUME);return a;}
static void check(boolean v){if(!v)throw new AssertionError();}
public static void main(String[] args){
MwmActivity a=start(true);View.drain(1499);check(a.map.reapply==0&&a.map.reattach==0);
View.drain(2900);check(a.map.reapply==1&&a.map.reattach==0);View.drain(10000);check(a.map.reapply==1&&a.map.reattach==1);
// Additional presentation/layout notifications must not re-arm this geometry epoch.
InCarWindowGeometryCoordinator.onPresentationChanged(a);View.drain(15000);check(a.map.reapply==1&&a.map.reattach==1);InCarWindowGeometryCoordinator.release(a);
a=start(false);View.drain(10000);check(a.map.reapply==0&&a.map.reattach==0);InCarWindowGeometryCoordinator.release(a);
a=start(true);state.renderingCreated(System.identityHashCode(a));View.drain(10000);check(a.map.reapply==0&&a.map.reattach==0);InCarWindowGeometryCoordinator.release(a);
a=start(true);View.drain(1000);state.presentation(System.identityHashCode(a),true,2);InCarWindowGeometryCoordinator.onPresentationChanged(a);View.drain(10000);check(a.map.reapply==0&&a.map.reattach==0);InCarWindowGeometryCoordinator.release(a);
a=start(true);View.drain(1000);a.life.state=androidx.lifecycle.Lifecycle.State.STARTED;View.drain(10000);check(a.map.reapply==0&&a.map.reattach==0);InCarWindowGeometryCoordinator.release(a);
a=start(true);View.drain(1000);a.map.surface.valid=false;View.drain(10000);check(a.map.reapply==0&&a.map.reattach==0);InCarWindowGeometryCoordinator.release(a);
a=start(true);View.drain(1000);InCarWindowGeometryCoordinator.release(a);View.drain(10000);check(a.map.reapply==0&&a.map.reattach==0);
a=start(true);View.drain(1000);a.destroyed=true;View.drain(10000);check(a.map.reapply==0&&a.map.reattach==0);
a=start(true);View.drain(1000);state.created(123,43);View.drain(10000);check(a.map.reapply==0&&a.map.reattach==0);InCarWindowGeometryCoordinator.release(a);
System.out.println("PASS actual coordinator frame wait, bounded methods, unknown evidence, recovery, pause, invalid surface, release, destroy and replacement");
}}
''',
        }
        for path in ('app/organicmaps/util/InCarWindowGeometryCoordinator.java',
                     'app/organicmaps/incar/InCarCompactStartupState.java'):
            files[path] = (ROOT / 'android/app/src/main/java' / path).read_text()
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            for path, content in files.items():
                target = root / path
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_text(content)
            result = subprocess.run(['java', '-m', 'jdk.compiler/com.sun.tools.javac.Main', '-d', tmp,
                                     *map(str, root.rglob('*.java'))], capture_output=True, text=True, timeout=30)
            self.assertEqual(0, result.returncode, result.stderr)
            result = subprocess.run(['java', '-cp', tmp, 'Harness'], capture_output=True, text=True, timeout=10)
            self.assertEqual(0, result.returncode, result.stderr)
