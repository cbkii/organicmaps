package app.organicmaps.incar;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Binder;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.os.UserManager;
import android.provider.Settings;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import app.organicmaps.BuildConfig;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** V1 read-only evidence. No incoming task IDs, bounds, commands or preference writes. */
public final class InCarCompactStartupProvider extends ContentProvider
{
  public static final Uri URI = Uri.parse("content://" + BuildConfig.APPLICATION_ID + ".compact.startup/status");
  private static final java.util.concurrent.ExecutorService WORKER = new java.util.concurrent.ThreadPoolExecutor(
      0, 2, 15L, TimeUnit.SECONDS, new java.util.concurrent.SynchronousQueue<>(), runnable -> {
        final Thread thread = new Thread(runnable, "compact-capability");
        thread.setDaemon(true);
        return thread;
      });
  private static final java.util.concurrent.ScheduledExecutorService TIMER =
      Executors.newSingleThreadScheduledExecutor(runnable -> {
        final Thread thread = new Thread(runnable, "compact-deadline");
        thread.setDaemon(true);
        return thread;
      });
  private static final java.util.concurrent.ExecutorService SIGNALS = Executors.newSingleThreadExecutor(runnable -> {
    final Thread thread = new Thread(runnable, "compact-signal");
    thread.setDaemon(true);
    return thread;
  });
  private static final java.util.concurrent.atomic.AtomicBoolean SIGNAL_PENDING =
      new java.util.concurrent.atomic.AtomicBoolean();
  private static final java.util.concurrent.atomic.AtomicLong SIGNAL_VERSION =
      new java.util.concurrent.atomic.AtomicLong();
  private static android.os.Messenger sPeer;

  private static int boot(Context context)
  {
    return android.os.Build.VERSION.SDK_INT >= 24
      ? Settings.Global.getInt(context.getContentResolver(), Settings.Global.BOOT_COUNT, -1)
      : -1;
  }

  private Context requirePeer()
  {
    final Context context = getContext();
    if (android.os.Build.VERSION.SDK_INT < 28 || context == null || !BuildConfig.IS_IN_CAR
        || !InCarCompactPeer.trusted(context, Binder.getCallingUid()))
      throw new SecurityException("Untrusted compact startup peer");
    return context;
  }

  @Override
  public android.os.Bundle call(@NonNull String method, @Nullable String arg, @Nullable android.os.Bundle extras)
  {
    final Context context = requirePeer();
    if (!"1".equals(arg) || extras == null || boot(context) < 0 || extras.getInt("boot", -1) != boot(context))
      throw new IllegalArgumentException("Stale startup observer");
    final android.os.IBinder binder = extras.getBinder("observer");
    if (binder == null)
      throw new IllegalArgumentException("Observer required");
    synchronized (InCarCompactStartupProvider.class)
    {
      if ("observe".equals(method))
        sPeer = new android.os.Messenger(binder);
      else if ("unobserve".equals(method))
      {
        if (sPeer != null && sPeer.getBinder().equals(binder))
          sPeer = null;
      }
      else
        throw new IllegalArgumentException("Unknown startup method");
    }
    return android.os.Bundle.EMPTY;
  }

  @Override
  public boolean onCreate()
  {
    return true;
  }

  @Override
  public Cursor query(@NonNull Uri uri, @Nullable String[] projection, @Nullable String selection,
                      @Nullable String[] args, @Nullable String sort)
  {
    if (android.os.Build.VERSION.SDK_INT < 28)
      throw new SecurityException("API unavailable");
    final Context context = requirePeer();
    final int boot = boot(context);
    final UserManager users = (UserManager) context.getSystemService(Context.USER_SERVICE);
    if (!"/status".equals(uri.getPath()) || !"1".equals(uri.getQueryParameter("v")) || boot < 0
        || !Integer.toString(boot).equals(uri.getQueryParameter("boot")) || users == null || !users.isUserUnlocked())
      throw new IllegalArgumentException("Incompatible or stale startup request");
    final MatrixCursor cursor = new MatrixCursor(new String[] {
        "protocol", "package", "boot", "user", "generation", "enabled", "pid", "elapsed_ms", "stage", "task", "core",
        "native", "attached", "width", "height", "resumed", "renderer_ready", "boot_signal"});
    final Object[] row = new Object[18];
    final Object[] header = {1,
                             BuildConfig.APPLICATION_ID,
                             boot,
                             android.os.Process.myUid() / 100000,
                             boot + ":" + android.os.Process.myUid() / 100000 + ":1",
                             InCarSettingsStore.compactStartupEnabled(context) ? 1 : 0,
                             android.os.Process.myPid(),
                             SystemClock.elapsedRealtime()};
    System.arraycopy(header, 0, row, 0, header.length);
    System.arraycopy(InCarCompactStartupState.INSTANCE.row(), 0, row, header.length, 9);
    row[17] =
        context
            .getSharedPreferences(context.getString(app.organicmaps.sdk.R.string.pref_file_name), Context.MODE_PRIVATE)
            .getInt("InCarCompactBootSignal", -1);
    cursor.addRow(row);
    return cursor;
  }

