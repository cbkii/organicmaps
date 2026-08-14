package app.organicmaps.sdk.sound;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import app.organicmaps.sdk.routing.CarDirection;
import app.organicmaps.sdk.routing.NavigationNotification;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

public class OfflineNavigationVoicePackTest
{
  @Test
  public void mapsCoreTurnDirectionsToOriginalPackCues()
  {
    assertEquals(
        Collections.singletonList("07_turn_left.ogg"),
        OfflineNavigationVoicePack.selectClipNames(CarDirection.TurnLeft, 0, OfflineNavigationVoicePack.Cue.MANEUVER));
    assertEquals(
        Collections.singletonList("08_turn_right.ogg"),
        OfflineNavigationVoicePack.selectClipNames(CarDirection.TurnRight, 0, OfflineNavigationVoicePack.Cue.MANEUVER));
    assertEquals(Collections.singletonList("09_bear_left_stay_cool.ogg"),
                 OfflineNavigationVoicePack.selectClipNames(CarDirection.TurnSlightLeft, 0,
                                                            OfflineNavigationVoicePack.Cue.MANEUVER));
    assertEquals(Collections.singletonList("10_bear_right_easy_now.ogg"),
                 OfflineNavigationVoicePack.selectClipNames(CarDirection.TurnSlightRight, 0,
                                                            OfflineNavigationVoicePack.Cue.MANEUVER));
    assertEquals(
        Collections.singletonList("13_make_u_turn.ogg"),
        OfflineNavigationVoicePack.selectClipNames(CarDirection.UTurnLeft, 0, OfflineNavigationVoicePack.Cue.MANEUVER));
  }

  @Test
  public void combinesShortCuesOnlyWhenDirectionWouldOtherwiseBeAmbiguous()
  {
    assertEquals(Arrays.asList("47_sharp_turn.ogg", "07_turn_left.ogg"),
                 OfflineNavigationVoicePack.selectClipNames(CarDirection.TurnSharpLeft, 0,
                                                            OfflineNavigationVoicePack.Cue.MANEUVER));
    assertEquals(Arrays.asList("47_sharp_turn.ogg", "08_turn_right.ogg"),
                 OfflineNavigationVoicePack.selectClipNames(CarDirection.TurnSharpRight, 0,
                                                            OfflineNavigationVoicePack.Cue.MANEUVER));
    assertEquals(Arrays.asList("02_navigation_started_lets_roll.ogg", "05_continue_straight_nice_easy.ogg"),
                 OfflineNavigationVoicePack.selectClipNames(CarDirection.StartAtEndOfStreet, 0,
                                                            OfflineNavigationVoicePack.Cue.MANEUVER));
  }

  @Test
  public void mapsRoundaboutExitNumbersAndHighwayExits()
  {
    assertEquals(Collections.singletonList("15_take_first_exit.ogg"),
                 OfflineNavigationVoicePack.selectClipNames(CarDirection.LeaveRoundAbout, 1,
                                                            OfflineNavigationVoicePack.Cue.MANEUVER));
    assertEquals(Collections.singletonList("18_take_fourth_exit.ogg"),
                 OfflineNavigationVoicePack.selectClipNames(CarDirection.LeaveRoundAbout, 4,
                                                            OfflineNavigationVoicePack.Cue.MANEUVER));
    assertEquals(Collections.singletonList("26_take_next_exit.ogg"),
                 OfflineNavigationVoicePack.selectClipNames(CarDirection.LeaveRoundAbout, 8,
                                                            OfflineNavigationVoicePack.Cue.MANEUVER));
    assertEquals(Collections.singletonList("19_exit_left.ogg"),
                 OfflineNavigationVoicePack.selectClipNames(CarDirection.ExitHighwayToLeft, 0,
                                                            OfflineNavigationVoicePack.Cue.MANEUVER));
    assertEquals(Collections.singletonList("20_exit_right.ogg"),
                 OfflineNavigationVoicePack.selectClipNames(CarDirection.ExitHighwayToRight, 0,
                                                            OfflineNavigationVoicePack.Cue.MANEUVER));
  }

  @Test
  public void mapsTypedRouteAndGpsEventsWithoutLocalizedText()
  {
    assertEquals(Collections.singletonList("39_way_updated.ogg"),
                 OfflineNavigationVoicePack.selectClipNames(CarDirection.TurnLeft, 0,
                                                            OfflineNavigationVoicePack.Cue.ROUTE_UPDATED));
    assertEquals(Collections.singletonList("40_gps_lost.ogg"),
                 OfflineNavigationVoicePack.selectClipNames(CarDirection.GoStraight, 0,
                                                            OfflineNavigationVoicePack.Cue.GPS_LOST));
    assertEquals(Collections.singletonList("41_gps_restored.ogg"),
                 OfflineNavigationVoicePack.selectClipNames(CarDirection.GoStraight, 0,
                                                            OfflineNavigationVoicePack.Cue.GPS_RESTORED));
    assertEquals(Collections.singletonList("36_you_made_it_irie.ogg"),
                 OfflineNavigationVoicePack.selectClipNames(CarDirection.ReachedYourDestination, 0,
                                                            OfflineNavigationVoicePack.Cue.MANEUVER));
  }

