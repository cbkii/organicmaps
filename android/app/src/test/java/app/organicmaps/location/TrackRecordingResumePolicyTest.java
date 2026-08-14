package app.organicmaps.location;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class TrackRecordingResumePolicyTest
{
  @Test
  public void activeRecordingContinuesWithoutAnotherPrompt()
  {
    assertEquals(TrackRecordingResumePolicy.StartDecision.CONTINUE_EXISTING,
                 TrackRecordingResumePolicy.decideStart(true, true));
    assertEquals(TrackRecordingResumePolicy.StartDecision.CONTINUE_EXISTING,
                 TrackRecordingResumePolicy.decideStart(true, false));
  }

  @Test
  public void disabledRestartOptionStartsOnceOnly()
  {
    assertEquals(TrackRecordingResumePolicy.StartDecision.START_ONCE,
                 TrackRecordingResumePolicy.decideStart(false, false));
  }

  @Test
  public void enabledRestartOptionRequiresExplicitSessionChoice()
  {
    assertEquals(TrackRecordingResumePolicy.StartDecision.ASK_RESUME_MODE,
                 TrackRecordingResumePolicy.decideStart(false, true));
  }
}
