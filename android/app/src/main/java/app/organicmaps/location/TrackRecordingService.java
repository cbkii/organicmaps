package app.organicmaps.location;

import static android.Manifest.permission.ACCESS_FINE_LOCATION;
import static android.Manifest.permission.POST_NOTIFICATIONS;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;

import android.app.Activity;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.location.Location;
import android.os.Build;
import android.os.IBinder;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresPermission;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationChannelCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.ServiceCompat;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;
import app.organicmaps.MwmActivity;
import app.organicmaps.MwmApplication;
import app.organicmaps.R;
import app.organicmaps.sdk.location.LocationHelper;
import app.organicmaps.sdk.location.LocationListener;
import app.organicmaps.sdk.location.LocationUtils;
import app.organicmaps.sdk.location.TrackRecorder;
import app.organicmaps.sdk.util.log.Logger;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class TrackRecordingService extends Service implements LocationListener
{
  public static final String TRACK_REC_CHANNEL_ID = "TRACK RECORDING";
  public static final String STOP_TRACK_RECORDING = "STOP_TRACK_RECORDING";
  public static final int TRACK_REC_NOTIFICATION_ID = 54321;
  private NotificationCompat.Builder mNotificationBuilder;
  private static final String TAG = TrackRecordingService.class.getSimpleName();
  private boolean mWarningNotification = false;
  private NotificationCompat.Builder mWarningBuilder;
  private PendingIntent mPendingIntent;
  private PendingIntent mExitPendingIntent;

  @Nullable
  @Override
  public IBinder onBind(Intent intent)
  {
    return null;
  }

  @RequiresPermission(value = ACCESS_FINE_LOCATION)
  public static void startForegroundService(@NonNull Context context)
  {
    final boolean alreadyRecording = TrackRecorder.nativeIsTrackRecordingEnabled();
    final boolean askResumeMode = PreferenceManager.getDefaultSharedPreferences(context).getBoolean(
        context.getString(R.string.pref_track_recording_auto_resume), false);

    // Keep the native restart gate synchronized with the user-facing setting. This is deliberately
    // done at each manual/resume entry as well as by the preference itself, so restored preferences
    // or other non-UI changes still fail closed on the next start.
    TrackRecorder.nativeSetAutoResumeFeatureEnabled(askResumeMode);

    switch (TrackRecordingResumePolicy.decideStart(alreadyRecording, askResumeMode))
    {
    case CONTINUE_EXISTING:
      startServiceInternal(context);
      return;
    case START_ONCE:
      startNewRecording(context, false);
      return;
    case ASK_RESUME_MODE:
      showResumeModeDialog(context);
      return;
    }
  }

  @RequiresPermission(value = ACCESS_FINE_LOCATION)
  private static void showResumeModeDialog(@NonNull Context context)
  {
    final Activity activity = MwmApplication.from(context).getTopActivity();
    if (!(activity instanceof MwmActivity))
    {
      // The only supported OFF -> ON authority is the visible map menu. Do not silently choose a
      // persistence mode if some future/background caller tries to start recording.
      Logger.w(TAG, "Can't ask for track recording resume mode without a visible map activity");
      return;
    }

    new MaterialAlertDialogBuilder(activity)
        .setTitle(R.string.track_recording_resume_dialog_title)
        .setMessage(R.string.track_recording_resume_dialog_message)
        .setCancelable(false)
        .setNegativeButton(R.string.track_recording_resume_once,
                           (dialog, which) -> startNewRecording(context, false))
        .setPositiveButton(R.string.track_recording_resume_auto,
                           (dialog, which) -> startNewRecording(context, true))
        .show();
  }

  @RequiresPermission(value = ACCESS_FINE_LOCATION)
  private static void startNewRecording(@NonNull Context context, boolean autoResume)
  {
    TrackRecorder.nativeStartTrackRecording();
    TrackRecorder.nativeSetAutoResumeForCurrentRecording(autoResume);
    startServiceInternal(context);
  }

  @RequiresPermission(value = ACCESS_FINE_LOCATION)
  private static void startServiceInternal(@NonNull Context context)
  {
    MwmApplication.from(context).getLocationHelper().restartWithNewMode();
    ContextCompat.startForegroundService(context, new Intent(context, TrackRecordingService.class));
  }

  public static void createNotificationChannel(@NonNull Context context)
  {
    final NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
    final NotificationChannelCompat channel =
        new NotificationChannelCompat.Builder(TRACK_REC_CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
            .setName(context.getString(R.string.track_recording))
            .setLightsEnabled(false)
            .setVibrationEnabled(false)
            .build();
    notificationManager.createNotificationChannel(channel);
  }

  private PendingIntent getPendingIntent(@NonNull Context context)
  {
    if (mPendingIntent != null)
      return mPendingIntent;

    final int FLAG_IMMUTABLE = Build.VERSION.SDK_INT < Build.VERSION_CODES.M ? 0 : PendingIntent.FLAG_IMMUTABLE;
    final Intent contentIntent = new Intent(context, MwmActivity.class);
    mPendingIntent =
        PendingIntent.getActivity(context, 0, contentIntent, PendingIntent.FLAG_UPDATE_CURRENT | FLAG_IMMUTABLE);
    return mPendingIntent;
  }

  private PendingIntent getExitPendingIntent(@NonNull Context context)
  {
    if (mExitPendingIntent != null)
      return mExitPendingIntent;

    final int FLAG_IMMUTABLE = Build.VERSION.SDK_INT < Build.VERSION_CODES.M ? 0 : PendingIntent.FLAG_IMMUTABLE;
    final Intent exitIntent = new Intent(context, TrackRecordingService.class);
    exitIntent.setAction(STOP_TRACK_RECORDING);
    mExitPendingIntent =
        PendingIntent.getService(context, 1, exitIntent, PendingIntent.FLAG_UPDATE_CURRENT | FLAG_IMMUTABLE);
    return mExitPendingIntent;
  }

  @NonNull
  public NotificationCompat.Builder getNotificationBuilder(@NonNull Context context)
  {
    if (mNotificationBuilder != null)
      return mNotificationBuilder;

    mNotificationBuilder =
        new NotificationCompat.Builder(context, TRACK_REC_CHANNEL_ID)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationManager.IMPORTANCE_DEFAULT)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setOngoing(true)
            .setShowWhen(true)
            .setOnlyAlertOnce(true)
            .setSmallIcon(app.organicmaps.branding.R.drawable.ic_splash)
            .setContentTitle(context.getString(R.string.track_recording))
            .addAction(0, context.getString(R.string.navigation_stop_button), getExitPendingIntent(context))
            .setContentIntent(getPendingIntent(context))
            .setColor(ContextCompat.getColor(context, R.color.notification));

    return mNotificationBuilder;
  }

  public static void stopService(@NonNull Context context)
  {
    Logger.i(TAG);
    // Main-menu/place-page stop, save and cancel are explicit user actions. Disarm any future
    // restart before stopping the live recorder. onDestroy itself intentionally preserves consent
    // so unexpected service teardown or device shutdown can still resume an auto-resume session.
    TrackRecorder.nativeSetAutoResumeForCurrentRecording(false);
    context.stopService(new Intent(context, TrackRecordingService.class));
  }

  @Override
  public void onDestroy()
  {
    Logger.d(TAG);
    mNotificationBuilder = null;
    mWarningBuilder = null;
    if (TrackRecorder.nativeIsTrackRecordingEnabled())
      TrackRecorder.nativeStopTrackRecording();
    MwmApplication.from(this).getLocationHelper().removeListener(this);
    // The notification is cancelled automatically by the system.
  }

  // Do not stop the recording merely because the task was removed. If the process/service is later
  // torn down, onDestroy pauses the live recorder while the explicit auto-resume marker (if any)
  // remains armed for the next process.
  // See https://github.com/organicmaps/organicmaps/issues/11840
  // @Override
  // public void onTaskRemoved(@NonNull Intent rootIntent)
  // {
  //   Logger.d(TAG, "Task removed, stopping service");
  //   stopSelf();
  //   super.onTaskRemoved(rootIntent);
  // }

  @Override
  public int onStartCommand(@NonNull Intent intent, int flags, int startId)
  {
    if (!MwmApplication.from(this).getOrganicMaps().arePlatformAndCoreInitialized())
    {
      Logger.w(TAG, "Application is not initialized");
      stopSelf();
      return START_NOT_STICKY; // The service will be stopped by stopSelf().
    }

    if (!LocationUtils.checkFineLocationPermission(this))
    {
      Logger.w(TAG, "Permission ACCESS_FINE_LOCATION is not granted, skipping TrackRecordingService");
      stopSelf();
      return START_NOT_STICKY; // The service will be stopped by stopSelf().
    }

    if (!TrackRecorder.nativeIsTrackRecordingEnabled())
    {
      Logger.i(TAG, "Service can't be started because Track Recorder is off");
      stopSelf();
      return START_NOT_STICKY;
    }

    final String action = intent.getAction();
    if (action != null && STOP_TRACK_RECORDING.equals(action))
    {
      Logger.d(TAG, "Stop action received");
      TrackRecorder.nativeSetAutoResumeForCurrentRecording(false);
      TrackRecorder.nativeStopTrackRecording();
      stopSelf();
      return START_NOT_STICKY;
    }

    Logger.i(TAG, "Starting Track Recording Foreground service");
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
      ServiceCompat.startForeground(this, TrackRecordingService.TRACK_REC_NOTIFICATION_ID,
                                    getNotificationBuilder(this).build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
    else
      ServiceCompat.startForeground(this, TrackRecordingService.TRACK_REC_NOTIFICATION_ID,
                                    getNotificationBuilder(this).build(), 0);

    final LocationHelper locationHelper = MwmApplication.from(this).getLocationHelper();

    // Subscribe to location updates. This call is idempotent.
    locationHelper.addListener(this);

    // Restart the location with more frequent refresh interval for Track Recording.
    locationHelper.restartWithNewMode();

    return START_NOT_STICKY;
  }

  public NotificationCompat.Builder getWarningBuilder(Context context)
  {
    if (mWarningBuilder != null)
      return mWarningBuilder;

    mWarningBuilder =
        new NotificationCompat.Builder(context, TRACK_REC_CHANNEL_ID)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationManager.IMPORTANCE_DEFAULT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setShowWhen(true)
            .setOnlyAlertOnce(true)
            .setSmallIcon(R.drawable.warning_icon)
            .setContentTitle(context.getString(R.string.current_location_unknown_error_title))
            .setContentText(context.getString(R.string.dialog_routing_location_turn_wifi))
            .setStyle(new NotificationCompat.BigTextStyle().bigText(
                context.getString(R.string.dialog_routing_location_turn_wifi)))
            .addAction(0, context.getString(R.string.navigation_stop_button), getExitPendingIntent(context))
            .setContentIntent(getPendingIntent(context))
            .setColor(ContextCompat.getColor(context, R.color.notification_warning));

    return mWarningBuilder;
  }

  @Override
  public void onLocationUpdateTimeout()
  {
    Logger.i(TAG, "Location update timeout");
    mWarningNotification = true;
    // post notification permission is not there but we will not stop the runnable because if
    // in between user gives permission then warning will not be updated until next restart
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        && ActivityCompat.checkSelfPermission(this, POST_NOTIFICATIONS) != PERMISSION_GRANTED)
      return;

    NotificationManagerCompat.from(this).notify(TRACK_REC_NOTIFICATION_ID, getWarningBuilder(this).build());
  }

  @Override
  public void onLocationUpdated(@NonNull Location location)
  {
    Logger.d(TAG, "Location is being updated in Track Recording service");

    if (mWarningNotification)
    {
      mWarningNotification = false;

      // post notification permission is not there but we will not stop the runnable because if
      // in between user gives permission then warning will not be updated until next restart
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
          && ActivityCompat.checkSelfPermission(this, POST_NOTIFICATIONS) != PERMISSION_GRANTED)
        return;

      NotificationManagerCompat.from(this).notify(TRACK_REC_NOTIFICATION_ID, getNotificationBuilder(this).build());
    }
  }
}
