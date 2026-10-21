package app.organicmaps.util;

import android.location.Location;
import androidx.annotation.NonNull;
import app.organicmaps.sdk.Framework;
import app.organicmaps.sdk.location.LocationHelper;
import app.organicmaps.sdk.location.LocationListener;
import app.organicmaps.sdk.util.Config;
import app.organicmaps.sdk.util.concurrency.UiThread;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/** Local binary policy: switch at sunrise/sunset, with immediate anchored clock fallback. */
final class SynchronizedThemeController
{
  interface ThemeDelegate
  {
    void applyTheme(@NonNull Config.UiTheme theme);
  }

  private static final LocalTime DEFAULT_LIGHT_THEME_START = LocalTime.of(7, 0);
  private static final LocalTime DEFAULT_LIGHT_THEME_END = LocalTime.of(19, 0);
  private static final long RETRY_MS = TimeUnit.MINUTES.toMillis(1);
  private static final long RECONCILE_MS = TimeUnit.MINUTES.toMillis(15);
  @NonNull
  private final LocationHelper mLocationHelper;
  @NonNull
  private final ThemeDelegate mThemeDelegate;
  @NonNull
  private final Supplier<ZonedDateTime> mNow;
  private boolean mRunning;
  private boolean mWaitingForInitialLocation;

  SynchronizedThemeController(@NonNull LocationHelper locationHelper, @NonNull ThemeDelegate themeDelegate)
  {
    this(locationHelper, themeDelegate, ZonedDateTime::now);
  }

  SynchronizedThemeController(@NonNull LocationHelper locationHelper, @NonNull ThemeDelegate themeDelegate,
                              @NonNull Supplier<ZonedDateTime> now)
  {
    mLocationHelper = locationHelper;
    mThemeDelegate = themeDelegate;
    mNow = now;
  }

  private final Runnable mAutoDarkChecker = new Runnable() {
    @Override
    public void run()
    {
      UiThread.cancelDelayedTasks(this);
      if (!mRunning)
        return;
      final ZonedDateTime now = mNow.get();
      final Location location = mLocationHelper.getSavedLocation();
      if (location == null)
      {
        applyFallback(now, false);
        waitForInitialLocation();
        return;
      }
      stopWaitingForInitialLocation();
      applyAstronomicalTheme(location, now);
    }
  };

  private final LocationListener mInitialLocationListener = location ->
  {
    if (mRunning && mWaitingForInitialLocation)
    {
      stopWaitingForInitialLocation();
      UiThread.run(mAutoDarkChecker);
    }
  };

  @androidx.annotation.UiThread
  void synchronizeTimeDependentTheme()
  {
    // Scheduled mode always resolves to concrete LIGHT or DARK, never SYSTEM.
    mRunning = true;
    UiThread.run(mAutoDarkChecker);
  }

  void stop()
  {
    mRunning = false;
    UiThread.cancelDelayedTasks(mAutoDarkChecker);
    stopWaitingForInitialLocation();
  }

  private void applyAstronomicalTheme(@NonNull Location location, ZonedDateTime now)
  {
    final long seconds = now.toEpochSecond();
    final double latitude = location.getLatitude();
    final double longitude = location.getLongitude();
    final @Framework.DayTimeType int type = Framework.nativeGetDayTimeType(seconds, latitude, longitude);
    if (type == Framework.DAY_TIME_TYPE_POLAR_DAY || type == Framework.DAY_TIME_TYPE_POLAR_NIGHT)
    {
      mThemeDelegate.applyTheme(type == Framework.DAY_TIME_TYPE_POLAR_DAY ? Config.UiTheme.LIGHT : Config.UiTheme.DARK);
      scheduleNextCheck(RECONCILE_MS);
      return;
    }
    // Native event calculations use the input's UTC calendar date, not its instant's
    // local date. Select the solar calendar day at the location (independent of the
    // device time zone), otherwise Canberra dawn can resolve yesterday's sunrise.
    final long solarDay = Math.floorDiv(seconds + Math.round(longitude * 240.0), TimeUnit.DAYS.toSeconds(1));
    final long reference = solarDay * TimeUnit.DAYS.toSeconds(1) + TimeUnit.HOURS.toSeconds(12);
    final long sunrise = Framework.nativeGetSunriseTime(reference, latitude, longitude);
    final long sunset = Framework.nativeGetSunsetTime(reference, latitude, longitude);
    if (sunrise <= 0 || sunset <= sunrise)
    {
      applyFallback(now, true);
      return;
    }
    long next;
    final Config.UiTheme theme;
    if (seconds < sunrise)
    {
      theme = Config.UiTheme.DARK;
      next = sunrise;
    }
    else if (seconds < sunset)
    {
      theme = Config.UiTheme.LIGHT;
      next = sunset;
    }
    else
    {
      next = Framework.nativeGetSunriseTime(reference + TimeUnit.DAYS.toSeconds(1), latitude, longitude);
      if (next <= seconds || next > seconds + TimeUnit.DAYS.toSeconds(2))
      {
        applyFallback(now, true);
        return;
      }
      theme = Config.UiTheme.DARK;
    }
    mThemeDelegate.applyTheme(theme);
    // Reconcile long journeys without subscribing to every GPS sample or touching the renderer.
    scheduleNextCheck(Math.min(RECONCILE_MS, Math.max(1L, next * 1000L - now.toInstant().toEpochMilli())));
  }

  private void applyFallback(ZonedDateTime now, boolean retryAstronomy)
  {
    final LocalTime time = now.toLocalTime();
    final boolean day = !time.isBefore(DEFAULT_LIGHT_THEME_START) && time.isBefore(DEFAULT_LIGHT_THEME_END);
    mThemeDelegate.applyTheme(day ? Config.UiTheme.LIGHT : Config.UiTheme.DARK);
    final LocalTime next = time.isBefore(DEFAULT_LIGHT_THEME_START) || !time.isBefore(DEFAULT_LIGHT_THEME_END)
                             ? DEFAULT_LIGHT_THEME_START
                             : DEFAULT_LIGHT_THEME_END;
    ZonedDateTime boundary = now.with(next);
    if (!boundary.isAfter(now))
      boundary = boundary.plusDays(1);
    final long delay = Math.max(1L, ChronoUnit.MILLIS.between(now, boundary));
    scheduleNextCheck(retryAstronomy ? Math.min(RETRY_MS, delay) : delay);
  }

  private void scheduleNextCheck(long delayMs)
  {
    UiThread.cancelDelayedTasks(mAutoDarkChecker);
    if (mRunning)
      UiThread.runLater(mAutoDarkChecker, delayMs);
  }

  private void waitForInitialLocation()
  {
    if (mWaitingForInitialLocation || !mRunning)
      return;
    mWaitingForInitialLocation = true;
    mLocationHelper.addListener(mInitialLocationListener);
  }

  private void stopWaitingForInitialLocation()
  {
    if (!mWaitingForInitialLocation)
      return;
    mWaitingForInitialLocation = false;
    mLocationHelper.removeListener(mInitialLocationListener);
  }
}
