package app.organicmaps.sdk.location;

import static android.Manifest.permission.ACCESS_COARSE_LOCATION;
import static android.Manifest.permission.ACCESS_FINE_LOCATION;
import static app.organicmaps.sdk.util.concurrency.UiThread.runLater;

import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Looper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresPermission;
import androidx.core.content.ContextCompat;
import androidx.core.location.LocationListenerCompat;
import androidx.core.location.LocationManagerCompat;
import androidx.core.location.LocationRequestCompat;
import app.organicmaps.sdk.util.log.Logger;
import java.util.HashSet;
import java.util.Set;

public class AndroidNativeProvider extends BaseLocationProvider
{
  private static final String TAG = AndroidNativeProvider.class.getSimpleName();

  private class NativeLocationListener implements LocationListenerCompat
  {
    @Override
    public void onLocationChanged(@NonNull Location location)
    {
      if (this == mNativeLocationListener)
        mListener.onLocationChanged(location);
    }

    @Override
    public void onProviderDisabled(@NonNull String provider)
    {
      Logger.d(TAG, "Disabled location provider: " + provider);
      if (this != mNativeLocationListener)
        return;
      mEnabledProviders.remove(provider);
      if (mEnabledProviders.isEmpty())
        mListener.onLocationDisabled();
    }

    @Override
    public void onProviderEnabled(@NonNull String provider)
    {
      Logger.d(TAG, "Enabled location provider: " + provider);
      if (this == mNativeLocationListener && mProviders.contains(provider))
        mEnabledProviders.add(provider);
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras)
    {
      Logger.d(TAG, "Status changed for location provider: " + provider + "; new status = " + status);
    }
  }

  @NonNull
  private final LocationManager mLocationManager;
  // Successful subscriptions and currently enabled subscriptions are separate facts.
  private final Set<String> mProviders;
  private final Set<String> mEnabledProviders = new HashSet<>();
  private final Context mContext;
  private final boolean mInCar;

  @Nullable
  private NativeLocationListener mNativeLocationListener;

  public AndroidNativeProvider(@NonNull Context context, @NonNull BaseLocationProvider.Listener listener)
  {
    this(context, listener, false);
  }

  public AndroidNativeProvider(@NonNull Context context, @NonNull BaseLocationProvider.Listener listener, boolean inCar)
  {
    super(listener);
    mContext = context;
    mInCar = inCar;
    mLocationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
    mProviders = new HashSet<>();
    // This service is always available on all versions of Android
    if (mLocationManager == null)
      throw new IllegalStateException("Can't get LOCATION_SERVICE");
  }

