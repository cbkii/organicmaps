package app.organicmaps.incar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class InCarBackPolicyTest
{
  // --- Per-surface action mapping ---

  @Test
  public void moreOpenClosesMore()
  {
    assertEquals(InCarBackPolicy.BackAction.CLOSE_MORE,
                 InCarBackPolicy.actionFor(InCarBackPolicy.ActiveSurface.MORE));
  }

  @Test
  public void placeDetailsOpenReturnsToPlaceCard()
  {
    assertEquals(InCarBackPolicy.BackAction.RETURN_TO_PLACE_CARD,
                 InCarBackPolicy.actionFor(InCarBackPolicy.ActiveSurface.PLACE_DETAILS));
  }

  @Test
  public void searchOpenClosesSearch()
  {
    assertEquals(InCarBackPolicy.BackAction.CLOSE_SEARCH,
                 InCarBackPolicy.actionFor(InCarBackPolicy.ActiveSurface.SEARCH));
  }

  @Test
  public void routeEditOpenReturnsToRoutePreview()
  {
    assertEquals(InCarBackPolicy.BackAction.RETURN_TO_ROUTE_PREVIEW,
                 InCarBackPolicy.actionFor(InCarBackPolicy.ActiveSurface.ROUTE_EDIT));
  }

  @Test
  public void cleanNavigationBackIsDoNothing()
  {
    assertEquals(InCarBackPolicy.BackAction.DO_NOTHING,
                 InCarBackPolicy.actionFor(InCarBackPolicy.ActiveSurface.CLEAN_NAVIGATION));
  }

  @Test
  public void mapBackIsDefaultBack()
  {
    assertEquals(InCarBackPolicy.BackAction.DEFAULT_BACK,
                 InCarBackPolicy.actionFor(InCarBackPolicy.ActiveSurface.MAP));
  }

  // --- Navigation cancellation guard ---

  @Test
  public void backBlockedDuringCleanNavigation()
  {
    assertTrue(InCarBackPolicy.shouldBlockBackFromCancellingNavigation(true, false));
  }

  @Test
  public void backNotBlockedWhenTransientSurfaceIsVisible()
  {
    assertFalse(InCarBackPolicy.shouldBlockBackFromCancellingNavigation(true, true));
  }

  @Test
  public void backNotBlockedWhenNotNavigating()
  {
    assertFalse(InCarBackPolicy.shouldBlockBackFromCancellingNavigation(false, false));
    assertFalse(InCarBackPolicy.shouldBlockBackFromCancellingNavigation(false, true));
  }

  // --- All surfaces covered exhaustively ---

  @Test
  public void allSurfacesHaveAMapping()
  {
    for (final InCarBackPolicy.ActiveSurface surface : InCarBackPolicy.ActiveSurface.values())
    {
      final InCarBackPolicy.BackAction action = InCarBackPolicy.actionFor(surface);
      assertTrue("Every surface must have a non-null BackAction", action != null);
    }
  }
}
