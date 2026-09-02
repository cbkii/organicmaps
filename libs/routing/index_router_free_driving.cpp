#include "routing/index_router.hpp"

#include "indexer/ftypes_matcher.hpp"

#include "geometry/mercator.hpp"

#include <algorithm>

namespace routing
{
bool IndexRouter::FindFreeDrivingRoadCandidates(m2::PointD const & point, m2::PointD const & direction, double radius,
                                                uint32_t count,
                                                std::vector<FreeDrivingRoadCandidate> & candidates)
{
  candidates.clear();
  if (count == 0)
    return false;

  auto const rect = mercator::RectByCenterXYAndSizeInMeters(point, radius);
  std::vector<EdgeProjectionT> projections;
  m_roadGraph.FindClosestEdges(rect, std::max<uint32_t>(count, 1), projections);
  if (projections.empty())
    return false;

  candidates.reserve(std::min<size_t>(projections.size(), count));
  auto const & wayChecker = ftypes::IsWayChecker::Instance();
  auto const & oneWayChecker = ftypes::IsOneWayChecker::Instance();

  for (auto const & [edge, projection] : projections)
  {
    feature::TypesHolder types;
    m_roadGraph.GetEdgeTypes(edge, types);

    uint8_t roadRank = static_cast<uint8_t>(ftypes::IsWayChecker::SearchRank::Default);
    for (uint32_t const type : types)
      roadRank = std::max<uint8_t>(roadRank, static_cast<uint8_t>(wayChecker.GetSearchRank(type)));

    FreeDrivingRoadCandidate candidate;
    candidate.m_projection.m_edge = edge;
    candidate.m_projection.m_point = projection.GetPoint();
    candidate.m_roadRank = roadRank;
    candidate.m_oneWay = oneWayChecker(types);
    candidates.push_back(std::move(candidate));
    if (candidates.size() >= count)
      break;
  }

  return !candidates.empty();
}
}  // namespace routing
