package app.organicmaps.incar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class InCarQuickDestinationsPolicyTest
{
  @Test
  public void normalFlavoursNeverExposeQuickDestinations()
  {
    assertFalse(InCarQuickDestinationsPolicy.shouldShowSurface(false, false, false));
    for (InCarQuickDestinationsStore.Action action : InCarQuickDestinationsStore.Action.values())
      assertFalse(InCarQuickDestinationsPolicy.shouldShow(false, action, true, true));
  }

  @Test
  public void mapSurfaceKeepsPrimaryControlAvailable()
  {
    assertTrue(InCarQuickDestinationsPolicy.shouldShowSurface(true, false, false));
    assertFalse(InCarQuickDestinationsPolicy.shouldShowSurface(true, true, false));
    assertFalse(InCarQuickDestinationsPolicy.shouldShowSurface(true, false, true));
  }

  @Test
  public void fixedActionsRespectUserEnableState()
  {
    final InCarQuickDestinationsStore.Action[] fixed = {
        InCarQuickDestinationsStore.Action.FUEL_CHARGING, InCarQuickDestinationsStore.Action.FUEL,
        InCarQuickDestinationsStore.Action.CHARGING, InCarQuickDestinationsStore.Action.PARKING,
        InCarQuickDestinationsStore.Action.TOILETS, InCarQuickDestinationsStore.Action.FOOD};
    for (InCarQuickDestinationsStore.Action action : fixed)
    {
      assertTrue(InCarQuickDestinationsPolicy.shouldShow(true, action, true, false));
      assertFalse(InCarQuickDestinationsPolicy.shouldShow(true, action, false, true));
    }
  }

  @Test
  public void fuelChargingModeUsesDirectActionUnlessBothAreEnabled()
  {
    assertEquals(InCarQuickDestinationsPolicy.FuelChargingMode.HIDDEN,
                 InCarQuickDestinationsPolicy.resolveFuelChargingMode(false, false));
    assertEquals(InCarQuickDestinationsPolicy.FuelChargingMode.FUEL,
                 InCarQuickDestinationsPolicy.resolveFuelChargingMode(true, false));
    assertEquals(InCarQuickDestinationsPolicy.FuelChargingMode.CHARGING,
                 InCarQuickDestinationsPolicy.resolveFuelChargingMode(false, true));
    assertEquals(InCarQuickDestinationsPolicy.FuelChargingMode.CHOOSER,
                 InCarQuickDestinationsPolicy.resolveFuelChargingMode(true, true));
  }

  @Test
  public void savedAndRecentActionsRequireAvailableDestination()
  {
    final InCarQuickDestinationsStore.Action[] destinations = {
        InCarQuickDestinationsStore.Action.HOME, InCarQuickDestinationsStore.Action.WORK,
        InCarQuickDestinationsStore.Action.RECENT_1, InCarQuickDestinationsStore.Action.RECENT_2};
    for (InCarQuickDestinationsStore.Action action : destinations)
    {
      assertFalse(InCarQuickDestinationsPolicy.shouldShow(true, action, true, false));
      assertTrue(InCarQuickDestinationsPolicy.shouldShow(true, action, true, true));
      assertFalse(InCarQuickDestinationsPolicy.shouldShow(true, action, false, true));
    }
  }
}
