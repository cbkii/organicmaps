#include "routing/routing_session.hpp"

#include "routing/free_driving_road_snap_policy.hpp"

#include "geometry/mercator.hpp"

namespace routing
{
namespace
{
bool SameRoadFeature(EdgeProj const & lhs, EdgeProj const & rhs)
{
  return lhs.m_edge.GetFeatureId() == rhs.m_edge.GetFeatureId();
}

void ApplyProjection(EdgeProj const & projection, location::GpsInfo & displayLocation)
{
  displayLocation.m_latitude = mercator::YToLat(projection.m_point.y);
  displayLocation.m_longitude = mercator::XToLon(projection.m_point.x);
}
}  // namespace

void RoutingSession::ResetFreeDrivingRoadGraphMatch()
{
  m_freeDrivingPositionAccumulator.Clear();
  m_freeDrivingProjection = {};
  m_freeDrivingProjectionSeeded = false;
  m_freeDrivingConfident = false;
  m_freeDrivingLastConfidentMovingTimestamp = 0.0;
}

bool RoutingSession::MatchFreeDrivingLocationToRoadGraph(location::GpsInfo const & rawLocation,
                                                         location::GpsInfo & displayLocation)
{
  CHECK_THREAD_CHECKER(m_threadChecker, ());
  displayLocation = rawLocation;

  // Optional free-driving projection must be harmless during early startup or map/router transitions.
  if (!m_router || !m_router->HasRouter())
  {
    ResetFreeDrivingRoadGraphMatch();
    return false;
  }

  if (!free_driving_snap::IsAccuracyUsable(rawLocation))
  {
    ResetFreeDrivingRoadGraphMatch();
    return false;
  }

  m2::PointD const rawPoint = mercator::FromLatLon(rawLocation.m_latitude, rawLocation.m_longitude);
  m_freeDrivingPositionAccumulator.PushNextPoint(rawPoint);

  auto const mode =
      free_driving_snap::ResolveMode(rawLocation, m_freeDrivingConfident, m_freeDrivingLastConfidentMovingTimestamp);
  if (mode == free_driving_snap::Mode::None)
  {
    // Preserve accepted raw direction history so a later >15 km/h sample can qualify quickly, but do not
    // carry road confidence through the deliberately-unsnapped 5..15 km/h band or stale/unknown state.
    m_freeDrivingProjection = {};
    m_freeDrivingProjectionSeeded = false;
    m_freeDrivingConfident = false;
    m_freeDrivingLastConfidentMovingTimestamp = 0.0;
    return false;
  }

  m2::PointD direction;
  double radiusM;
  if (mode == free_driving_snap::Mode::Moving)
  {
    direction = m_freeDrivingPositionAccumulator.GetDirection();
    radiusM = free_driving_snap::MovingProjectionRadiusM(rawLocation);
  }
  else
  {
    // At a stop, use only the newest ~20 m of accepted movement so intersection jitter does not drag the marker
    // sideways onto a crossing road. This is a bounded hold of an already-confident moving match, never a new snap.
    direction = m_freeDrivingPositionAccumulator.GetRecentDirection(free_driving_snap::kRecentDirectionTrackLengthM);
    radiusM = free_driving_snap::kStationaryProjectionRadiusM;
  }

  if (direction.IsAlmostZero())
    return false;

  EdgeProj projection;
  if (!m_router->FindClosestProjectionToRoad(rawPoint, direction, radiusM, projection))
  {
    if (mode == free_driving_snap::Mode::StationaryHold)
    {
      m_freeDrivingProjectionSeeded = false;
      m_freeDrivingConfident = false;
      m_freeDrivingLastConfidentMovingTimestamp = 0.0;
    }
    return false;
  }

  // FindClosestProjectionToRoad searches a bounded rectangle. Keep a separate geodesic gate so a projection is
  // accepted only when the raw fix is genuinely close to the road, rather than merely present in that rectangle.
  if (mercator::DistanceOnEarth(rawPoint, projection.m_point) > radiusM)
  {
    if (mode == free_driving_snap::Mode::StationaryHold)
    {
      m_freeDrivingProjectionSeeded = false;
      m_freeDrivingConfident = false;
      m_freeDrivingLastConfidentMovingTimestamp = 0.0;
    }
    return false;
  }

  if (mode == free_driving_snap::Mode::StationaryHold)
  {
    if (!m_freeDrivingProjectionSeeded || !m_freeDrivingConfident ||
        !SameRoadFeature(m_freeDrivingProjection, projection))
    {
      m_freeDrivingProjectionSeeded = false;
      m_freeDrivingConfident = false;
      m_freeDrivingLastConfidentMovingTimestamp = 0.0;
      return false;
    }

    // Do not refresh the moving-confidence timestamp here: a stationary hold must expire by itself.
    m_freeDrivingProjection = projection;
    ApplyProjection(projection, displayLocation);
    return true;
  }

  // Moving mode follows the existing road-graph matcher principle: the first candidate only seeds the feature;
  // a second consecutive projection to the same road is required before display coordinates are modified.
  if (!m_freeDrivingProjectionSeeded || !SameRoadFeature(m_freeDrivingProjection, projection))
  {
    m_freeDrivingProjection = projection;
    m_freeDrivingProjectionSeeded = true;
    m_freeDrivingConfident = false;
    m_freeDrivingLastConfidentMovingTimestamp = 0.0;
    return false;
  }

  m_freeDrivingProjection = projection;
  m_freeDrivingConfident = true;
  m_freeDrivingLastConfidentMovingTimestamp = rawLocation.m_timestamp;
  ApplyProjection(projection, displayLocation);
  return true;
}
}  // namespace routing