  @Test
  public void gpsSignalTransitionsAreEmittedOnce()
  {
    final OfflineNavigationVoicePack.GpsSignalState state = new OfflineNavigationVoicePack.GpsSignalState();

    assertEquals(OfflineNavigationVoicePack.GpsSignalEvent.NONE, state.onLocationUpdated());
    assertEquals(OfflineNavigationVoicePack.GpsSignalEvent.LOST, state.onUnavailable());
    assertEquals(OfflineNavigationVoicePack.GpsSignalEvent.NONE, state.onUnavailable());
    assertEquals(OfflineNavigationVoicePack.GpsSignalEvent.RESTORED, state.onLocationUpdated());
    assertEquals(OfflineNavigationVoicePack.GpsSignalEvent.NONE, state.onLocationUpdated());
  }

  @Test
  public void toneModesHaveDistinctEventPolicies()
  {
    assertFalse(OfflineNavigationVoicePack.shouldPlayTone(OfflineNavigationVoicePack.Mode.TONE_ALERTS, true, false));
    assertTrue(OfflineNavigationVoicePack.shouldPlayTone(OfflineNavigationVoicePack.Mode.TONE_ALERTS, true, true));
    assertTrue(OfflineNavigationVoicePack.shouldPlayTone(OfflineNavigationVoicePack.Mode.TONE_ALL, true, false));
    assertFalse(OfflineNavigationVoicePack.shouldPlayTone(OfflineNavigationVoicePack.Mode.TONE_ALL, false, false));
    assertFalse(OfflineNavigationVoicePack.shouldPlayTone(OfflineNavigationVoicePack.Mode.VOICE, true, true));
    assertFalse(OfflineNavigationVoicePack.shouldPlayTone(OfflineNavigationVoicePack.Mode.OFF, true, true));
  }

  @Test
  public void voiceUsesToneForAdvanceStageAndSpeechForImmediateTurn()
  {
    assertFalse(OfflineNavigationVoicePack.shouldPlayVoiceCue(NavigationNotification.Event.MANEUVER,
                                                              NavigationNotification.Stage.ADVANCE));
    assertTrue(OfflineNavigationVoicePack.shouldPlayVoiceCue(NavigationNotification.Event.MANEUVER,
                                                             NavigationNotification.Stage.IMMEDIATE));
    assertTrue(OfflineNavigationVoicePack.shouldPlayVoiceCue(NavigationNotification.Event.ROUTE_RECALCULATION,
                                                             NavigationNotification.Stage.NONE));
    assertFalse(OfflineNavigationVoicePack.shouldPlayVoiceCue(NavigationNotification.Event.SPEED_CAMERA,
                                                              NavigationNotification.Stage.NONE));
  }

  @Test
  public void fallbackModeValuesAreStableAndUnknownValuesPreferBasicTone()
  {
    assertEquals(OfflineNavigationVoicePack.Mode.OFF, OfflineNavigationVoicePack.Mode.fromPreferenceValue("off"));
    assertEquals(OfflineNavigationVoicePack.Mode.VOICE, OfflineNavigationVoicePack.Mode.fromPreferenceValue("voice"));
    assertEquals(OfflineNavigationVoicePack.Mode.TONE_ALERTS,
                 OfflineNavigationVoicePack.Mode.fromPreferenceValue("tone_alerts"));
    assertEquals(OfflineNavigationVoicePack.Mode.TONE_ALL,
                 OfflineNavigationVoicePack.Mode.fromPreferenceValue("tone_all"));
    assertEquals(OfflineNavigationVoicePack.Mode.TONE_ALL,
                 OfflineNavigationVoicePack.Mode.fromPreferenceValue("unknown"));
  }

  @Test
  public void cleanInstallDefaultsToToneAllAndLegacyBooleanMigratesDeliberately()
  {
    assertEquals(OfflineNavigationVoicePack.Mode.TONE_ALL, OfflineNavigationVoicePack.resolveMode(false, null, null));
    assertEquals(OfflineNavigationVoicePack.Mode.VOICE, OfflineNavigationVoicePack.resolveMode(false, null, true));
    assertEquals(OfflineNavigationVoicePack.Mode.OFF, OfflineNavigationVoicePack.resolveMode(false, null, false));
    assertEquals(OfflineNavigationVoicePack.Mode.TONE_ALERTS,
                 OfflineNavigationVoicePack.resolveMode(true, "tone_alerts", true));
  }

  @Test
  public void packKeepsOriginalGpsCuesAndOmitsShortDuplicates()
  {
    assertTrue(OfflineNavigationVoicePack.containsClip("40_gps_lost.ogg"));
    assertTrue(OfflineNavigationVoicePack.containsClip("41_gps_restored.ogg"));
    assertFalse(OfflineNavigationVoicePack.containsClip("68_gps_lost_short.ogg"));
    assertFalse(OfflineNavigationVoicePack.containsClip("69_gps_restored_short.ogg"));
  }

  @Test
  public void packUsesRecutRouteUpdatedCue()
  {
    assertTrue(OfflineNavigationVoicePack.containsClip("39_way_updated.ogg"));
  }
}
