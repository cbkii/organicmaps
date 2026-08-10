package app.organicmaps.sdk;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MapSurfaceResizePolicyTest
{
  @Test
  public void ignoresChangesBeforeSurfaceCreation()
  {
    assertFalse(Map.shouldApplySurfaceChange(false, false, false, 1280, 720, 0, 0));
  }

  @Test
  public void ignoresInvalidSurfaceBounds()
  {
    assertFalse(Map.shouldApplySurfaceChange(true, true, false, 0, 720, 735, 387));
    assertFalse(Map.shouldApplySurfaceChange(true, true, false, 1280, 0, 735, 387));
  }

  @Test
  public void appliesRequiredReattachResize()
  {
    assertTrue(Map.shouldApplySurfaceChange(true, true, true, 1280, 720, 735, 387));
  }

  @Test
  public void ignoresRedundantCreatingCallback()
  {
    assertFalse(Map.shouldApplySurfaceChange(true, false, true, 735, 387, 735, 387));
  }

  @Test
  public void appliesChangedCreatingCallback()
  {
    assertTrue(Map.shouldApplySurfaceChange(true, false, true, 1280, 720, 735, 387));
  }

  @Test
  public void preservesOrdinarySurfaceChangedBehaviour()
  {
    assertTrue(Map.shouldApplySurfaceChange(true, false, false, 735, 387, 735, 387));
  }
}
