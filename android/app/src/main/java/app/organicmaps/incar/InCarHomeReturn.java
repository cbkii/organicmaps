package app.organicmaps.incar;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import java.lang.ref.WeakReference;
import java.util.WeakHashMap;

/** Main-thread, task-preserving fallback when an authenticated HOME observer cannot respond. */
final class InCarHomeReturn
{
  private static final Handler MAIN = new Handler(Looper.getMainLooper());
  private static final WeakHashMap<Activity, java.util.concurrent.atomic.AtomicBoolean> PENDING = new WeakHashMap<>();

  private InCarHomeReturn() {}

  static boolean schedule(Activity activity, int task)
  {
    if (PENDING.containsKey(activity))
      return false;
    final java.util.concurrent.atomic.AtomicBoolean token = new java.util.concurrent.atomic.AtomicBoolean(true);
    PENDING.put(activity, token);
    final WeakReference<Activity> owner = new WeakReference<>(activity);
    MAIN.postDelayed(() -> {
      final Activity current = owner.get();
      if (current == null || PENDING.get(current) != token)
        return;
      PENDING.remove(current);
      token.set(false);
      // HOME success, a permission dialog or another user action wins. Never finish or relaunch
      // Maps, or move a task after the initiating Activity has lost its actual window focus.
      if (!current.isFinishing() && !current.isDestroyed() && current.getTaskId() == task && current.hasWindowFocus())
      {
        try
        {
          current.moveTaskToBack(true);
        }
        catch (RuntimeException unavailable)
        {
          android.util.Log.w("InCarCompactStartup", "Task-preserving Back unavailable");
        }
      }
    }, 1500L);
    return true;
  }

  static void cancel(Activity activity)
  {
    final java.util.concurrent.atomic.AtomicBoolean token = PENDING.remove(activity);
    if (token != null)
      token.set(false);
  }

  static java.util.concurrent.atomic.AtomicBoolean pending(Activity activity)
  {
    return PENDING.get(activity);
  }
}
