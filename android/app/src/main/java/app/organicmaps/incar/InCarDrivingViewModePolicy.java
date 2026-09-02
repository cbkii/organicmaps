package app.organicmaps.incar;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import app.organicmaps.MwmApplication;

/**
 * Settings projection for the existing InCar Driving View runtime state machine.
 *
 * <p>The runtime authority remains {@link InCarDrivingViewController}/{@link InCarDrivingViewPolicy}.
 * This class only collapses the legacy Driving View settings booleans into one user-facing mode and projects that
 * mode back onto the legacy keys still consumed by that runtime. Normal-launch location following is deliberately
 * separate and remains owned by AutoStartLocationFollowAndRotate.
 */
public final class InCarDrivingViewModePolicy
{
  static final String KEY_DRIVING_VIEW_MODE = "InCarDrivingViewMode";

  // Existing Driving View runtime keys retained as the compatibility/storage projection used by the controller.
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
        return MANUAL;
      }
    }
  }

  private InCarDrivingViewModePolicy() {}

  /**
   * Reads the canonical mode, migrating once from the existing Driving View booleans when needed. Migration is
   * idempotent and keeps only the keys that still describe Driving View itself.
   */
  @NonNull
  public static DrivingViewMode getMode(@NonNull Context context)
  {
    final SharedPreferences prefs = prefs(context);
    if (prefs.contains(KEY_DRIVING_VIEW_MODE))
    {
      final DrivingViewMode mode =
          DrivingViewMode.fromPreferenceValue(prefs.getString(KEY_DRIVING_VIEW_MODE, DrivingViewMode.MANUAL.name()));
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
    persistMode(prefs(context), mode);
  }

  /**
   * Derives the closest canonical mode from the legacy Driving View settings.
   *
   * <ul>
   *   <li>automatic=true → AUTOMATIC</li>
   *   <li>automatic=false and show-button=true → MANUAL</li>
   *   <li>automatic=false and show-button=false → OFF</li>
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
    return showButton ? DrivingViewMode.MANUAL : DrivingViewMode.OFF;
  }

  private static void persistMode(@NonNull SharedPreferences prefs, @NonNull DrivingViewMode mode)
  {
    prefs.edit().putString(KEY_DRIVING_VIEW_MODE, mode.preferenceValue()).apply();
    projectRuntimeKeys(prefs, mode);
  }

  private static void projectRuntimeKeys(@NonNull SharedPreferences prefs, @NonNull DrivingViewMode mode)
  {
    final boolean enabled = mode != DrivingViewMode.OFF;
    final boolean automatic = mode == DrivingViewMode.AUTOMATIC;
    prefs.edit()
        .putBoolean(LEGACY_KEY_SHOW_BUTTON, enabled)
        .putBoolean(LEGACY_KEY_AUTO_DRIVING_VIEW, automatic)
        .apply();
  }

  @NonNull
  private static SharedPreferences prefs(@NonNull Context context)
  {
    return MwmApplication.prefs(context);
  }
}
