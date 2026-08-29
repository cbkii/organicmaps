package app.organicmaps.incar;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.NonNull;
import app.organicmaps.MwmApplication;

/** Application-layer storage for preferences that only exist in the direct-display InCar flavour. */
public final class InCarSettingsStore
{
  private static final String KEY_SHOW_DRIVING_VIEW_BUTTON = "InCarShowDrivingViewButton";
  private static final String KEY_START_DRIVING_VIEW_ON_LAUNCH = "InCarStartDrivingViewOnLaunch";
  private static final String KEY_AUTO_DRIVING_VIEW = "InCarAutomaticDrivingView";
  private static final String KEY_AUTO_RETURN_DRIVING_VIEW = "InCarAutoReturnDrivingView";
  private static final String KEY_BUDGET_RENDERING = "InCarBudgetRendering";
  private static final String KEY_MAP_AGE_WARNING = "InCarMapAgeWarning";
  private static final String KEY_DRIVING_VIEW_SESSION_ENABLED = "InCarDrivingViewSessionEnabled";
  private static final String KEY_DRIVING_VIEW_SESSION_SOURCE = "InCarDrivingViewSessionSource";
  private static final String KEY_BUDGET_SAVED_3D_BUILDINGS = "InCarBudgetSaved3dBuildings";
  private static final String KEY_BUDGET_HAS_SAVED_3D_BUILDINGS = "InCarBudgetHasSaved3dBuildings";
  private static final String KEY_WALKING_SESSION_ACTIVE = "InCarWalkingSessionActive";

  private InCarSettingsStore() {}

  public static boolean showDrivingViewButton(@NonNull Context context)
  {
    return prefs(context).getBoolean(KEY_SHOW_DRIVING_VIEW_BUTTON, true);
  }

  public static void setShowDrivingViewButton(@NonNull Context context, boolean enabled)
  {
    prefs(context).edit().putBoolean(KEY_SHOW_DRIVING_VIEW_BUTTON, enabled).apply();
  }

  public static boolean startDrivingViewOnLaunch(@NonNull Context context)
  {
    return prefs(context).getBoolean(KEY_START_DRIVING_VIEW_ON_LAUNCH, false);
  }

  public static void setStartDrivingViewOnLaunch(@NonNull Context context, boolean enabled)
  {
    prefs(context).edit().putBoolean(KEY_START_DRIVING_VIEW_ON_LAUNCH, enabled).apply();
  }

  public static boolean automaticDrivingViewEnabled(@NonNull Context context)
  {
    return prefs(context).getBoolean(KEY_AUTO_DRIVING_VIEW, false);
  }

  public static void setAutomaticDrivingViewEnabled(@NonNull Context context, boolean enabled)
  {
    prefs(context).edit().putBoolean(KEY_AUTO_DRIVING_VIEW, enabled).apply();
  }

  public static boolean autoReturnDrivingViewEnabled(@NonNull Context context)
  {
    return prefs(context).getBoolean(KEY_AUTO_RETURN_DRIVING_VIEW, true);
  }

  public static void setAutoReturnDrivingViewEnabled(@NonNull Context context, boolean enabled)
  {
    prefs(context).edit().putBoolean(KEY_AUTO_RETURN_DRIVING_VIEW, enabled).apply();
  }

  public static boolean budgetRenderingEnabled(@NonNull Context context)
  {
    return prefs(context).getBoolean(KEY_BUDGET_RENDERING, false);
  }

  public static void setBudgetRenderingEnabled(@NonNull Context context, boolean enabled)
  {
    prefs(context).edit().putBoolean(KEY_BUDGET_RENDERING, enabled).apply();
  }

  public static boolean mapAgeWarningEnabled(@NonNull Context context)
  {
    return prefs(context).getBoolean(KEY_MAP_AGE_WARNING, true);
  }

