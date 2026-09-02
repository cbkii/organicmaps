#pragma once

#include "platform/location.hpp"

namespace df::driving_policy
{
double constexpr kStationarySpeedMps = 5.0 / 3.6;
double constexpr kStationaryHoldRadiusMeters = 30.0;
double constexpr kFreeDrivingMinSpeedMps = 10.0 / 3.6;
double constexpr kFreeDrivingAutoReturnSeconds = 10.0;

inline bool IsNavigationStyleCameraActive(bool isRouting, bool isDrivingView)
{
  return isRouting || isDrivingView;
}

inline bool ShouldAutoReturn(bool isRouting, bool isDrivingView, bool drivingAutoReturn)
{
  return isRouting || (isDrivingView && drivingAutoReturn);
}

inline bool IsInCarFreeDrivingMotion(bool isInCar, bool autoFollow, bool hasSpeed, double speedMps)
{
  return isInCar && autoFollow && hasSpeed && speedMps >= kFreeDrivingMinSpeedMps;
}

inline bool ShouldAutoReturn(bool isRouting, bool isDrivingView, bool drivingAutoReturn, bool isInCar, bool autoFollow,
                             bool hasSpeed, double speedMps)
{
  if (ShouldAutoReturn(isRouting, isDrivingView, drivingAutoReturn))
    return true;
  return !isRouting && !isDrivingView && IsInCarFreeDrivingMotion(isInCar, autoFollow, hasSpeed, speedMps);
}

inline bool ShouldUseAutoZoom(location::EMyPositionMode mode, bool isRouting, bool isDrivingView, bool routingAutoZoom,
                              bool blocked)
{
  if (mode != location::FollowAndRotate || blocked)
    return false;
  return (isRouting && routingAutoZoom) || (!isRouting && isDrivingView);
}

inline bool ShouldUseAutoZoom(location::EMyPositionMode mode, bool isRouting, bool isDrivingView, bool routingAutoZoom,
                              bool freeDrivingAutoZoom, bool autoFollow, bool hasSpeed, double speedMps, bool blocked)
{
  if (ShouldUseAutoZoom(mode, isRouting, isDrivingView, routingAutoZoom, blocked))
    return true;
  if (mode != location::FollowAndRotate || blocked || isRouting || isDrivingView)
    return false;
  return freeDrivingAutoZoom && autoFollow && hasSpeed && speedMps >= kFreeDrivingMinSpeedMps;
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
