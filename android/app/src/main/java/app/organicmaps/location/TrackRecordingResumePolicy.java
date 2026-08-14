package app.organicmaps.location;

import androidx.annotation.NonNull;

final class TrackRecordingResumePolicy
{
  enum StartDecision
  {
    CONTINUE_EXISTING,
    START_ONCE,
    ASK_RESUME_MODE
  }

  private TrackRecordingResumePolicy() {}

  @NonNull
  static StartDecision decideStart(boolean alreadyRecording, boolean askResumeMode)
  {
    if (alreadyRecording)
      return StartDecision.CONTINUE_EXISTING;
    return askResumeMode ? StartDecision.ASK_RESUME_MODE : StartDecision.START_ONCE;
  }
}
