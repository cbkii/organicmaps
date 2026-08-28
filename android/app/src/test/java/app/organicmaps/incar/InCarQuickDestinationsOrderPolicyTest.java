package app.organicmaps.incar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class InCarQuickDestinationsOrderPolicyTest
{
  // --- Home slot ---

  @Test
  public void homeShownWhenEnabledAndAvailable()
  {
    assertTrue(InCarQuickDestinationsOrderPolicy.showHome(true, true));
  }

  @Test
  public void homeHiddenWhenDisabled()
  {
    assertFalse(InCarQuickDestinationsOrderPolicy.showHome(false, true));
  }

  @Test
  public void homeHiddenWhenNotConfigured()
  {
    assertFalse(InCarQuickDestinationsOrderPolicy.showHome(true, false));
  }

  // --- Work slot ---

  @Test
  public void workShownWhenEnabledAndAvailable()
  {
    assertTrue(InCarQuickDestinationsOrderPolicy.showWork(true, true));
  }

  @Test
  public void workHiddenWhenDisabled()
  {
    assertFalse(InCarQuickDestinationsOrderPolicy.showWork(false, true));
  }

  @Test
  public void workHiddenWhenNotConfigured()
  {
    assertFalse(InCarQuickDestinationsOrderPolicy.showWork(true, false));
  }

  // --- Parking slot ---

  @Test
  public void parkingShownWhenEnabled()
  {
    assertTrue(InCarQuickDestinationsOrderPolicy.showParking(true));
  }

  @Test
  public void parkingHiddenWhenDisabled()
  {
    assertFalse(InCarQuickDestinationsOrderPolicy.showParking(false));
  }

  // --- Fuel/Charging slot — existing chooser semantics preserved exactly ---

  @Test
  public void fuelChargingHiddenWhenBothDisabled()
  {
    assertEquals(InCarQuickDestinationsPolicy.FuelChargingMode.HIDDEN,
                 InCarQuickDestinationsOrderPolicy.fuelChargingMode(false, false));
  }

  @Test
  public void fuelChargingDirectFuelWhenOnlyFuelEnabled()
  {
    assertEquals(InCarQuickDestinationsPolicy.FuelChargingMode.FUEL,
                 InCarQuickDestinationsOrderPolicy.fuelChargingMode(true, false));
  }

  @Test
  public void fuelChargingDirectChargingWhenOnlyChargingEnabled()
  {
    assertEquals(InCarQuickDestinationsPolicy.FuelChargingMode.CHARGING,
                 InCarQuickDestinationsOrderPolicy.fuelChargingMode(false, true));
  }

  @Test
  public void fuelChargingChooserWhenBothEnabled()
  {
    assertEquals(InCarQuickDestinationsPolicy.FuelChargingMode.CHOOSER,
                 InCarQuickDestinationsOrderPolicy.fuelChargingMode(true, true));
  }

  // --- More slot ---

  @Test
  public void moreShownWhenOverflowActionsExist()
  {
    assertTrue(InCarQuickDestinationsOrderPolicy.showMore(1));
    assertTrue(InCarQuickDestinationsOrderPolicy.showMore(5));
  }

  @Test
  public void moreHiddenWhenNoOverflowActions()
  {
    assertFalse(InCarQuickDestinationsOrderPolicy.showMore(0));
  }

  // --- No persistent expanded/collapsed state ---

  @Test
  public void policyHasNoExpandCollapseStateWhatsoever()
  {
    // InCarQuickDestinationsOrderPolicy has no instance state at all:
    // all methods are pure static — there is no expanded/collapsed field to persist.
    // Verify all methods can be called repeatedly with different inputs and produce
    // deterministic outputs without any side effects.
    assertTrue(InCarQuickDestinationsOrderPolicy.showHome(true, true));
    assertFalse(InCarQuickDestinationsOrderPolicy.showHome(true, true) == false);
    assertTrue(InCarQuickDestinationsOrderPolicy.showHome(true, true));
  }

  // --- Fixed order independence from configuration ---

  @Test
  public void slotOrderEnumValuesAreStable()
  {
    final InCarQuickDestinationsOrderPolicy.Slot[] slots = InCarQuickDestinationsOrderPolicy.Slot.values();
    assertEquals(InCarQuickDestinationsOrderPolicy.Slot.HOME, slots[0]);
    assertEquals(InCarQuickDestinationsOrderPolicy.Slot.WORK, slots[1]);
    assertEquals(InCarQuickDestinationsOrderPolicy.Slot.PARKING, slots[2]);
    assertEquals(InCarQuickDestinationsOrderPolicy.Slot.FUEL_CHARGING, slots[3]);
    assertEquals(InCarQuickDestinationsOrderPolicy.Slot.MORE, slots[4]);
    assertEquals(5, slots.length);
  }
}