  // A permission is checked externally
  @Override
  @RequiresPermission(anyOf = {ACCESS_COARSE_LOCATION, ACCESS_FINE_LOCATION})
  public void start(long interval)
  {
    Logger.d(TAG);
    if (mNativeLocationListener != null)
      return;
    mNativeLocationListener = new NativeLocationListener();

    final LocationRequestCompat locationRequest =
        new LocationRequestCompat
            .Builder(interval)
            // The quality is a hint to providers on how they should weigh power vs accuracy tradeoffs.
            .setQuality(LocationRequestCompat.QUALITY_HIGH_ACCURACY)
            .build();

    // API 31+ provides `fused` provider which aggregates `gps` and `network` and potentially other sensors as well.
    // Unfortunately, certain LineageOS ROMs have broken `fused` provider that pretends to be enabled, but in
    // reality it does absolutely nothing and doesn't return any location updates. For this reason, we try all
    // (`fused`, `network`, `gps`) providers here, but prefer `fused` in LocationHelper.onLocationChanged().
    //
    // https://developer.android.com/reference/android/location/LocationManager#FUSED_PROVIDER
    // https://issuetracker.google.com/issues/215186921#comment3
    // https://github.com/organicmaps/organicmaps/issues/4158
    //
    final Set<String> available = new HashSet<>(mLocationManager.getProviders(true));
    available.remove(LocationManager.PASSIVE_PROVIDER);
    if (mInCar
        && ContextCompat.checkSelfPermission(mContext, ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
      available.remove(LocationManager.GPS_PROVIDER);
    for (String provider : available)
    {
      try
      {
        LocationManagerCompat.requestLocationUpdates(mLocationManager, provider, locationRequest,
                                                     mNativeLocationListener, Looper.getMainLooper());
        mProviders.add(provider);
        mEnabledProviders.add(provider);
      }
      catch (RuntimeException e)
      {
        if (!mInCar)
          throw e;
        Logger.w(TAG, "Registration failed for " + provider + ": " + e.getClass().getSimpleName());
      }
    }
    if (mProviders.isEmpty())
    {
      Logger.w(TAG, "No successful location subscriptions");
      // A broken registration with Location enabled belongs to the acquisition supervisor, not Settings.
      final NativeLocationListener attempt = mNativeLocationListener;
      if (!mInCar || !LocationUtils.areLocationServicesTurnedOn(mContext))
        runLater(() -> {
          if (attempt == mNativeLocationListener)
            mListener.onLocationDisabled();
        });
    }
  }

  public boolean hasSubscriptions()
  {
    return !mEnabledProviders.isEmpty();
  }
  public boolean hasGpsSubscription()
  {
    return mEnabledProviders.contains(LocationManager.GPS_PROVIDER);
  }
  public boolean isGpsEnabled()
  {
    return mLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
  }

  private static final java.util.concurrent.ExecutorService CACHE_WORKER =
      new java.util.concurrent.ThreadPoolExecutor(0, 2, 15L, java.util.concurrent.TimeUnit.SECONDS,
                                                new java.util.concurrent.SynchronousQueue<>(), task -> {
                                                  Thread thread = new Thread(task, "location-cache");
                                                  thread.setDaemon(true);
                                                  return thread;
                                                });

  /** Optional cache never delays subscriptions or queries mutable provider state off-main. */
  public void requestRecentCachedLocation(java.util.function.Consumer<Location> callback)
  {
    final NativeLocationListener attempt = mNativeLocationListener;
    if (attempt == null)
      return;
    final Set<String> providers = new HashSet<>(mProviders);
    try
    {
      CACHE_WORKER.execute(() -> {
        final Location cached = getRecentCachedLocation(providers);
        runLater(() -> {
          if (attempt == mNativeLocationListener)
            callback.accept(cached);
        });
      });
    }
    catch (java.util.concurrent.RejectedExecutionException saturated)
    {
      Logger.w(TAG, "Optional location cache workers unavailable");
    }
  }

  /** Cache is returned separately: it must never be replayed as a newly confirmed navigation fix. */
  @Nullable
  @SuppressWarnings("MissingPermission")
  private Location getRecentCachedLocation(Set<String> providers)
  {
    Location best = null;
    for (String provider : providers)
    {
      try
      {
        final Location location = mLocationManager.getLastKnownLocation(provider);
        if (location == null
            || !LocationAcquisitionPolicy.isUsableObservation(
                android.os.SystemClock.elapsedRealtimeNanos(), location.getElapsedRealtimeNanos(),
                location.getLatitude(), location.getLongitude(), location.getAccuracy(),
                LocationAcquisitionPolicy.CACHE_MAX_AGE_MS)
            || !location.hasAccuracy() || location.getAccuracy() > 2000)
          continue;
        if (best == null || location.getElapsedRealtimeNanos() > best.getElapsedRealtimeNanos())
          best = new Location(location);
      }
      catch (RuntimeException e)
      {
        Logger.w(TAG, "Cache unavailable for " + provider + ": " + e.getClass().getSimpleName());
      }
    }
    return best;
  }

  @SuppressWarnings("MissingPermission")
  // A permission is checked externally
  @Override
  public void stop()
  {
    Logger.d(TAG);
    final NativeLocationListener listener = mNativeLocationListener;
    mNativeLocationListener = null; // Invalidate queued callbacks before unregistering.
    mProviders.clear();
    mEnabledProviders.clear();
    if (listener != null)
    {
      try
      {
        LocationManagerCompat.removeUpdates(mLocationManager, listener);
      }
      catch (RuntimeException e)
      {
        Logger.w(TAG, "Unregister failed: " + e.getClass().getSimpleName());
      }
    }
  }
}
