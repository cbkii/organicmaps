#pragma once

#include "platform/location.hpp"

namespace df
{
struct MyPositionStartupModes
{
  location::EMyPositionMode m_mode;
  location::EMyPositionMode m_desiredMode;
};

inline MyPositionStartupModes ResolveMyPositionStartupModes(bool autoStartFollowAndRotate, bool isLaunchByDeepLink,
                                                            bool isFirstLaunch, bool isLongBackground, bool isInRouting,
                                                            location::EMyPositionMode initMode)
{
  MyPositionStartupModes modes{location::PendingPosition, initMode};

  // Explicit map targets must not be replaced by the current position on launch.
  if (isLaunchByDeepLink)
    modes.m_desiredMode = location::NotFollow;
  else if (autoStartFollowAndRotate)
    modes.m_desiredMode = location::FollowAndRotate;
  else if (isFirstLaunch || isLongBackground)
    modes.m_desiredMode = location::Follow;
  else if (!isInRouting && modes.m_desiredMode == location::NotFollowNoPosition)
    modes.m_mode = location::NotFollowNoPosition;

  return modes;
}

inline bool ShouldUseStartupDrivingAreaZoom(bool forceDrivingArea, int currentZoom, int pathologicalZoomThreshold)
{
  return forceDrivingArea || currentZoom < pathologicalZoomThreshold;
}
}  // namespace df
