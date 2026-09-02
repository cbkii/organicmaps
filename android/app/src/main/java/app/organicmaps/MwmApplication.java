package app.organicmaps;

import static app.organicmaps.sdk.location.LocationState.LOCATION_TAG;

import android.app.Activity;
import android.app.Application;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ProcessLifecycleOwner;
import androidx.preference.PreferenceManager;
import app.organicmaps.background.OsmUploadScheduler;
import app.organicmaps.downloader.DownloaderNotifier;
import app.organicmaps.incar.InCarBudgetRendering;
import app.organicmaps.incar.InCarDrivingUi;
import app.organicmaps.incar.InCarDrivingViewController;
import app.organicmaps.incar.InCarQuickDestinationsUi;
import app.organicmaps.location.TrackRecordingService;
import app.organicmaps.routing.NavigationService;
import app.organicmaps.sdk.Map;
import app.organicmaps.sdk.OrganicMaps;
import app.organicmaps.sdk.display.DisplayManager;
import app.organicmaps.sdk.location.LocationHelper;
import app.organicmaps.sdk.location.LocationState;
import app.organicmaps.sdk.location.SensorHelper;
import app.organicmaps.sdk.location.TrackRecorder;
import app.organicmaps.sdk.maplayer.isolines.IsolinesManager;
import app.organicmaps.sdk.maplayer.subway.SubwayManager;
import app.organicmaps.sdk.routing.RoutingController;
import app.organicmaps.sdk.util.Config;
import app.organicmaps.sdk.util.log.Logger;
import app.organicmaps.util.InCarVisuals;
import app.organicmaps.util.ThemeSwitcher;
import app.organicmaps.util.Utils;
import java.io.IOException;
import java.lang.ref.WeakReference;

public class MwmApplication extends Application implements Application.ActivityLifecycleCallbacks
{
  @NonNull
  private static final String TAG = MwmApplication.class.getSimpleName();

  @SuppressWarnings("NotNullFieldNotInitialized")
  @NonNull
  private OrganicMaps mOrganicMaps;

  @SuppressWarnings("NotNullFieldNotInitialized")
  @NonNull
  private DisplayManager mDisplayManager;

  @Nullable
  private InCarDrivingViewController mInCarDrivingViewController;

  @Nullable
  private WeakReference<Activity> mTopActivity;

  @SuppressWarnings("NotNullFieldNotInitialized")
  @NonNull
  public static MwmApplication sInstance;

  @UiThread
  @Nullable
  public Activity getTopActivity()
  {
    return mTopActivity != null ? mTopActivity.get() : null;
  }

  @Nullable
  public SubwayManager getSubwayManager()
  {
    return getOrganicMaps().getSubwayManager();
  }

  @NonNull
  public IsolinesManager getIsolinesManager()
  {
    return getOrganicMaps().getIsolinesManager();
  }

  @NonNull
  public LocationHelper getLocationHelper()
  {
    return getOrganicMaps().getLocationHelper();
  }

  @NonNull
  public SensorHelper getSensorHelper()
  {
    return getOrganicMaps().getSensorHelper();
  }

  @NonNull
  public DisplayManager getDisplayManager()
  {
    return mDisplayManager;
  }

  @Nullable
  public InCarDrivingViewController getInCarDrivingViewController()
  {
    return mInCarDrivingViewController;
  }

  @NonNull
  public OrganicMaps getOrganicMaps()
  {
    return mOrganicMaps;
  }

  @NonNull
  public static MwmApplication from(@NonNull Context context)
  {
    return (MwmApplication) context.getApplicationContext();
  }

  @NonNull
  public static SharedPreferences prefs(@NonNull Context context)
  {
    return from(context).getOrganicMaps().getPreferences();
  }

  @Override
  public void onCreate()
  {
    super.onCreate();
    Logger.i(TAG, "Initializing application");

    sInstance = this;

    PreferenceManager.setDefaultValues(this, R.xml.prefs_main, false);
    mOrganicMaps = new OrganicMaps(getApplicationContext(), BuildConfig.FLAVOR, BuildConfig.APPLICATION_ID,
                                   BuildConfig.VERSION_CODE, BuildConfig.VERSION_NAME, BuildConfig.IS_IN_CAR);
    if (BuildConfig.IS_IN_CAR)
    {
      try
      {
        mInCarDrivingViewController = new InCarDrivingViewController(this, getLocationHelper());
      }
      catch (RuntimeException e)
      {
        Logger.e(TAG, "InCar Driving View controller initialization failed; continuing without it.", e);
      }
    }

    DownloaderNotifier.createNotificationChannel(this);
    initNavigationService();
    TrackRecordingService.createNotificationChannel(this);

    registerActivityLifecycleCallbacks(this);
    mDisplayManager = new DisplayManager();
  }

  public boolean initOrganicMaps(@Nullable Runnable onComplete) throws IOException
  {
    ThemeSwitcher.INSTANCE.initialize(this);
    return mOrganicMaps.init(() -> {
      ThemeSwitcher.INSTANCE.synchronizeApplicationTheme();
      ProcessLifecycleOwner.get().getLifecycle().addObserver(mProcessLifecycleObserver);
      if (BuildConfig.IS_IN_CAR)
      {
        runInCarDrivingViewController("framework attachment", InCarDrivingViewController::onFrameworkReady);
        InCarBudgetRendering.applyCurrent(this);
      }
      if (onComplete != null)
        onComplete.run();
    });
  }

  private final LifecycleObserver mProcessLifecycleObserver = new DefaultLifecycleObserver() {
    @Override
    public void onStart(@NonNull LifecycleOwner owner)
    {
      MwmApplication.this.onForeground();
    }

    @Override
    public void onStop(@NonNull LifecycleOwner owner)
    {
      MwmApplication.this.onBackground();
    }
  };

