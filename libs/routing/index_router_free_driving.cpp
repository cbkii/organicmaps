#include "routing/index_router.hpp"

#include "indexer/ftypes_matcher.hpp"

#include "geometry/mercator.hpp"
#include "geometry/parametrized_segment.hpp"

#include <algorithm>
#include <cstdint>
#include <deque>
#include <limits>
#include <utility>
#include <vector>

namespace routing
{
namespace
{
bool SamePhysicalSegment(Edge const & lhs, Edge const & rhs)
{
  return lhs.GetFeatureId() == rhs.GetFeatureId() && lhs.GetSegId() == rhs.GetSegId();
}

bool HasPhysicalSegment(std::vector<Edge> const & selected, Edge const & edge)
{
  return std::any_of(selected.begin(), selected.end(),
                     [&edge](Edge const & candidate) { return SamePhysicalSegment(candidate, edge); });
}

bool HasDirectedEdge(std::vector<Edge> const & selected, Edge const & edge)
{
  return std::any_of(selected.begin(), selected.end(),
                     [&edge](Edge const & candidate) { return candidate.SameRoadSegmentAndDirection(edge); });
}

m2::PointD ProjectToEdge(m2::PointD const & point, Edge const & edge)
{
  m2::ParametrizedSegment<m2::PointD> const segment(edge.GetStartPoint(), edge.GetEndPoint());
  return segment.ClosestPointTo(point);
}

free_driving_snap::RoadClass ToRoadClass(std::optional<HighwayType> highwayType)
{
  if (!highwayType)
    return free_driving_snap::RoadClass::Unknown;

  switch (*highwayType)
  {
  case HighwayType::HighwayMotorway: return free_driving_snap::RoadClass::Motorway;
  case HighwayType::HighwayMotorwayLink: return free_driving_snap::RoadClass::MotorwayLink;
  case HighwayType::HighwayTrunk: return free_driving_snap::RoadClass::Trunk;
  case HighwayType::HighwayTrunkLink: return free_driving_snap::RoadClass::TrunkLink;
  case HighwayType::HighwayPrimary: return free_driving_snap::RoadClass::Primary;
  case HighwayType::HighwayPrimaryLink: return free_driving_snap::RoadClass::PrimaryLink;
  case HighwayType::HighwaySecondary: return free_driving_snap::RoadClass::Secondary;
  case HighwayType::HighwaySecondaryLink: return free_driving_snap::RoadClass::SecondaryLink;
  case HighwayType::HighwayTertiary: return free_driving_snap::RoadClass::Tertiary;
  case HighwayType::HighwayTertiaryLink: return free_driving_snap::RoadClass::TertiaryLink;
  case HighwayType::HighwayResidential: return free_driving_snap::RoadClass::Residential;
  case HighwayType::HighwayUnclassified: return free_driving_snap::RoadClass::Unclassified;
  case HighwayType::HighwayService: return free_driving_snap::RoadClass::Service;
  case HighwayType::HighwayLivingStreet: return free_driving_snap::RoadClass::LivingStreet;
  case HighwayType::HighwayTrack: return free_driving_snap::RoadClass::Track;
  default: return free_driving_snap::RoadClass::Other;
  }
}
}  // namespace

void IndexRouter::FindClosestProjectionsToRoad(m2::PointD const & point, double radius, size_t maxCount,
                                               std::vector<EdgeProj> & projections)
{
  projections.clear();
  if (maxCount == 0)
    return;

  auto const rect = mercator::RectByCenterXYAndSizeInMeters(point, radius);
  std::vector<EdgeProjectionT> candidates;

  // NearestEdgeFinder counts directed results, so a normal two-way road consumes two entries.
  // Ask for enough directed states to fill the effective budget, then cap the returned directed
  // projections themselves.  The caller may therefore rely on |maxCount| as a hard work bound.
  size_t const directedLimit = std::min(maxCount * 2 + 2, static_cast<size_t>(std::numeric_limits<uint32_t>::max()));
  m_roadGraph.FindClosestEdges(rect, static_cast<uint32_t>(directedLimit), candidates);

  std::vector<Edge> selectedPhysicalSegments;
  selectedPhysicalSegments.reserve(maxCount);
  projections.reserve(std::min(candidates.size(), maxCount));
  for (auto const & [edge, projection] : candidates)
  {
    if (projections.size() >= maxCount)
      break;
    if (!HasPhysicalSegment(selectedPhysicalSegments, edge))
    {
      if (selectedPhysicalSegments.size() >= maxCount)
        continue;
      selectedPhysicalSegments.push_back(edge);
    }
    projections.push_back({edge, projection.GetPoint()});
  }
}

void IndexRouter::FindFreeDrivingRoadCorridor(EdgeProj const & from, m2::PointD const & point, double maxDistanceM,
                                              size_t maxEdges, size_t maxHops,
                                              std::vector<FreeDrivingCorridorProjection> & projections) const
{
  projections.clear();
  if (maxEdges == 0 || maxHops == 0 || !from.m_edge.HasRealPart())
    return;

  EdgeProj current = from;
  current.m_point = ProjectToEdge(point, from.m_edge);
  double const fromToEndM = mercator::DistanceOnEarth(from.m_point, from.m_edge.GetEndPoint());
  double const currentToEndM = mercator::DistanceOnEarth(current.m_point, current.m_edge.GetEndPoint());
  double const currentProgressM = std::max(0.0, fromToEndM - currentToEndM);
  projections.push_back({current, currentProgressM, 0});

  struct PendingEdge
  {
    Edge m_edge;
    double m_distanceBeforeEdgeM = 0.0;
    size_t m_hops = 0;
  };

  std::deque<PendingEdge> pending;
  IRoadGraph::EdgeListT outgoing;
  m_roadGraph.GetOutgoingEdges(from.m_edge.GetEndJunction(), outgoing);
  for (auto const & edge : outgoing)
    pending.push_back({edge, fromToEndM, 1});

  std::vector<Edge> visited;
  visited.reserve(maxEdges);
  visited.push_back(from.m_edge);

  while (!pending.empty() && projections.size() < maxEdges)
  {
    PendingEdge node = pending.front();
    pending.pop_front();

    if (node.m_hops > maxHops || node.m_distanceBeforeEdgeM > maxDistanceM || HasDirectedEdge(visited, node.m_edge))
      continue;
    visited.push_back(node.m_edge);

    EdgeProj projection{node.m_edge, ProjectToEdge(point, node.m_edge)};
    double const toProjectionM = mercator::DistanceOnEarth(node.m_edge.GetStartPoint(), projection.m_point);
    double const pathDistanceM = node.m_distanceBeforeEdgeM + toProjectionM;
    if (pathDistanceM <= maxDistanceM + 20.0)
      projections.push_back({projection, pathDistanceM, node.m_hops});

    double const edgeLengthM = mercator::DistanceOnEarth(node.m_edge.GetStartPoint(), node.m_edge.GetEndPoint());
    double const distanceAtEndM = node.m_distanceBeforeEdgeM + edgeLengthM;
    if (distanceAtEndM > maxDistanceM || node.m_hops >= maxHops || projections.size() >= maxEdges)
      continue;

    outgoing.clear();
    m_roadGraph.GetOutgoingEdges(node.m_edge.GetEndJunction(), outgoing);
    for (auto const & next : outgoing)
      if (!HasDirectedEdge(visited, next))
        pending.push_back({next, distanceAtEndM, node.m_hops + 1});
  }
}

bool IndexRouter::GetFreeDrivingRoadMetadata(Edge const & edge, free_driving_snap::RoadMetadata & metadata) const
{
  metadata = {};
  if (!edge.HasRealPart())
    return false;

  feature::TypesHolder types;
  m_roadGraph.GetEdgeTypes(edge, types);
  if (types.Empty())
    return false;

  auto const model = m_vehicleModelFactory->GetVehicleModelForCountry(edge.GetFeatureId().GetMwmName());
  metadata.m_class = ToRoadClass(model ? model->GetHighwayType(types) : std::nullopt);

  static ftypes::BaseCheckerEx const parkingAisle({{"highway", "service", "parking_aisle"}});
  static ftypes::BaseCheckerEx const driveway({{"highway", "service", "driveway"}});
  static ftypes::BaseCheckerEx const roundabout({{"junction", "roundabout"}});
  metadata.m_parkingAisle = parkingAisle(types);
  metadata.m_driveway = driveway(types);
  metadata.m_roundabout = roundabout(types);
  return true;
}

bool IndexRouter::AreRoadEdgesConnected(Edge const & from, Edge const & to) const
{
  IRoadGraph::EdgeListT outgoing;
  m_roadGraph.GetOutgoingEdges(from.GetEndJunction(), outgoing);
  return std::any_of(outgoing.begin(), outgoing.end(),
                     [&to](Edge const & edge) { return edge.SameRoadSegmentAndDirection(to); });
}
}  // namespace routing
