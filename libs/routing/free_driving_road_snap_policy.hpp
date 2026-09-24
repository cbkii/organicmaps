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
double constexpr kNoRunnerUpScore = std::numeric_limits<double>::max();

// These are effective directed-candidate budgets.  Corridor expansion and reverse edges are
// folded into the same bound before the expensive scoring pass.
size_t constexpr kNormalSpatialRoadCount = 6;
size_t constexpr kRecoverySpatialRoadCount = 8;
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
  bool m_nearParkingArea = false;
  bool m_nearParkingEntrance = false;
  bool m_nearParkingSpace = false;
  bool m_insideStreetSideParking = false;
  bool m_insideParkingLane = false;
  bool m_insideLargeBuilding = false;
  bool m_insideStrongOpenArea = false;
  bool m_insideWeakOpenArea = false;
  double m_nearestParkingAreaDistanceM = std::numeric_limits<double>::max();

  // Free-area geometry is used only after ParkingFree has been entered. Nearby parking geometry
  // is separately retained so bounded road candidates can be classified as inside/outside a
  // facility without turning parking proximity itself into snap authority.
  std::vector<m2::PointD> m_freeAreaTriangles;
  std::vector<m2::PointD> m_nearbyParkingAreaTriangles;

  bool HasParkingArea() const { return m_insideParking || m_insideStructuredParking; }
  bool HasParkingSignals() const
  {
    return HasParkingArea() || m_nearParkingArea || m_nearParkingEntrance || m_nearParkingSpace ||
           m_insideStreetSideParking || m_insideParkingLane || m_insideLargeBuilding;
  }
  bool SuppressesParkingFree() const { return m_insideStreetSideParking || m_insideParkingLane; }
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
  case AccuracyBand::Unusable: return std::numeric_limits<double>::max();
  }
  return std::numeric_limits<double>::max();
}

inline bool IsUnambiguous(double bestScore, double runnerUpScore, AccuracyBand accuracy)
{
  if (accuracy == AccuracyBand::Poor || accuracy == AccuracyBand::Unusable)
    return false;
  if (runnerUpScore == kNoRunnerUpScore)
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
  {
    double const providerSpeed = std::max(0.0, info.m_speed);
    if (info.HasSpeedAccuracy() && deltaSeconds > 0.0 && rawStepM >= 0.0)
    {
      double const plausibleRawLimitM = std::max(20.0, providerSpeed * deltaSeconds * 4.0 + 10.0);
      if (rawStepM <= plausibleRawLimitM)
      {
        double const displacementSpeed = rawStepM / deltaSeconds;
        double const providerWeight = std::clamp(1.0 - info.m_speedAccuracy / 5.0, 0.25, 1.0);
        return providerWeight * providerSpeed + (1.0 - providerWeight) * displacementSpeed;
      }
    }
    return providerSpeed;
  }
  if (deltaSeconds <= 0.0 || rawStepM < 0.0 || rawStepM > 80.0)
    return 0.0;
  return rawStepM / deltaSeconds;
}

inline double HeadingWeightForSpeed(double speedMps)
{
  if (speedMps <= kDirectionUsefulMinSpeedMps)
    return 0.0;
  return std::clamp(
      (speedMps - kDirectionUsefulMinSpeedMps) / (kDirectionFullWeightSpeedMps - kDirectionUsefulMinSpeedMps), 0.0,
      1.0);
}

inline double BearingReliability(location::GpsInfo const & info)
{
  if (!info.HasBearing())
    return 0.0;
  if (!info.HasBearingAccuracy())
    return 0.65;
  if (info.m_bearingAccuracy <= 10.0)
    return 1.0;
  if (info.m_bearingAccuracy >= 60.0)
    return 0.10;
  return 1.0 - (info.m_bearingAccuracy - 10.0) / 50.0 * 0.90;
}

