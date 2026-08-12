package app.organicmaps.incar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class InCarQuickDestinationsStoreTest
{
  private static final InCarQuickDestination A = new InCarQuickDestination("A", "", -35.0, 149.0);
  private static final InCarQuickDestination B = new InCarQuickDestination("B", "", -35.1, 149.1);
  private static final InCarQuickDestination C = new InCarQuickDestination("C", "", -35.2, 149.2);

  @Test
  public void actionOrderAndDefaultsMatchInCarPolicy()
  {
    assertEquals(InCarQuickDestinationsStore.Action.FUEL_CHARGING, InCarQuickDestinationsStore.Action.values()[0]);
    assertEquals(InCarQuickDestinationsStore.Action.PARKING, InCarQuickDestinationsStore.Action.values()[1]);
    assertEquals(InCarQuickDestinationsStore.Action.TOILETS, InCarQuickDestinationsStore.Action.values()[2]);
    assertEquals(InCarQuickDestinationsStore.Action.FOOD, InCarQuickDestinationsStore.Action.values()[3]);
    assertEquals(InCarQuickDestinationsStore.Action.REST_WATER, InCarQuickDestinationsStore.Action.values()[4]);
    assertEquals(InCarQuickDestinationsStore.Action.HOME, InCarQuickDestinationsStore.Action.values()[5]);
    assertEquals(InCarQuickDestinationsStore.Action.WORK, InCarQuickDestinationsStore.Action.values()[6]);
    assertEquals(InCarQuickDestinationsStore.Action.RECENT_1, InCarQuickDestinationsStore.Action.values()[7]);
    assertEquals(InCarQuickDestinationsStore.Action.RECENT_2, InCarQuickDestinationsStore.Action.values()[8]);

    assertTrue(InCarQuickDestinationsStore.defaultEnabled(InCarQuickDestinationsStore.Action.FUEL_CHARGING));
    assertTrue(InCarQuickDestinationsStore.defaultEnabled(InCarQuickDestinationsStore.Action.PARKING));
    assertTrue(InCarQuickDestinationsStore.defaultEnabled(InCarQuickDestinationsStore.Action.TOILETS));
    assertFalse(InCarQuickDestinationsStore.defaultEnabled(InCarQuickDestinationsStore.Action.FOOD));
    assertFalse(InCarQuickDestinationsStore.defaultEnabled(InCarQuickDestinationsStore.Action.REST_WATER));
    assertTrue(InCarQuickDestinationsStore.defaultEnabled(InCarQuickDestinationsStore.Action.HOME));
    assertTrue(InCarQuickDestinationsStore.defaultEnabled(InCarQuickDestinationsStore.Action.WORK));
    assertTrue(InCarQuickDestinationsStore.defaultEnabled(InCarQuickDestinationsStore.Action.RECENT_1));
    assertTrue(InCarQuickDestinationsStore.defaultEnabled(InCarQuickDestinationsStore.Action.RECENT_2));
  }

  @Test
  public void absentPreferenceUsesDynamicDefaultAndExplicitOffWins()
  {
    assertTrue(InCarQuickDestinationsStore.resolvedEnabled(false, false, true));
    assertFalse(InCarQuickDestinationsStore.resolvedEnabled(true, false, true));
    assertTrue(InCarQuickDestinationsStore.resolvedEnabled(true, true, false));
  }

  @Test
  public void newestTwoDestinationsAreSelected()
  {
    final InCarQuickDestinationsStore.RecentPair first = InCarQuickDestinationsStore.selectNewestTwo(A, null, null);
    assertEquals(A, first.first);
    assertNull(first.second);

    final InCarQuickDestinationsStore.RecentPair second =
        InCarQuickDestinationsStore.selectNewestTwo(B, first.first, first.second);
    assertEquals(B, second.first);
    assertEquals(A, second.second);

    final InCarQuickDestinationsStore.RecentPair third =
        InCarQuickDestinationsStore.selectNewestTwo(C, second.first, second.second);
    assertEquals(C, third.first);
    assertEquals(B, third.second);
  }

  @Test
  public void duplicateRecentIsPromotedWithoutDuplicatingSlots()
  {
    final InCarQuickDestinationsStore.RecentPair pair = InCarQuickDestinationsStore.selectNewestTwo(A, B, A);
    assertEquals(A, pair.first);
    assertEquals(B, pair.second);
  }

  @Test
  public void invalidCandidateDoesNotDisplaceValidHistory()
  {
    final InCarQuickDestination invalid = new InCarQuickDestination("bad", "", 100.0, 149.0);
    final InCarQuickDestinationsStore.RecentPair pair = InCarQuickDestinationsStore.selectNewestTwo(invalid, A, B);
    assertEquals(A, pair.first);
    assertEquals(B, pair.second);
  }

  @Test
  public void destinationStorageRoundTripsAndNullMeansClear()
  {
    final String encoded = InCarQuickDestinationsStore.encodeDestination(A);
    assertNotNull(encoded);
    final InCarQuickDestination restored = InCarQuickDestinationsStore.decodeDestination(encoded);
    assertNotNull(restored);
    assertEquals(A.getTitle(), restored.getTitle());
    assertEquals(A.getLat(), restored.getLat(), 0.0);
    assertEquals(A.getLon(), restored.getLon(), 0.0);

    assertNull(InCarQuickDestinationsStore.encodeDestination(null));
    assertNull(InCarQuickDestinationsStore.decodeDestination(null));
  }
}
