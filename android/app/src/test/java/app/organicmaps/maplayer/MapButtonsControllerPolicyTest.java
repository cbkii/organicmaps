package app.organicmaps.maplayer;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MapButtonsControllerPolicyTest
{
  @Test
  public void onlyLegacySearchAndPlacesAreSuppressedDuringInCarNavigation()
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
      assertFalse(
          MapButtonsController.shouldSuppressLegacyButton(true, MapButtonsController.LayoutMode.planning, button));
      assertFalse(
          MapButtonsController.shouldSuppressLegacyButton(true, MapButtonsController.LayoutMode.regular, button));
    }
  }
}
