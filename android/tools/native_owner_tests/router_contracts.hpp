#pragma once
// Domain and GUI boundary doubles; AsyncRouter itself is compiled in full from libs/routing.
#include <atomic>
#include <condition_variable>
#include <cstdint>
#include <functional>
#include <memory>
#include <mutex>
#include <queue>
#include <set>
#include <stdexcept>
#include <string>
#include <vector>
namespace m2 { struct PointD { static PointD Zero(){return {};} }; }
namespace ms { struct LatLon {}; }
class RootException : public std::exception { public: char const * Msg() const {return "injected route failure";} };
namespace routing {
struct Edge {};struct EdgeProj {};struct FreeDrivingCorridorProjection {};
namespace free_driving_snap { struct RoadMetadata {bool set=false;}; }
enum class RouterResultCode {StartPointNotFound,EndPointNotFound,PointsInDifferentMWM,RouteNotFound,RouteFileNotExist,
NeedMoreMaps,Cancelled,NoError,NoCurrentPosition,InconsistentMWMandRoute,InternalError,FileTooOld,
IntermediatePointNotFound,TransitRouteNotFoundNoNetwork,TransitRouteNotFoundTooLongPedestrian,RouteNotFoundRedressRouteError,HasWarnings};
struct Checkpoints { double GetSummaryLengthBetweenPointsMeters() const {return 0;} };
using GuidesTracks=std::vector<int>;
struct RoutesResult {
 uint64_t m_routesId;bool valid=false;
 RoutesResult(std::string const &,uint64_t id):m_routesId(id){}
 bool IsValid()const{return valid;} RoutesResult const & GetActive() const {return *this;}
 double GetTotalTimeSec()const{return 0;}
};
using PointCheckCallback=std::function<void(ms::LatLon const&)>;
using ProgressCallback=std::function<void(float)>;
using ReadyCallbackOwnership=std::function<void(std::shared_ptr<RoutesResult>,RouterResultCode)>;
using NeedMoreMapsCallback=std::function<void(uint64_t,std::set<std::string>const&)>;
using RemoveRouteCallback=std::function<void(RouterResultCode)>;
class RouterDelegate {
 std::atomic<bool> cancelled=false;ProgressCallback progress;
public:
 static constexpr uint32_t kNoTimeout=0;
 void Reset(){cancelled=false;}void Cancel(){cancelled=true;}bool IsCancelled()const{return cancelled;}
 void SetPointCheckCallback(PointCheckCallback const&){}void SetProgressCallback(ProgressCallback const&p){progress=p;}
 void SetTimeout(uint32_t){}void OnProgress(float v)const{if(progress)progress(v);}
};
class IRouter {public:
 virtual ~IRouter()=default;
 virtual std::string GetName()const{return "controlled";}
 virtual void ClearState()=0;virtual void SetGuides(GuidesTracks&&){}
 virtual void SwapAltRouteToActive()=0;
 virtual RouterResultCode CalculateRoute(Checkpoints const&,m2::PointD const&,bool,bool,RouterDelegate const&,RoutesResult&)=0;
 virtual bool FindClosestProjectionToRoad(m2::PointD const&,m2::PointD const&,double,EdgeProj&)=0;
 virtual void FindClosestProjectionsToRoad(m2::PointD const&,double,size_t,std::vector<EdgeProj>&)=0;
 virtual void FindFreeDrivingRoadCorridor(EdgeProj const&,m2::PointD const&,double,size_t,size_t,std::vector<FreeDrivingCorridorProjection>&)=0;
 virtual bool GetFreeDrivingRoadMetadata(Edge const&,free_driving_snap::RoadMetadata&)=0;
 virtual bool AreRoadEdgesConnected(Edge const&,Edge const&)=0;
 virtual double GetFreeDrivingRoadSpeedLimit(Edge const&)=0;
};
class AbsentRegionsFinder {public:
 using RegionsSetT=std::set<std::string>;
 RouterResultCode GenerateAbsentRegions(Checkpoints const&,RouterDelegate const&){return RouterResultCode::NoError;}
 RegionsSetT GetAbsentRegions(){return {};}
};
}
class Platform {
 std::mutex guard;std::queue<std::function<void()>> tasks;
public:
 enum class Thread{Gui};
 void RunTask(Thread,std::function<void()> f){std::lock_guard lock(guard);tasks.push(std::move(f));}
 void Drain(){for(;;){std::function<void()> f;{std::lock_guard lock(guard);if(tasks.empty())return;f=std::move(tasks.front());tasks.pop();}f();}}
};
inline Platform & GetPlatform(){static Platform p;return p;}
