#pragma once

#include "routing/free_driving_road_snap_policy.hpp"

#include "indexer/data_source.hpp"
#include "indexer/feature.hpp"
#include "indexer/feature_algo.hpp"
#include "indexer/ftypes_matcher.hpp"
#include "indexer/scales.hpp"

#include "geometry/mercator.hpp"

namespace routing::free_driving_snap
{
namespace detail
{
inline bool IsInsideArea(FeatureType & feature, m2::PointD const & point)
{
  return feature.GetGeomType() == feature::GeomType::Area && feature::GetMinDistanceMeters(feature, point) == 0.0;
}

inline double ApproximateAreaM2(FeatureType & feature)
{
  auto const rect = feature.GetLimitRect(FeatureType::BEST_GEOMETRY);
  if (!rect.IsValid())
    return 0.0;
  double const widthM = mercator::DistanceOnEarth(rect.LeftBottom(), rect.RightBottom());
  double const heightM = mercator::DistanceOnEarth(rect.LeftBottom(), rect.LeftTop());
  return widthM * heightM;
}
}  // namespace detail

// Reads only a small map neighbourhood and parses geometry only after a type has
// been identified as relevant. Callers cache the result; this is not a display-tick API.
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

  // A 30 m neighbourhood is enough to see a parking entrance without turning
  // this into a general nearby-feature search.
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
        if (structuredParking(types))
          context.m_insideStructuredParking = true;
      }
      return;
    }

    if (parkingLikeLand(types))
    {
      if (detail::IsInsideArea(feature, point))
        context.m_insideParking = true;
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

    if (ftypes::IsBuildingChecker::Instance()(types) && detail::IsInsideArea(feature, point) &&
        detail::ApproximateAreaM2(feature) >= kLargeBuildingMinAreaM2)
    {
      // Generic building membership is deliberately weak. The matcher requires
      // low speed plus sustained road mismatch before it can release on this alone.
      context.m_insideLargeBuilding = true;
    }
  }, rect, scales::GetUpperScale());

  return context;
}
}  // namespace routing::free_driving_snap
