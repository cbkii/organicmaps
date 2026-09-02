package app.organicmaps.incar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class InCarStartupCameraStoreTest
{
  @Test
  public void startupMapViewDefaultsAndRoundTripsKnownValues()
  {
    assertEquals(InCarStartupCameraStore.StartupMapView.DRIVING_AREA,
                 InCarStartupCameraStore.StartupMapView.fromPreferenceValue(null));
    assertEquals(InCarStartupCameraStore.StartupMapView.DRIVING_AREA,
                 InCarStartupCameraStore.StartupMapView.fromPreferenceValue("unknown"));
    assertEquals(InCarStartupCameraStore.StartupMapView.LAST_MAP_VIEW,
                 InCarStartupCameraStore.StartupMapView.fromPreferenceValue("LAST_MAP_VIEW"));
  }

  @Test
  public void anchorValidationRejectsInvalidCoordinatesAccuracyAndAge()
  {
    final long now = 2_000_000_000_000L;
    assertTrue(InCarStartupCameraStore.isValidAnchor(-35.28, 149.13, now - 1_000L, 8.0f, now));
    assertFalse(InCarStartupCameraStore.isValidAnchor(91.0, 149.13, now - 1_000L, 8.0f, now));
    assertFalse(InCarStartupCameraStore.isValidAnchor(-35.28, 181.0, now - 1_000L, 8.0f, now));
    assertFalse(InCarStartupCameraStore.isValidAnchor(-35.28, 149.13, now - 1_000L, 0.0f, now));
    assertFalse(InCarStartupCameraStore.isValidAnchor(-35.28, 149.13,
                                                      now - InCarStartupCameraStore.MAX_ANCHOR_AGE_MS - 1L, 8.0f, now));
    assertFalse(InCarStartupCameraStore.isValidAnchor(Double.NaN, 149.13, now - 1_000L, 8.0f, now));
  }

  @Test
  public void persistenceIsImmediateThenThrottled()
  {
    assertTrue(InCarStartupCameraStore.shouldPersist(false, 0L, 100L));
    assertFalse(
        InCarStartupCameraStore.shouldPersist(true, 1_000L, 1_000L + InCarStartupCameraStore.WRITE_INTERVAL_MS - 1L));
    assertTrue(InCarStartupCameraStore.shouldPersist(true, 1_000L, 1_000L + InCarStartupCameraStore.WRITE_INTERVAL_MS));
    assertFalse(InCarStartupCameraStore.shouldPersist(true, 2_000L, 1_999L));
  }
}
