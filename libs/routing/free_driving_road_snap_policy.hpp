#pragma once

#include "platform/location.hpp"

#include "geometry/point2d.hpp"

#include <algorithm>
#include <cmath>
#include <cstddef>
#include <limits>
#include <vector>

namespace routing::free_driving_snap
{
// Free-driving matching is an online display matcher, not routed navigation. It intentionally
// keeps all searches bounded and lets speed influence confidence rather than act as a binary gate.
double constexpr kCruiseSpeedMps = 20.0 / 3.6;
double constexpr kParkingFreeMaxSpeedMps = 15.0 / 3.6;
double constexpr kParkingFinePositionMaxSpeedMps = 6.0 / 3.6;
double constexpr kStationaryHoldMaxSpeedMps = 3.0 / 3.6;
double constexpr kMotionEstablishedSpeedMps = 4.0 / 3.6;
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
double constexpr kParkingEntranceHintSeconds = 10.0;
double constexpr kPersistenceRefreshSeconds = 5.0;
double constexpr kPersistenceMaxAgeSeconds = 7.0 * 24.0 * 60.0 * 60.0;
double constexpr kPersistenceMaxDistanceM = 250.0;

size_t constexpr kNormalSpatialRoadCount = 8;
size_t constexpr kRecoverySpatialRoadCount = 12;
size_t constexpr kMaxCorridorEdges = 12;
size_t constexpr kMaxCorridorHops = 5;
size_t constexpr kMaxDisplayCorridorEdges = 8;

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
  ReverseSameRoad,
  Connected,
  Unrelated,
};

enum class RoadClass
{
  Unknown,
  Motorway,
  MotorwayLink,
  Trunk,
  TrunkLink,
  Primary,
  PrimaryLink,
  Secondary,
  SecondaryLink,
  Tertiary,
  TertiaryLink,
  Residential,
  Unclassified,
  Service,
  LivingStreet,
  Track,
  Other,
};

struct RoadMetadata
{
  RoadClass m_class = RoadClass::Unknown;
  bool m_parkingAisle = false;
  bool m_driveway = false;
  bool m_roundabout = false;
};

struct AreaContext
{
  bool m_insideParking = false;
  bool m_insideStructuredParking = false;
  bool m_nearParkingEntrance = false;
  bool m_insideLargeBuilding = false;
  bool m_insideStrongOpenArea = false;
  bool m_insideWeakOpenArea = false;

  // When the current fix is in a mapped parking/building area this stores that area's
  // triangulation. ParkingFree display projection can therefore keep the actual measured
  // position (or its nearest in-area point) rather than collapsing to an area centre.
  std::vector<m2::PointD> m_freeAreaTriangles;

  bool HasParkingArea() const { return m_insideParking || m_insideStructuredParking; }
  bool HasFreeAreaGeometry() const { return !m_freeAreaTriangles.empty(); }
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
  switch (GetAccuracyBand(info))
  {
  case AccuracyBand::Good: return std::clamp(1.15 * info.m_horizontalAccuracy + 4.0, 10.0, 22.0);
  case AccuracyBand::Moderate: return std::clamp(0.75 * info.m_horizontalAccuracy + 7.0, 18.0, 30.0);
  case AccuracyBand::Poor: return std::clamp(0.45 * info.m_horizontalAccuracy + 10.0, 24.0, 32.0);
  case AccuracyBand::Unusable: return 0.0;
  }
  return 0.0;
}

inline double StrongRoadDistanceM(location::GpsInfo const & info)
{
  return std::min(AcceptanceDistanceM(info), std::clamp(0.7 * info.m_horizontalAccuracy + 3.0, 8.0, 20.0));
}

inline double CandidateScoreLimit(AccuracyBand accuracy)
{
  switch (accuracy)
  {
  case AccuracyBand::Good: return 3.0;
  case AccuracyBand::Moderate: return 2.6;
  case AccuracyBand::Poor: return 2.2;
  case AccuracyBand::Unusable: return 0.0;
  }
  return 0.0;
}

inline double RequiredRunnerUpMargin(AccuracyBand accuracy)
{
  switch (accuracy)
  {
  case AccuracyBand::Good: return 0.28;
  case AccuracyBand::Moderate: return 0.50;
  case AccuracyBand::Poor:
  case AccuracyBand::Unusable: return std::numeric_limits<double>::infinity();
  }
  return std::numeric_limits<double>::infinity();
}

