"""Run the production task-preserving Back fallback with controlled lifecycle/focus."""
import subprocess
import tempfile
import unittest
from pathlib import Path
from test_compact_startup import has_compiler

ROOT = Path(__file__).resolve().parents[3]

class HomeReturnTests(unittest.TestCase):
    @unittest.skipUnless(has_compiler(), 'Java compiler unavailable')
    def test_back_fallback_is_bounded_coalesced_and_cancelled_on_focus_loss(self):
        files = {
            'InCarHomeReturn.java': (ROOT / 'android/app/src/main/java/app/organicmaps/incar/InCarHomeReturn.java').read_text(),
            'android/app/Activity.java': '''package android.app;
public class Activity {public boolean finishing,destroyed,focused=true;public int task=42,moves;
public boolean isFinishing(){return finishing;}public boolean isDestroyed(){return destroyed;}
public boolean hasWindowFocus(){return focused;}public int getTaskId(){return task;}
public boolean moveTaskToBack(boolean root){if(!root)throw new AssertionError();moves++;return true;}}''',
            'android/os/Looper.java': 'package android.os;public class Looper {public static Looper getMainLooper(){return new Looper();}}',
            'android/util/Log.java': 'package android.util;public class Log {public static int w(String t,String m){return 0;}}',
            'android/os/Handler.java': '''package android.os;public class Handler {
public static java.util.ArrayDeque<Runnable> jobs=new java.util.ArrayDeque<>();
public Handler(Looper l){}public boolean postDelayed(Runnable r,long ms){if(ms!=1500)throw new AssertionError();jobs.add(r);return true;}
public static void drain(){while(!jobs.isEmpty())jobs.remove().run();}}''',
            'Harness.java': '''package app.organicmaps.incar;
import android.app.Activity;import android.os.Handler;
class Harness {static void check(boolean b){if(!b)throw new AssertionError();}
public static void main(String[] a){
Activity stale=new Activity();check(InCarHomeReturn.schedule(stale,42));check(!InCarHomeReturn.schedule(stale,42));
var lease=InCarHomeReturn.pending(stale);check(lease.get());Handler.drain();check(stale.moves==1&&!lease.get());
Activity success=new Activity();InCarHomeReturn.schedule(success,42);success.focused=false;Handler.drain();check(success.moves==0);
Activity reopened=new Activity();InCarHomeReturn.schedule(reopened,42);lease=InCarHomeReturn.pending(reopened);
InCarHomeReturn.cancel(reopened);check(!lease.get());reopened.focused=true;Handler.drain();check(reopened.moves==0);
Activity replaced=new Activity();InCarHomeReturn.schedule(replaced,42);InCarHomeReturn.cancel(replaced);
InCarHomeReturn.schedule(replaced,42);Handler.jobs.remove().run();check(replaced.moves==0);Handler.drain();check(replaced.moves==1);
Activity otherTask=new Activity();InCarHomeReturn.schedule(otherTask,42);otherTask.task=43;Handler.drain();check(otherTask.moves==0);
Activity dead=new Activity();InCarHomeReturn.schedule(dead,42);dead.destroyed=true;Handler.drain();check(dead.moves==0);
System.out.println("PASS Back fallback: stale peer, coalescing, focus success, reopen cancellation, task replacement, destroyed owner");}}
'''
        }
        with tempfile.TemporaryDirectory() as d:
            root = Path(d)
            for name, content in files.items():
                p = root / name
                p.parent.mkdir(parents=True, exist_ok=True)
                p.write_text(content)
            subprocess.run(['java', '-m', 'jdk.compiler/com.sun.tools.javac.Main', '-d', str(root), *map(str, root.rglob('*.java'))], check=True)
            subprocess.run(['java', '-cp', str(root), 'app.organicmaps.incar.Harness'], check=True, timeout=10)