  @Override
  public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState)
  {}

  @Override
  public void onActivityStarted(@NonNull Activity activity)
  {
    if (BuildConfig.IS_IN_CAR && activity instanceof MwmActivity)
      runInCarDrivingViewController("map activity attachment", InCarDrivingViewController::onMapActivityStarted);
  }

  @Override
  public void onActivityResumed(@NonNull Activity activity)
  {
    Logger.d(TAG, "activity = " + activity);
    Utils.showOnLockScreen(Config.isShowOnLockScreenEnabled(), activity);
    getSensorHelper().setRotation(activity.getWindowManager().getDefaultDisplay().getRotation());

    // The resumed Activity is authoritative before any InCar callback reads getTopActivity().
    mTopActivity = new WeakReference<>(activity);
    if (BuildConfig.IS_IN_CAR && activity instanceof MwmActivity mapActivity)
    {
      InCarVisuals.applyAndObserve(mapActivity);
      InCarQuickDestinationsUi.attach(mapActivity);
      runInCarDrivingViewController("map activity resume", controller -> {
        controller.onMapActivityResumed();
        InCarDrivingUi.attach(mapActivity, controller);
        if (Map.isEngineCreated())
          InCarBudgetRendering.applyCurrent(mapActivity);
      });
    }
  }

  @Override
  public void onActivityPaused(@NonNull Activity activity)
  {
    Logger.d(TAG, "activity = " + activity);
    mTopActivity = null;
  }

  @Override
  public void onActivityStopped(@NonNull Activity activity)
  {
    if (BuildConfig.IS_IN_CAR && activity instanceof MwmActivity)
      runInCarDrivingViewController("map activity detachment", InCarDrivingViewController::onMapActivityStopped);
  }

  void onInCarRenderingCreated()
  {
    runInCarDrivingViewController("rendering attachment", InCarDrivingViewController::onRenderingCreated);
  }

  void onInCarRenderingDetached()
  {
    runInCarDrivingViewController("rendering detachment", InCarDrivingViewController::onRenderingDetached);
  }

  private void runInCarDrivingViewController(@NonNull String phase, @NonNull InCarControllerAction action)
  {
    final InCarDrivingViewController controller = mInCarDrivingViewController;
    if (controller == null)
      return;

    try
    {
      action.run(controller);
    }
    catch (RuntimeException exception)
    {
      disableInCarDrivingViewController(phase, controller, exception);
    }
  }

  private void disableInCarDrivingViewController(@NonNull String phase, @NonNull InCarDrivingViewController controller,
                                                 @NonNull RuntimeException exception)
  {
    mInCarDrivingViewController = null;
    try
    {
      controller.onFrameworkDetached();
    }
    catch (RuntimeException detachException)
    {
      exception.addSuppressed(detachException);
    }
    Logger.e(TAG, "InCar Driving View " + phase + " failed; continuing without it.", exception);
  }

  @FunctionalInterface
  private interface InCarControllerAction {
    void run(@NonNull InCarDrivingViewController controller);
  }

  @Override
  public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState)
  {
    Logger.d(TAG, "activity = " + activity + " outState = " + outState);
  }

  @Override
  public void onActivityDestroyed(@NonNull Activity activity)
  {
    Logger.d(TAG, "activity = " + activity);
    if (BuildConfig.IS_IN_CAR && activity instanceof MwmActivity mapActivity)
      InCarDrivingUi.release(mapActivity);
  }

  private void onForeground()
  {
    Logger.d(TAG);

    getLocationHelper().resumeLocationInForeground();
  }

  private void onBackground()
  {
    Logger.d(TAG);

    if (!BuildConfig.IS_IN_CAR)
      OsmUploadScheduler.schedule(this);

    if (!BuildConfig.IS_IN_CAR && !mDisplayManager.isDeviceDisplayUsed())
      Logger.i(LOCATION_TAG, "Android Auto is active, keeping location in the background");
    else if (RoutingController.get().isNavigating())
      Logger.i(LOCATION_TAG, "Navigation is in progress, keeping location in the background");
    else if (!Map.isEngineCreated() || LocationState.getMode() == LocationState.PENDING_POSITION)
      Logger.i(LOCATION_TAG, "PENDING_POSITION mode, keeping location in the background");
    else if (TrackRecorder.nativeIsTrackRecordingEnabled())
      Logger.i(LOCATION_TAG, "Track Recordr is active, keeping location in the background");
    else if (mInCarDrivingViewController != null && mInCarDrivingViewController.shouldKeepLocationInBackground())
      Logger.i(LOCATION_TAG, "Visible InCar Driving View session is keeping location in the background");
    else
    {
      Logger.i(LOCATION_TAG, "Stopping location in the background");
      getLocationHelper().stop();
    }
  }

  private void initNavigationService()
  {
    NavigationService.createNotificationChannel(this);
    NavigationService.setOrganicMaps(getOrganicMaps());
    NavigationService.setTtsFallbackSoundResource(BuildConfig.IS_IN_CAR ? R.raw.in_car_tts_fallback : 0);

    final int FLAG_IMMUTABLE = Build.VERSION.SDK_INT < Build.VERSION_CODES.M ? 0 : PendingIntent.FLAG_IMMUTABLE;
    final Intent contentIntent = new Intent(this, MwmActivity.class);
    final PendingIntent pendingIntent =
        PendingIntent.getActivity(this, 0, contentIntent, PendingIntent.FLAG_UPDATE_CURRENT | FLAG_IMMUTABLE);
    NavigationService.setOpenAppPendingIntent(pendingIntent);
  }
}