inline bool IsUnambiguous(double bestScore, double runnerUpScore, AccuracyBand accuracy)
{
  if (accuracy == AccuracyBand::Poor || accuracy == AccuracyBand::Unusable)
    return false;
  if (!std::isfinite(runnerUpScore))
    return true;
  return runnerUpScore - bestScore >= RequiredRunnerUpMargin(accuracy);
}

inline double RecentDirectionTrackLengthM(location::GpsInfo const & info)
{
  double const speed = info.HasSpeed() ? std::max(0.0, info.m_speed) : 0.0;
  return std::clamp(speed * kRecentDirectionSeconds, kRecentDirectionMinTrackM, kRecentDirectionMaxTrackM);
}

inline double EffectiveSpeedMps(location::GpsInfo const & info, double rawStepM, double deltaSeconds)
{
  if (info.HasSpeed())
    return std::max(0.0, info.m_speed);
  if (deltaSeconds <= 0.0 || rawStepM < 0.0 || rawStepM > 80.0)
    return 0.0;
  return rawStepM / deltaSeconds;
}

inline double HeadingWeightForSpeed(double speedMps)
{
  if (speedMps <= kDirectionUsefulMinSpeedMps)
    return 0.0;
  return std::clamp((speedMps - kDirectionUsefulMinSpeedMps) /
                        (kDirectionFullWeightSpeedMps - kDirectionUsefulMinSpeedMps),
                    0.0, 1.0);
}

inline double HeadingAgreementFactor(double disagreementDegrees)
{
  disagreementDegrees = std::clamp(disagreementDegrees, 0.0, 180.0);
  if (disagreementDegrees <= 30.0)
    return 1.0;
  if (disagreementDegrees <= 60.0)
    return 1.0 - (disagreementDegrees - 30.0) / 30.0 * 0.35;
  if (disagreementDegrees <= 90.0)
    return 0.65 - (disagreementDegrees - 60.0) / 30.0 * 0.40;
  return std::max(0.10, 0.25 - (disagreementDegrees - 90.0) / 90.0 * 0.15);
}

inline bool IsStationaryHold(location::GpsInfo const & info, double rawStepM, double deltaSeconds)
{
  double const speed = EffectiveSpeedMps(info, rawStepM, deltaSeconds);
  double const jitterM = std::clamp(info.m_horizontalAccuracy * 0.75, 4.0, 12.0);
  return speed <= kStationaryHoldMaxSpeedMps && rawStepM <= jitterM;
}

inline bool HasEstablishedMotion(location::GpsInfo const & info, double rawStepM, double deltaSeconds)
{
  if (info.HasSpeed() && info.m_speed >= kMotionEstablishedSpeedMps)
    return true;
  if (deltaSeconds <= 0.0)
    return false;
  double const displacementThresholdM = std::clamp(info.m_horizontalAccuracy * 0.4, 2.0, 6.0);
  return rawStepM >= displacementThresholdM && rawStepM / deltaSeconds >= kMotionEstablishedSpeedMps;
}

inline bool ShouldRefreshAreaContext(double previousTimestamp, double currentTimestamp, double movedMeters)
{
  if (previousTimestamp <= 0.0 || currentTimestamp <= previousTimestamp)
    return true;
  return currentTimestamp - previousTimestamp >= kAreaContextRefreshSeconds ||
         movedMeters >= kAreaContextRefreshDistanceM;
}

inline double TemporalEvidenceDeltaSeconds(double previousTimestamp, double currentTimestamp)
{
  if (previousTimestamp <= 0.0 || currentTimestamp <= previousTimestamp)
    return 0.0;
  double const delta = currentTimestamp - previousTimestamp;
  if (delta > kMaxAcceptedObservationGapSeconds)
    return 0.0;
  return std::min(delta, kMaxTemporalEvidenceDeltaSeconds);
}

inline double CorridorSearchDistanceM(location::GpsInfo const & info, double deltaSeconds)
{
  double const expectedM = EffectiveSpeedMps(info, 0.0, deltaSeconds) * std::max(1.0, deltaSeconds);
  return std::clamp(expectedM * 2.0 + AcceptanceDistanceM(info) + 20.0, 45.0, 120.0);
}

