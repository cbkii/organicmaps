package app.organicmaps.sdk.sound;

import static org.junit.Assert.assertEquals;

import app.organicmaps.sdk.routing.CarDirection;
import org.junit.Test;

public class NavigationTonePolicyTest
{
  @Test
  public void directionFamiliesHaveDistinctToneIds()
  {
    assertEquals(NavigationTonePolicy.LEFT, NavigationTonePolicy.directionTone(CarDirection.TurnLeft));
    assertEquals(NavigationTonePolicy.LEFT, NavigationTonePolicy.directionTone(CarDirection.TurnSharpLeft));
    assertEquals(NavigationTonePolicy.RIGHT, NavigationTonePolicy.directionTone(CarDirection.TurnRight));
    assertEquals(NavigationTonePolicy.RIGHT, NavigationTonePolicy.directionTone(CarDirection.TurnSlightRight));
    assertEquals(NavigationTonePolicy.COMPLEX, NavigationTonePolicy.directionTone(CarDirection.UTurnLeft));
    assertEquals(NavigationTonePolicy.COMPLEX, NavigationTonePolicy.directionTone(CarDirection.LeaveRoundAbout));
    assertEquals(NavigationTonePolicy.COMPLEX, NavigationTonePolicy.directionTone(CarDirection.ExitHighwayToRight));
    assertEquals(NavigationTonePolicy.ROUTE, NavigationTonePolicy.directionTone(CarDirection.ReachedYourDestination));
    assertEquals(NavigationTonePolicy.ADVANCE, NavigationTonePolicy.directionTone(CarDirection.GoStraight));
    assertEquals(NavigationTonePolicy.ADVANCE, NavigationTonePolicy.directionTone(null));
  }

  @Test
  public void gpsTransitionAndMissingEventSelection()
  {
    assertEquals(NavigationTonePolicy.GPS,
                 NavigationTonePolicy.selectToneIndex(null, null, OfflineNavigationVoicePack.GpsSignalEvent.LOST));
    assertEquals(NavigationTonePolicy.ROUTE,
                 NavigationTonePolicy.selectToneIndex(null, null, OfflineNavigationVoicePack.GpsSignalEvent.RESTORED));
    assertEquals(-1, NavigationTonePolicy.selectToneIndex(null, null, OfflineNavigationVoicePack.GpsSignalEvent.NONE));
  }
}
