#include "routing/async_router.hpp"
#include <cassert>
#include <chrono>
#include <iostream>
#include <thread>
using namespace routing;
using namespace std::chrono_literals;
struct ControlledRouter : IRouter {
 std::mutex guard;std::condition_variable cv;
 int calls=0;bool released=false;std::atomic<int> clears=0,queries=0,swaps=0;
 void ClearState()override{++clears;}
 void SwapAltRouteToActive()override{++swaps;}
 RouterResultCode CalculateRoute(Checkpoints const&,m2::PointD const&,bool,bool,RouterDelegate const&d,RoutesResult&r)override{
  int call;{std::unique_lock lock(guard);call=++calls;cv.notify_all();if(call==2||call==4){released=false;d.OnProgress(25);cv.wait(lock,[&]{return released;});}}
  if(call==6)return RouterResultCode::RouteNotFound;
  r.valid=true;if(call==5)throw RootException();return RouterResultCode::NoError;
 }
 void Wait(int call){std::unique_lock lock(guard);assert(cv.wait_for(lock,3s,[&]{return calls>=call;}));}
 void Release(){std::lock_guard lock(guard);released=true;cv.notify_all();}
 bool FindClosestProjectionToRoad(m2::PointD const&,m2::PointD const&,double,EdgeProj&)override{++queries;return true;}
 void FindClosestProjectionsToRoad(m2::PointD const&,double,size_t,std::vector<EdgeProj>&v)override{++queries;v.emplace_back();}
 void FindFreeDrivingRoadCorridor(EdgeProj const&,m2::PointD const&,double,size_t,size_t,std::vector<FreeDrivingCorridorProjection>&v)override{++queries;v.emplace_back();}
 bool GetFreeDrivingRoadMetadata(Edge const&,free_driving_snap::RoadMetadata&m)override{++queries;m.set=true;return true;}
 bool AreRoadEdgesConnected(Edge const&,Edge const&)override{++queries;return true;}
 double GetFreeDrivingRoadSpeedLimit(Edge const&)override{++queries;return 50;}
};
int main(){
 AsyncRouter a({});auto router=std::make_unique<ControlledRouter>();auto *r=router.get();a.SetRouter(std::move(router),{});
 std::vector<uint64_t> ready;std::vector<RouterResultCode> codes;int progress=0,removed=0;
 auto request=[&]{a.CalculateRoute({}, {},false,true,[&](auto result,auto code){ready.push_back(result->m_routesId);codes.push_back(code);},{},[&](auto){++removed;},[&](float){++progress;});};
 auto pump=[&](auto done){auto until=std::chrono::steady_clock::now()+3s;do{GetPlatform().Drain();if(done())return;assert(std::chrono::steady_clock::now()<until);std::this_thread::sleep_for(1ms);}while(true);};
 auto idle=[&]{EdgeProj p;auto until=std::chrono::steady_clock::now()+3s;while(!a.FindClosestProjectionToRoad({}, {},1,p)){assert(std::chrono::steady_clock::now()<until);std::this_thread::sleep_for(1ms);}};
 request();r->Wait(1);idle();pump([&]{return !ready.empty();});assert(ready==std::vector<uint64_t>{1});assert(a.SwapAltRouteToActive(1));
 request();r->Wait(2);assert(!a.SwapAltRouteToActive(1));int queries=r->queries;
 EdgeProj p;assert(!a.FindClosestProjectionToRoad({}, {},1,p));
 std::vector<EdgeProj> projections(1);a.FindClosestProjectionsToRoad({},1,1,projections);assert(projections.empty());
 std::vector<FreeDrivingCorridorProjection> corridor(1);a.FindFreeDrivingRoadCorridor({}, {},1,1,1,corridor);assert(corridor.empty());
 free_driving_snap::RoadMetadata metadata;metadata.set=true;assert(!a.GetFreeDrivingRoadMetadata({},metadata)&&!metadata.set);
 assert(!a.AreRoadEdgesConnected({},{}));assert(a.GetFreeDrivingRoadSpeedLimit({})==-1);assert(r->queries==queries);
 request();r->Release();r->Wait(3);idle();pump([&]{return ready.size()==2;});assert((ready==std::vector<uint64_t>{1,3}));
 assert(progress==0); // The superseded request's queued GUI progress cannot update the new route.
 assert(!a.SwapAltRouteToActive(2)&&a.SwapAltRouteToActive(3));
 request();r->Wait(4);a.ClearState();r->Release();idle();
 auto deadline=std::chrono::steady_clock::now()+3s;while(r->clears==0){assert(std::chrono::steady_clock::now()<deadline);std::this_thread::sleep_for(1ms);}
 GetPlatform().Drain();assert(ready.size()==2&&!a.SwapAltRouteToActive(3)&&!a.SwapAltRouteToActive(4));
 request();r->Wait(5);idle();pump([&]{return codes.size()==3;});assert(codes.back()==RouterResultCode::InternalError&&!a.SwapAltRouteToActive(5));
 request();r->Wait(6);idle();pump([&]{return removed==1;});assert(removed==1&&!a.SwapAltRouteToActive(6));
 request();r->Wait(7);idle();pump([&]{return ready.size()==4;});assert(ready.back()==7&&a.SwapAltRouteToActive(7));
 std::cout<<"PASS complete AsyncRouter owner: worker/supersession/clear/exception/delegate generations/all six graph guards\n";
}
