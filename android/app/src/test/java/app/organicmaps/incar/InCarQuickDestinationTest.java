package app.organicmaps.incar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.location.Location;
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
  public void decodeReturnsNullForMalformedStrings()
  {
    assertNull(InCarQuickDestination.decode(null));
    assertNull(InCarQuickDestination.decode(""));
    assertNull(InCarQuickDestination.decode("JustSomeTextWithNoSeparators"));
    assertNull(InCarQuickDestination.decode("4:Home"));
    assertNull(InCarQuickDestination.decode("4:Home0"));
    assertNull(InCarQuickDestination.decode("-1:Home0:-35.28:149.13"));
    assertNull(InCarQuickDestination.decode("99999999999999999999:Home0:-35.28:149.13"));
    assertNull(InCarQuickDestination.decode("4:Home999:North:-35.28:149.13"));
    assertNull(InCarQuickDestination.decode("4:Home0:notALat:149.13"));
    assertNull(InCarQuickDestination.decode("4:Home0:-35.28:notANumber"));
  }

  @Test
  public void invalidStoredCoordinatesAreRejected()
  {
    assertFalse(new InCarQuickDestination("bad", "", 91.0, 149.0).isValid());
    assertFalse(new InCarQuickDestination("bad", "", -35.0, 181.0).isValid());
    assertNull(InCarQuickDestination.decode("3:bad0:91.0:149.0"));
  }

  @Test
  public void samePlaceUsesOneEMinusSixTolerance()
  {
    final InCarQuickDestination base = new InCarQuickDestination("Base", "", -35.280000, 149.130000);
    final InCarQuickDestination withinLatitude = new InCarQuickDestination("Near", "", -35.2800005, 149.130000);
    final InCarQuickDestination withinLongitude = new InCarQuickDestination("Near", "", -35.280000, 149.1300005);
    final InCarQuickDestination outsideLatitude = new InCarQuickDestination("Far", "", -35.280002, 149.130000);
    final InCarQuickDestination outsideLongitude = new InCarQuickDestination("Far", "", -35.280000, 149.130002);

    assertTrue(base.samePlace(withinLatitude));
    assertTrue(base.samePlace(withinLongitude));
    assertFalse(base.samePlace(outsideLatitude));
    assertFalse(base.samePlace(outsideLongitude));
    assertFalse(base.samePlace(null));
  }

  @Test
  public void fromMapObjectRejectsNullMyPositionAndInvalidCoordinates()
  {
    assertNull(InCarQuickDestination.fromMapObject(null));

    final MapObject myPosition =
        MapObject.createMapObject(MapObject.MY_POSITION, "Current position", "", -35.28, 149.13);
    assertNull(InCarQuickDestination.fromMapObject(myPosition));

    final MapObject invalidLatitude = MapObject.createMapObject(MapObject.SEARCH, "Bad", "", 91.0, 149.13);
    assertNull(InCarQuickDestination.fromMapObject(invalidLatitude));

    final MapObject invalidLongitude = MapObject.createMapObject(MapObject.SEARCH, "Bad", "", -35.28, 181.0);
    assertNull(InCarQuickDestination.fromMapObject(invalidLongitude));
  }

  @Test
  public void fromLocationRejectsNullAndInvalidCoordinates()
  {
    assertNull(InCarQuickDestination.fromLocation("Current position", null));

    final Location invalidLatitude = mock(Location.class);
    when(invalidLatitude.getLatitude()).thenReturn(91.0);
    when(invalidLatitude.getLongitude()).thenReturn(149.13);
    assertNull(InCarQuickDestination.fromLocation("Current position", invalidLatitude));

    final Location invalidLongitude = mock(Location.class);
    when(invalidLongitude.getLatitude()).thenReturn(-35.28);
    when(invalidLongitude.getLongitude()).thenReturn(181.0);
    assertNull(InCarQuickDestination.fromLocation("Current position", invalidLongitude));
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
