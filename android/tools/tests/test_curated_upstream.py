"""Execute curated native production methods without a GPU, Qt or an Android SDK.

These boundary harnesses complement (and do not replace) the retained native suites
and Android CI. Method text comes from the owning source, never a copied algorithm.
"""
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[3]


def read(path):
    return (ROOT / path).read_text()


def method(source, signature):
    start = source.index(signature)
    opening = source.index("{", start)
    depth = 1
    end = opening + 1
    while depth:
        depth += (source[end] == "{") - (source[end] == "}")
        end += 1
    return source[start:end]


class CuratedUpstreamTests(unittest.TestCase):
    def compile_run(self, code):
        compiler = shutil.which("c++") or shutil.which("g++")
        if not compiler:
            self.skipTest("NOT_RUN: C++ compiler unavailable")
        with tempfile.TemporaryDirectory() as temp:
            source, binary = Path(temp) / "probe.cpp", Path(temp) / "probe"
            source.write_text(code)
            result = subprocess.run(
                [compiler, "-std=c++23", "-DRELEASE", "-pthread", "-I", str(ROOT / "libs"),
                 "-I", str(ROOT), str(source), "-o", str(binary)],
                capture_output=True, text=True, timeout=60)
            self.assertEqual(result.returncode, 0, result.stderr)
            subprocess.run([str(binary)], check=True, timeout=10)

    def test_real_geometry_headers(self):
        self.compile_run(r'''
#include "geometry/any_rect2d.hpp"
#include "geometry/rect_intersect.hpp"
#include <cassert>
#include <cmath>
int main() {
  m2::RectD const r(0,0,1,10);
  m2::AnyRectD const a(r);
  m2::AnyRectD const b(m2::PointD(-5,7),ang::AngleD(-math::pi/6),m2::RectD(0,0,15,20));
  assert(a.IsIntersect(b) && b.IsIntersect(a));
  assert(!a.IsIntersect(m2::AnyRectD()));
  assert(!m2::AnyRectD().IsIntersect(a));
  m2::PointD p(-2,-5),q(3,15);
  assert(m2::Intersect(r,p,q));
  assert(r.IsPointInside(p) && r.IsPointInside(q));
  assert(std::isfinite(p.x) && std::isfinite(p.y));
  p={-2,-2};q={-1,-1}; assert(!m2::Intersect(r,p,q));
}
''')

    def test_router_cache_generation_and_all_fork_graph_queries(self):
        cpp = read("libs/routing/async_router.cpp")
        header = read("libs/routing/async_router.hpp")
        inline = "\n".join(method(header, signature) for signature in (
            "void FindClosestProjectionsToRoad(", "void FindFreeDrivingRoadCorridor(",
            "bool GetFreeDrivingRoadMetadata(", "bool AreRoadEdgesConnected(",
            "double GetFreeDrivingRoadSpeedLimit("))
        self.compile_run(r'''
#include <algorithm>
#include <cassert>
#include <cstdint>
#include <memory>
#include <mutex>
#include <vector>
#define ASSERT(x,...) assert(x)
using std::lock_guard;
namespace m2 { struct PointD {}; }
struct Edge {}; struct EdgeProj {}; struct FreeDrivingCorridorProjection {};
namespace free_driving_snap { struct RoadMetadata {}; }
struct Router {
 int calls=0,swaps=0;
 void SwapAltRouteToActive(){++swaps;}
 bool FindClosestProjectionToRoad(m2::PointD const&,m2::PointD const&,double,EdgeProj&){++calls;return true;}
 void FindClosestProjectionsToRoad(m2::PointD const&,double,size_t,std::vector<EdgeProj>& v){++calls;v.emplace_back();}
 void FindFreeDrivingRoadCorridor(EdgeProj const&,m2::PointD const&,double,size_t,size_t,
    std::vector<FreeDrivingCorridorProjection>& v){++calls;v.emplace_back();}
 bool GetFreeDrivingRoadMetadata(Edge const&,free_driving_snap::RoadMetadata&){++calls;return true;}
 bool AreRoadEdgesConnected(Edge const&,Edge const&){++calls;return true;}
 double GetFreeDrivingRoadSpeedLimit(Edge const&){++calls;return 50;}
};
struct AsyncRouter {
 std::mutex m_guard; bool m_isCalculating=false; uint64_t m_cachedRoutesId=0;
 std::shared_ptr<Router> m_router;
 bool SwapAltRouteToActive(uint64_t);
 bool FindClosestProjectionToRoad(m2::PointD const&,m2::PointD const&,double,EdgeProj&);
''' + inline + r'''
};
''' + method(cpp, "bool AsyncRouter::SwapAltRouteToActive(") + "\n" + method(
            cpp, "bool AsyncRouter::FindClosestProjectionToRoad(") + r'''
int main(){
 AsyncRouter a; EdgeProj p; Edge e; free_driving_snap::RoadMetadata metadata;
 assert(!a.SwapAltRouteToActive(1)); assert(!a.FindClosestProjectionToRoad({}, {}, 1, p));
 a.m_router=std::make_shared<Router>(); a.m_cachedRoutesId=7;
 assert(!a.SwapAltRouteToActive(0) && !a.SwapAltRouteToActive(6));
 assert(a.SwapAltRouteToActive(7) && a.m_router->swaps==1);
 std::vector<EdgeProj> ps(1); std::vector<FreeDrivingCorridorProjection> cs(1);
 for(bool busy:{true,false}){
  a.m_isCalculating=busy; a.m_cachedRoutesId=busy?0:7; a.m_router->calls=0;
  assert(a.FindClosestProjectionToRoad({}, {}, 1,p)==!busy);
  a.FindClosestProjectionsToRoad({},1,2,ps); assert(ps.size()==(busy?0:1));
  a.FindFreeDrivingRoadCorridor(p,{},1,2,3,cs); assert(cs.size()==(busy?0:1));
  assert(a.GetFreeDrivingRoadMetadata(e,metadata)==!busy);
  assert(a.AreRoadEdgesConnected(e,e)==!busy);
  assert(a.GetFreeDrivingRoadSpeedLimit(e)==(busy?-1:50));
  assert(a.m_router->calls==(busy?0:6));
  if(busy) assert(!a.SwapAltRouteToActive(7));
 }
}
''')

    def test_routing_request_snapshot_invalidates_both_suffixes(self):
        source = read("libs/routing/index_router.cpp")
        start = source.index("    m_requestRoutingOptions =")
        snapshot = source[start:source.index("    SCOPE_GUARD(featureRoadGraphClear", start)]
        start = source.index("RoutingOptions const routingOptions =", source.index("IndexRouter::MakeWorldGraph"))
        selected = source[start:source.index(";", start) + 1]
        self.compile_run(r'''
#include <cassert>
#include <memory>
#include <optional>
struct RoutingOptions {
 using RoadType=unsigned; unsigned mask;
 static unsigned saved;
 static RoutingOptions LoadCarOptionsFromSettings(){return {saved};}
 unsigned GetOptions()const{return mask;}
};
unsigned RoutingOptions::saved=0;
struct State {
 std::optional<RoutingOptions> m_requestRoutingOptions;
 unsigned m_previousRoutingOptions=0;
 std::unique_ptr<int> m_lastRoute,m_lastFakeEdges,m_lastAltRoute,m_lastAltFakeEdges;
 void seed(){m_lastRoute=std::make_unique<int>(1);m_lastFakeEdges=std::make_unique<int>(2);
 m_lastAltRoute=std::make_unique<int>(3);m_lastAltFakeEdges=std::make_unique<int>(4);}
 void begin(){
''' + snapshot + r'''
 }
 unsigned graphOptions(){
''' + selected + r'''
 return routingOptions.GetOptions();}
};
int main(){State s;s.seed();s.begin();assert(s.m_lastRoute && s.m_lastAltRoute);
 RoutingOptions::saved=4;assert(s.graphOptions()==0);
 s.m_requestRoutingOptions.reset();s.begin();
 assert(!s.m_lastRoute && !s.m_lastFakeEdges && !s.m_lastAltRoute && !s.m_lastAltFakeEdges);
 assert(s.graphOptions()==4);s.seed();s.begin();assert(s.m_lastRoute && s.m_lastAltRoute);
}
''')

    def test_bookmark_replacement_keeps_identity_and_replaces_metadata(self):
        source = read("libs/map/bookmark_manager.cpp")
        actual = method(source, "void BookmarkManager::DeleteCompilations(") + "\n" + method(
            source, "void BookmarkManager::UpdateBookmarkCategory(")
        self.compile_run(r'''
#include <cassert>
#include <map>
#include <memory>
#include <string>
#define CHECK_THREAD_CHECKER(...) ((void)0)
#define CHECK(x,...) assert(x)
#include <vector>
namespace kml {using MarkGroupId=int;using GroupIdCollection=std::vector<int>;
struct CategoryData{int m_id;std::string name;GroupIdCollection m_compilationIds;};}
struct BookmarkCategory{ kml::CategoryData data;bool autosave;
 BookmarkCategory(kml::CategoryData&& d,bool a):data(std::move(d)),autosave(a){}
 kml::CategoryData const& GetCategoryData()const{return data;} };
struct BookmarkManager {
 std::map<int,std::unique_ptr<BookmarkCategory>> m_categories; std::map<int,int> m_compilations; int cleared=0;
 struct Tracker {int id=0;void OnAddGroup(int i){id=i;}} m_changesTracker;
 void ClearGroup(int id){cleared=id;}
 void DeleteCompilations(kml::GroupIdCollection const&);
 void UpdateBookmarkCategory(kml::MarkGroupId,kml::CategoryData&&);
};
''' + actual + r'''
int main(){BookmarkManager b;b.m_categories[7]=std::make_unique<BookmarkCategory>(kml::CategoryData{7,"old",{11,12}},true);
 b.m_compilations={{11,1},{12,1},{21,1},{99,1}};
 b.UpdateBookmarkCategory(7,{99,"new",{21}});assert(b.m_categories.size()==1);
 auto const& c=*b.m_categories.at(7);assert(c.data.m_id==7 && c.data.name=="new" && !c.autosave);
 assert(b.cleared==7 && b.m_changesTracker.id==7);
 assert(b.m_compilations.size()==2 && b.m_compilations.count(21) && b.m_compilations.count(99));
 assert(c.data.m_compilationIds==kml::GroupIdCollection{21});}
''')

    def test_actual_shape_batching_and_cancelled_tail(self):
        source = read("libs/drape_frontend/engine_context.cpp")
        actual = "\n".join(method(source, signature) for signature in (
            "void EngineContext::Flush(", "void EngineContext::FlushGeometry(",
            "void EngineContext::FlushOverlays(", "void EngineContext::EndReadTile("))
        self.compile_run(r'''
#include <cassert>
#include <atomic>
#include <memory>
#include <numeric>
#include <utility>
#include <vector>
using TMapShapes=std::vector<int>;
using TileReadCancellation=std::shared_ptr<std::atomic<bool>>;
struct Message {int type;TMapShapes shapes;TileReadCancellation token;
 Message(int t,TMapShapes s={},TileReadCancellation c={}):type(t),shapes(std::move(s)),token(c){}
 virtual ~Message()=default;};
struct MapShapeReadedMessage:Message{MapShapeReadedMessage(int,TMapShapes s,TileReadCancellation c):Message(1,std::move(s),c){}};
struct OverlayMapShapeReadedMessage:Message{OverlayMapShapeReadedMessage(int,TMapShapes s,TileReadCancellation c):Message(2,std::move(s),c){}};
struct TileReadEndMessage:Message{TileReadEndMessage(int):Message(3){}};
template<class T,class... A> auto make_unique_dp(A&&... a){return std::make_unique<T>(std::forward<A>(a)...);}
struct EngineContext{
 int m_tileKey=1;TileReadCancellation m_readCancelled=std::make_shared<std::atomic<bool>>(false);
 TMapShapes m_geometry;std::vector<std::unique_ptr<Message>> sent;
 bool IsCancelled()const{return m_readCancelled->load();}
 void PostMessage(std::unique_ptr<Message> m){sent.push_back(std::move(m));}
 void Flush(TMapShapes&&);void FlushGeometry();void FlushOverlays(TMapShapes&&);void EndReadTile();
};
''' + actual + r'''
int main(){
 EngineContext c;std::vector<int> v(130);std::iota(v.begin(),v.end(),0);c.Flush(std::move(v));
 assert(v.empty() && c.sent.size()==2 && c.m_geometry.size()==2);
 c.FlushOverlays({200});c.EndReadTile();assert(c.sent.size()==5);
 assert(c.sent[0]->shapes.size()==64 && c.sent[1]->shapes.size()==64);
 assert(c.sent[2]->shapes==TMapShapes({128,129}));assert(c.sent[3]->type==2 && c.sent[4]->type==3);
 int expected=0;for(int i=0;i<3;++i)for(int id:c.sent[i]->shapes)assert(id==expected++);
 EngineContext cancelled;cancelled.Flush({1,2,3});cancelled.m_readCancelled->store(true);
 cancelled.FlushOverlays({4});cancelled.EndReadTile();
 assert(cancelled.m_geometry.empty() && cancelled.sent.size()==1 && cancelled.sent[0]->type==3);
 c.m_readCancelled->store(true);assert(c.sent[0]->token->load());
 EngineContext sameKey;assert(sameKey.m_tileKey==c.m_tileKey && !sameKey.IsCancelled());
}
''')

    def test_vulkan_handler_removal_uses_full_width_ids(self):
        actual = method(read("libs/drape/vulkan/vulkan_base_context.cpp"),
                        "void VulkanBaseContext::UnregisterHandler(")
        self.compile_run(r'''
#include <algorithm>
#include <array>
#include <cassert>
#include <cstdint>
#include <functional>
#include <utility>
#include <vector>
struct VulkanBaseContext {
 using ContextHandler=std::function<void(uint32_t)>;
 std::array<std::vector<std::pair<uint32_t,ContextHandler>>,3> m_handlers;
 void UnregisterHandler(uint32_t);
};
''' + actual + r'''
int main(){VulkanBaseContext c;int calls=0;
 for(auto& h:c.m_handlers){h.emplace_back(1,[&](uint32_t){++calls;});
 h.emplace_back(257,[&](uint32_t){calls+=10;});}
 c.UnregisterHandler(1);
 for(auto const& h:c.m_handlers){assert(h.size()==1 && h.front().first==257);h.front().second(0);}
 assert(calls==30);c.UnregisterHandler(257);
 for(auto const& h:c.m_handlers)assert(h.empty());
 c.UnregisterHandler(999);
}
''')


if __name__ == "__main__":
    unittest.main()
