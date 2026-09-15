package app.organicmaps.incar;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import app.organicmaps.MwmApplication;

/**
 * Settings projection for the existing InCar Driving View runtime state machine.
 *
 * <p>The map-facing camera authority is the normal My Position control. Driving View is now an
 * automatic/off policy only; the legacy MANUAL value remains readable solely so existing installs
 * migrate without losing their intent after the separate Driving View map button was removed.
 */
public final class InCarDrivingViewModePolicy
{
  static final String KEY_DRIVING_VIEW_MODE = "InCarDrivingViewMode";

  // Existing runtime keys retained as the compatibility/storage projection consumed by the controller.
  static final String LEGACY_KEY_AUTO_DRIVING_VIEW = "InCarAutomaticDrivingView";
  static final String LEGACY_KEY_SHOW_BUTTON = "InCarShowDrivingViewButton";

  public enum DrivingViewMode
  {
    OFF,
    MANUAL,
    AUTOMATIC;

    @NonNull
    public String preferenceValue()
    {
      return name();
    }

    @NonNull
    public static DrivingViewMode fromPreferenceValue(@NonNull String value)
    {
      try
      {
        return DrivingViewMode.valueOf(value);
      }
      catch (IllegalArgumentException ignored)
      {
        return AUTOMATIC;
      }
    }
  }

  private InCarDrivingViewModePolicy() {}

  /**
   * Reads the canonical mode, migrating the old manual-button contract to AUTOMATIC. Migration is
   * idempotent and projects only the runtime keys still consumed by the controller.
   */
  @NonNull
  public static DrivingViewMode getMode(@NonNull Context context)
  {
    final SharedPreferences prefs = prefs(context);
    if (prefs.contains(KEY_DRIVING_VIEW_MODE))
    {
      final DrivingViewMode stored =
          DrivingViewMode.fromPreferenceValue(prefs.getString(KEY_DRIVING_VIEW_MODE, DrivingViewMode.AUTOMATIC.name()));
      final DrivingViewMode mode = normalize(stored);
      if (mode != stored)
        persistMode(prefs, mode);
      else
        projectRuntimeKeys(prefs, mode);
      return mode;
    }

    final DrivingViewMode migrated = migrateFromLegacy(prefs);
    persistMode(prefs, migrated);
    return migrated;
  }

  /** Persists the user-facing mode and updates the existing Driving View runtime settings projection. */
  public static void setMode(@NonNull Context context, @NonNull DrivingViewMode mode)
  {
    persistMode(prefs(context), normalize(mode));
  }

  /**
   * Preserves legacy intent after removing the separate map button.
   *
   * <ul>
   *   <li>automatic=true → AUTOMATIC</li>
   *   <li>legacy manual-button enabled → AUTOMATIC</li>
   *   <li>both disabled → OFF</li>
   * </ul>
   */
  @VisibleForTesting
  @NonNull
  static DrivingViewMode migrateFromLegacy(@NonNull SharedPreferences prefs)
  {
    final boolean autoEnabled = prefs.getBoolean(LEGACY_KEY_AUTO_DRIVING_VIEW, false);
    if (autoEnabled)
      return DrivingViewMode.AUTOMATIC;

    final boolean showButton = prefs.getBoolean(LEGACY_KEY_SHOW_BUTTON, true);
    return showButton ? DrivingViewMode.AUTOMATIC : DrivingViewMode.OFF;
  }

  @VisibleForTesting
  @NonNull
  static DrivingViewMode normalize(@NonNull DrivingViewMode mode)
  {
    return mode == DrivingViewMode.MANUAL ? DrivingViewMode.AUTOMATIC : mode;
  }

  private static void persistMode(@NonNull SharedPreferences prefs, @NonNull DrivingViewMode mode)
  {
    prefs.edit().putString(KEY_DRIVING_VIEW_MODE, mode.preferenceValue()).apply();
    projectRuntimeKeys(prefs, mode);
  }

  private static void projectRuntimeKeys(@NonNull SharedPreferences prefs, @NonNull DrivingViewMode mode)
  {
    final boolean automatic = mode != DrivingViewMode.OFF;
    prefs
        .edit()
        // The separate map button no longer exists. Keep the old key false so old code paths cannot
        // resurrect a second camera authority if an install is downgraded/upgraded across this change.
        .putBoolean(LEGACY_KEY_SHOW_BUTTON, false)
        .putBoolean(LEGACY_KEY_AUTO_DRIVING_VIEW, automatic)
        .apply();
  }

  @NonNull
  private static SharedPreferences prefs(@NonNull Context context)
  {
    return MwmApplication.prefs(context);
  }
}
