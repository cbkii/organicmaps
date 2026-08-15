package app.organicmaps.incar;

import androidx.annotation.NonNull;

/** Pure visibility rules for the InCar Quick Destinations strip. */
public final class InCarQuickDestinationsPolicy
{
  public enum FuelChargingMode
  {
    HIDDEN,
    FUEL,
    CHARGING,
    CHOOSER
  }

  private InCarQuickDestinationsPolicy() {}

  public static boolean shouldShowSurface(boolean inCarFlavor, boolean searchOpen, boolean placePageOpen)
  {
    return inCarFlavor && !searchOpen && !placePageOpen;
  }

  public static boolean shouldShow(boolean inCarFlavor, @NonNull InCarQuickDestinationsStore.Action action,
                                   boolean enabled, boolean destinationAvailable)
  {
    if (!inCarFlavor || !enabled)
      return false;

    return switch (action)
    {
      case FUEL_CHARGING, FUEL, CHARGING, PARKING, TOILETS, FOOD -> true;
      case HOME, WORK, RECENT_1, RECENT_2 -> destinationAvailable;
    };
  }

  @NonNull
  public static FuelChargingMode resolveFuelChargingMode(boolean fuelEnabled, boolean chargingEnabled)
  {
    if (fuelEnabled && chargingEnabled)
      return FuelChargingMode.CHOOSER;
    if (fuelEnabled)
      return FuelChargingMode.FUEL;
    if (chargingEnabled)
      return FuelChargingMode.CHARGING;
    return FuelChargingMode.HIDDEN;
  }
}
