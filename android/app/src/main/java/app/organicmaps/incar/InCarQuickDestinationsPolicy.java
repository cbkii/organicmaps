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
    return shouldShowSurface(inCarFlavor, searchOpen, placePageOpen, false,
                             false, false, false, false);
  }

  /** Browsing chrome follows its host; active navigation retains its own Quick rail. */
  public static boolean shouldShowSurface(boolean inCarFlavor, boolean searchOpen, boolean placePageOpen,
                                          boolean routePlanning, boolean navigation, boolean buttonsHidden,
                                          boolean bottomButtonsHidden, boolean modalOpen)
  {
    if (!inCarFlavor || searchOpen || placePageOpen || modalOpen)
      return false;
    if (navigation)
      return true; // Navigation intentionally hides legacy map buttons.
    return !routePlanning && !buttonsHidden && !bottomButtonsHidden;
  }

  public static boolean shouldShow(boolean inCarFlavor, @NonNull InCarQuickDestinationsStore.Action action,
                                   boolean enabled, boolean destinationAvailable)
  {
    if (!inCarFlavor || !enabled)
      return false;

    return switch (action)
    {
      case FUEL, CHARGING, PARKING, TOILETS, FOOD -> true;
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
