#pragma once

#include "platform/location.hpp"

#include <algorithm>

namespace routing::free_driving_snap
{
// Free-driving road projection is intentionally conservative. It is a display aid, not a replacement
// for raw GNSS or normal routed-navigation matching.
double constexpr kMovingMinSpeedMps = 15.0 / 3.6;
double constexpr kStationaryMaxSpeedMps = 5.0 / 3.6;
double constexpr kMaxHorizontalAccuracyM = 25.0;
double constexpr kMinMovingProjectionRadiusM = 6.0;
double constexpr kMaxMovingProjectionRadiusM = 12.0;
double constexpr kStationaryProjectionRadiusM = 10.0;
double constexpr kRecentDirectionTrackLengthM = 20.0;
double constexpr kMaxStationaryHoldSeconds = 90.0;

enum class Mode
{
  None,
  Moving,
  StationaryHold,
};

inline bool IsAccuracyUsable(location::GpsInfo const & info)
{
  return info.m_horizontalAccuracy > 0.0 && info.m_horizontalAccuracy <= kMaxHorizontalAccuracyM;
}

inline Mode ResolveMode(location::GpsInfo const & info, bool hasConfidentMovingMatch,
                        double lastConfidentMovingTimestamp)
{
  if (!IsAccuracyUsable(info))
    return Mode::None;

  if (info.HasSpeed() && info.m_speed >= kMovingMinSpeedMps)
    return Mode::Moving;

  if (!hasConfidentMovingMatch)
    return Mode::None;

  bool const stationary = !info.HasSpeed() || info.m_speed <= kStationaryMaxSpeedMps;
  if (!stationary || lastConfidentMovingTimestamp <= 0.0)
    return Mode::None;

  double const ageSeconds = info.m_timestamp - lastConfidentMovingTimestamp;
  if (ageSeconds < 0.0 || ageSeconds > kMaxStationaryHoldSeconds)
    return Mode::None;

  return Mode::StationaryHold;
}

inline double MovingProjectionRadiusM(location::GpsInfo const & info)
{
  return std::clamp(info.m_horizontalAccuracy, kMinMovingProjectionRadiusM, kMaxMovingProjectionRadiusM);
}
}  // namespace routing::free_driving_snap
