package app.organicmaps.routing;

import static android.Manifest.permission.ACCESS_COARSE_LOCATION;
import static android.Manifest.permission.ACCESS_FINE_LOCATION;
import static android.Manifest.permission.POST_NOTIFICATIONS;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static app.organicmaps.sdk.util.Constants.Vendor.XIAOMI;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.os.Build;
import android.os.IBinder;
import android.os.SystemClock;
import android.text.TextUtils;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RawRes;
import androidx.annotation.RequiresPermission;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationChannelCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.ServiceCompat;
import androidx.core.content.ContextCompat;
import app.organicmaps.sdk.Framework;
import app.organicmaps.sdk.OrganicMaps;
import app.organicmaps.sdk.location.LocationHelper;
import app.organicmaps.sdk.location.LocationListener;
import app.organicmaps.sdk.location.LocationUtils;
import app.organicmaps.sdk.routing.NavigationNotification;
import app.organicmaps.sdk.routing.RoutingController;
import app.organicmaps.sdk.routing.RoutingInfo;
import app.organicmaps.sdk.sound.MediaPlayerWrapper;
import app.organicmaps.sdk.sound.OfflineNavigationVoicePack;
import app.organicmaps.sdk.sound.TtsFallbackPolicy;
import app.organicmaps.sdk.sound.TtsPlayer;
import app.organicmaps.sdk.util.Assert;
import app.organicmaps.sdk.util.Config;
import app.organicmaps.sdk.util.Graphics;
import app.organicmaps.sdk.util.log.Logger;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class NavigationService extends Service implements LocationListener
{
  private static final String TAG = NavigationService.class.getSimpleName();
  private static final String STOP_NAVIGATION = "STOP_NAVIGATION";

  private static final String CHANNEL_ID = "NAVIGATION";
  private static final int NOTIFICATION_ID = 12345678;
  private static final long NOTIFICATION_UPDATE_INTERVAL_MS = 1000;

  private static OrganicMaps sOrganicMaps;
  @Nullable
  private static NotificationCompat.Extender sCarNotificationExtender;
  @Nullable
  private static PendingIntent sOpenAppPendingIntent;
  @RawRes
  private static int sTtsFallbackSoundResId;

  @SuppressWarnings("NotNullFieldNotInitialized")
  @NonNull
  private MediaPlayerWrapper mPlayer;
  @NonNull
  private final OfflineNavigationVoicePack.GpsSignalState mGpsSignalState =
      new OfflineNavigationVoicePack.GpsSignalState();

  // Destroyed in onDestroy(). Uses application context to avoid leaking Activity.
  @Nullable
  private static NotificationCompat.Builder mNotificationBuilder;

  // Cached turn direction bitmap to avoid re-creating on every location update.
  private int mLastTurnResId;
  @Nullable
  private Bitmap mLastTurnBitmap;

  private boolean mHasPublishedNavigationUpdate;
  private long mLastNotificationUpdateTimeMs;
  private int mLastNotificationTurnResId;
  @Nullable
  private String mLastNotificationStreet;

  public static void setOrganicMaps(@NonNull OrganicMaps organicMaps)
  {
    sOrganicMaps = organicMaps;
  }

  public static void setCarNotificationExtender(@Nullable NotificationCompat.Extender carNotificationExtender)
  {
    sCarNotificationExtender = carNotificationExtender;
  }

  public static void setOpenAppPendingIntent(@NonNull PendingIntent openAppPendingIntent)
  {
    sOpenAppPendingIntent = openAppPendingIntent;
  }

  public static void setTtsFallbackSoundResource(@RawRes int soundResId)
  {
    sTtsFallbackSoundResId = soundResId;
  }

  /**
   * Start the foreground service for turn-by-turn voice-guided navigation.
   *
   * @param context Context to start service from.
   */
  @RequiresPermission(value = ACCESS_FINE_LOCATION)
  public static void startForegroundService(@NonNull Context context)
  {
    Logger.i(TAG);
    ContextCompat.startForegroundService(context, new Intent(context, NavigationService.class));
  }

  /**
   * Stop the foreground service for turn-by-turn voice-guided navigation.
   *
   * @param context Context to stop service from.
   */
  public static void stopService(@NonNull Context context)
  {
    Logger.i(TAG);
    context.stopService(new Intent(context, NavigationService.class));
  }

  /**
   * Creates notification channel for navigation.
   *
   * @param context Context to create channel from.
   */
  public static void createNotificationChannel(@NonNull Context context)
  {
    Logger.i(TAG);

    final NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
    final NotificationChannelCompat channel =
        new NotificationChannelCompat.Builder(CHANNEL_ID,
                                              NotificationManagerCompat.IMPORTANCE_LOW)
            .setName(context.getString(R.string.navigation_channel_name))
            .setLightsEnabled(false) // less annoying
            .setVibrationEnabled(false) // less annoying
            .build();
    notificationManager.createNotificationChannel(channel);
  }

  /**
   * See {@link android.app.Notification.Builder#setColorized(boolean) }
   */
  private static boolean isColorizedSupported()
  {
    // Nice colorized notifications should be supported on API=26 and later.
    // Nonetheless, even on API=32, Xiaomi uses their own legacy implementation that displays white-on-white instead.
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !XIAOMI.equalsIgnoreCase(Build.MANUFACTURER);
  }

  @NonNull
  public static NotificationCompat.Builder getNotificationBuilder(@NonNull Context context)
  {
    if (mNotificationBuilder != null)
      return mNotificationBuilder;

    final int FLAG_IMMUTABLE = Build.VERSION.SDK_INT < Build.VERSION_CODES.M ? 0 : PendingIntent.FLAG_IMMUTABLE;

    final Context appContext = context.getApplicationContext();
    final Intent exitIntent = new Intent(appContext, NavigationService.class);
    exitIntent.setAction(STOP_NAVIGATION);
    final PendingIntent exitPendingIntent =
        PendingIntent.getService(appContext, 0, exitIntent, PendingIntent.FLAG_UPDATE_CURRENT | FLAG_IMMUTABLE);

    mNotificationBuilder = new NotificationCompat.Builder(appContext, CHANNEL_ID)
                               .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
                               .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                               .setOngoing(true)
                               .setShowWhen(false)
                               .setOnlyAlertOnce(true)
                               .setSmallIcon(app.organicmaps.branding.R.drawable.ic_splash)
                               .addAction(0, context.getString(R.string.navigation_stop_button), exitPendingIntent)
                               .setColorized(isColorizedSupported())
                               .setColor(ContextCompat.getColor(context, R.color.notification));

    if (Build.VERSION.SDK_INT >= 24)
      mNotificationBuilder.setPriority(NotificationManager.IMPORTANCE_LOW);

    if (sOpenAppPendingIntent != null)
      mNotificationBuilder.setContentIntent(sOpenAppPendingIntent);

    return mNotificationBuilder;
  }

  @Override
  public void onCreate()
  {
    Logger.i(TAG);
    Assert.always(sOrganicMaps != null, "OrganicMaps instance must be set before starting NavigationService");

    mPlayer = new MediaPlayerWrapper(getApplicationContext());
  }

  @Override
  public void onDestroy()
  {
    Logger.i(TAG);

    mNotificationBuilder = null;
    mHasPublishedNavigationUpdate = false;
    mLastNotificationUpdateTimeMs = 0;
    mLastNotificationTurnResId = 0;
    mLastNotificationStreet = null;
    sOrganicMaps.getLocationHelper().removeListener(this);
    TtsPlayer.INSTANCE.stop();

    // The notification is cancelled automatically by the system.

    mPlayer.release();
  }

  @Override
  public void onLowMemory()
  {
    Logger.d(TAG);
  }

  @Override
  public int onStartCommand(@NonNull Intent intent, int flags, int startId)
  {
    final String action = intent.getAction();
    if (action != null && action.equals(STOP_NAVIGATION))
    {
      RoutingController.get().cancel();
      stopSelf();
      return START_NOT_STICKY;
    }

    if (!sOrganicMaps.arePlatformAndCoreInitialized())
    {
      // The system restarts the service if the app's process has crashed or been stopped. It would be nice to
      // automatically restore the last route and resume navigation. Unfortunately, the current implementation of
      // the routing state machine (RoutingController and underlying NDK part) requires a complete re-planning of
      // the route. Such operation can fail for some reason. We have no UI (i.e. RoutePlanFragment) started to
      // handle any route planning errors. Starting any new Activities from Services is not allowed also.
      // https://github.com/organicmaps/organicmaps/issues/6233
      Logger.w(TAG, "Application is not initialized");
      stopSelf();
      return START_NOT_STICKY; // The service will be stopped by stopSelf().
    }

    if (!LocationUtils.checkFineLocationPermission(this))
    {
      // In a hypothetical scenario, the user could revoke location permissions after the app's process crashed,
      // but before the service with START_STICKY was restarted by the system.
      Logger.w(TAG, "Permission ACCESS_FINE_LOCATION is not granted, skipping NavigationService");
      stopSelf();
      return START_NOT_STICKY; // The service will be stopped by stopSelf().
    }

    Logger.i(TAG, "Starting Navigation Foreground service");

    try
    {
      ServiceCompat.startForeground(
          this, NavigationService.NOTIFICATION_ID, getNotificationBuilder(this).build(),
          ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION | ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
    }
    catch (SecurityException e)
    {
      // It is unknown why, but on Android 14+ devices starting services fails despite permission checks above.
      Logger.e(TAG, "Failed to start foreground service, stopping the service", e);
      stopSelf();
      return START_NOT_STICKY;
    }

    final LocationHelper locationHelper = sOrganicMaps.getLocationHelper();

    // Subscribe to location updates. This call is idempotent.
    locationHelper.addListener(this);

    // Restart the location with more frequent refresh interval for navigation.
    locationHelper.restartWithNewMode();

    // Please make this service START_STICKY after fixing the issues at the beginning of the function.
    return START_NOT_STICKY;
  }

  @Nullable
  @Override
  public IBinder onBind(Intent intent)
  {
    return null;
  }

  private boolean shouldPublishNavigationUpdate(int turnResId, @Nullable String nextStreet, long nowMs)
  {
    if (!mHasPublishedNavigationUpdate)
      return true;

    if (turnResId != mLastNotificationTurnResId || !TextUtils.equals(nextStreet, mLastNotificationStreet))
      return true;

    return nowMs - mLastNotificationUpdateTimeMs >= NOTIFICATION_UPDATE_INTERVAL_MS;
  }

  private void recordPublishedNavigationUpdate(int turnResId, @Nullable String nextStreet, long nowMs)
  {
    mHasPublishedNavigationUpdate = true;
    mLastNotificationUpdateTimeMs = nowMs;
    mLastNotificationTurnResId = turnResId;
    mLastNotificationStreet = nextStreet;
  }

  private boolean playFallback(@NonNull OfflineNavigationVoicePack.Mode mode,
                               @Nullable NavigationNotification notification, @Nullable RoutingInfo routingInfo,
                               @NonNull OfflineNavigationVoicePack.GpsSignalEvent gpsSignalEvent)
  {
    final boolean hasRoutingEvent = notification != null;
    final boolean hasGpsEvent = gpsSignalEvent != OfflineNavigationVoicePack.GpsSignalEvent.NONE;
    final boolean engineWarning = OfflineNavigationVoicePack.isCriticalEvent(routingInfo);
    final boolean speedCameraEvent =
        notification != null && notification.getEvent() == NavigationNotification.Event.SPEED_CAMERA;
    final boolean criticalEvent =
        engineWarning || speedCameraEvent || gpsSignalEvent == OfflineNavigationVoicePack.GpsSignalEvent.LOST;

    if (mode == OfflineNavigationVoicePack.Mode.VOICE)
    {
      // A typed engine warning outranks a simultaneous manoeuvre cue. Otherwise the
      // normal clip could consume the cycle and suppress the safety tone below.
      if ((engineWarning || speedCameraEvent) && sTtsFallbackSoundResId != 0)
        return mPlayer.playback(sTtsFallbackSoundResId);

      final List<File> clips = new ArrayList<>();
      if (gpsSignalEvent == OfflineNavigationVoicePack.GpsSignalEvent.LOST)
        clips.addAll(
            OfflineNavigationVoicePack.resolveCurrentCues(this, routingInfo, OfflineNavigationVoicePack.Cue.GPS_LOST));
      else if (gpsSignalEvent == OfflineNavigationVoicePack.GpsSignalEvent.RESTORED)
        clips.addAll(OfflineNavigationVoicePack.resolveCurrentCues(this, routingInfo,
                                                                   OfflineNavigationVoicePack.Cue.GPS_RESTORED));

      if (notification != null)
      {
        if (OfflineNavigationVoicePack.shouldPlayVoiceCue(notification.getEvent(), notification.getStage()))
        {
          final OfflineNavigationVoicePack.Cue cue =
              notification.getEvent() == NavigationNotification.Event.ROUTE_RECALCULATION
                  ? OfflineNavigationVoicePack.Cue.ROUTE_UPDATED
                  : OfflineNavigationVoicePack.Cue.MANEUVER;
          clips.addAll(OfflineNavigationVoicePack.resolveCurrentCues(this, routingInfo, cue));
        }
      }

      if (!clips.isEmpty() && mPlayer.playback(clips))
        return true;

      // Voice mode deliberately falls back to the one-tone alert for an unmapped
      // event or a pack-integrity/extraction failure.
      if ((hasRoutingEvent || hasGpsEvent) && sTtsFallbackSoundResId != 0)
        return mPlayer.playback(sTtsFallbackSoundResId);
      return false;
    }

    if (sTtsFallbackSoundResId == 0
        || !OfflineNavigationVoicePack.shouldPlayTone(mode, hasRoutingEvent || hasGpsEvent, criticalEvent))
      return false;

    return mPlayer.playback(sTtsFallbackSoundResId);
  }

  private void onLocationUnavailable()
  {
    if (!RoutingController.get().isNavigating())
      return;

    final OfflineNavigationVoicePack.GpsSignalEvent event = mGpsSignalState.onUnavailable();
    if (event == OfflineNavigationVoicePack.GpsSignalEvent.NONE)
      return;

    final TtsPlayer.State ttsState = TtsPlayer.getState();
    if (!TtsFallbackPolicy.shouldPlayFallback(ttsState, OfflineNavigationVoicePack.isFallbackEnabled(this)))
      return;

    final OfflineNavigationVoicePack.Mode fallbackMode = OfflineNavigationVoicePack.getMode(this);
    if (playFallback(fallbackMode, null, Framework.nativeGetRouteFollowingInfo(), event))
      Logger.d(TAG, "Played GPS-loss fallback; mode=" + fallbackMode + ", state=" + ttsState);
  }

  @Override
  public void onLocationUpdateTimeout()
  {
    onLocationUnavailable();
  }

  @Override
  public void onLocationDisabled()
  {
    onLocationUnavailable();
  }

  @Override
  @RequiresPermission(anyOf = {ACCESS_COARSE_LOCATION, ACCESS_FINE_LOCATION})
  public void onLocationUpdated(@NonNull Location location)
  {
    // Ignore any pending notifications when service is being stopping.
    final RoutingController routingController = RoutingController.get();
    if (!routingController.isNavigating())
      return;

    final OfflineNavigationVoicePack.GpsSignalEvent gpsSignalEvent = mGpsSignalState.onLocationUpdated();
    final NavigationNotification notification = Framework.nativeGenerateNotifications(Config.TTS.getAnnounceStreets());
    final String[] turnNotifications = notification == null ? null : notification.getTexts();
    final RoutingInfo routingInfo = Framework.nativeGetRouteFollowingInfo();
    final TtsPlayer.State ttsState = TtsPlayer.getState();
    boolean playedTtsFallback = false;

    if (OfflineNavigationVoicePack.hasNotifications(turnNotifications) && ttsState == TtsPlayer.State.READY_ON)
    {
      TtsPlayer.INSTANCE.playTurnNotifications(turnNotifications);
    }
    else
    {
      final OfflineNavigationVoicePack.Mode fallbackMode = OfflineNavigationVoicePack.getMode(this);
      if (TtsFallbackPolicy.shouldPlayFallback(ttsState, OfflineNavigationVoicePack.isFallbackEnabled(this)))
      {
        playedTtsFallback = playFallback(fallbackMode, notification, routingInfo, gpsSignalEvent);
        if (playedTtsFallback)
          Logger.d(TAG, "Played navigation fallback; mode=" + fallbackMode + ", state=" + ttsState);
      }
    }

    // TODO: consider to create callback mechanism to transfer 'ROUTE_IS_FINISHED' event from
    // the core to the platform code (https://github.com/organicmaps/organicmaps/issues/3589),
    // because calling the native method 'nativeIsRouteFinished'
    // too often can result in poor UI performance.
    // This check should be done after playTurnNotifications() to play the last turn notification.
    if (Framework.nativeIsRouteFinished())
    {
      routingController.cancel();
      sOrganicMaps.getLocationHelper().restartWithNewMode();
      stopSelf();
      return;
    }

    if (routingInfo == null)
      return;

    if (!playedTtsFallback && routingInfo.shouldPlayWarningSignal())
      mPlayer.playback(R.raw.speed_cams_beep);

    // Don't spend time on updating RemoteView if notifications are not allowed.
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        && ActivityCompat.checkSelfPermission(this, POST_NOTIFICATIONS) != PERMISSION_GRANTED)
      return;

    final int turnResId = routingInfo.carDirection.getTurnRes(routingInfo.exitNum);
    final long nowMs = SystemClock.elapsedRealtime();
    if (!shouldPublishNavigationUpdate(turnResId, routingInfo.nextStreet, nowMs))
      return;

    final NotificationCompat.Builder notificationBuilder = getNotificationBuilder(this)
                                                               .setContentTitle(routingInfo.distToTurn.toString(this))
                                                               .setContentText(routingInfo.nextStreet);

    if (turnResId != mLastTurnResId || mLastTurnBitmap == null)
    {
      final Drawable drawable = AppCompatResources.getDrawable(this, turnResId);
      if (drawable != null)
      {
        mLastTurnBitmap = isColorizedSupported()
                            ? Graphics.drawableToBitmap(drawable)
                            : Graphics.drawableToBitmapWithTint(
                                  drawable, ContextCompat.getColor(this, app.organicmaps.branding.R.color.base_accent));
        mLastTurnResId = turnResId;
      }
    }
    if (mLastTurnBitmap != null)
      notificationBuilder.setLargeIcon(mLastTurnBitmap);

    if (sCarNotificationExtender != null)
      notificationBuilder.extend(sCarNotificationExtender);

    // The notification object must be re-created for every published update.
    NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notificationBuilder.build());
    recordPublishedNavigationUpdate(turnResId, routingInfo.nextStreet, nowMs);
  }
}
