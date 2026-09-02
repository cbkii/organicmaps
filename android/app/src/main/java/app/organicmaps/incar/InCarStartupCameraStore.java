package app.organicmaps.incar;

import android.content.Context;
import android.content.SharedPreferences;
import android.location.Location;
import android.os.SystemClock;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import app.organicmaps.MwmApplication;

/**
 * Persists the small amount of InCar-only state needed to frame a useful map before the next live location fix.
 * Cached coordinates are a camera hint only; they are never fed back into LocationHelper or native location state.
 */
public final class InCarStartupCameraStore
{
  public enum StartupMapView
  {
    DRIVING_AREA("DRIVING_AREA"),
    LAST_MAP_VIEW("LAST_MAP_VIEW");

    @NonNull
    private final String mPreferenceValue;

    StartupMapView(@NonNull String preferenceValue)
    {
      mPreferenceValue = preferenceValue;
    }

    @NonNull
    public String preferenceValue()
    {
      return mPreferenceValue;
    }

    @NonNull
    public static StartupMapView fromPreferenceValue(@Nullable String value)
    {
      for (final StartupMapView mode : values())
      {
        if (mode.mPreferenceValue.equals(value))
          return mode;
      }
      return DRIVING_AREA;
    }
  }

  public static final double DRIVING_AREA_RADIUS_METERS = 5_000.0;

  @VisibleForTesting
  static final long WRITE_INTERVAL_MS = 30_000L;
  @VisibleForTesting
  static final long MAX_ANCHOR_AGE_MS = 30L * 24L * 60L * 60L * 1_000L;
  private static final long MAX_FUTURE_SKEW_MS = 5L * 60L * 1_000L;
  private static final float MAX_CACHE_ACCURACY_METERS = 10_000.0f;
  private static final int CACHE_VERSION = 1;

  private static final String KEY_STARTUP_MAP_VIEW = "InCarStartupMapView";
  private static final String KEY_ANCHOR_VERSION = "InCarStartupAnchorVersion";
  private static final String KEY_ANCHOR_LAT_BITS = "InCarStartupAnchorLatBits";
  private static final String KEY_ANCHOR_LON_BITS = "InCarStartupAnchorLonBits";
  private static final String KEY_ANCHOR_FIX_TIME = "InCarStartupAnchorFixTime";
  private static final String KEY_ANCHOR_ACCURACY_BITS = "InCarStartupAnchorAccuracyBits";

  public static final class Anchor
  {
    public final double latitude;
    public final double longitude;
    public final long fixTimeMillis;
    public final float accuracyMeters;

    Anchor(double latitude, double longitude, long fixTimeMillis, float accuracyMeters)
    {
      this.latitude = latitude;
      this.longitude = longitude;
      this.fixTimeMillis = fixTimeMillis;
      this.accuracyMeters = accuracyMeters;
    }
  }

  @NonNull
  private final SharedPreferences mPrefs;
  @Nullable
  private Anchor mPendingAnchor;
  private boolean mPersistedInSession;
  private long mLastWriteElapsedRealtime;

  public InCarStartupCameraStore(@NonNull Context context)
  {
    mPrefs = MwmApplication.prefs(context.getApplicationContext());
  }

  @NonNull
  public StartupMapView getStartupMapView()
  {
    return StartupMapView.fromPreferenceValue(mPrefs.getString(KEY_STARTUP_MAP_VIEW, null));
  }

  public void setStartupMapView(@NonNull StartupMapView mode)
  {
    mPrefs.edit().putString(KEY_STARTUP_MAP_VIEW, mode.preferenceValue()).apply();
  }

  /** Records an already-accepted Organic Maps location without changing live location authority. */
  public void recordAcceptedLocation(@NonNull Location location)
  {
    recordAcceptedLocation(location, System.currentTimeMillis(), SystemClock.elapsedRealtime());
  }

  @VisibleForTesting
  void recordAcceptedLocation(@NonNull Location location, long nowWallMillis, long nowElapsedRealtime)
  {
    final Anchor anchor = fromLocation(location, nowWallMillis);
    if (anchor == null)
      return;

    mPendingAnchor = anchor;
    if (!shouldPersist(mPersistedInSession, mLastWriteElapsedRealtime, nowElapsedRealtime))
      return;

    persist(anchor, nowElapsedRealtime);
  }

