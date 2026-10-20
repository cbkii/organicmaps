package app.organicmaps.maplayer;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MapButtonsControllerPolicyTest
{
  @Test
  public void savedPlacesIsAlwaysSuppressedAndSearchOnlyDuringNavigation()
  {
    for (MapButtonsController.MapButtons button : MapButtonsController.MapButtons.values())
    {
      final boolean duplicate =
          button == MapButtonsController.MapButtons.search || button == MapButtonsController.MapButtons.bookmarks;
      if (duplicate)
        assertTrue(
            MapButtonsController.shouldSuppressLegacyButton(true, MapButtonsController.LayoutMode.navigation, button));
      else
        assertFalse(
            MapButtonsController.shouldSuppressLegacyButton(true, MapButtonsController.LayoutMode.navigation, button));
      assertFalse(
          MapButtonsController.shouldSuppressLegacyButton(false, MapButtonsController.LayoutMode.navigation, button));
      for (MapButtonsController.LayoutMode mode : MapButtonsController.LayoutMode.values())
      {
        final boolean suppressed =
            button == MapButtonsController.MapButtons.bookmarks
            || (mode == MapButtonsController.LayoutMode.navigation && button == MapButtonsController.MapButtons.search);
        org.junit.Assert.assertEquals(suppressed, MapButtonsController.shouldSuppressLegacyButton(true, mode, button));
        assertFalse(MapButtonsController.shouldSuppressLegacyButton(false, mode, button));
      }
      org.junit.Assert.assertEquals(button == MapButtonsController.MapButtons.bookmarks,
                                    MapButtonsController.shouldSuppressLegacyButton(true, null, button));
      assertFalse(MapButtonsController.shouldSuppressLegacyButton(false, null, button));
    }
  }
}