  /** Bounded, off-main capability check; root execution is separately verified by the task owner. */
  public static void checkController(Context supplied, Consumer<Boolean> callback)
  {
    final Context context = supplied.getApplicationContext();
    final Handler main = new Handler(Looper.getMainLooper());
    final CancellationSignal cancel = new CancellationSignal();
    final java.util.concurrent.atomic.AtomicBoolean delivered = new java.util.concurrent.atomic.AtomicBoolean();
    final Runnable unavailable = () ->
    {
      if (delivered.compareAndSet(false, true))
        main.post(() -> callback.accept(false));
    };
    final java.util.concurrent.ScheduledFuture<?> timeout = TIMER.schedule(() -> {
      unavailable.run();
      final Thread thread = new Thread(cancel::cancel, "compact-capability-cancel");
      thread.setDaemon(true);
      thread.start();
    }, 2, TimeUnit.SECONDS);
    try
    {
      WORKER.execute(() -> {
        boolean available = false;
        try
        {
          final int uid = context.getPackageManager().getApplicationInfo(InCarCompactPeer.PACKAGE, 0).uid;
          if (InCarCompactPeer.trusted(context, uid)
              && InCarCompactPeer.ownsProvider(context, "com.cbkii.ts18launcher.compact.startup"))
          {
            final int currentBoot = boot(context);
            final Uri uri = Uri.parse("content://com.cbkii.ts18launcher.compact.startup/status?v=1")
                                .buildUpon()
                                .appendQueryParameter("boot", Integer.toString(currentBoot))
                                .build();
            try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, null, cancel))
            {
              available = cursor != null && cursor.moveToFirst()
                       && cursor.getInt(cursor.getColumnIndexOrThrow("protocol")) == 1
                       && cursor.getInt(cursor.getColumnIndexOrThrow("available")) == 1
                       && cursor.getInt(cursor.getColumnIndexOrThrow("boot")) == currentBoot
                       && cursor.getInt(cursor.getColumnIndexOrThrow("user")) == android.os.Process.myUid() / 100000;
            }
          }
        }
        catch (android.content.pm.PackageManager.NameNotFoundException | RuntimeException ignored)
        {}
        timeout.cancel(false);
        final boolean result = available;
        if (delivered.compareAndSet(false, true))
          main.post(() -> callback.accept(result));
      });
    }
    catch (java.util.concurrent.RejectedExecutionException full)
    {
      timeout.cancel(false);
      unavailable.run();
    }
  }

  public static void changed(Context context)
  {
    if (BuildConfig.IS_IN_CAR)
    {
      android.util.Log.i("InCarCompactStartup",
                         "elapsedMs=" + SystemClock.elapsedRealtime() + " pid=" + android.os.Process.myPid()
                             + " state=" + java.util.Arrays.toString(InCarCompactStartupState.INSTANCE.row()));
      SIGNAL_VERSION.incrementAndGet();
      scheduleSignal();
    }
  }

  private static void scheduleSignal()
  {
    if (SIGNAL_PENDING.compareAndSet(false, true))
      SIGNALS.execute(() -> {
        final long version = SIGNAL_VERSION.get();
        android.os.Messenger peer;
        synchronized (InCarCompactStartupProvider.class)
        {
          peer = sPeer;
        }
        try
        {
          if (peer != null)
            peer.send(android.os.Message.obtain(null, 1));
        }
        catch (android.os.RemoteException dead)
        {
          synchronized (InCarCompactStartupProvider.class)
          {
            if (sPeer == peer)
              sPeer = null;
          }
        }
        finally
        {
          SIGNAL_PENDING.set(false);
          // Recheck after releasing the slot: changes during send must schedule another signal.
          if (SIGNAL_VERSION.get() != version)
            scheduleSignal();
        }
      });
  }

  @Override
  public String getType(@NonNull Uri uri)
  {
    return "vnd.android.cursor.item/compact-startup";
  }
  @Override
  public Uri insert(@NonNull Uri uri, @Nullable ContentValues values)
  {
    throw new UnsupportedOperationException();
  }
  @Override
  public int delete(@NonNull Uri uri, @Nullable String where, @Nullable String[] args)
  {
    throw new UnsupportedOperationException();
  }
  @Override
  public int update(@NonNull Uri uri, @Nullable ContentValues values, @Nullable String where, @Nullable String[] args)
  {
    throw new UnsupportedOperationException();
  }
}
