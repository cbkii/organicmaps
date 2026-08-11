#pragma once

#include "platform/location.hpp"

namespace df::driving_policy
{
double constexpr kStationarySpeedMps = 5.0 / 3.6;

inline bool IsNavigationStyleCameraActive(bool isRouting, bool isDrivingView)
{
  return isRouting || isDrivingView;
}

inline bool ShouldAutoReturn(bool isRouting, bool isDrivingView, bool drivingAutoReturn)
{
  return isRouting || (isDrivingView && drivingAutoReturn);
}

inline bool ShouldUseAutoZoom(location::EMyPositionMode mode, bool isRouting, bool isDrivingView,
                              bool routingAutoZoom, bool blocked)
{
  if (mode != location::FollowAndRotate || blocked)
    return false;
  return (isRouting && routingAutoZoom) || (!isRouting && isDrivingView);
}

inline bool ShouldHoldFreeDrivingCamera(bool isRouting, bool isDrivingView, bool positionAssigned, bool hasSpeed,
                                        double speedMps)
{
  if (isRouting || !isDrivingView || !positionAssigned)
    return false;
  return !hasSpeed || speedMps < kStationarySpeedMps;
}
}  // namespace df::driving_policy
