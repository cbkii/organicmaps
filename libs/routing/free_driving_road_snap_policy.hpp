#pragma once

#include "platform/location.hpp"

#include <algorithm>
#include <cstddef>

namespace routing::free_driving_snap
{
// Free-driving matching intentionally has no single speed cut-off. Speed changes
// the strength of direction evidence; topology and mapped context decide whether
// a slow vehicle remains road-matched or should move freely.
double constexpr kCruiseSpeedMps = 20.0 / 3.6;
double constexpr kParkingFreeMaxSpeedMps = 15.0 / 3.6;
double constexpr kDirectionUsefulMinSpeedMps = 5.0 / 3.6;
double constexpr kDirectionFullWeightSpeedMps = 30.0 / 3.6;

double constexpr kMinSearchRadiusM = 20.0;
double constexpr kMaxSearchRadiusM = 50.0;
double constexpr kMaxUsableHorizontalAccuracyM = 80.0;
double constexpr kAreaContextRefreshSeconds = 2.0;
double constexpr kAreaContextRefreshDistanceM = 20.0;
double constexpr kMaxTemporalEvidenceDeltaSeconds = 2.0;
double constexpr kMaxAcceptedObservationGapSeconds = 5.0;
double constexpr kRecentDirectionMinTrackM = 15.0;
double constexpr kRecentDirectionMaxTrackM = 35.0;
double constexpr kRecentDirectionSeconds = 2.5;
double constexpr kLargeBuildingMinAreaM2 = 1500.0;

size_t constexpr kCruiseProbeCount = 6;
size_t constexpr kLowSpeedProbeCount = 8;

enum class AccuracyBand
{
  Good,
  Moderate,
  Poor,
  Unusable,
};

enum class MatchState
{
  Unsnapped,
  Road,
  ParkingFree,
  OffRoadFree,
};

enum class RoadRelation
{
  SameDirectedEdge,
  SameRoad,
  Connected,
  Unrelated,
};

struct AreaContext
{
  bool m_insideParking = false;
  bool m_insideStructuredParking = false;
  bool m_nearParkingEntrance = false;
  bool m_insideLargeBuilding = false;
  bool m_insideStrongOpenArea = false;
  bool m_insideWeakOpenArea = false;

  bool HasStrongParkingEvidence() const
  {
    return m_insideParking || m_insideStructuredParking || m_nearParkingEntrance;
  }
};

inline AccuracyBand GetAccuracyBand(location::GpsInfo const & info)
{
  if (info.m_horizontalAccuracy <= 0.0 || info.m_horizontalAccuracy > kMaxUsableHorizontalAccuracyM)
    return AccuracyBand::Unusable;
  if (info.m_horizontalAccuracy <= 15.0)
    return AccuracyBand::Good;
  if (info.m_horizontalAccuracy <= 35.0)
    return AccuracyBand::Moderate;
  return AccuracyBand::Poor;
}

inline double SearchRadiusM(location::GpsInfo const & info)
{
  return std::clamp(1.5 * info.m_horizontalAccuracy + 5.0, kMinSearchRadiusM, kMaxSearchRadiusM);
}

inline double AcceptanceDistanceM(location::GpsInfo const & info)
{
  return std::clamp(1.35 * info.m_horizontalAccuracy + 5.0, 10.0, 35.0);
}

inline double RecentDirectionTrackLengthM(location::GpsInfo const & info)
{
  double const speed = info.HasSpeed() ? std::max(0.0, info.m_speed) : 0.0;
  return std::clamp(speed * kRecentDirectionSeconds, kRecentDirectionMinTrackM, kRecentDirectionMaxTrackM);
}

inline double HeadingWeight(location::GpsInfo const & info)
{
  if (!info.HasSpeed() || info.m_speed <= kDirectionUsefulMinSpeedMps)
    return 0.0;
  return std::clamp(
      (info.m_speed - kDirectionUsefulMinSpeedMps) / (kDirectionFullWeightSpeedMps - kDirectionUsefulMinSpeedMps), 0.0,
      1.0);
}

inline size_t DirectionProbeCount(location::GpsInfo const & info)
{
  return info.HasSpeed() && info.m_speed >= kCruiseSpeedMps ? kCruiseProbeCount : kLowSpeedProbeCount;
}

// GpsInfo wall-clock timestamps are not guaranteed monotonic. Broken or long intervals
// must not manufacture parking/off-road confidence.
inline double TemporalEvidenceDeltaSeconds(double previousTimestamp, double currentTimestamp)
{
  if (previousTimestamp <= 0.0 || currentTimestamp <= previousTimestamp)
    return 0.0;
  double const delta = currentTimestamp - previousTimestamp;
  if (delta > kMaxAcceptedObservationGapSeconds)
    return 0.0;
  return std::min(delta, kMaxTemporalEvidenceDeltaSeconds);
}

inline bool ShouldRefreshAreaContext(double previousTimestamp, double currentTimestamp, double movedMeters)
{
  if (previousTimestamp <= 0.0 || currentTimestamp <= previousTimestamp)
    return true;
  return currentTimestamp - previousTimestamp >= kAreaContextRefreshSeconds ||
         movedMeters >= kAreaContextRefreshDistanceM;
}

inline double ContinuityPenalty(RoadRelation relation, bool lowSpeed)
{
  switch (relation)
  {
  case RoadRelation::SameDirectedEdge: return 0.0;
  case RoadRelation::SameRoad: return 0.12;
  case RoadRelation::Connected: return lowSpeed ? 0.22 : 0.42;
  case RoadRelation::Unrelated: return lowSpeed ? 1.10 : 1.35;
  }
  return 1.35;
}

inline double HeadingPenaltyDegrees(double angleDegrees, double headingWeight)
{
  // Continuous instead of the previous hard ~14-degree rejection. At low speed the
  // weight approaches zero, allowing legitimate sharp junction/laneway turns.
  return std::clamp(angleDegrees, 0.0, 180.0) / 90.0 * 1.35 * headingWeight;
}

inline double ParkingReleaseTimeSeconds(AreaContext const & context)
{
  if (context.m_insideStructuredParking || context.m_nearParkingEntrance)
    return 2.0;
  if (context.m_insideParking)
    return 3.0;
  return 5.0;  // Large-building evidence is deliberately weak and also requires road mismatch.
}

inline double OffRoadReleaseTimeSeconds(AreaContext const & context)
{
  if (context.m_insideStrongOpenArea)
    return 3.0;
  if (context.m_insideWeakOpenArea)
    return 5.0;
  return 8.0;
}

inline double OffRoadReleaseDistanceM(AreaContext const & context)
{
  if (context.m_insideStrongOpenArea)
    return 12.0;
  if (context.m_insideWeakOpenArea)
    return 20.0;
  return 35.0;
}

inline bool CanEnterParkingFree(location::GpsInfo const & info, AreaContext const & context, double evidenceSeconds,
                                bool roadMismatch)
{
  if (!info.HasSpeed() || info.m_speed > kParkingFreeMaxSpeedMps)
    return false;
  if (context.HasStrongParkingEvidence())
    return evidenceSeconds >= ParkingReleaseTimeSeconds(context);
  return context.m_insideLargeBuilding && roadMismatch && evidenceSeconds >= ParkingReleaseTimeSeconds(context);
}

inline bool CanEnterOffRoadFree(AreaContext const & context, double evidenceSeconds, double evidenceDistanceM)
{
  return evidenceSeconds >= OffRoadReleaseTimeSeconds(context) && evidenceDistanceM >= OffRoadReleaseDistanceM(context);
}
}  // namespace routing::free_driving_snap
