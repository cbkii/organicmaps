package app.organicmaps.widget.placepage;

import static org.junit.Assert.*;
import java.util.List;
import java.util.HashSet;
import org.junit.Test;

public class PlacePageActionProjectionTest
{
  @Test public void primaryReservedAndEverySecondaryRemainsAvailable()
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
    }
  }
  @Test public void ordinaryFlavourRetainsOriginalOrder()
  {
    var actions = List.of(PlacePageButtons.ButtonType.BACK, PlacePageButtons.ButtonType.ROUTE_TO);
    assertEquals(actions, PlacePageActionProjection.project(actions, 3, false).direct());
  }
}
