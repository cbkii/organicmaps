package app.organicmaps.incar;

import androidx.annotation.NonNull;

/** Pure visibility rules for the InCar Quick Destinations strip. */
public final class InCarQuickDestinationsPolicy
{
  private InCarQuickDestinationsPolicy() {}

  public static boolean shouldShow(boolean inCarFlavor, @NonNull InCarQuickDestinationsStore.Action action,
                                   boolean enabled, boolean destinationAvailable)
  {
    if (!inCarFlavor || !enabled)
      return false;

    return switch (action)
    {
      case FUEL_CHARGING, PARKING, TOILETS, FOOD -> true;
      case HOME, WORK, RECENT_1, RECENT_2 -> destinationAvailable;
    };
  }
}
