package app.organicmaps.widget.placepage;

import static org.junit.Assert.*;

import java.util.HashSet;
import java.util.List;
import org.junit.Test;

public class PlacePageActionProjectionTest
{
  @Test
  public void primaryReservedAndEverySecondaryRemainsAvailable()
  {
    var actions = List.of(PlacePageButtons.ButtonType.BACK, PlacePageButtons.ButtonType.BOOKMARK_SAVE,
                          PlacePageButtons.ButtonType.ROUTE_FROM, PlacePageButtons.ButtonType.ROUTE_TO,
                          PlacePageButtons.ButtonType.WALK_TO);
    for (int max : new int[] {1, 2, 3, 4, 5})
    {
      var p = PlacePageActionProjection.project(actions, max, true);
      assertEquals(PlacePageButtons.ButtonType.ROUTE_TO, p.direct().get(0));
      var all = new HashSet<>(p.direct());
      all.remove(PlacePageButtons.ButtonType.MORE);
      all.addAll(p.overflow());
      assertEquals(new HashSet<>(actions), all);
      assertFalse(p.overflow().contains(PlacePageButtons.ButtonType.ROUTE_TO));
      assertTrue(p.direct().size() <= Math.max(2, max));
      if (!p.overflow().isEmpty())
        assertEquals(PlacePageButtons.ButtonType.MORE, p.direct().get(p.direct().size() - 1));
      else
        assertFalse(p.direct().contains(PlacePageButtons.ButtonType.MORE));
    }
  }
  @Test
  public void measuredWidthReservesPrimaryAndOverflowWithoutClipping()
  {
    assertEquals(4, PlacePageActionProjection.slotsForWidth(496, 124, 4));
    assertEquals(3, PlacePageActionProjection.slotsForWidth(495, 124, 4));
    assertEquals(2, PlacePageActionProjection.slotsForWidth(248, 124, 4));
    assertEquals(2, PlacePageActionProjection.slotsForWidth(128, 124, 4));
    for (int available : new int[] {110, 128, 200, 248, 372, 495, 496})
    {
      int slots = PlacePageActionProjection.slotsForWidth(available, 124, 4);
      int gap = Math.min(8, Math.max(0, (available / slots - 55) / 2));
      int width = PlacePageActionProjection.cellWidth(available, slots, 108, gap);
      assertTrue(width >= 55);
      assertTrue(slots * (width + 2 * gap) <= available);
    }
  }

  @Test
  public void ordinaryFlavourRetainsOriginalOrder()
  {
    var actions = List.of(PlacePageButtons.ButtonType.BACK, PlacePageButtons.ButtonType.ROUTE_TO);
    assertEquals(actions, PlacePageActionProjection.project(actions, 3, false).direct());
  }
}
