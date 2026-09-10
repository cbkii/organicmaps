#include "routing/index_router.hpp"

#include "geometry/mercator.hpp"

#include <algorithm>
#include <cstdint>
#include <limits>
#include <vector>

namespace routing
{
void IndexRouter::FindClosestProjectionsToRoad(m2::PointD const & point, double radius, size_t maxCount,
                                               std::vector<EdgeProj> & projections)
{
  projections.clear();
  if (maxCount == 0)
    return;

  auto const rect = mercator::RectByCenterXYAndSizeInMeters(point, radius);
  std::vector<EdgeProjectionT> candidates;
  uint32_t const count = static_cast<uint32_t>(
      std::min(maxCount, static_cast<size_t>(std::numeric_limits<uint32_t>::max())));
  m_roadGraph.FindClosestEdges(rect, count, candidates);

  projections.reserve(candidates.size());
  for (auto const & [edge, projection] : candidates)
    projections.push_back({edge, projection.GetPoint()});
}

bool IndexRouter::AreRoadEdgesConnected(Edge const & from, Edge const & to) const
{
  IRoadGraph::EdgeListT outgoing;
  m_roadGraph.GetOutgoingEdges(from.GetEndJunction(), outgoing);
  return std::any_of(outgoing.begin(), outgoing.end(), [&to](Edge const & edge)
  { return edge.SameRoadSegmentAndDirection(to); });
}
}  // namespace routing
