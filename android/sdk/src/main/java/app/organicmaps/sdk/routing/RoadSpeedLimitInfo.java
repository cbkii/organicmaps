package app.organicmaps.sdk.routing;

import androidx.annotation.Keep;

/** Posted metadata from the current directed road match, independent of route guidance. */
@Keep
public final class RoadSpeedLimitInfo
{
  public static final long MAX_AGE_NANOS = 5_000_000_000L;
  public final double speedLimitMps;
  public final long observationTimeNanos;
  public final long roadToken;

  public RoadSpeedLimitInfo(double speedLimitMps, long observationTimeNanos, long roadToken)
  {
    this.speedLimitMps = speedLimitMps;
    this.observationTimeNanos = observationTimeNanos;
    this.roadToken = roadToken;
  }

  public boolean isFresh(long nowNanos)
  {
    return Double.isFinite(speedLimitMps) && speedLimitMps >= 0.0 && roadToken != 0 && observationTimeNanos > 0
        && nowNanos >= observationTimeNanos && nowNanos - observationTimeNanos < MAX_AGE_NANOS;
  }

  public boolean isFromObservation(long timeNanos)
  {
    // The JNI seconds-to-nanoseconds conversion can round by a few ns; do not accept another provider fix.
    return observationTimeNanos > 0 && timeNanos > 0 && Math.abs(observationTimeNanos - timeNanos) <= 1_000L;
  }
}
