package app.organicmaps.incar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class InCarQuickDestinationsStoreTest
{
  private static final InCarQuickDestination A = new InCarQuickDestination("A", "", -35.0, 149.0);
  private static final InCarQuickDestination B = new InCarQuickDestination("B", "", -35.1, 149.1);
  private static final InCarQuickDestination C = new InCarQuickDestination("C", "", -35.2, 149.2);

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
}