inline double TrajectoryHeadingWeight(double speedMps, double motionConfidence)
{
  motionConfidence = std::clamp(motionConfidence, 0.0, 1.0);
  double const lowSpeedWeight = 0.35 * motionConfidence;
  return std::max(lowSpeedWeight, HeadingWeightForSpeed(speedMps) * motionConfidence);
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

inline double ProgressScaleM(location::GpsInfo const & info, double expectedM, double motionConfidence)
{
  double floorM = 10.0;
  auto const accuracy = GetAccuracyBand(info);
  if (accuracy == AccuracyBand::Good && motionConfidence >= 0.35)
  {
    double const confidence = std::clamp((motionConfidence - 0.35) / 0.65, 0.0, 1.0);
    floorM = 6.0 - 2.0 * confidence;
  }
  else if (accuracy == AccuracyBand::Moderate && motionConfidence >= 0.60)
  {
    floorM = 7.0;
  }
  return std::max(floorM, expectedM + 3.0);
}

inline double ParkingCandidateScoreAdjustment(AreaContext const & context, RoadMetadata const & metadata,
                                              RoadRelation relation, bool candidateInsideParkingArea,
                                              double motionConfidence)
{
  if (context.SuppressesParkingFree())
    return 0.0;

  double adjustment = 0.0;
  if (candidateInsideParkingArea && context.HasParkingArea())
  {
    if (metadata.m_parkingAisle)
      adjustment -= 0.45;
    else if (metadata.m_class == RoadClass::Service)
      adjustment -= 0.28;

    if (context.m_nearParkingEntrance && relation == RoadRelation::Connected)
      adjustment -= 0.18;
  }
  else if (candidateInsideParkingArea && context.m_nearParkingEntrance && relation == RoadRelation::Connected &&
           motionConfidence >= 0.25)
  {
    // Entry transition: useful only when topology and coherent motion agree. Mere proximity to a
    // car park must not pull a slowly moving vehicle off the adjacent public road.
    adjustment -= metadata.m_parkingAisle ? 0.28 : (metadata.m_class == RoadClass::Service ? 0.18 : 0.0);
    if (metadata.m_driveway)
      adjustment -= 0.08;
  }

  if (context.m_nearParkingSpace && candidateInsideParkingArea && metadata.m_parkingAisle)
    adjustment -= 0.06;

  if (context.HasParkingArea() && !candidateInsideParkingArea && relation == RoadRelation::Unrelated &&
      metadata.m_class != RoadClass::Service)
  {
    adjustment += 0.15;
  }

  return adjustment;
}

inline bool IsParallelCandidateAmbiguous(double undirectedAngleDegrees, double separationM, double scoreGap,
                                         double semanticGap, location::GpsInfo const & info, double motionConfidence)
{
  if (undirectedAngleDegrees > 20.0 || scoreGap > 0.75)
    return false;
  double const separationLimitM = std::clamp(info.m_horizontalAccuracy * 1.5, 8.0, 30.0);
  if (separationM > separationLimitM)
    return false;
  // Strong, motion-supported semantic evidence (for example a mapped aisle inside the parking
  // polygon versus a parallel public road outside it) may break an otherwise parallel tie.
  return !(semanticGap >= 0.25 && motionConfidence >= 0.35);
}

inline double ParkingReleaseTimeSeconds(AreaContext const & context, bool recentEntranceHint)
{
  double seconds = context.m_insideStructuredParking ? 2.0 : (context.m_insideParking ? 3.0 : 5.0);
  if (recentEntranceHint && context.HasParkingArea())
    seconds = std::max(1.5, seconds - 1.0);
  return seconds;
}

inline bool ParkingReleaseEligible(location::GpsInfo const & info, AreaContext const & context, bool roadMismatch,
                                   bool bestParkingRoad, bool stationaryHold, bool motionEstablished)
{
  if (!info.HasSpeed() || info.m_speed > kParkingFreeMaxSpeedMps || context.SuppressesParkingFree())
    return false;

  // Entrance proximity is a transition hint only. It must never release a car that is simply
  // travelling slowly on a public road beside a car-park entrance.
  if (!context.HasParkingArea() && !context.m_insideLargeBuilding)
    return false;

  if (context.m_insideLargeBuilding && !context.HasParkingArea())
    return roadMismatch;

  // A moving vehicle on a plausible mapped parking road remains road-snapped even below 6 km/h.
  // ParkingFree is for final manoeuvring/stationary positioning, missing geometry or sustained
  // mismatch, not a speed threshold by itself.
  if (bestParkingRoad && !roadMismatch && motionEstablished)
    return false;
  if (bestParkingRoad && !roadMismatch && !stationaryHold && info.m_speed > kParkingFinePositionMaxSpeedMps)
    return false;
  return roadMismatch || stationaryHold || !bestParkingRoad || info.m_speed <= kParkingFinePositionMaxSpeedMps;
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