  /** Flushes the newest accepted anchor once when the map is orderly stopped. */
  public void flush()
  {
    if (mPendingAnchor == null)
      return;
    persist(mPendingAnchor, SystemClock.elapsedRealtime());
  }

  @Nullable
  public Anchor readAnchor()
  {
    return readAnchor(System.currentTimeMillis());
  }

  @VisibleForTesting
  @Nullable
  Anchor readAnchor(long nowWallMillis)
  {
    if (mPrefs.getInt(KEY_ANCHOR_VERSION, 0) != CACHE_VERSION)
      return null;

    final long latBits = mPrefs.getLong(KEY_ANCHOR_LAT_BITS, Long.MIN_VALUE);
    final long lonBits = mPrefs.getLong(KEY_ANCHOR_LON_BITS, Long.MIN_VALUE);
    final long fixTimeMillis = mPrefs.getLong(KEY_ANCHOR_FIX_TIME, 0L);
    final int accuracyBits = mPrefs.getInt(KEY_ANCHOR_ACCURACY_BITS, Integer.MIN_VALUE);
    if (latBits == Long.MIN_VALUE || lonBits == Long.MIN_VALUE || accuracyBits == Integer.MIN_VALUE)
      return null;

    final double latitude = Double.longBitsToDouble(latBits);
    final double longitude = Double.longBitsToDouble(lonBits);
    final float accuracyMeters = Float.intBitsToFloat(accuracyBits);
    if (!isValidAnchor(latitude, longitude, fixTimeMillis, accuracyMeters, nowWallMillis))
      return null;

    return new Anchor(latitude, longitude, fixTimeMillis, accuracyMeters);
  }

  @Nullable
  private static Anchor fromLocation(@NonNull Location location, long nowWallMillis)
  {
    final double latitude = location.getLatitude();
    final double longitude = location.getLongitude();
    final long fixTimeMillis = location.getTime();
    final float accuracyMeters = location.getAccuracy();
    if (!isValidAnchor(latitude, longitude, fixTimeMillis, accuracyMeters, nowWallMillis))
      return null;
    return new Anchor(latitude, longitude, fixTimeMillis, accuracyMeters);
  }

  @VisibleForTesting
  static boolean isValidAnchor(double latitude, double longitude, long fixTimeMillis, float accuracyMeters,
                               long nowWallMillis)
  {
    if (!Double.isFinite(latitude) || !Double.isFinite(longitude) || !Float.isFinite(accuracyMeters))
      return false;
    if (latitude < -90.0 || latitude > 90.0 || longitude < -180.0 || longitude > 180.0)
      return false;
    if (accuracyMeters <= 0.0f || accuracyMeters > MAX_CACHE_ACCURACY_METERS)
      return false;
    if (fixTimeMillis <= 0L || nowWallMillis <= 0L || fixTimeMillis > nowWallMillis + MAX_FUTURE_SKEW_MS)
      return false;
    return nowWallMillis - fixTimeMillis <= MAX_ANCHOR_AGE_MS;
  }

  @VisibleForTesting
  static boolean shouldPersist(boolean persistedInSession, long lastWriteElapsedRealtime, long nowElapsedRealtime)
  {
    if (!persistedInSession)
      return true;
    return nowElapsedRealtime >= lastWriteElapsedRealtime
        && nowElapsedRealtime - lastWriteElapsedRealtime >= WRITE_INTERVAL_MS;
  }

  private void persist(@NonNull Anchor anchor, long nowElapsedRealtime)
  {
    mPrefs
        .edit()
        .putInt(KEY_ANCHOR_VERSION, CACHE_VERSION)
        .putLong(KEY_ANCHOR_LAT_BITS, Double.doubleToRawLongBits(anchor.latitude))
        .putLong(KEY_ANCHOR_LON_BITS, Double.doubleToRawLongBits(anchor.longitude))
        .putLong(KEY_ANCHOR_FIX_TIME, anchor.fixTimeMillis)
        .putInt(KEY_ANCHOR_ACCURACY_BITS, Float.floatToRawIntBits(anchor.accuracyMeters))
        .apply();
    mPersistedInSession = true;
    mLastWriteElapsedRealtime = nowElapsedRealtime;
    mPendingAnchor = null;
  }
}
