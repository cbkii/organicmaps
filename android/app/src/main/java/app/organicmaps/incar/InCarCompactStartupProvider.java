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
  private static final java.util.concurrent.ExecutorService WORKER = Executors.newSingleThreadExecutor();
  private static final java.util.concurrent.ScheduledExecutorService TIMER = Executors.newSingleThreadScheduledExecutor();

  @Override public boolean onCreate() { return true; }

  @Override
  public Cursor query(@NonNull Uri uri, @Nullable String[] projection, @Nullable String selection,
                      @Nullable String[] args, @Nullable String sort)
  {
    final Context context = getContext();
    if (context == null || !BuildConfig.IS_IN_CAR || !InCarCompactPeer.trusted(context, Binder.getCallingUid()))
      throw new SecurityException("Untrusted compact startup peer");
    final int boot = Settings.Global.getInt(context.getContentResolver(), Settings.Global.BOOT_COUNT, -1);
    final UserManager users = (UserManager) context.getSystemService(Context.USER_SERVICE);
    if (!"/status".equals(uri.getPath()) || !"1".equals(uri.getQueryParameter("v"))
        || boot < 0 || !Integer.toString(boot).equals(uri.getQueryParameter("boot"))
        || users == null || !users.isUserUnlocked())
      throw new IllegalArgumentException("Incompatible or stale startup request");
    final MatrixCursor cursor = new MatrixCursor(new String[] {
        "protocol", "package", "boot", "user", "generation", "enabled", "pid", "elapsed_ms",
        "stage", "task", "core", "native", "attached", "width", "height", "resumed", "renderer_ready"});
    final Object[] row = new Object[17];
    final Object[] header = {1, BuildConfig.APPLICATION_ID, boot, android.os.Process.myUid() / 100000,
                            boot + ":" + android.os.Process.myUid() / 100000 + ":1",
                            InCarSettingsStore.compactStartupEnabled(context) ? 1 : 0,
                            android.os.Process.myPid(), SystemClock.elapsedRealtime()};
    System.arraycopy(header, 0, row, 0, header.length);
    System.arraycopy(InCarCompactStartupState.INSTANCE.row(), 0, row, header.length, 9);
    cursor.addRow(row);
    cursor.setNotificationUri(context.getContentResolver(), URI);
    return cursor;
  }

  /** Bounded, off-main capability check; root execution is separately verified by the task owner. */
  public static void checkController(Context supplied, Consumer<Boolean> callback)
  {
    final Context context = supplied.getApplicationContext();
    final Handler main = new Handler(Looper.getMainLooper());
    final CancellationSignal cancel = new CancellationSignal();
    final java.util.concurrent.atomic.AtomicBoolean delivered = new java.util.concurrent.atomic.AtomicBoolean();
    final Runnable unavailable = () -> {
      if (delivered.compareAndSet(false, true)) main.post(() -> callback.accept(false));
    };
    final java.util.concurrent.ScheduledFuture<?> timeout = TIMER.schedule(() -> {
      cancel.cancel(); unavailable.run();
    }, 2, TimeUnit.SECONDS);
    WORKER.execute(() -> {
      boolean available = false;
      try
      {
        final int uid = context.getPackageManager().getApplicationInfo(InCarCompactPeer.PACKAGE, 0).uid;
        if (InCarCompactPeer.trusted(context, uid))
        {
          final Uri uri = Uri.parse("content://com.cbkii.ts18launcher.compact.startup/status?v=1");
          try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, null, cancel))
          {
            available = cursor != null && cursor.moveToFirst()
                && cursor.getInt(cursor.getColumnIndexOrThrow("protocol")) == 1
                && cursor.getInt(cursor.getColumnIndexOrThrow("available")) == 1;
          }
        }
      }
      catch (android.content.pm.PackageManager.NameNotFoundException | RuntimeException ignored) { }
      timeout.cancel(false);
      final boolean result = available;
      if (delivered.compareAndSet(false, true)) main.post(() -> callback.accept(result));
    });
  }

  public static void changed(Context context)
  {
    if (BuildConfig.IS_IN_CAR)
    {
      android.util.Log.i("InCarCompactStartup", "elapsedMs=" + SystemClock.elapsedRealtime()
          + " pid=" + android.os.Process.myPid() + " state="
          + java.util.Arrays.toString(InCarCompactStartupState.INSTANCE.row()));
      context.getContentResolver().notifyChange(URI, null);
    }
  }

  @Override public String getType(@NonNull Uri uri) { return "vnd.android.cursor.item/compact-startup"; }
  @Override public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) { throw new UnsupportedOperationException(); }
  @Override public int delete(@NonNull Uri uri, @Nullable String where, @Nullable String[] args) { throw new UnsupportedOperationException(); }
  @Override public int update(@NonNull Uri uri, @Nullable ContentValues values, @Nullable String where, @Nullable String[] args) { throw new UnsupportedOperationException(); }
}
