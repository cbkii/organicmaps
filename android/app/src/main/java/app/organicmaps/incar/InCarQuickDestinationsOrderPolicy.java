package app.organicmaps.incar;

import androidx.annotation.NonNull;

/**
 * Pure-logic order policy for the InCar Quick Destinations fixed compact control group.
 *
 * <p>The canonical slot order is always:
 * <ol>
 *   <li>Home (if configured)</li>
 *   <li>Work (if configured)</li>
 *   <li>Parking (if enabled)</li>
 *   <li>Fuel / Charging (if either or both are enabled)</li>
 *   <li>More (for overflow actions when present)</li>
 * </ol>
 *
 * <p>There is no persistent expanded/collapsed state — the group is always shown in this stable
 * order. Actions that are not available or disabled are simply absent from the rendered list;
 * the remaining actions do not reorder.
 */
public final class InCarQuickDestinationsOrderPolicy
{
  /** The canonical slot identifiers in priority order. */
  public enum Slot
  {
    HOME,
    WORK,
    PARKING,
    FUEL_CHARGING,
    MORE
  }

  private InCarQuickDestinationsOrderPolicy() {}

  /**
   * Returns {@code true} if the Home slot should be shown.
   *
   * @param homeEnabled whether the Home action is enabled in settings.
   * @param homeAvailable whether a Home destination has been configured.
   */
  public static boolean showHome(boolean homeEnabled, boolean homeAvailable)
  {
    return homeEnabled && homeAvailable;
  }

  /**
   * Returns {@code true} if the Work slot should be shown.
   *
   * @param workEnabled whether the Work action is enabled in settings.
   * @param workAvailable whether a Work destination has been configured.
   */
  public static boolean showWork(boolean workEnabled, boolean workAvailable)
  {
    return workEnabled && workAvailable;
  }

  /**
   * Returns {@code true} if the Parking slot should be shown.
   *
   * @param parkingEnabled whether the Parking action is enabled in settings.
   */
  public static boolean showParking(boolean parkingEnabled)
  {
    return parkingEnabled;
  }

  /**
   * Returns the Fuel/Charging mode for the combined slot.
   *
   * <p>The slot is hidden only when both are disabled. Otherwise the existing
   * {@link InCarQuickDestinationsPolicy#resolveFuelChargingMode(boolean, boolean)} semantics
   * are preserved exactly.
   *
   * @param fuelEnabled   whether the Fuel action is enabled in settings.
   * @param chargingEnabled whether the Charging action is enabled in settings.
   */
  @NonNull
  public static InCarQuickDestinationsPolicy.FuelChargingMode fuelChargingMode(boolean fuelEnabled,
                                                                               boolean chargingEnabled)
  {
    return InCarQuickDestinationsPolicy.resolveFuelChargingMode(fuelEnabled, chargingEnabled);
  }

  /**
   * Returns {@code true} if the More slot (overflow) should be shown.
   *
   * @param overflowCount the number of configured overflow actions available.
   */
  public static boolean showMore(int overflowCount)
  {
    return overflowCount > 0;
  }
}
