#include "routing/routing_session.hpp"

#include "routing/free_driving_road_snap_policy.hpp"

#include "geometry/mercator.hpp"
#include "geometry/parametrized_segment.hpp"

#include "base/logging.hpp"

#include <algorithm>
#include <cmath>
#include <limits>
#include <utility>
#include <vector>

namespace routing
{
namespace
{
double constexpr kPi = 3.14159265358979323846;
double constexpr kCandidateScoreLimit = 3.20;
double constexpr kPoorAccuracyHoldScoreLimit = 2.50;
double constexpr kUnrelatedSwitchMargin = 0.65;

struct Candidate
{
  EdgeProj m_projection;
  free_driving_snap::RoadRelation m_relation = free_driving_snap::RoadRelation::Unrelated;
  double m_distanceM = 0.0;
  double m_headingDegrees = 0.0;
  double m_distancePenalty = 0.0;
  double m_headingPenalty = 0.0;
  double m_continuityPenalty = 0.0;
  double m_progressPenalty = 0.0;
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

bool SameLogicalRoad(Edge const & lhs, Edge const & rhs)
{
  return lhs.GetFeatureId() == rhs.GetFeatureId() && lhs.IsForward() == rhs.IsForward();
}

free_driving_snap::RoadRelation RelationTo(AsyncRouter & router, Edge const & previous, Edge const & candidate)
{
  if (SameDirectedEdge(previous, candidate))
    return free_driving_snap::RoadRelation::SameDirectedEdge;
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

bool SameCandidate(AsyncRouter & router, EdgeProj const & lhs, EdgeProj const & rhs)
{
  return RelationTo(router, lhs.m_edge, rhs.m_edge) != free_driving_snap::RoadRelation::Unrelated;
}

void ApplyProjection(EdgeProj const & projection, location::GpsInfo & displayLocation)
{
  displayLocation.m_latitude = mercator::YToLat(projection.m_point.y);
  displayLocation.m_longitude = mercator::XToLon(projection.m_point.x);
}

void AddUniqueProjection(std::vector<EdgeProj> & projections, EdgeProj const & projection)
{
  for (auto const & existing : projections)
    if (SameDirectedEdge(existing.m_edge, projection.m_edge))
      return;
  projections.push_back(projection);
}

std::vector<EdgeProj> FindCandidateProjections(AsyncRouter & router, location::GpsInfo const & info,
                                               m2::PointD const & rawPoint)
{
  std::vector<EdgeProj> projections;
  router.FindClosestProjectionsToRoad(rawPoint, free_driving_snap::SearchRadiusM(info),
                                      free_driving_snap::DirectionProbeCount(info), projections);
  return projections;
}

Candidate ScoreCandidate(AsyncRouter & router, EdgeProj projection, location::GpsInfo const & info,
                         m2::PointD const & rawPoint, m2::PointD const & movementDirection, EdgeProj const * previous,
                         double observationDeltaSeconds)
{
  Candidate candidate;
  candidate.m_projection = std::move(projection);
  candidate.m_distanceM = mercator::DistanceOnEarth(rawPoint, candidate.m_projection.m_point);
  candidate.m_distancePenalty = candidate.m_distanceM / std::max(5.0, info.m_horizontalAccuracy);

  bool const lowSpeed = !info.HasSpeed() || info.m_speed < free_driving_snap::kCruiseSpeedMps;
  if (previous != nullptr)
    candidate.m_relation = RelationTo(router, previous->m_edge, candidate.m_projection.m_edge);
  candidate.m_continuityPenalty = free_driving_snap::ContinuityPenalty(candidate.m_relation, lowSpeed);

  if (!movementDirection.IsAlmostZero())
  {
    candidate.m_headingDegrees = DirectionAngleDegrees(movementDirection, candidate.m_projection.m_edge.GetDirection());
    candidate.m_headingPenalty =
        free_driving_snap::HeadingPenaltyDegrees(candidate.m_headingDegrees, free_driving_snap::HeadingWeight(info));
  }

  if (previous != nullptr && observationDeltaSeconds > 0.0 && info.HasSpeed())
  {
    double const expectedM = std::max(0.0, info.m_speed) * observationDeltaSeconds;
    double const projectedM = mercator::DistanceOnEarth(previous->m_point, candidate.m_projection.m_point);
    double const scaleM = std::max(10.0, expectedM + 5.0);
    candidate.m_progressPenalty = std::min(2.0, std::abs(projectedM - expectedM) / scaleM) * 0.75;

    if (candidate.m_relation == free_driving_snap::RoadRelation::SameDirectedEdge)
    {
      double const previousToEndM = mercator::DistanceOnEarth(previous->m_point, previous->m_edge.GetEndPoint());
      double const candidateToEndM =
          mercator::DistanceOnEarth(candidate.m_projection.m_point, candidate.m_projection.m_edge.GetEndPoint());
      if (candidateToEndM > previousToEndM + 3.0)
        candidate.m_progressPenalty += 1.0;
    }
  }

  candidate.m_score = candidate.m_distancePenalty + candidate.m_headingPenalty + candidate.m_continuityPenalty +
                      candidate.m_progressPenalty;
  return candidate;
}

bool IsAcceptable(Candidate const & candidate, location::GpsInfo const & info, bool hasCurrentRoad)
{
  if (candidate.m_distanceM > free_driving_snap::AcceptanceDistanceM(info))
    return false;

  auto const accuracy = free_driving_snap::GetAccuracyBand(info);
  if (accuracy == free_driving_snap::AccuracyBand::Unusable)
    return false;
  if (accuracy == free_driving_snap::AccuracyBand::Poor)
  {
    // Poor fixes may maintain an already-established road but cannot opportunistically acquire/switch to another.
    return hasCurrentRoad && candidate.m_relation != free_driving_snap::RoadRelation::Unrelated &&
           candidate.m_score <= kPoorAccuracyHoldScoreLimit;
  }
  return candidate.m_score <= kCandidateScoreLimit;
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
  m_freeDrivingPositionAccumulator.Clear();
  m_freeDrivingMatchState = free_driving_snap::MatchState::Unsnapped;
  m_freeDrivingProjection = {};
  m_freeDrivingProjectionSeeded = false;
  m_freeDrivingPendingProjection = {};
  m_freeDrivingPendingProjectionSeeded = false;
  m_freeDrivingPendingObservationCount = 0;
  m_freeDrivingLastRawPoint = {};
  m_freeDrivingHasLastRawPoint = false;
  m_freeDrivingLastObservationTimestamp = 0.0;
  m_freeDrivingAreaContext = {};
  m_freeDrivingAreaContextPoint = {};
  m_freeDrivingHasAreaContextPoint = false;
  m_freeDrivingAreaContextTimestamp = 0.0;
  m_freeDrivingParkingEvidenceSeconds = 0.0;
  m_freeDrivingOffRoadEvidenceSeconds = 0.0;
  m_freeDrivingOffRoadEvidenceDistanceM = 0.0;
  m_freeDrivingReacquireObservationCount = 0;
}

void RoutingSession::ObserveFreeDrivingLocation(location::GpsInfo const & rawLocation)
{
  CHECK_THREAD_CHECKER(m_threadChecker, ());

  if (!m_freeDrivingRoadSnapEnabled || IsActive() || !m_router || !m_router->HasRouter())
  {
    if (m_freeDrivingProjectionSeeded || m_freeDrivingMatchState != free_driving_snap::MatchState::Unsnapped)
      ResetFreeDrivingRoadGraphMatch();
    return;
  }

  m2::PointD const rawPoint = mercator::FromLatLon(rawLocation.m_latitude, rawLocation.m_longitude);

  if (m_freeDrivingLastObservationTimestamp > 0.0 && rawLocation.m_timestamp - m_freeDrivingLastObservationTimestamp >
                                                         free_driving_snap::kMaxAcceptedObservationGapSeconds)
  {
    // Long provider gaps invalidate temporal confidence. Re-seed from this real observation.
    ResetFreeDrivingRoadGraphMatch();
  }

  double const observationDeltaSeconds =
      free_driving_snap::TemporalEvidenceDeltaSeconds(m_freeDrivingLastObservationTimestamp, rawLocation.m_timestamp);
  double rawStepM = 0.0;
  if (m_freeDrivingHasLastRawPoint && observationDeltaSeconds > 0.0)
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

  m_freeDrivingPositionAccumulator.PushNextPoint(rawPoint);

  auto const accuracy = free_driving_snap::GetAccuracyBand(rawLocation);
  if (accuracy == free_driving_snap::AccuracyBand::Unusable)
  {
    // Do not turn a bad GNSS fix into road/parking/off-road evidence. Existing road identity
    // remains available for a later usable fix, but display projection fails open for this fix.
    m_freeDrivingPendingProjectionSeeded = false;
    m_freeDrivingPendingObservationCount = 0;
    m_freeDrivingParkingEvidenceSeconds = 0.0;
    m_freeDrivingOffRoadEvidenceSeconds = 0.0;
    m_freeDrivingOffRoadEvidenceDistanceM = 0.0;
    m_freeDrivingLastRawPoint = rawPoint;
    m_freeDrivingHasLastRawPoint = true;
    m_freeDrivingLastObservationTimestamp = rawLocation.m_timestamp;
    return;
  }

  m2::PointD movementDirection;
  if (rawLocation.HasBearing())
    movementDirection = DirectionFromBearing(rawLocation.m_bearing);
  else
    movementDirection = m_freeDrivingPositionAccumulator.GetRecentDirection(
        free_driving_snap::RecentDirectionTrackLengthM(rawLocation));

  EdgeProj const * previous = m_freeDrivingProjectionSeeded ? &m_freeDrivingProjection : nullptr;
  auto projections = FindCandidateProjections(*m_router, rawLocation, rawPoint);

  // Always score the current directed edge directly as the cheap sticky fast path. This lets a
  // stopped car remain on its road without asking a crossing road to win a fresh nearest-edge query.
  if (previous != nullptr)
  {
    EdgeProj current = *previous;
    current.m_point = ProjectToEdge(rawPoint, current.m_edge);
    AddUniqueProjection(projections, current);
  }

  std::vector<Candidate> candidates;
  candidates.reserve(projections.size());
  for (auto & projection : projections)
  {
    candidates.push_back(ScoreCandidate(*m_router, std::move(projection), rawLocation, rawPoint, movementDirection,
                                        previous, observationDeltaSeconds));
  }
  std::sort(candidates.begin(), candidates.end(),
            [](Candidate const & lhs, Candidate const & rhs) { return lhs.m_score < rhs.m_score; });

  Candidate const * best = candidates.empty() ? nullptr : &candidates.front();
  bool const bestAcceptable = best != nullptr && IsAcceptable(*best, rawLocation, previous != nullptr);
  bool const roadMismatch = !bestAcceptable;

  if (roadMismatch)
  {
    m_freeDrivingOffRoadEvidenceSeconds += observationDeltaSeconds;
    // Distance only counts when it came from a valid temporal interval and is not an obvious position teleport.
    double const plausibleStepLimitM =
        rawLocation.HasSpeed() ? std::max(60.0, rawLocation.m_speed * observationDeltaSeconds * 4.0 + 30.0) : 60.0;
    if (observationDeltaSeconds > 0.0 && rawStepM <= plausibleStepLimitM)
      m_freeDrivingOffRoadEvidenceDistanceM += rawStepM;
  }
  else
  {
    // A good mapped road/track always outranks an enclosing farmland/beach polygon.
    m_freeDrivingOffRoadEvidenceSeconds = 0.0;
    m_freeDrivingOffRoadEvidenceDistanceM = 0.0;
  }

  bool const lowParkingSpeed =
      rawLocation.HasSpeed() && rawLocation.m_speed <= free_driving_snap::kParkingFreeMaxSpeedMps;
  bool const parkingEvidence = lowParkingSpeed && (m_freeDrivingAreaContext.HasStrongParkingEvidence() ||
                                                   (m_freeDrivingAreaContext.m_insideLargeBuilding && roadMismatch));
  if (parkingEvidence)
    m_freeDrivingParkingEvidenceSeconds += observationDeltaSeconds;
  else
    m_freeDrivingParkingEvidenceSeconds = 0.0;

  auto transitionTo = [&](free_driving_snap::MatchState state, char const * reason)
  {
    if (m_freeDrivingMatchState == state)
      return;
    LOG(LDEBUG, ("Free-driving road snap:", StateName(m_freeDrivingMatchState), "->", StateName(state),
                 "reason=", reason, "speed_mps=", rawLocation.m_speed, "accuracy_m=", rawLocation.m_horizontalAccuracy,
                 "parking=", m_freeDrivingAreaContext.m_insideParking,
                 "structured_parking=", m_freeDrivingAreaContext.m_insideStructuredParking,
                 "large_building=", m_freeDrivingAreaContext.m_insideLargeBuilding,
                 "strong_open=", m_freeDrivingAreaContext.m_insideStrongOpenArea,
                 "weak_open=", m_freeDrivingAreaContext.m_insideWeakOpenArea));
    m_freeDrivingMatchState = state;
  };

  if (free_driving_snap::CanEnterParkingFree(rawLocation, m_freeDrivingAreaContext, m_freeDrivingParkingEvidenceSeconds,
                                             roadMismatch))
  {
    transitionTo(free_driving_snap::MatchState::ParkingFree, "parking-context");
    m_freeDrivingPendingProjectionSeeded = false;
    m_freeDrivingPendingObservationCount = 0;
    m_freeDrivingReacquireObservationCount = 0;
  }
  else if (roadMismatch &&
           free_driving_snap::CanEnterOffRoadFree(m_freeDrivingAreaContext, m_freeDrivingOffRoadEvidenceSeconds,
                                                  m_freeDrivingOffRoadEvidenceDistanceM))
  {
    transitionTo(free_driving_snap::MatchState::OffRoadFree, "sustained-road-mismatch");
    m_freeDrivingPendingProjectionSeeded = false;
    m_freeDrivingPendingObservationCount = 0;
    m_freeDrivingReacquireObservationCount = 0;
  }

  if (m_freeDrivingMatchState == free_driving_snap::MatchState::ParkingFree ||
      m_freeDrivingMatchState == free_driving_snap::MatchState::OffRoadFree)
  {
    bool mayReacquire = bestAcceptable;
    if (m_freeDrivingMatchState == free_driving_snap::MatchState::ParkingFree &&
        m_freeDrivingAreaContext.HasStrongParkingEvidence() &&
        (!rawLocation.HasSpeed() || rawLocation.m_speed < free_driving_snap::kCruiseSpeedMps))
    {
      mayReacquire = false;
    }

    if (mayReacquire)
    {
      if (!m_freeDrivingPendingProjectionSeeded ||
          !SameCandidate(*m_router, m_freeDrivingPendingProjection, best->m_projection))
      {
        m_freeDrivingPendingProjection = best->m_projection;
        m_freeDrivingPendingProjectionSeeded = true;
        m_freeDrivingReacquireObservationCount = 1;
      }
      else
      {
        ++m_freeDrivingReacquireObservationCount;
      }

      if (m_freeDrivingReacquireObservationCount >= 2)
      {
        m_freeDrivingProjection = best->m_projection;
        m_freeDrivingProjectionSeeded = true;
        m_freeDrivingPendingProjectionSeeded = false;
        m_freeDrivingReacquireObservationCount = 0;
        transitionTo(free_driving_snap::MatchState::Road, "persistent-road-reacquisition");
      }
    }
    else
    {
      m_freeDrivingPendingProjectionSeeded = false;
      m_freeDrivingReacquireObservationCount = 0;
    }
  }
  else if (m_freeDrivingMatchState == free_driving_snap::MatchState::Unsnapped)
  {
    if (bestAcceptable && accuracy != free_driving_snap::AccuracyBand::Poor)
    {
      if (!m_freeDrivingPendingProjectionSeeded ||
          !SameCandidate(*m_router, m_freeDrivingPendingProjection, best->m_projection))
      {
        m_freeDrivingPendingProjection = best->m_projection;
        m_freeDrivingPendingProjectionSeeded = true;
        m_freeDrivingPendingObservationCount = 1;
      }
      else
      {
        ++m_freeDrivingPendingObservationCount;
      }

      if (m_freeDrivingPendingObservationCount >= 2)
      {
        m_freeDrivingProjection = best->m_projection;
        m_freeDrivingProjectionSeeded = true;
        m_freeDrivingPendingProjectionSeeded = false;
        m_freeDrivingPendingObservationCount = 0;
        transitionTo(free_driving_snap::MatchState::Road, "two-real-observation-acquisition");
      }
    }
    else
    {
      m_freeDrivingPendingProjectionSeeded = false;
      m_freeDrivingPendingObservationCount = 0;
    }
  }
  else if (m_freeDrivingMatchState == free_driving_snap::MatchState::Road && m_freeDrivingProjectionSeeded)
  {
    if (bestAcceptable)
    {
      auto const relation = RelationTo(*m_router, m_freeDrivingProjection.m_edge, best->m_projection.m_edge);
      if (relation != free_driving_snap::RoadRelation::Unrelated)
      {
        // Connected turns, service roads, driveways, parking aisles and roundabout segments can
        // transition immediately. Low speed reduces heading weight rather than disabling snapping.
        m_freeDrivingProjection = best->m_projection;
        m_freeDrivingPendingProjectionSeeded = false;
        m_freeDrivingPendingObservationCount = 0;
      }
      else if (accuracy != free_driving_snap::AccuracyBand::Poor)
      {
        EdgeProj currentProjection = m_freeDrivingProjection;
        currentProjection.m_point = ProjectToEdge(rawPoint, currentProjection.m_edge);
        Candidate const current = ScoreCandidate(*m_router, currentProjection, rawLocation, rawPoint, movementDirection,
                                                 &m_freeDrivingProjection, observationDeltaSeconds);
        if (best->m_score + kUnrelatedSwitchMargin < current.m_score)
        {
          if (!m_freeDrivingPendingProjectionSeeded ||
              !SameCandidate(*m_router, m_freeDrivingPendingProjection, best->m_projection))
          {
            m_freeDrivingPendingProjection = best->m_projection;
            m_freeDrivingPendingProjectionSeeded = true;
            m_freeDrivingPendingObservationCount = 1;
          }
          else
          {
            ++m_freeDrivingPendingObservationCount;
          }

          if (m_freeDrivingPendingObservationCount >= 2)
          {
            m_freeDrivingProjection = best->m_projection;
            m_freeDrivingPendingProjectionSeeded = false;
            m_freeDrivingPendingObservationCount = 0;
            LOG(LDEBUG, ("Free-driving road snap: unrelated road switch after persistent score margin",
                         "best_score=", best->m_score, "current_score=", current.m_score));
          }
        }
        else
        {
          m_freeDrivingPendingProjectionSeeded = false;
          m_freeDrivingPendingObservationCount = 0;
        }
      }
    }
    else
    {
      m_freeDrivingPendingProjectionSeeded = false;
      m_freeDrivingPendingObservationCount = 0;
    }
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

  // Display ticks are intentionally read-only with respect to matcher state. In particular,
  // the 200 ms extrapolator cadence cannot manufacture acquisition/switch/release confidence.
  if (!m_freeDrivingRoadSnapEnabled || IsActive() || m_freeDrivingMatchState != free_driving_snap::MatchState::Road ||
      !m_freeDrivingProjectionSeeded ||
      free_driving_snap::GetAccuracyBand(displayInput) == free_driving_snap::AccuracyBand::Unusable)
  {
    return false;
  }

  m2::PointD const rawPoint = mercator::FromLatLon(displayInput.m_latitude, displayInput.m_longitude);
  EdgeProj displayProjection = m_freeDrivingProjection;
  displayProjection.m_point = ProjectToEdge(rawPoint, displayProjection.m_edge);

  // A display-only extrapolation is allowed to follow the established segment, but never to
  // search for a new road. If it has travelled implausibly far from that edge, fail open to raw.
  double const distanceM = mercator::DistanceOnEarth(rawPoint, displayProjection.m_point);
  double const maxDisplayDistanceM = std::max(20.0, free_driving_snap::AcceptanceDistanceM(displayInput) * 1.5);
  if (distanceM > maxDisplayDistanceM)
    return false;

  ApplyProjection(displayProjection, displayOutput);
  return true;
}
}  // namespace routing
