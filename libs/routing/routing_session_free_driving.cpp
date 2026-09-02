#include "routing/routing_session.hpp"

#include "geometry/angles.hpp"
#include "geometry/mercator.hpp"

#include "base/math.hpp"

#include <algorithm>
#include <cmath>
#include <limits>
#include <vector>

namespace routing
{
namespace
{
double constexpr kConnectedEndpointToleranceM = 4.0;
double constexpr kDrivingSideMinOffsetM = 1.5;
double constexpr kDrivingSideMaxOffsetM = 20.0;
uint8_t constexpr kRegularRoadRank = 6;
uint8_t constexpr kMotorwayRoadRank = 8;

bool SameRoadFeature(FreeDrivingRoadCandidate const & lhs, FreeDrivingRoadCandidate const & rhs)
{
  return lhs.m_projection.m_edge.GetFeatureId() == rhs.m_projection.m_edge.GetFeatureId();
}

bool RoadsConnected(FreeDrivingRoadCandidate const & lhs, FreeDrivingRoadCandidate const & rhs)
{
  if (SameRoadFeature(lhs, rhs))
    return true;

  auto const & a = lhs.m_projection.m_edge;
  auto const & b = rhs.m_projection.m_edge;
  return mercator::DistanceOnEarth(a.GetStartPoint(), b.GetStartPoint()) <= kConnectedEndpointToleranceM ||
         mercator::DistanceOnEarth(a.GetStartPoint(), b.GetEndPoint()) <= kConnectedEndpointToleranceM ||
         mercator::DistanceOnEarth(a.GetEndPoint(), b.GetStartPoint()) <= kConnectedEndpointToleranceM ||
         mercator::DistanceOnEarth(a.GetEndPoint(), b.GetEndPoint()) <= kConnectedEndpointToleranceM;
}

void ApplyProjection(FreeDrivingRoadCandidate const & candidate, location::GpsInfo & displayLocation)
{
  auto const & projection = candidate.m_projection;
  displayLocation.m_latitude = mercator::YToLat(projection.m_point.y);
  displayLocation.m_longitude = mercator::XToLon(projection.m_point.x);

  auto const direction = projection.m_edge.GetDirection();
  if (!direction.IsAlmostZero())
    displayLocation.m_bearing = location::AngleToBearing(math::RadToDeg(ang::AngleTo(m2::PointD::Zero(), direction)));
}

m2::PointD DirectionFromBearing(location::GpsInfo const & info)
{
  if (!info.HasBearing())
    return m2::PointD::Zero();

  double const angle = math::DegToRad(location::BearingToAngle(info.m_bearing));
  return {std::cos(angle), std::sin(angle)};
}

double CandidateScore(location::GpsInfo const & rawLocation, m2::PointD const & rawPoint,
                      m2::PointD const & motionDirection, double radiusM,
                      FreeDrivingRoadCandidate const & candidate, FreeDrivingRoadCandidate const * previous,
                      m2::PointD const * previousRawPoint, bool leftHandTraffic)
{
  auto const & edge = candidate.m_projection.m_edge;
  double const lateralM = mercator::DistanceOnEarth(rawPoint, candidate.m_projection.m_point);
  double score = 4.0 * std::min(lateralM / std::max(radiusM, 1.0), 2.0);

  auto const edgeDirection = edge.GetDirection();
  if (!motionDirection.IsAlmostZero() && !edgeDirection.IsAlmostZero())
  {
    double const cosDirection =
        std::clamp(m2::DotProduct(motionDirection.Normalize(), edgeDirection.Normalize()), -1.0, 1.0);
    // The car graph already obeys oneway tags; keep an explicit strong guard because direction is important
    // enough to outrank a merely close parallel carriageway.
    if (candidate.m_oneWay && cosDirection < -0.05)
      return std::numeric_limits<double>::infinity();
    score += 3.2 * (1.0 - cosDirection);
  }

  if (previous != nullptr)
  {
    if (candidate.m_projection.m_edge.SameRoadSegmentAndDirection(previous->m_projection.m_edge))
      score -= 2.8;
    else if (SameRoadFeature(candidate, *previous))
      score -= 2.2;
    else if (RoadsConnected(candidate, *previous))
      score -= 1.1;
    else
      score += 1.5;

    if (previousRawPoint != nullptr)
    {
      double const rawTravelM = mercator::DistanceOnEarth(*previousRawPoint, rawPoint);
      double const matchedTravelM = mercator::DistanceOnEarth(previous->m_projection.m_point, candidate.m_projection.m_point);
      if (rawTravelM > 2.0)
        score += 1.2 * std::min(std::abs(matchedTravelM - rawTravelM) / std::max(rawTravelM, 5.0), 2.0);
    }
  }

  if (rawLocation.HasSpeed() && rawLocation.m_speed >= free_driving_snap::kHighSpeedStrongPriorMps)
  {
    if (candidate.m_roadRank < kRegularRoadRank)
      score += 0.35 * static_cast<double>(kRegularRoadRank - candidate.m_roadRank);
    else
      score -= 0.10 * static_cast<double>(std::min<uint8_t>(candidate.m_roadRank, kMotorwayRoadRank) - kRegularRoadRank);
  }

  // Driving side is deliberately only a weak tie-breaker. It can help choose separated/parallel carriageways,
  // but normal GNSS is not lane-accurate and this term must never defeat coherent recent motion or topology.
  if (lateralM >= kDrivingSideMinOffsetM && lateralM <= kDrivingSideMaxOffsetM && !edgeDirection.IsAlmostZero())
  {
    m2::PointD const lateral = rawPoint - candidate.m_projection.m_point;
    double const cross = edgeDirection.x * lateral.y - edgeDirection.y * lateral.x;
    bool const pointIsLeftOfTravel = cross > 0.0;
    if (pointIsLeftOfTravel != leftHandTraffic)
      score += 0.18;
    else
      score -= 0.08;
  }

  // Reported accuracy is intentionally low-weight. It slightly reduces confidence but cannot dominate a coherent
  // recent track, road direction or network transition.
  if (rawLocation.m_horizontalAccuracy > 0.0)
    score += 0.25 * std::min(rawLocation.m_horizontalAccuracy / 100.0, 1.0);

  return score;
}
}  // namespace

void RoutingSession::ResetFreeDrivingRoadGraphMatch()
{
  m_freeDrivingSamples.clear();
  m_freeDrivingCandidate = {};
  m_freeDrivingCandidateSeeded = false;
  m_freeDrivingConfident = false;
  m_freeDrivingLastConfidentTimestamp = 0.0;
  m_freeDrivingUnmatchedMovingSince = 0.0;
  m_freeDrivingMatchState = free_driving_snap::MatchState::Disabled;
}

bool RoutingSession::MatchFreeDrivingLocationToRoadGraph(location::GpsInfo const & rawLocation,
                                                         location::GpsInfo & displayLocation,
                                                         free_driving_snap::SnapMode mode, bool leftHandTraffic,
                                                         bool isMeasurement)
{
  CHECK_THREAD_CHECKER(m_threadChecker, ());
  displayLocation = rawLocation;

  if (mode == free_driving_snap::SnapMode::Off)
  {
    ResetFreeDrivingRoadGraphMatch();
    return false;
  }

  if (!m_router || !m_router->HasRouter())
  {
    ResetFreeDrivingRoadGraphMatch();
    m_freeDrivingMatchState = free_driving_snap::MatchState::Raw;
    return false;
  }

  if (free_driving_snap::IsAccuracyCatastrophic(rawLocation))
  {
    m_freeDrivingUnmatchedMovingSince = 0.0;
    m_freeDrivingMatchState = free_driving_snap::MatchState::Raw;
    return false;
  }

  m2::PointD const rawPoint = mercator::FromLatLon(rawLocation.m_latitude, rawLocation.m_longitude);
  m2::PointD previousRawPoint;
  bool hasPreviousRawPoint = false;
  double previousTimestamp = 0.0;
  if (!m_freeDrivingSamples.empty())
  {
    previousRawPoint = m_freeDrivingSamples.back().m_point;
    previousTimestamp = m_freeDrivingSamples.back().m_timestamp;
    hasPreviousRawPoint = true;
  }

  bool movingEvidence = rawLocation.HasSpeed() && rawLocation.m_speed >= free_driving_snap::kMovingEvidenceSpeedMps;
  if (isMeasurement && hasPreviousRawPoint)
  {
    double const dt = rawLocation.m_timestamp - previousTimestamp;
    double const displacementM = mercator::DistanceOnEarth(previousRawPoint, rawPoint);
    if (dt > 0.0 && dt <= 3.0 && displacementM >= 3.0)
      movingEvidence = true;
  }

  if (isMeasurement)
  {
    if (!m_freeDrivingSamples.empty() && rawLocation.m_timestamp < m_freeDrivingSamples.back().m_timestamp)
      m_freeDrivingSamples.clear();

    if (m_freeDrivingSamples.empty() ||
        std::abs(rawLocation.m_timestamp - m_freeDrivingSamples.back().m_timestamp) > 1.0e-5)
    {
      m_freeDrivingSamples.push_back({rawPoint, rawLocation.m_timestamp,
                                      rawLocation.HasSpeed() ? rawLocation.m_speed : -1.0,
                                      rawLocation.HasBearing() ? rawLocation.m_bearing : -1.0});
    }

    while (m_freeDrivingSamples.size() > free_driving_snap::kHistoryMaxSamples)
      m_freeDrivingSamples.pop_front();
    while (m_freeDrivingSamples.size() > 1 &&
           rawLocation.m_timestamp - m_freeDrivingSamples.front().m_timestamp >
               free_driving_snap::kHistoryWindowSeconds)
    {
      m_freeDrivingSamples.pop_front();
    }
  }

  m2::PointD motionDirection;
  if (m_freeDrivingSamples.size() >= 2)
  {
    for (auto it = m_freeDrivingSamples.begin(); it != m_freeDrivingSamples.end(); ++it)
    {
      if (mercator::DistanceOnEarth(it->m_point, m_freeDrivingSamples.back().m_point) >=
          free_driving_snap::kMinHistoryDisplacementM)
      {
        motionDirection = m_freeDrivingSamples.back().m_point - it->m_point;
        break;
      }
    }
  }
  if (motionDirection.IsAlmostZero())
    motionDirection = DirectionFromBearing(rawLocation);

  double const radiusM = free_driving_snap::ProjectionRadiusM(rawLocation, mode);
  std::vector<FreeDrivingRoadCandidate> candidates;
  if (!m_router->FindFreeDrivingRoadCandidates(rawPoint, motionDirection, radiusM,
                                               static_cast<uint32_t>(free_driving_snap::kCandidateLimit), candidates))
  {
    if (isMeasurement && movingEvidence)
    {
      if (m_freeDrivingUnmatchedMovingSince <= 0.0)
        m_freeDrivingUnmatchedMovingSince = rawLocation.m_timestamp;
      if (rawLocation.m_timestamp - m_freeDrivingUnmatchedMovingSince >= free_driving_snap::kOffRoadEvidenceSeconds)
        m_freeDrivingMatchState = free_driving_snap::MatchState::OffRoadSuspected;
      else
        m_freeDrivingMatchState = free_driving_snap::MatchState::Raw;
    }
    else if (isMeasurement)
    {
      m_freeDrivingUnmatchedMovingSince = 0.0;
      m_freeDrivingMatchState = free_driving_snap::MatchState::Raw;
    }
    return false;
  }

  FreeDrivingRoadCandidate const * previous = m_freeDrivingCandidateSeeded ? &m_freeDrivingCandidate : nullptr;
  FreeDrivingRoadCandidate const * best = nullptr;
  double bestScore = std::numeric_limits<double>::infinity();
  for (auto const & candidate : candidates)
  {
    double const score = CandidateScore(rawLocation, rawPoint, motionDirection, radiusM, candidate, previous,
                                        hasPreviousRawPoint ? &previousRawPoint : nullptr, leftHandTraffic);
    if (score < bestScore)
    {
      best = &candidate;
      bestScore = score;
    }
  }

  if (best == nullptr || !std::isfinite(bestScore) || bestScore > free_driving_snap::AcceptanceScore(mode))
  {
    if (isMeasurement && movingEvidence)
    {
      if (m_freeDrivingUnmatchedMovingSince <= 0.0)
        m_freeDrivingUnmatchedMovingSince = rawLocation.m_timestamp;
      m_freeDrivingMatchState =
          rawLocation.m_timestamp - m_freeDrivingUnmatchedMovingSince >= free_driving_snap::kOffRoadEvidenceSeconds
              ? free_driving_snap::MatchState::OffRoadSuspected
              : free_driving_snap::MatchState::Raw;
    }
    else if (isMeasurement)
    {
      m_freeDrivingUnmatchedMovingSince = 0.0;
      m_freeDrivingMatchState = free_driving_snap::MatchState::Raw;
    }
    return false;
  }

  bool const continuous = previous != nullptr && RoadsConnected(*previous, *best);
  if (!isMeasurement)
  {
    // Prediction may move only along a still-plausible continuation of a road hypothesis established by real fixes.
    if (!m_freeDrivingConfident || !continuous || bestScore > free_driving_snap::AcceptanceScore(mode) + 1.0)
      return false;
    ApplyProjection(*best, displayLocation);
    return true;
  }

  bool const highSpeed = rawLocation.HasSpeed() && rawLocation.m_speed >= free_driving_snap::kHighSpeedStrongPriorMps;
  bool accept = false;
  if (m_freeDrivingConfident && continuous)
    accept = true;
  else if (highSpeed && bestScore <= free_driving_snap::HighSpeedSingleFixScore(mode))
    accept = true;
  else if (previous != nullptr && SameRoadFeature(*previous, *best))
    accept = true;

  if (!accept)
  {
    m_freeDrivingCandidate = *best;
    m_freeDrivingCandidateSeeded = true;
    m_freeDrivingConfident = false;
    m_freeDrivingLastConfidentTimestamp = 0.0;
    m_freeDrivingUnmatchedMovingSince = 0.0;
    m_freeDrivingMatchState = free_driving_snap::MatchState::Raw;
    return false;
  }

  m_freeDrivingCandidate = *best;
  m_freeDrivingCandidateSeeded = true;
  m_freeDrivingConfident = true;
  m_freeDrivingLastConfidentTimestamp = rawLocation.m_timestamp;
  m_freeDrivingUnmatchedMovingSince = 0.0;
  m_freeDrivingMatchState = free_driving_snap::MatchState::Matched;
  ApplyProjection(*best, displayLocation);
  return true;
}
}  // namespace routing
