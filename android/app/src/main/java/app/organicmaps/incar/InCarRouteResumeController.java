package app.organicmaps.incar;

import android.content.Context;
import android.content.SharedPreferences;
import android.location.Location;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.Settings;
import androidx.annotation.NonNull;
import app.organicmaps.MwmApplication;
import app.organicmaps.routing.NavigationService;
import app.organicmaps.sdk.location.LocationListener;
import app.organicmaps.sdk.routing.RoutingController;
import app.organicmaps.sdk.util.log.Logger;

/** Application-owned resume lease; no second navigation service or route store. */
public final class InCarRouteResumeController implements LocationListener
{
  private static final String TAG = "InCarRouteResume";
  private static final String WALL = "InCarRouteLastActiveWallMs";
  private static final String ELAPSED = "InCarRouteLastActiveElapsedMs";
  private static final String BOOT = "InCarRouteLastActiveBoot";
  private final MwmApplication mApplication;
  private final Handler mHandler = new Handler(Looper.getMainLooper());
  private boolean mForeground;
  private final Runnable mHeartbeat = new Runnable() {
    @Override
    public void run()
    {
      // Handler time pauses in deep sleep; elapsedRealtime does not. Check before renewal.
      discardExpiredRoute();
      if (mForeground && RoutingController.get().isNavigating())
      {
        if (isInteractive())
          recordActivity();
        mHandler.postDelayed(this, InCarRouteResumePolicy.HEARTBEAT_MS);
      }
    }
  };

  public InCarRouteResumeController(@NonNull MwmApplication application)
  {
    mApplication = application;
    application.getLocationHelper().addListener(this);
    RoutingController.get().addNavigationStateListener(navigating -> {
      mHandler.removeCallbacks(mHeartbeat);
      if (navigating)
      {
        recordActivity();
        if (mForeground)
          mHandler.postDelayed(mHeartbeat, InCarRouteResumePolicy.HEARTBEAT_MS);
      }
      else
        prefs().edit().remove(WALL).remove(ELAPSED).remove(BOOT).apply();
    });
  }

  public void onForeground()
  {
    discardExpiredRoute();
    mForeground = true;
    mHandler.removeCallbacks(mHeartbeat);
    if (RoutingController.get().isNavigating())
      mHeartbeat.run();
  }

  public void onBackground()
  {
    // Never renew a stale lease just because Android delivered a delayed lifecycle callback.
    discardExpiredRoute();
    if (RoutingController.get().isNavigating())
      recordActivity();
    mForeground = false;
    mHandler.removeCallbacks(mHeartbeat);
  }

  public void discardExpiredRoute()
  {
    if (!mApplication.getOrganicMaps().arePlatformAndCoreInitialized())
      return;
    final RoutingController routing = RoutingController.get();
    if (!routing.isNavigating() && !routing.hasSavedRoute())
      return;
    final SharedPreferences prefs = prefs();
    if (!InCarRouteResumePolicy.isExpired(prefs.getLong(WALL, 0), prefs.getLong(ELAPSED, -1), prefs.getInt(BOOT, -1),
                                          System.currentTimeMillis(), SystemClock.elapsedRealtime(),
                                          bootCount(mApplication), InCarSettingsStore.routeResumeMinutes(mApplication)))
      return;
    Logger.i(TAG, "Discarding route after navigation inactivity");
    if (routing.isNavigating())
    {
      routing.cancel();
      NavigationService.stopService(mApplication);
    }
    routing.deleteSavedRoute();
    prefs.edit().remove(WALL).remove(ELAPSED).remove(BOOT).apply();
    InCarSettingsStore.setWalkingSessionActive(mApplication, false);
  }

  @Override
  public void onLocationUpdated(@NonNull Location location)
  {
    if (!RoutingController.get().isNavigating())
      return;
    discardExpiredRoute();
    // A cached fix must not prolong a sleeping/background journey.
    final long ageNs = SystemClock.elapsedRealtimeNanos() - location.getElapsedRealtimeNanos();
    if (RoutingController.get().isNavigating() && isInteractive() && ageNs >= 0
        && ageNs <= InCarRouteResumePolicy.HEARTBEAT_MS * 1_000_000L)
    {
      final long now = SystemClock.elapsedRealtime();
      if (now - prefs().getLong(ELAPSED, -1) >= InCarRouteResumePolicy.HEARTBEAT_MS)
        recordActivity();
    }
  }

  private boolean isInteractive()
  {
    final PowerManager power = (PowerManager) mApplication.getSystemService(Context.POWER_SERVICE);
    return power != null && power.isInteractive();
  }

  private void recordActivity()
  {
    prefs()
        .edit()
        .putLong(WALL, System.currentTimeMillis())
        .putLong(ELAPSED, SystemClock.elapsedRealtime())
        .putInt(BOOT, bootCount(mApplication))
        .apply();
  }

  private SharedPreferences prefs()
  {
    return MwmApplication.prefs(mApplication);
  }

  private static int bootCount(@NonNull Context context)
  {
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
      ? Settings.Global.getInt(context.getContentResolver(), Settings.Global.BOOT_COUNT, -1)
      : -1;
  }
}
