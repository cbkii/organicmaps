#pragma once

#include "routing/free_driving_road_snap_policy.hpp"

#include "indexer/data_source.hpp"
#include "indexer/feature.hpp"
#include "indexer/feature_algo.hpp"
#include "indexer/ftypes_matcher.hpp"
#include "indexer/scales.hpp"

#include "geometry/mercator.hpp"
#include "geometry/parametrized_segment.hpp"
#include "geometry/triangle2d.hpp"

#include <algorithm>
#include <cmath>
#include <limits>
#include <vector>

namespace routing::free_driving_snap
{
namespace detail
{
inline bool IsInsideArea(FeatureType & feature, m2::PointD const & point)
{
  return feature.GetGeomType() == feature::GeomType::Area && feature::GetMinDistanceMeters(feature, point) == 0.0;
}

inline double TriangleAreaM2(m2::PointD const & p1, m2::PointD const & p2, m2::PointD const & p3)
{
  double const a = mercator::DistanceOnEarth(p1, p2);
  double const b = mercator::DistanceOnEarth(p2, p3);
  double const c = mercator::DistanceOnEarth(p3, p1);
  double const s = (a + b + c) * 0.5;
  return std::sqrt(std::max(0.0, s * (s - a) * (s - b) * (s - c)));
}

inline double AreaM2(FeatureType & feature)
{
  double areaM2 = 0.0;
  feature.ForEachTriangle([&](m2::PointD const & p1, m2::PointD const & p2, m2::PointD const & p3)
  { areaM2 += TriangleAreaM2(p1, p2, p3); }, FeatureType::BEST_GEOMETRY);
  return areaM2;
}

inline void CopyAreaTriangles(FeatureType & feature, std::vector<m2::PointD> & triangles)
{
  triangles.clear();
  feature.ForEachTriangle([&](m2::PointD const & p1, m2::PointD const & p2, m2::PointD const & p3)
  {
    triangles.push_back(p1);
    triangles.push_back(p2);
    triangles.push_back(p3);
  }, FeatureType::BEST_GEOMETRY);
}

inline void MaybeSelectFreeArea(FeatureType & feature, int priority, int & selectedPriority, double & selectedAreaM2,
                                std::vector<m2::PointD> & triangles)
{
  double const areaM2 = AreaM2(feature);
  if (priority < selectedPriority || (priority == selectedPriority && areaM2 >= selectedAreaM2))
    return;

  std::vector<m2::PointD> candidate;
  CopyAreaTriangles(feature, candidate);
  if (candidate.empty())
    return;

  selectedPriority = priority;
  selectedAreaM2 = areaM2;
  triangles = std::move(candidate);
}
}  // namespace detail

// Constrains |point| to the cached mapped parking/building area. A point already inside the
// triangulated area is preserved exactly. A noisy point just outside is projected to the nearest
// point on the area's triangles; the area centre is never used.
inline bool ConstrainPointToFreeArea(AreaContext const & context, m2::PointD const & point, m2::PointD & constrained)
{
  constrained = point;
  if (context.m_freeAreaTriangles.size() < 3)
    return false;

  for (size_t i = 0; i + 2 < context.m_freeAreaTriangles.size(); i += 3)
  {
    if (m2::IsPointInsideTriangle(point, context.m_freeAreaTriangles[i], context.m_freeAreaTriangles[i + 1],
                                  context.m_freeAreaTriangles[i + 2]))
    {
      return true;
    }
  }

  double bestDistanceSquared = std::numeric_limits<double>::max();
  m2::PointD bestPoint = point;
  auto checkEdge = [&](m2::PointD const & a, m2::PointD const & b)
  {
    m2::ParametrizedSegment<m2::PointD> const segment(a, b);
    m2::PointD const candidate = segment.ClosestPointTo(point);
    double const distanceSquared = point.SquaredLength(candidate);
    if (distanceSquared < bestDistanceSquared)
    {
      bestDistanceSquared = distanceSquared;
      bestPoint = candidate;
    }
  };

  for (size_t i = 0; i + 2 < context.m_freeAreaTriangles.size(); i += 3)
  {
    auto const & p1 = context.m_freeAreaTriangles[i];
    auto const & p2 = context.m_freeAreaTriangles[i + 1];
    auto const & p3 = context.m_freeAreaTriangles[i + 2];
    checkEdge(p1, p2);
    checkEdge(p2, p3);
    checkEdge(p3, p1);
  }

  if (bestDistanceSquared == std::numeric_limits<double>::max())
    return false;
  constrained = bestPoint;
  return true;
}

// Reads only a small map neighbourhood and parses geometry only after a type has been identified
// as relevant. Callers cache the result; this is never called by 200 ms display ticks.
inline AreaContext ReadAreaContext(DataSource const & dataSource, m2::PointD const & point)
{
  AreaContext context;

  static ftypes::BaseCheckerEx const structuredParking(
      {{"amenity", "parking", "underground"}, {"amenity", "parking", "multi-storey"}});
  static ftypes::BaseCheckerEx const parkingEntrance({{"amenity", "parking_entrance"}});
  static ftypes::BaseCheckerEx const parkingLikeLand({{"landuse", "garages"}});
  static ftypes::BaseCheckerEx const strongOpenArea({{"landuse", "farmland"},
                                                     {"landuse", "field"},
                                                     {"landuse", "meadow"},
                                                     {"natural", "beach"},
                                                     {"natural", "grassland"},
                                                     {"natural", "shingle"}});
  static ftypes::BaseCheckerEx const weakOpenArea({{"landuse", "grass"},
                                                   {"landuse", "farmyard"},
                                                   {"landuse", "orchard"},
                                                   {"landuse", "vineyard"},
                                                   {"landuse", "brownfield"},
                                                   {"landuse", "construction"},
                                                   {"landuse", "quarry"},
                                                   {"natural", "heath"},
                                                   {"natural", "scrub"},
                                                   {"natural", "scree"}});

  int selectedFreeAreaPriority = 0;
  double selectedFreeAreaM2 = std::numeric_limits<double>::max();

  // A 30 m neighbourhood sees parking entrances without turning this into a broad feature search.
  auto const rect = mercator::RectByCenterXYAndSizeInMeters(point, 30.0);
  dataSource.ForEachInRect([&](FeatureType & feature)
  {
    feature::TypesHolder const types(feature);

    if (parkingEntrance(types))
    {
      if (feature::GetMinDistanceMeters(feature, point) <= 20.0)
        context.m_nearParkingEntrance = true;
      return;
    }

    if (ftypes::IsParkingChecker::Instance()(types))
    {
      if (detail::IsInsideArea(feature, point))
      {
        context.m_insideParking = true;
        bool const structured = structuredParking(types);
        if (structured)
          context.m_insideStructuredParking = true;
        detail::MaybeSelectFreeArea(feature, structured ? 3 : 2, selectedFreeAreaPriority, selectedFreeAreaM2,
                                    context.m_freeAreaTriangles);
      }
      return;
    }

    if (parkingLikeLand(types))
    {
      if (detail::IsInsideArea(feature, point))
      {
        context.m_insideParking = true;
        detail::MaybeSelectFreeArea(feature, 2, selectedFreeAreaPriority, selectedFreeAreaM2,
                                    context.m_freeAreaTriangles);
      }
      return;
    }

    if (strongOpenArea(types))
    {
      if (detail::IsInsideArea(feature, point))
        context.m_insideStrongOpenArea = true;
      return;
    }

    if (weakOpenArea(types))
    {
      if (detail::IsInsideArea(feature, point))
        context.m_insideWeakOpenArea = true;
      return;
    }

    if (ftypes::IsBuildingChecker::Instance()(types) && detail::IsInsideArea(feature, point))
    {
      double const areaM2 = detail::AreaM2(feature);
      if (areaM2 >= kLargeBuildingMinAreaM2)
      {
        context.m_insideLargeBuilding = true;
        if (selectedFreeAreaPriority == 0)
          detail::MaybeSelectFreeArea(feature, 1, selectedFreeAreaPriority, selectedFreeAreaM2,
                                      context.m_freeAreaTriangles);
      }
    }
  }, rect, scales::GetUpperScale());

  return context;
}
}  // namespace routing::free_driving_snap
