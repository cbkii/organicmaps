#pragma once

#include <cstdint>

// Android Location elapsedRealtimeNanos and CLOCK_BOOTTIME include deep sleep. Missing, future or
// replayed observations cannot gain a new lifetime when the JNI bridge receives them.
inline bool IsFreshPositionObservation(int64_t observedAtNanos, int64_t nowNanos)
{
  int64_t constexpr kLifetimeNanos = 10000000000;
  return observedAtNanos > 0 && nowNanos >= observedAtNanos && nowNanos - observedAtNanos <= kLifetimeNanos;
}
