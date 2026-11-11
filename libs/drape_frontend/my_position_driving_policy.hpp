#pragma once

#include "platform/location.hpp"

#include <bit>
#include <cstdint>

namespace df::driving_policy
{
double constexpr kStationarySpeedMps = 5.0 / 3.6;
double constexpr kStationaryHoldRadiusMeters = 30.0;
// Initial in-car acquisition alone gets this short pan/rotate/zoom duration cap.
double constexpr kFastAcquisitionDurationSec = 0.65;

inline bool IsNavigationStyleCameraActive(bool isRouting, bool isDrivingView)
{
  return isRouting || isDrivingView;
}

inline bool ShouldAutoReturn(bool isRouting, bool isDrivingView, bool drivingAutoReturn)
{
  return isRouting || (isDrivingView && drivingAutoReturn);
}

inline bool ShouldUseAutoZoom(location::EMyPositionMode mode, bool isRouting, bool isDrivingView, bool routingAutoZoom,
                              bool blocked)
{
  if (mode != location::FollowAndRotate || blocked)
    return false;
  return (isRouting && routingAutoZoom) || (!isRouting && isDrivingView);
}

// Bit classification remains valid in the Android native build's -ffast-math mode.
inline bool IsFiniteCourseValue(double value)
{
  return (std::bit_cast<uint64_t>(value) & UINT64_C(0x7ff0000000000000)) != UINT64_C(0x7ff0000000000000);
}

inline bool HasReliableDrivingCourse(bool isRouting, bool isDriving, bool hasSpeed, double speedMps, bool hasBearing,
                                     double bearing = 0.0, double speedAccuracy = -1.0, double bearingAccuracy = -1.0)
{
  if (!isDriving || isRouting || !hasSpeed || !hasBearing || !IsFiniteCourseValue(speedMps) ||
      !IsFiniteCourseValue(bearing) || !IsFiniteCourseValue(speedAccuracy) || !IsFiniteCourseValue(bearingAccuracy) ||
      speedMps < kStationarySpeedMps || bearing < 0.0 || bearing >= 360.0)
    return false;
  // OEM fixes may omit accuracy (-1). When present, movement must clear the stationary
  // threshold even at the lower speed-confidence bound, and course uncertainty is at most 30°.
  return (speedAccuracy < 0.0 || speedMps - speedAccuracy >= kStationarySpeedMps) &&
         (bearingAccuracy < 0.0 || bearingAccuracy <= 30.0);
}

inline bool ShouldHoldFreeDrivingCamera(bool isRouting, bool isDrivingView, bool positionAssigned, bool hasSpeed,
                                        double speedMps, double displacementMeters)
{
  if (isRouting || !isDrivingView || !positionAssigned)
    return false;
  if (displacementMeters >= kStationaryHoldRadiusMeters)
    return false;
  return !hasSpeed || speedMps < kStationarySpeedMps;
}
}  // namespace df::driving_policy
