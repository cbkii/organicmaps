package app.organicmaps.incar;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import app.organicmaps.MwmApplication;

/**
 * Consolidates the overlapping InCar Driving View switches into a single coherent mode with
 * clean migration from the legacy per-key preferences.
 *
 * <p>The three modes are:
 * <ul>
 *   <li>{@link DrivingViewMode#OFF} — Driving View is disabled.</li>
 *   <li>{@link DrivingViewMode#MANUAL} — Driving View is enabled and controlled manually by the
 *       driver (enabled/disabled by pressing the button).</li>
 *   <li>{@link DrivingViewMode#AUTOMATIC} — Driving View activates automatically when the vehicle
 *       is detected to be moving.</li>
 * </ul>
 *
 * <p>Migration logic reads the legacy keys ({@code InCarAutomaticDrivingView},
 * {@code InCarStartDrivingViewOnLaunch}, {@code InCarShowDrivingViewButton}) and derives the
 * best matching mode once, then persists the mode key and removes the legacy keys.
 */
public final class InCarDrivingViewModePolicy
{
  static final String KEY_DRIVING_VIEW_MODE = "InCarDrivingViewMode";

  // Legacy keys that are migrated away from.
  static final String LEGACY_KEY_AUTO_DRIVING_VIEW = "InCarAutomaticDrivingView";
  static final String LEGACY_KEY_START_ON_LAUNCH = "InCarStartDrivingViewOnLaunch";
  static final String LEGACY_KEY_SHOW_BUTTON = "InCarShowDrivingViewButton";

  public enum DrivingViewMode
  {
    OFF,
    MANUAL,
    AUTOMATIC;

    @NonNull
    String preferenceValue()
    {
      return name();
    }

    @NonNull
    static DrivingViewMode fromPreferenceValue(@NonNull String value)
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
   * Reads the current mode, migrating from legacy keys if needed.
   *
   * <p>Migration is idempotent: once the mode key is written the legacy keys are no longer read.
   */
  @NonNull
  public static DrivingViewMode getMode(@NonNull Context context)
  {
    final SharedPreferences prefs = prefs(context);
    if (prefs.contains(KEY_DRIVING_VIEW_MODE))
      return DrivingViewMode.fromPreferenceValue(prefs.getString(KEY_DRIVING_VIEW_MODE, DrivingViewMode.MANUAL.name()));

    // Migrate from legacy keys.
    final DrivingViewMode migrated = migrateFromLegacy(prefs);
    persistMode(prefs, migrated);
    return migrated;
  }

  /** Persists the given mode. */
  public static void setMode(@NonNull Context context, @NonNull DrivingViewMode mode)
  {
    persistMode(prefs(context), mode);
  }

  /**
   * Derives the best matching {@link DrivingViewMode} from the legacy separate boolean keys.
   *
   * <p>Migration table:
   * <ul>
   *   <li>auto=true → {@link DrivingViewMode#AUTOMATIC}</li>
   *   <li>auto=false, showButton=true (default) → {@link DrivingViewMode#MANUAL}</li>
   *   <li>auto=false, showButton=false → {@link DrivingViewMode#OFF}</li>
   * </ul>
   *
   * <p>{@code startOnLaunch} was a specialist toggle that pre-set the driving view when the map
   * opens; it maps to {@link DrivingViewMode#MANUAL} (the button is available but not automatic).
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
  }

  @NonNull
  private static SharedPreferences prefs(@NonNull Context context)
  {
    return MwmApplication.prefs(context);
  }
}
