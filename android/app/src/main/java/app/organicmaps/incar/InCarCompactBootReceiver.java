package app.organicmaps.incar;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import app.organicmaps.MwmApplication;

/** Credential-unlocked boot signal only. HOME reconciliation survives loss of this process. */
public final class InCarCompactBootReceiver extends BroadcastReceiver
{
  // Complete the durable signal before releasing the receiver process; this is a worker thread.
  @android.annotation.SuppressLint("ApplySharedPref")
  @Override
  public void onReceive(Context context, Intent intent)
  {
    if (android.os.Build.VERSION.SDK_INT < 28 || intent == null
        || !Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction()))
      return;
    final PendingResult result = goAsync();
    new Thread(() -> {
      try
      {
        final int boot = Settings.Global.getInt(context.getContentResolver(), Settings.Global.BOOT_COUNT, -1);
        if (!MwmApplication.prefs(context).edit().putInt("InCarCompactBootSignal", boot).commit())
          android.util.Log.w("InCarCompactStartup", "Boot signal not persisted");
      }
      catch (RuntimeException unavailable)
      {
        android.util.Log.w("InCarCompactStartup", "Boot signal unavailable", unavailable);
      }
      finally
      {
        result.finish();
      }
    }, "compact-boot-signal").start();
  }
}
