#pragma once

#include "platform/location.hpp"

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

inline bool HasReliableDrivingCourse(bool isRouting, bool isDriving, bool hasSpeed, double speedMps, bool hasBearing)
{
  return isDriving && !isRouting && hasSpeed && speedMps >= kStationarySpeedMps && hasBearing;
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
