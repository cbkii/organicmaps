#include "routing/routing_session.hpp"

#include "routing/free_driving_area_context.hpp"
#include "routing/free_driving_road_matcher.hpp"
#include "routing/free_driving_road_snap_policy.hpp"

#include "platform/platform.hpp"
#include "platform/settings.hpp"

#include "geometry/mercator.hpp"
#include "geometry/parametrized_segment.hpp"

#include "base/logging.hpp"

#include <algorithm>
#include <cmath>
#include <cstdint>
#include <functional>
#include <limits>
#include <map>
#include <string>
#include <utility>
#include <vector>

namespace routing
{
namespace
{
double constexpr kPi = 3.14159265358979323846;
double constexpr kUnrelatedSwitchMargin = 0.55;
double constexpr kStrongScoreHeadroom = 0.35;
double constexpr kMaximumProgressIntervalSeconds = 5.0;

char constexpr kPersistedStateKey[] = "InCarFreeDrivingMatchStateV2";
char constexpr kPersistedLatitudeKey[] = "InCarFreeDrivingMatchLatitudeV2";
char constexpr kPersistedLongitudeKey[] = "InCarFreeDrivingMatchLongitudeV2";
char constexpr kPersistedTimestampKey[] = "InCarFreeDrivingMatchTimestampV2";

struct CandidateSeed
{
  EdgeProj m_projection;
  bool m_fromCorridor = false;
  double m_pathDistanceM = -1.0;
  size_t m_hops = 0;
};

struct Candidate
{
  EdgeProj m_projection;
  free_driving_snap::RoadRelation m_relation = free_driving_snap::RoadRelation::Unrelated;
  free_driving_snap::RoadMetadata m_metadata;
  bool m_hasMetadata = false;
  bool m_fromCorridor = false;
  double m_pathDistanceM = -1.0;
  double m_distanceM = 0.0;
  double m_headingDegrees = 0.0;
  double m_distancePenalty = 0.0;
  double m_headingPenalty = 0.0;
  double m_continuityPenalty = 0.0;
  double m_pathProgressPenalty = 0.0;
  double m_chordProgressPenalty = 0.0;
  double m_roadClassPenalty = 0.0;
  double m_score = std::numeric_limits<double>::max();
};

char const * StateName(free_driving_snap::MatchState state)
{
  switch (state)
  {
  case free_driving_snap::MatchState::Unsnapped: return "unsnapped";
  case free_driving_snap::MatchState::Road: return "road";
  case free_driving_snap::MatchState::ParkingFree: return "parking-free";
  case free_driving_snap::MatchState::OffRoadFree: return "off-road-free";
  }
  return "unknown";
}

bool SameDirectedEdge(Edge const & lhs, Edge const & rhs)
{
  return lhs.SameRoadSegmentAndDirection(rhs);
}

bool SamePhysicalSegment(Edge const & lhs, Edge const & rhs)
{
  return lhs.GetFeatureId() == rhs.GetFeatureId() && lhs.GetSegId() == rhs.GetSegId();
}

bool SameLogicalRoad(Edge const & lhs, Edge const & rhs)
{
  return lhs.GetFeatureId() == rhs.GetFeatureId() && lhs.IsForward() == rhs.IsForward();
}

free_driving_snap::RoadRelation RelationTo(AsyncRouter & router, Edge const & previous, Edge const & candidate)
{
  if (SameDirectedEdge(previous, candidate))
    return free_driving_snap::RoadRelation::SameDirectedEdge;
  if (previous.GetFeatureId() == candidate.GetFeatureId() && previous.IsForward() != candidate.IsForward())
    return free_driving_snap::RoadRelation::ReverseSameRoad;
  if (SameLogicalRoad(previous, candidate))
    return free_driving_snap::RoadRelation::SameRoad;
  if (router.AreRoadEdgesConnected(previous, candidate))
    return free_driving_snap::RoadRelation::Connected;
  return free_driving_snap::RoadRelation::Unrelated;
}

m2::PointD ProjectToEdge(m2::PointD const & point, Edge const & edge)
{
  m2::ParametrizedSegment<m2::PointD> const segment(edge.GetStartPoint(), edge.GetEndPoint());
  return segment.ClosestPointTo(point);
}

m2::PointD DirectionFromBearing(double bearingDegrees)
{
  double const angle = location::BearingToAngle(bearingDegrees) * kPi / 180.0;
  return {std::cos(angle), std::sin(angle)};
}

double DirectionAngleDegrees(m2::PointD const & lhs, m2::PointD const & rhs)
{
  double const lhsLength = std::hypot(lhs.x, lhs.y);
  double const rhsLength = std::hypot(rhs.x, rhs.y);
  if (lhsLength <= 1e-9 || rhsLength <= 1e-9)
    return 0.0;
  double const cosine = std::clamp((lhs.x * rhs.x + lhs.y * rhs.y) / (lhsLength * rhsLength), -1.0, 1.0);
  return std::acos(cosine) * 180.0 / kPi;
}

uint64_t CandidateToken(Edge const & edge)
{
  uint64_t token = static_cast<uint64_t>(std::hash<FeatureID>{}(edge.GetFeatureId()));
  token ^= static_cast<uint64_t>(edge.GetSegId()) * 0x9e3779b97f4a7c15ULL;
  token ^= edge.IsForward() ? 0x85ebca6b27d4eb2fULL : 0xc2b2ae3d27d4eb4fULL;
  return token == 0 ? 1 : token;
}

void ApplyPoint(m2::PointD const & point, location::GpsInfo & displayLocation)
{
  displayLocation.m_latitude = mercator::YToLat(point.y);
  displayLocation.m_longitude = mercator::XToLon(point.x);
}

void ApplyProjection(EdgeProj const & projection, location::GpsInfo & displayLocation)
{
  ApplyPoint(projection.m_point, displayLocation);
}

void AddUniqueSeed(std::vector<CandidateSeed> & seeds, CandidateSeed seed)
{
  for (auto & existing : seeds)
  {
    if (!SameDirectedEdge(existing.m_projection.m_edge, seed.m_projection.m_edge))
      continue;
    if (seed.m_fromCorridor && !existing.m_fromCorridor)
      existing = std::move(seed);
    return;
  }
  seeds.push_back(std::move(seed));
}

void AddUniqueEdge(std::vector<Edge> & edges, Edge const & edge)
{
  if (std::none_of(edges.begin(), edges.end(), [&edge](Edge const & existing) { return SameDirectedEdge(existing, edge); }))
    edges.push_back(edge);
}

double ObservationIntervalSeconds(double previousTimestamp, double currentTimestamp)
{
  if (previousTimestamp <= 0.0 || currentTimestamp <= previousTimestamp)
    return 0.0;
  double const delta = currentTimestamp - previousTimestamp;
  return delta <= free_driving_snap::kMaxAcceptedObservationGapSeconds ? delta : 0.0;
}

double ExpectedTravelMeters(location::GpsInfo const & info, double intervalSeconds, double rawTravelM)
{
  if (intervalSeconds <= 0.0)
    return 0.0;
  if (!info.HasSpeed())
    return rawTravelM;

  double const speedTravelM = std::max(0.0, info.m_speed) * intervalSeconds;
  double const plausibleRawLimitM = std::max(20.0, speedTravelM * 3.0 + 15.0);
  if (rawTravelM >= 0.0 && rawTravelM <= plausibleRawLimitM)
    return 0.8 * speedTravelM + 0.2 * rawTravelM;
  return speedTravelM;
}

Candidate ScoreCandidate(AsyncRouter & router, CandidateSeed seed, location::GpsInfo const & info,
                         m2::PointD const & rawPoint, m2::PointD const & movementDirection, double headingWeight,
                         EdgeProj const * previous, double roadIntervalSeconds, double rawTravelSinceAcceptedM,
                         bool parkingContext)
{
  Candidate candidate;
  candidate.m_projection = std::move(seed.m_projection);
  candidate.m_fromCorridor = seed.m_fromCorridor;
  candidate.m_pathDistanceM = seed.m_pathDistanceM;
  candidate.m_distanceM = mercator::DistanceOnEarth(rawPoint, candidate.m_projection.m_point);
  candidate.m_distancePenalty = candidate.m_distanceM / std::max(6.0, info.m_horizontalAccuracy);

  double const effectiveSpeedMps =
      info.HasSpeed() ? std::max(0.0, info.m_speed)
                      : (roadIntervalSeconds > 0.0 ? rawTravelSinceAcceptedM / roadIntervalSeconds : 0.0);
  bool const lowSpeed = effectiveSpeedMps < free_driving_snap::kCruiseSpeedMps;
  if (previous != nullptr)
    candidate.m_relation = RelationTo(router, previous->m_edge, candidate.m_projection.m_edge);

  candidate.m_continuityPenalty =
      previous == nullptr ? 0.0 : free_driving_snap::ContinuityPenalty(candidate.m_relation, lowSpeed);

  if (!movementDirection.IsAlmostZero())
  {
    candidate.m_headingDegrees = DirectionAngleDegrees(movementDirection, candidate.m_projection.m_edge.GetDirection());
    candidate.m_headingPenalty =
        free_driving_snap::HeadingPenaltyDegrees(candidate.m_headingDegrees, headingWeight);
  }

  bool const needMetadata = candidate.m_relation == free_driving_snap::RoadRelation::Unrelated || parkingContext;
  if (needMetadata)
    candidate.m_hasMetadata = router.GetFreeDrivingRoadMetadata(candidate.m_projection.m_edge, candidate.m_metadata);

  if (previous != nullptr && roadIntervalSeconds > 0.0)
  {
    double const expectedM = ExpectedTravelMeters(info, roadIntervalSeconds, rawTravelSinceAcceptedM);
    double const scaleM = std::max(10.0, expectedM + 5.0);
    double const chordM = mercator::DistanceOnEarth(previous->m_point, candidate.m_projection.m_point);

    if (candidate.m_pathDistanceM >= 0.0)
    {
      candidate.m_pathProgressPenalty =
          std::min(2.0, std::abs(candidate.m_pathDistanceM - expectedM) / scaleM) * 0.70;

      double const curveRatio = chordM > 3.0 ? candidate.m_pathDistanceM / chordM : 1.0;
      double const chordWeight = candidate.m_metadata.m_roundabout || curveRatio > 1.15 ? 0.04 : 0.12;
      candidate.m_chordProgressPenalty =
          std::min(2.0, std::abs(chordM - expectedM) / scaleM) * chordWeight;
    }
    else
    {
      // Spatial recovery still retains a weak progress clue. It never outranks the local
      // along-road corridor because chord distance underestimates bends and roundabouts.
      candidate.m_chordProgressPenalty =
          std::min(2.0, std::abs(chordM - expectedM) / scaleM) * 0.18;
    }

    if (candidate.m_relation == free_driving_snap::RoadRelation::SameDirectedEdge)
    {
      double const previousToEndM = mercator::DistanceOnEarth(previous->m_point, previous->m_edge.GetEndPoint());
      double const candidateToEndM =
          mercator::DistanceOnEarth(candidate.m_projection.m_point, candidate.m_projection.m_edge.GetEndPoint());
      if (candidateToEndM > previousToEndM + 3.0)
        candidate.m_pathProgressPenalty += 0.8;
    }
  }

  candidate.m_roadClassPenalty =
      free_driving_snap::RoadClassSpeedPenalty(info, candidate.m_metadata, candidate.m_relation);
  candidate.m_score = candidate.m_distancePenalty + candidate.m_headingPenalty + candidate.m_continuityPenalty +
                      candidate.m_pathProgressPenalty + candidate.m_chordProgressPenalty +
                      candidate.m_roadClassPenalty;
  return candidate;
}

bool IsAcceptable(Candidate const & candidate, location::GpsInfo const & info, bool hasCurrentRoad)
{
  auto const accuracy = free_driving_snap::GetAccuracyBand(info);
  if (accuracy == free_driving_snap::AccuracyBand::Unusable ||
      candidate.m_distanceM > free_driving_snap::AcceptanceDistanceM(info))
  {
    return false;
  }

  if (accuracy == free_driving_snap::AccuracyBand::Poor)
  {
    return hasCurrentRoad && candidate.m_relation != free_driving_snap::RoadRelation::Unrelated &&
           candidate.m_score <= free_driving_snap::CandidateScoreLimit(accuracy);
  }
  return candidate.m_score <= free_driving_snap::CandidateScoreLimit(accuracy);
}

Candidate const * FindCurrentCandidate(std::vector<Candidate> const & candidates, Edge const & current)
{
  auto const it = std::find_if(candidates.begin(), candidates.end(), [&current](Candidate const & candidate)
  { return SameDirectedEdge(candidate.m_projection.m_edge, current); });
  return it == candidates.end() ? nullptr : &*it;
}

double RunnerUpScore(std::vector<Candidate> const & candidates, Candidate const & best)
{
  for (auto const & candidate : candidates)
  {
    if (candidate.m_projection.m_edge.GetFeatureId() != best.m_projection.m_edge.GetFeatureId())
      return candidate.m_score;
  }
  return std::numeric_limits<double>::infinity();
}

bool HasDecisiveCorridorSeed(AsyncRouter & router, std::vector<CandidateSeed> const & seeds,
                             location::GpsInfo const & info, m2::PointD const & rawPoint,
                             m2::PointD const & movementDirection, Edge const & current, bool stationaryHold,
                             bool motionEstablished)
{
  if (seeds.empty())
    return false;

  if (!movementDirection.IsAlmostZero() && motionEstablished &&
      DirectionAngleDegrees(movementDirection, current.GetDirection()) > 120.0)
  {
    // A confirmed reversal must expose the reverse-direction spatial candidate rather than
    // letting the forward sticky edge hide a U-turn.
    return false;
  }

  double const strongDistanceM = free_driving_snap::StrongRoadDistanceM(info);
  for (auto const & seed : seeds)
  {
    auto const relation = RelationTo(router, current, seed.m_projection.m_edge);
    if (relation == free_driving_snap::RoadRelation::Unrelated ||
        mercator::DistanceOnEarth(rawPoint, seed.m_projection.m_point) > strongDistanceM)
    {
      continue;
    }

    if (stationaryHold || movementDirection.IsAlmostZero() ||
        DirectionAngleDegrees(movementDirection, seed.m_projection.m_edge.GetDirection()) <= 75.0)
    {
      return true;
    }
  }
  return false;
}

void RefreshDisplayCorridor(AsyncRouter & router, EdgeProj const & accepted, location::GpsInfo const & info,
                            std::vector<Edge> & displayCorridor)
{
  displayCorridor.clear();
  AddUniqueEdge(displayCorridor, accepted.m_edge);

  double const speedMps = info.HasSpeed() ? std::max(0.0, info.m_speed) : 0.0;
  double const distanceM = std::clamp(speedMps * 2.5 + 35.0, 60.0, 120.0);
  std::vector<FreeDrivingCorridorProjection> corridor;
  router.FindFreeDrivingRoadCorridor(accepted, accepted.m_point, distanceM,
                                     free_driving_snap::kMaxDisplayCorridorEdges, 4, corridor);
  for (auto const & item : corridor)
  {
    AddUniqueEdge(displayCorridor, item.m_projection.m_edge);
    if (displayCorridor.size() >= free_driving_snap::kMaxDisplayCorridorEdges)
      break;
  }
}

void SchedulePersistedState(free_driving_snap::MatchState state, location::GpsInfo const & rawLocation)
{
  std::map<std::string, std::string> values;
  values[kPersistedStateKey] = std::to_string(static_cast<int>(state));
  values[kPersistedLatitudeKey] = std::to_string(rawLocation.m_latitude);
  values[kPersistedLongitudeKey] = std::to_string(rawLocation.m_longitude);
  values[kPersistedTimestampKey] = std::to_string(rawLocation.m_timestamp);

  GetPlatform().RunTask(Platform::Thread::File, [values = std::move(values)]() { settings::Update(values); });
}
}  // namespace

void RoutingSession::SetFreeDrivingRoadSnapEnabled(bool enabled)
{
  CHECK_THREAD_CHECKER(m_threadChecker, ());
  if (m_freeDrivingRoadSnapEnabled == enabled)
    return;
  ResetFreeDrivingRoadGraphMatch();
  m_freeDrivingRoadSnapEnabled = enabled;
}

void RoutingSession::SetFreeDrivingAreaContextProvider(FreeDrivingAreaContextProvider provider)
{
  CHECK_THREAD_CHECKER(m_threadChecker, ());
  m_freeDrivingAreaContextProvider = std::move(provider);
  m_freeDrivingAreaContext = {};
  m_freeDrivingHasAreaContextPoint = false;
  m_freeDrivingAreaContextTimestamp = 0.0;
}

void RoutingSession::ResetFreeDrivingRoadGraphMatch()
{
  CHECK_THREAD_CHECKER(m_threadChecker, ());
  m_freeDrivingPositionAccumulator.Clear();
  m_freeDrivingRoadMatcher.Reset();
  m_freeDrivingProjection = {};
  m_freeDrivingProjectionSeeded = false;
  m_freeDrivingDisplayCorridor.clear();
  m_freeDrivingLastRawPoint = {};
  m_freeDrivingHasLastRawPoint = false;
  m_freeDrivingLastObservationTimestamp = 0.0;
  m_freeDrivingLastAcceptedRawPoint = {};
  m_freeDrivingHasLastAcceptedRawPoint = false;
  m_freeDrivingLastAcceptedRoadTimestamp = 0.0;
  m_freeDrivingAreaContext = {};
  m_freeDrivingAreaContextPoint = {};
  m_freeDrivingHasAreaContextPoint = false;
  m_freeDrivingAreaContextTimestamp = 0.0;
}

void RoutingSession::ObserveFreeDrivingLocation(location::GpsInfo const & rawLocation)
{
  CHECK_THREAD_CHECKER(m_threadChecker, ());

  if (!m_freeDrivingRoadSnapEnabled || IsActive() || !m_router || !m_router->HasRouter())
  {
    if (m_freeDrivingProjectionSeeded ||
        m_freeDrivingRoadMatcher.GetState() != free_driving_snap::MatchState::Unsnapped)
    {
      ResetFreeDrivingRoadGraphMatch();
    }
    return;
  }

  m2::PointD const rawPoint = mercator::FromLatLon(rawLocation.m_latitude, rawLocation.m_longitude);

  if (m_freeDrivingLastObservationTimestamp > 0.0 &&
      rawLocation.m_timestamp - m_freeDrivingLastObservationTimestamp >
          free_driving_snap::kMaxAcceptedObservationGapSeconds)
  {
    // ACC sleep/provider pauses invalidate fine-grained evidence but retain the coarse in-memory
    // state as a fast, safe reacquisition hint.
    auto const previousState = m_freeDrivingRoadMatcher.GetState();
    ResetFreeDrivingRoadGraphMatch();
    if (previousState == free_driving_snap::MatchState::Road)
      m_freeDrivingRoadMatcher.SetRoadRestoreHint(true);
    else if (previousState == free_driving_snap::MatchState::ParkingFree ||
             previousState == free_driving_snap::MatchState::OffRoadFree)
      m_freeDrivingRoadMatcher.RestoreFreeState(previousState);
  }

  double const observationIntervalSeconds =
      ObservationIntervalSeconds(m_freeDrivingLastObservationTimestamp, rawLocation.m_timestamp);
  double const evidenceDeltaSeconds =
      free_driving_snap::TemporalEvidenceDeltaSeconds(m_freeDrivingLastObservationTimestamp, rawLocation.m_timestamp);
  double rawStepM = 0.0;
  if (m_freeDrivingHasLastRawPoint && observationIntervalSeconds > 0.0)
    rawStepM = mercator::DistanceOnEarth(m_freeDrivingLastRawPoint, rawPoint);

  if (m_freeDrivingAreaContextProvider)
  {
    double contextMoveM = 0.0;
    if (m_freeDrivingHasAreaContextPoint)
      contextMoveM = mercator::DistanceOnEarth(m_freeDrivingAreaContextPoint, rawPoint);
    if (!m_freeDrivingHasAreaContextPoint ||
        free_driving_snap::ShouldRefreshAreaContext(m_freeDrivingAreaContextTimestamp, rawLocation.m_timestamp,
                                                    contextMoveM))
    {
      m_freeDrivingAreaContext = m_freeDrivingAreaContextProvider(rawPoint);
      m_freeDrivingAreaContextPoint = rawPoint;
      m_freeDrivingHasAreaContextPoint = true;
      m_freeDrivingAreaContextTimestamp = rawLocation.m_timestamp;
    }
  }

  if (!m_freeDrivingPersistenceLoaded)
  {
    m_freeDrivingPersistenceLoaded = true;
    int persistedState = 0;
    double persistedLatitude = 0.0;
    double persistedLongitude = 0.0;
    double persistedTimestamp = 0.0;
    if (settings::Get(kPersistedStateKey, persistedState) &&
        settings::Get(kPersistedLatitudeKey, persistedLatitude) &&
        settings::Get(kPersistedLongitudeKey, persistedLongitude) &&
        settings::Get(kPersistedTimestampKey, persistedTimestamp))
    {
      double const ageSeconds = rawLocation.m_timestamp - persistedTimestamp;
      m2::PointD const persistedPoint = mercator::FromLatLon(persistedLatitude, persistedLongitude);
      double const distanceM = mercator::DistanceOnEarth(persistedPoint, rawPoint);
      if (ageSeconds >= 0.0 && ageSeconds <= free_driving_snap::kPersistenceMaxAgeSeconds &&
          distanceM <= free_driving_snap::kPersistenceMaxDistanceM)
      {
        auto const state = static_cast<free_driving_snap::MatchState>(persistedState);
        if (state == free_driving_snap::MatchState::Road)
        {
          m_freeDrivingRoadMatcher.SetRoadRestoreHint(true);
        }
        else if (state == free_driving_snap::MatchState::OffRoadFree)
        {
          m_freeDrivingRoadMatcher.RestoreFreeState(state);
        }
        else if (state == free_driving_snap::MatchState::ParkingFree &&
                 (m_freeDrivingAreaContext.HasParkingArea() || m_freeDrivingAreaContext.m_insideLargeBuilding))
        {
          m_freeDrivingRoadMatcher.RestoreFreeState(state);
        }
      }
    }
  }

  m_freeDrivingPositionAccumulator.PushNextPoint(rawPoint);

  auto const accuracy = free_driving_snap::GetAccuracyBand(rawLocation);
  if (accuracy == free_driving_snap::AccuracyBand::Unusable)
  {
    m_freeDrivingLastRawPoint = rawPoint;
    m_freeDrivingHasLastRawPoint = true;
    m_freeDrivingLastObservationTimestamp = rawLocation.m_timestamp;
    return;
  }

  location::GpsInfo policyInfo = rawLocation;
  double const effectiveSpeedMps =
      free_driving_snap::EffectiveSpeedMps(rawLocation, rawStepM, observationIntervalSeconds);
  if (!policyInfo.HasSpeed() && observationIntervalSeconds > 0.0)
    policyInfo.m_speed = effectiveSpeedMps;

  m2::PointD displacementDirection;
  double const displacementThresholdM = std::clamp(rawLocation.m_horizontalAccuracy * 0.35, 2.0, 6.0);
  if (m_freeDrivingHasLastRawPoint && observationIntervalSeconds > 0.0 && rawStepM >= displacementThresholdM &&
      rawStepM <= 80.0)
  {
    displacementDirection = rawPoint - m_freeDrivingLastRawPoint;
  }
  if (displacementDirection.IsAlmostZero())
  {
    displacementDirection =
        m_freeDrivingPositionAccumulator.GetRecentDirection(free_driving_snap::RecentDirectionTrackLengthM(policyInfo));
  }

  m2::PointD movementDirection = displacementDirection;
  double headingWeight = free_driving_snap::HeadingWeightForSpeed(effectiveSpeedMps);
  if (rawLocation.HasBearing())
  {
    m2::PointD const bearingDirection = DirectionFromBearing(rawLocation.m_bearing);
    movementDirection = bearingDirection;
    if (!displacementDirection.IsAlmostZero())
    {
      headingWeight *= free_driving_snap::HeadingAgreementFactor(
          DirectionAngleDegrees(bearingDirection, displacementDirection));
    }
  }

  bool const stationaryHold =
      free_driving_snap::IsStationaryHold(policyInfo, rawStepM, observationIntervalSeconds);
  bool const motionEstablished =
      free_driving_snap::HasEstablishedMotion(policyInfo, rawStepM, observationIntervalSeconds);

  EdgeProj const * previous =
      m_freeDrivingProjectionSeeded && m_freeDrivingRoadMatcher.GetState() == free_driving_snap::MatchState::Road
          ? &m_freeDrivingProjection
          : nullptr;

  double roadIntervalSeconds = 0.0;
  double rawTravelSinceAcceptedM = 0.0;
  if (previous != nullptr && m_freeDrivingLastAcceptedRoadTimestamp > 0.0 &&
      rawLocation.m_timestamp > m_freeDrivingLastAcceptedRoadTimestamp)
  {
    double const interval = rawLocation.m_timestamp - m_freeDrivingLastAcceptedRoadTimestamp;
    if (interval <= kMaximumProgressIntervalSeconds)
    {
      roadIntervalSeconds = interval;
      if (m_freeDrivingHasLastAcceptedRawPoint)
        rawTravelSinceAcceptedM = mercator::DistanceOnEarth(m_freeDrivingLastAcceptedRawPoint, rawPoint);
    }
  }

  std::vector<CandidateSeed> seeds;
  if (previous != nullptr)
  {
    std::vector<FreeDrivingCorridorProjection> corridor;
    m_router->FindFreeDrivingRoadCorridor(*previous, rawPoint,
                                          free_driving_snap::CorridorSearchDistanceM(policyInfo, roadIntervalSeconds),
                                          free_driving_snap::kMaxCorridorEdges,
                                          free_driving_snap::kMaxCorridorHops, corridor);
    for (auto const & item : corridor)
      AddUniqueSeed(seeds, {item.m_projection, true, item.m_pathDistanceM, item.m_hops});

    EdgeProj current = *previous;
    current.m_point = ProjectToEdge(rawPoint, current.m_edge);
    AddUniqueSeed(seeds, {current, true, 0.0, 0});
  }

  bool const corridorDecisive =
      previous != nullptr && HasDecisiveCorridorSeed(*m_router, seeds, policyInfo, rawPoint, movementDirection,
                                                     previous->m_edge, stationaryHold, motionEstablished);
  if (!corridorDecisive)
  {
    size_t const roadCount = previous != nullptr || accuracy != free_driving_snap::AccuracyBand::Good
                                 ? free_driving_snap::kRecoverySpatialRoadCount
                                 : free_driving_snap::kNormalSpatialRoadCount;
    std::vector<EdgeProj> spatial;
    m_router->FindClosestProjectionsToRoad(rawPoint, free_driving_snap::SearchRadiusM(policyInfo), roadCount, spatial);
    for (auto & projection : spatial)
      AddUniqueSeed(seeds, {std::move(projection), false, -1.0, 0});
  }

  bool const parkingContext = m_freeDrivingAreaContext.HasParkingArea() ||
                              m_freeDrivingAreaContext.m_insideLargeBuilding ||
                              m_freeDrivingAreaContext.m_nearParkingEntrance;
  std::vector<Candidate> candidates;
  candidates.reserve(seeds.size());
  for (auto & seed : seeds)
  {
    candidates.push_back(ScoreCandidate(*m_router, std::move(seed), policyInfo, rawPoint, movementDirection,
                                        headingWeight, previous, roadIntervalSeconds, rawTravelSinceAcceptedM,
                                        parkingContext));
  }
  std::sort(candidates.begin(), candidates.end(),
            [](Candidate const & lhs, Candidate const & rhs) { return lhs.m_score < rhs.m_score; });

  Candidate const * best = candidates.empty() ? nullptr : &candidates.front();
  Candidate const * current = previous != nullptr ? FindCurrentCandidate(candidates, previous->m_edge) : nullptr;
  bool const bestAcceptable = best != nullptr && IsAcceptable(*best, policyInfo, previous != nullptr);
  double const runnerUpScore = best != nullptr ? RunnerUpScore(candidates, *best) : std::numeric_limits<double>::infinity();
  bool const bestUnambiguous =
      best != nullptr && free_driving_snap::IsUnambiguous(best->m_score, runnerUpScore, accuracy);
  bool const bestStrong =
      bestAcceptable && best->m_distanceM <= free_driving_snap::StrongRoadDistanceM(policyInfo) &&
      best->m_score <= free_driving_snap::CandidateScoreLimit(accuracy) - kStrongScoreHeadroom;
  bool const bestBeatsCurrent = current == nullptr || (best != nullptr && best == current) ||
                                (best != nullptr && best->m_score + kUnrelatedSwitchMargin < current->m_score);
  bool const roadMismatch = !bestAcceptable;
  bool const bestParkingAisle = best != nullptr && best->m_hasMetadata && best->m_metadata.m_parkingAisle;

  double const openWeakThresholdM =
      std::max(free_driving_snap::StrongRoadDistanceM(policyInfo), rawLocation.m_horizontalAccuracy * 1.5 + 2.0);
  double const genericWeakThresholdM =
      std::max(free_driving_snap::StrongRoadDistanceM(policyInfo), rawLocation.m_horizontalAccuracy * 2.2 + 3.0);
  bool const openContext = m_freeDrivingAreaContext.m_insideStrongOpenArea ||
                           m_freeDrivingAreaContext.m_insideWeakOpenArea;
  bool const weakOpenRoadMatch = bestAcceptable && !bestStrong && best->m_distanceM >= openWeakThresholdM && openContext;
  bool const weakGenericRoadMatch =
      bestAcceptable && !bestStrong && best->m_distanceM >= genericWeakThresholdM && !openContext;
  bool const weakRoadMatch = weakOpenRoadMatch || weakGenericRoadMatch;
  bool const offRoadEvidence = roadMismatch || weakRoadMatch;

  double evidenceStepM = 0.0;
  if (offRoadEvidence && observationIntervalSeconds > 0.0)
  {
    double const plausibleStepLimitM = rawLocation.HasSpeed()
                                           ? std::max(60.0, rawLocation.m_speed * observationIntervalSeconds * 4.0 + 30.0)
                                           : 60.0;
    if (rawStepM <= plausibleStepLimitM)
      evidenceStepM = rawStepM;
  }

  bool const parkingReleaseEligible = free_driving_snap::ParkingReleaseEligible(
      policyInfo, m_freeDrivingAreaContext, roadMismatch || weakRoadMatch, bestParkingAisle, stationaryHold);

  auto const currentState = m_freeDrivingRoadMatcher.GetState();
  bool allowFreeStateRoadReacquire = false;
  if (best != nullptr && bestAcceptable && bestStrong && bestUnambiguous && motionEstablished)
  {
    if (currentState == free_driving_snap::MatchState::OffRoadFree)
    {
      allowFreeStateRoadReacquire = true;
    }
    else if (currentState == free_driving_snap::MatchState::ParkingFree)
    {
      allowFreeStateRoadReacquire = !m_freeDrivingAreaContext.HasParkingArea() ||
                                    (m_freeDrivingAreaContext.m_nearParkingEntrance && !bestParkingAisle);
    }
  }

  free_driving_snap::MatcherEvidence evidence;
  evidence.m_hasBestRoad = best != nullptr;
  evidence.m_bestAcceptable = bestAcceptable;
  evidence.m_bestStrong = bestStrong;
  evidence.m_bestUnambiguous = bestUnambiguous;
  evidence.m_bestBeatsCurrent = bestBeatsCurrent;
  evidence.m_bestParkingAisle = bestParkingAisle;
  evidence.m_stationaryHold = stationaryHold;
  evidence.m_motionEstablished = motionEstablished;
  evidence.m_parkingReleaseEligible = parkingReleaseEligible;
  evidence.m_offRoadEvidence = offRoadEvidence;
  evidence.m_weakRoadMatch = weakRoadMatch;
  evidence.m_allowFreeStateRoadReacquire = allowFreeStateRoadReacquire;
  evidence.m_nearParkingEntrance = m_freeDrivingAreaContext.m_nearParkingEntrance;
  evidence.m_accuracy = accuracy;
  evidence.m_relation = best != nullptr ? best->m_relation : free_driving_snap::RoadRelation::Unrelated;
  evidence.m_candidateToken = best != nullptr ? CandidateToken(best->m_projection.m_edge) : 0;
  evidence.m_deltaSeconds = evidenceDeltaSeconds;
  evidence.m_rawStepM = evidenceStepM;

  auto const previousState = m_freeDrivingRoadMatcher.GetState();
  auto const decision = m_freeDrivingRoadMatcher.Update(evidence, m_freeDrivingAreaContext);

  if (decision.m_action == free_driving_snap::MatcherAction::UseBestRoad && best != nullptr)
  {
    bool const edgeChanged = !m_freeDrivingProjectionSeeded ||
                             !SameDirectedEdge(m_freeDrivingProjection.m_edge, best->m_projection.m_edge);
    m_freeDrivingProjection = best->m_projection;
    m_freeDrivingProjectionSeeded = true;
    m_freeDrivingLastAcceptedRawPoint = rawPoint;
    m_freeDrivingHasLastAcceptedRawPoint = true;
    m_freeDrivingLastAcceptedRoadTimestamp = rawLocation.m_timestamp;
    if (edgeChanged || m_freeDrivingDisplayCorridor.empty())
      RefreshDisplayCorridor(*m_router, m_freeDrivingProjection, policyInfo, m_freeDrivingDisplayCorridor);
  }
  else if (decision.m_action == free_driving_snap::MatcherAction::HoldCurrentRoad && previous != nullptr &&
           current != nullptr && IsAcceptable(*current, policyInfo, true))
  {
    // Hold road identity at a stationary/ambiguous junction, but still allow forward position
    // progress on that same directed segment when the current edge remains a plausible explanation.
    m_freeDrivingProjection = current->m_projection;
    m_freeDrivingProjectionSeeded = true;
    m_freeDrivingLastAcceptedRawPoint = rawPoint;
    m_freeDrivingHasLastAcceptedRawPoint = true;
    m_freeDrivingLastAcceptedRoadTimestamp = rawLocation.m_timestamp;
    if (m_freeDrivingDisplayCorridor.empty())
      RefreshDisplayCorridor(*m_router, m_freeDrivingProjection, policyInfo, m_freeDrivingDisplayCorridor);
  }
  else if (decision.m_action == free_driving_snap::MatcherAction::EnterParkingFree ||
           decision.m_action == free_driving_snap::MatcherAction::EnterOffRoadFree)
  {
    m_freeDrivingProjectionSeeded = false;
    m_freeDrivingDisplayCorridor.clear();
    m_freeDrivingHasLastAcceptedRawPoint = false;
    m_freeDrivingLastAcceptedRoadTimestamp = 0.0;
  }

  if (decision.m_stateChanged)
  {
    LOG(LDEBUG, ("Free-driving road snap:", StateName(previousState), "->", StateName(decision.m_state),
                 "speed_mps=", effectiveSpeedMps, "accuracy_m=", rawLocation.m_horizontalAccuracy,
                 "best_score=", best ? best->m_score : -1.0, "runner_up=",
                 std::isfinite(runnerUpScore) ? runnerUpScore : -1.0, "parking=",
                 m_freeDrivingAreaContext.m_insideParking, "structured_parking=",
                 m_freeDrivingAreaContext.m_insideStructuredParking, "strong_open=",
                 m_freeDrivingAreaContext.m_insideStrongOpenArea, "weak_open=",
                 m_freeDrivingAreaContext.m_insideWeakOpenArea));
  }

  bool const persistenceDue = decision.m_stateChanged || m_freeDrivingLastPersistenceTimestamp <= 0.0 ||
                              rawLocation.m_timestamp - m_freeDrivingLastPersistenceTimestamp >=
                                  free_driving_snap::kPersistenceRefreshSeconds;
  if (persistenceDue && rawLocation.m_timestamp > 0.0)
  {
    SchedulePersistedState(m_freeDrivingRoadMatcher.GetState(), rawLocation);
    m_freeDrivingLastPersistenceTimestamp = rawLocation.m_timestamp;
  }

  m_freeDrivingLastRawPoint = rawPoint;
  m_freeDrivingHasLastRawPoint = true;
  m_freeDrivingLastObservationTimestamp = rawLocation.m_timestamp;
}

bool RoutingSession::ProjectFreeDrivingLocationToRoadGraph(location::GpsInfo const & displayInput,
                                                           location::GpsInfo & displayOutput) const
{
  CHECK_THREAD_CHECKER(m_threadChecker, ());
  displayOutput = displayInput;

  if (!m_freeDrivingRoadSnapEnabled || IsActive() ||
      free_driving_snap::GetAccuracyBand(displayInput) == free_driving_snap::AccuracyBand::Unusable)
  {
    return false;
  }

  auto const state = m_freeDrivingRoadMatcher.GetState();
  m2::PointD const rawPoint = mercator::FromLatLon(displayInput.m_latitude, displayInput.m_longitude);

  if (state == free_driving_snap::MatchState::ParkingFree)
  {
    m2::PointD constrained;
    if (!free_driving_snap::ConstrainPointToFreeArea(m_freeDrivingAreaContext, rawPoint, constrained))
      return false;

    double const correctionM = mercator::DistanceOnEarth(rawPoint, constrained);
    double const maxCorrectionM = std::max(30.0, displayInput.m_horizontalAccuracy * 2.0 + 10.0);
    if (correctionM > maxCorrectionM)
      return false;

    ApplyPoint(constrained, displayOutput);
    return true;
  }

  if (state != free_driving_snap::MatchState::Road || !m_freeDrivingProjectionSeeded)
    return false;

  std::vector<Edge> const * edges = &m_freeDrivingDisplayCorridor;
  std::vector<Edge> fallback;
  if (edges->empty())
  {
    fallback.push_back(m_freeDrivingProjection.m_edge);
    edges = &fallback;
  }

  EdgeProj bestProjection;
  double bestScore = std::numeric_limits<double>::max();
  m2::PointD bearingDirection;
  if (displayInput.HasBearing())
    bearingDirection = DirectionFromBearing(displayInput.m_bearing);

  for (auto const & edge : *edges)
  {
    EdgeProj projection{edge, ProjectToEdge(rawPoint, edge)};
    double score = mercator::DistanceOnEarth(rawPoint, projection.m_point);
    if (!bearingDirection.IsAlmostZero() && displayInput.HasSpeed() &&
        displayInput.m_speed > free_driving_snap::kDirectionUsefulMinSpeedMps)
    {
      score += DirectionAngleDegrees(bearingDirection, edge.GetDirection()) / 90.0 * 5.0;
    }

    if (score < bestScore)
    {
      bestScore = score;
      bestProjection = projection;
    }
  }

  double const distanceM = mercator::DistanceOnEarth(rawPoint, bestProjection.m_point);
  double const maxDisplayDistanceM = std::max(20.0, free_driving_snap::AcceptanceDistanceM(displayInput) * 1.6);
  if (distanceM > maxDisplayDistanceM)
    return false;

  ApplyProjection(bestProjection, displayOutput);
  return true;
}
}  // namespace routing