inline double ContinuityPenalty(RoadRelation relation, bool lowSpeed)
{
  switch (relation)
  {
  case RoadRelation::SameDirectedEdge: return 0.0;
  case RoadRelation::SameRoad: return 0.10;
  case RoadRelation::ReverseSameRoad: return lowSpeed ? 0.20 : 1.0;
  case RoadRelation::Connected: return lowSpeed ? 0.20 : 0.38;
  case RoadRelation::Unrelated: return lowSpeed ? 1.10 : 1.35;
  }
  return 1.35;
}

inline double HeadingPenaltyDegrees(double angleDegrees, double headingWeight)
{
  return std::clamp(angleDegrees, 0.0, 180.0) / 90.0 * 1.35 * headingWeight;
}

inline double TypicalRoadSpeedKmh(RoadMetadata const & metadata)
{
  if (metadata.m_parkingAisle || metadata.m_driveway)
    return 15.0;

  switch (metadata.m_class)
  {
  case RoadClass::Motorway: return 100.0;
  case RoadClass::MotorwayLink: return 80.0;
  case RoadClass::Trunk: return 90.0;
  case RoadClass::TrunkLink: return 70.0;
  case RoadClass::Primary: return 70.0;
  case RoadClass::PrimaryLink: return 60.0;
  case RoadClass::Secondary: return 60.0;
  case RoadClass::SecondaryLink: return 50.0;
  case RoadClass::Tertiary:
  case RoadClass::TertiaryLink:
  case RoadClass::Residential:
  case RoadClass::Unclassified: return 50.0;
  case RoadClass::Service: return 30.0;
  case RoadClass::LivingStreet: return 20.0;
  case RoadClass::Track: return 30.0;
  case RoadClass::Unknown:
  case RoadClass::Other: return 50.0;
  }
  return 50.0;
}

inline double RoadClassSpeedPenalty(location::GpsInfo const & info, RoadMetadata const & metadata,
                                    RoadRelation relation)
{
  if (!info.HasSpeed() || relation != RoadRelation::Unrelated)
    return 0.0;
  double const excessKmh = info.m_speed * 3.6 - TypicalRoadSpeedKmh(metadata) - 10.0;
  if (excessKmh <= 0.0)
    return 0.0;
  return std::clamp(excessKmh / 40.0 * 0.65, 0.0, 0.65);
}

inline double ParkingReleaseTimeSeconds(AreaContext const & context, bool recentEntranceHint)
{
  double seconds = context.m_insideStructuredParking ? 2.0 : (context.m_insideParking ? 3.0 : 5.0);
  if (recentEntranceHint && context.HasParkingArea())
    seconds = std::max(1.5, seconds - 1.0);
  return seconds;
}

inline bool ParkingReleaseEligible(location::GpsInfo const & info, AreaContext const & context, bool roadMismatch,
                                   bool bestParkingAisle, bool stationaryHold)
{
  if (!info.HasSpeed() || info.m_speed > kParkingFreeMaxSpeedMps)
    return false;

  // Entrance proximity is a transition hint only. It must never release a car that is simply
  // travelling slowly on a public road beside a car-park entrance.
  if (!context.HasParkingArea() && !context.m_insideLargeBuilding)
    return false;

  if (context.m_insideLargeBuilding && !context.HasParkingArea())
    return roadMismatch;

  // Keep a good mapped parking aisle while traversing the facility, then allow fine-position
  // free movement as the car slows to manoeuvre/park or when the aisle no longer explains the fix.
  if (bestParkingAisle && !roadMismatch && !stationaryHold && info.m_speed > kParkingFinePositionMaxSpeedMps)
    return false;
  return roadMismatch || stationaryHold || !bestParkingAisle || info.m_speed <= kParkingFinePositionMaxSpeedMps;
}

inline double OffRoadReleaseTimeSeconds(AreaContext const & context, bool weakRoadMatch)
{
  if (context.m_insideStrongOpenArea)
    return 3.0;
  if (context.m_insideWeakOpenArea)
    return 5.0;
  return weakRoadMatch ? 12.0 : 8.0;
}

inline double OffRoadReleaseDistanceM(AreaContext const & context, bool weakRoadMatch)
{
  if (context.m_insideStrongOpenArea)
    return 12.0;
  if (context.m_insideWeakOpenArea)
    return 20.0;
  return weakRoadMatch ? 50.0 : 35.0;
}
}  // namespace routing::free_driving_snap