  public static void setMapAgeWarningEnabled(@NonNull Context context, boolean enabled)
  {
    prefs(context).edit().putBoolean(KEY_MAP_AGE_WARNING, enabled).apply();
  }

  public static boolean restoredDrivingViewEnabled(@NonNull Context context)
  {
    return prefs(context).getBoolean(KEY_DRIVING_VIEW_SESSION_ENABLED, false);
  }

  @NonNull
  public static InCarDrivingViewPolicy.ActivationSource restoredDrivingViewSource(@NonNull Context context)
  {
    final String stored = prefs(context).getString(KEY_DRIVING_VIEW_SESSION_SOURCE, null);
    if (stored == null)
      return InCarDrivingViewPolicy.ActivationSource.RESTORED;
    try
    {
      final InCarDrivingViewPolicy.ActivationSource source = InCarDrivingViewPolicy.ActivationSource.valueOf(stored);
      return source == InCarDrivingViewPolicy.ActivationSource.OFF ? InCarDrivingViewPolicy.ActivationSource.RESTORED
                                                                   : source;
    }
    catch (IllegalArgumentException ignored)
    {
      return InCarDrivingViewPolicy.ActivationSource.RESTORED;
    }
  }

  public static void persistDrivingViewSession(@NonNull Context context, boolean enabled,
                                               @NonNull InCarDrivingViewPolicy.ActivationSource source)
  {
    prefs(context)
        .edit()
        .putBoolean(KEY_DRIVING_VIEW_SESSION_ENABLED, enabled)
        .putString(KEY_DRIVING_VIEW_SESSION_SOURCE,
                   enabled ? source.name() : InCarDrivingViewPolicy.ActivationSource.OFF.name())
        .apply();
  }

  public static void saveBudget3dBuildings(@NonNull Context context, boolean enabled)
  {
    prefs(context)
        .edit()
        .putBoolean(KEY_BUDGET_SAVED_3D_BUILDINGS, enabled)
        .putBoolean(KEY_BUDGET_HAS_SAVED_3D_BUILDINGS, true)
        .apply();
  }

  public static boolean hasSavedBudget3dBuildings(@NonNull Context context)
  {
    return prefs(context).getBoolean(KEY_BUDGET_HAS_SAVED_3D_BUILDINGS, false);
  }

  public static boolean getSavedBudget3dBuildings(@NonNull Context context)
  {
    return prefs(context).getBoolean(KEY_BUDGET_SAVED_3D_BUILDINGS, true);
  }

  public static void clearSavedBudget3dBuildings(@NonNull Context context)
  {
    prefs(context).edit().remove(KEY_BUDGET_SAVED_3D_BUILDINGS).remove(KEY_BUDGET_HAS_SAVED_3D_BUILDINGS).apply();
  }

  public static boolean isWalkingSessionActive(@NonNull Context context)
  {
    return prefs(context).getBoolean(KEY_WALKING_SESSION_ACTIVE, false);
  }

  public static void setWalkingSessionActive(@NonNull Context context, boolean active)
  {
    prefs(context).edit().putBoolean(KEY_WALKING_SESSION_ACTIVE, active).apply();
  }

  private static final String KEY_SHOW_TRACK_RECORDING_BUTTON = "InCarShowTrackRecordingButton";

  public static boolean isShowTrackRecordingButton(@NonNull Context context)
  {
    return prefs(context).getBoolean(KEY_SHOW_TRACK_RECORDING_BUTTON, true);
  }

  public static void setShowTrackRecordingButton(@NonNull Context context, boolean show)
  {
    prefs(context).edit().putBoolean(KEY_SHOW_TRACK_RECORDING_BUTTON, show).apply();
  }

  public static String showTrackRecordingButtonPreferenceKey()
  {
    return KEY_SHOW_TRACK_RECORDING_BUTTON;
  }

  @NonNull
  private static SharedPreferences prefs(@NonNull Context context)
  {
    return MwmApplication.prefs(context);
  }
}
