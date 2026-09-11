#pragma once

#include "routing/checkpoints.hpp"
#include "routing/free_driving_road_snap_policy.hpp"
#include "routing/road_graph.hpp"
#include "routing/router_delegate.hpp"
#include "routing/routing_callbacks.hpp"

#include "kml/type_utils.hpp"

#include <cstddef>
#include <functional>
#include <map>
#include <string>
#include <vector>

namespace routing
{

using CountryParentNameGetterFn = std::function<std::string(std::string const &)>;

// Guides with integer ids containing multiple tracks. One track consists of its points.
using GuidesTracks = std::map<kml::MarkGroupId, std::vector<kml::TrackGeometry>>;

class RoutesResult;

struct EdgeProj
{
  Edge m_edge;
  m2::PointD m_point;
};

struct FreeDrivingCorridorProjection
{
  EdgeProj m_projection;
  double m_pathDistanceM = 0.0;
  size_t m_hops = 0;
};

/// Routing engine type.
enum class RouterType
{
  // @TODO It's necessary to rename Vehicle value to Car.
  Vehicle = 0,  /// For Car routing.
  Pedestrian,   /// For A star pedestrian routing.
  Bicycle,      /// For A star bicycle routing.
  Transit,      /// For A star pedestrian + transit routing.
  Ruler,        /// For simple straight line router.
  Count         /// Number of router types.
};

std::string ToString(RouterType type);
RouterType FromString(std::string const & str);
std::string DebugPrint(RouterType type);

class IRouter
{
public:
  virtual ~IRouter() = default;

  /// Return unique name of a router implementation.
  virtual std::string GetName() const = 0;

  /// Clear all temporary buffers.
  virtual void ClearState() = 0;

  virtual void SetGuides(GuidesTracks && guides) = 0;

  /// Override this function with routing implementation.
  /// It will be called in separate thread and only one function will processed in same time.
  /// @warning please support Cancellable interface calls. You must stop processing when it is true.
  ///
  /// @param checkpoints start, finish and intermediate points
  /// @param startDirection start direction for routers with high cost of the turnarounds
  /// @param adjust adjust route to the previous one if possible
  /// @param delegate callback functions and cancellation flag
  /// @param result populated with one or more alternative routes (as RouteBase) when successful;
  ///               callers consume the active alternative via result.GetActive()
  /// @return ResultCode error code or NoError if at least one route was produced
  /// @see Cancellable
  virtual RouterResultCode CalculateRoute(Checkpoints const & checkpoints, m2::PointD const & startDirection,
                                          bool adjust, RouterDelegate const & delegate, RoutesResult & result) = 0;

  virtual bool FindClosestProjectionToRoad(m2::PointD const & point, m2::PointD const & direction, double radius,
                                           EdgeProj & proj) = 0;

  /// Returns nearby directed projections from at most |maxCount| physical road segments.
  /// Implementations without a queryable graph fall back to one nearest projection.
  virtual void FindClosestProjectionsToRoad(m2::PointD const & point, double radius, size_t maxCount,
                                            std::vector<EdgeProj> & projections)
  {
    projections.clear();
    if (maxCount == 0)
      return;

    EdgeProj projection;
    if (FindClosestProjectionToRoad(point, m2::PointD::Zero(), radius, projection))
      projections.push_back(projection);
  }

  /// Builds a bounded local forward corridor from an already accepted projection. The returned
  /// path distances are along the explored graph, not straight-line chords. This is intentionally
  /// local and must not invoke route/A* calculation.
  virtual void FindFreeDrivingRoadCorridor(EdgeProj const & from, m2::PointD const & point, double maxDistanceM,
                                           size_t maxEdges, size_t maxHops,
                                           std::vector<FreeDrivingCorridorProjection> & projections) const
  {
    static_cast<void>(from);
    static_cast<void>(point);
    static_cast<void>(maxDistanceM);
    static_cast<void>(maxEdges);
    static_cast<void>(maxHops);
    projections.clear();
  }

  /// Returns lightweight, map-derived metadata for ambiguity scoring. False means metadata is
  /// unavailable and callers must treat the road as unknown rather than rejecting it.
  virtual bool GetFreeDrivingRoadMetadata(Edge const & edge, free_driving_snap::RoadMetadata & metadata) const
  {
    static_cast<void>(edge);
    metadata = {};
    return false;
  }

  /// Returns true only when |to| is an immediate outgoing graph continuation from |from|.
  virtual bool AreRoadEdgesConnected(Edge const & from, Edge const & to) const
  {
    static_cast<void>(from);
    static_cast<void>(to);
    return false;
  }

  /// Swap the saved last-route state with the alternative's saved state. Called when the user
  /// picks an alternative variant so a subsequent AdjustRoute (off-route rebuild) adjusts to
  /// the selected route rather than the original primary. Default: no-op.
  virtual void SwapAltRouteToActive() {}
};

}  // namespace routing
