package app.organicmaps.incar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import app.organicmaps.sdk.bookmarks.data.MapObject;
import org.junit.Test;

public class InCarQuickDestinationTest
{
  @Test
  public void codecSurvivesRestartStyleRoundTrip()
  {
    final InCarQuickDestination original = new InCarQuickDestination("Home: North", "Canberra ACT", -35.28, 149.13);
    assertTrue(InCarQuickDestination.codecRoundTrips(original));

    final InCarQuickDestination restored = InCarQuickDestination.decode(original.encode());
    assertNotNull(restored);
    assertEquals("Home: North", restored.getTitle());
    assertEquals("Canberra ACT", restored.getSubtitle());
    assertEquals(-35.28, restored.getLat(), 0.0);
    assertEquals(149.13, restored.getLon(), 0.0);
  }

  @Test
  public void invalidStoredCoordinatesAreRejected()
  {
    assertFalse(new InCarQuickDestination("bad", "", 91.0, 149.0).isValid());
    assertFalse(new InCarQuickDestination("bad", "", -35.0, 181.0).isValid());
    assertNull(InCarQuickDestination.decode("3:bad0:91.0:149.0"));
  }

  @Test
  public void routeFlowMapObjectPreservesDestination()
  {
    final InCarQuickDestination destination = new InCarQuickDestination("Work", "Civic", -35.279, 149.13);
    final MapObject mapObject = destination.toMapObject();
    assertEquals("Work", mapObject.getTitle());
    assertEquals("Civic", mapObject.getSubtitle());
    assertEquals(-35.279, mapObject.getLat(), 0.0);
    assertEquals(149.13, mapObject.getLon(), 0.0);
  }
}
