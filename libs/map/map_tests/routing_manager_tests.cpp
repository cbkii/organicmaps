#include "testing/testing.hpp"

#include "map/framework.hpp"
#include "map/routing_mark.hpp"

#include "routing/ruler_router.hpp"
#include "platform/gui_thread.hpp"
#include "base/scope_guard.hpp"
#include "base/thread_safe_queue.hpp"

#include <chrono>
#include <memory>
#include <thread>
#include <algorithm>
#include <vector>

namespace routing_manager_tests
{
namespace
{
// Pump only this fixture's tasks, not callbacks left by earlier Framework instances.
class TestGuiThread final : public base::TaskLoop
{
public:
  PushResult Push(Task && task) override
  {
    m_tasks.Push(std::move(task));
    return {true, kNoId};
  }
  PushResult Push(Task const & task) override { return Push(Task(task)); }

  void RunNext()
  {
    Task task;
    auto const deadline = std::chrono::steady_clock::now() + std::chrono::seconds(5);
    while (!m_tasks.TryPop(task))
    {
      TEST(std::chrono::steady_clock::now() < deadline, ("Routing callback timed out"));
      std::this_thread::yield();
    }
    task();
  }

private:
  threads::ThreadSafeQueue<Task> m_tasks;
};

RouteMarkData MakeRoutePoint(RouteMarkType type, size_t intermediateIndex, double coord)
{
  RouteMarkData data;
  data.m_pointType = type;
  data.m_intermediateIndex = intermediateIndex;
  data.m_position = m2::PointD(coord, coord);
  return data;
}

size_t GetIntermediatePointsCount(std::vector<RouteMarkData> const & points)
{
  return std::count_if(points.begin(), points.end(),
                       [](RouteMarkData const & d) { return d.m_pointType == RouteMarkType::Intermediate; });
}
}  // namespace

UNIT_TEST(RoutingManager_ContinueRouteToPointAtLimitKeepsFinish)
{
  Framework framework(FrameworkParams(false /* m_enableDiffs */));
  auto & routingManager = framework.GetRoutingManager();

  routingManager.AddRoutePoint(MakeRoutePoint(RouteMarkType::Start, 0, 0.0));
  for (size_t i = 0; i < RoutePointsLayout::kMaxIntermediatePointsCount; ++i)
    routingManager.AddRoutePoint(MakeRoutePoint(RouteMarkType::Intermediate, i, static_cast<double>(i + 1)),
                                 false /* reorderIntermediatePoints */);
  routingManager.AddRoutePoint(MakeRoutePoint(RouteMarkType::Finish, 0, 101.0));

  auto const pointsBefore = routingManager.GetRoutePoints();
  TEST_EQUAL(pointsBefore.size(), RoutePointsLayout::kMaxRoutePointsCount, ());
  TEST_EQUAL(GetIntermediatePointsCount(pointsBefore), RoutePointsLayout::kMaxIntermediatePointsCount, ());
  TEST(pointsBefore.back().m_pointType == RouteMarkType::Finish, ());

  auto newFinish = MakeRoutePoint(RouteMarkType::Finish, 0, 102.0);
  TEST(!routingManager.ContinueRouteToPoint(std::move(newFinish)), ());

  auto const pointsAfter = routingManager.GetRoutePoints();
  TEST_EQUAL(pointsAfter.size(), pointsBefore.size(), ());
  TEST_EQUAL(GetIntermediatePointsCount(pointsAfter), RoutePointsLayout::kMaxIntermediatePointsCount, ());
  TEST(pointsAfter.back().m_pointType == RouteMarkType::Finish, ());
  TEST_EQUAL(pointsAfter.back().m_position.x, pointsBefore.back().m_position.x, ());
  TEST_EQUAL(pointsAfter.back().m_position.y, pointsBefore.back().m_position.y, ());
}

// If route marks are wiped between IsRoutingActive() and ContinueRouteToPoint(),
// the latter must return false without mutating the (empty) layout.
UNIT_TEST(RoutingManager_ContinueRouteToPointWithoutFinishFailsCleanly)
{
  Framework framework(FrameworkParams(false /* m_enableDiffs */));
  auto & routingManager = framework.GetRoutingManager();

  TEST_EQUAL(routingManager.GetRoutePointsCount(), 0, ());

  auto newFinish = MakeRoutePoint(RouteMarkType::Finish, 0, 1.0);
  TEST(!routingManager.ContinueRouteToPoint(std::move(newFinish)), ());
  TEST_EQUAL(routingManager.GetRoutePointsCount(), 0, ());
}
UNIT_TEST(RoutingManager_RebuildClearsUnavailableAlternatives)
{
  class Delegate final : public RoutingManager::Delegate
  {
  public:
    void OnRouteFollow(routing::RouterType) override {}
  } delegate;
  class Router final : public routing::IRouter
  {
  public:
    std::string GetName() const override { return "route choices test"; }
    void ClearState() override {}
    void SetGuides(routing::GuidesTracks &&) override {}
    bool FindClosestProjectionToRoad(m2::PointD const &, m2::PointD const &, double, routing::EdgeProj &) override
    {
      return false;
    }
    routing::RouterResultCode CalculateRoute(routing::Checkpoints const & checkpoints, m2::PointD const & direction,
                                             bool adjust, bool alternatives, routing::RouterDelegate const & delegate,
                                             routing::RoutesResult & result) override
    {
      if (++m_calls == 2)
        return routing::RouterResultCode::RouteNotFound;
      auto const code = static_cast<routing::IRouter &>(m_ruler).CalculateRoute(checkpoints, direction, adjust,
                                                                                alternatives, delegate, result);
      auto alternative = result.GetActive();
      alternative.SetDiffMidpoint({2, 5});
      result.m_routes.emplace_back(std::move(alternative));
      return code;
    }

  private:
    routing::RulerRouter m_ruler;
    size_t m_calls = 0;
  };

  auto & platform = GetPlatform();
  auto gui = std::make_unique<TestGuiThread>();
  auto * guiThread = gui.get();
  platform.SetGuiThread(std::move(gui));
  SCOPE_GUARD(restoreGui, [&platform] { platform.SetGuiThread(std::make_unique<platform::GuiThread>()); });
  BookmarkManager bookmarks(BookmarkManager::Callbacks([]() -> StringsBundle const &
  {
    static StringsBundle const bundle;
    return bundle;
  }, nullptr, nullptr, nullptr, nullptr, nullptr, nullptr));
  RoutingManager manager(RoutingManager::Callbacks(nullptr, nullptr, nullptr, nullptr, nullptr), delegate);
  manager.SetBookmarkManager(&bookmarks);
  auto & session = manager.RoutingSession();
  session.SetRouter(std::make_unique<Router>(), nullptr);
  bool built = false;
  session.SetRoutingCallbacks([&built](routing::RoutesResult const &, routing::RouterResultCode) { built = true; },
                              nullptr, nullptr, nullptr);
  session.BuildRoute(routing::Checkpoints({1, 2}, {5, 6}), routing::RouterDelegate::kNoTimeout);
  while (!built)
    guiThread->RunNext();
  TEST(session.SwapActiveAlternative(1), ());
  {
    auto edit = bookmarks.GetEditSession();
    edit.CreateUserMark<RouteAltMark>({1, 2});
    edit.CreateUserMark<RouteAltMark>({2, 5});
  }
  TEST_EQUAL(bookmarks.GetUserMarkIds(UserMark::Type::ROUTE_ALT).size(), 2, ());

  bool failed = false;
  session.RebuildRoute({1, 2}, [](routing::RoutesResult const &, routing::RouterResultCode) {}, nullptr,
                       [&failed](routing::RouterResultCode) { failed = true; }, routing::RouterDelegate::kNoTimeout,
                       routing::SessionState::RouteRebuilding, false);
  TEST(bookmarks.GetUserMarkIds(UserMark::Type::ROUTE_ALT).empty(),
       ("Unavailable controls must disappear immediately."));
  session.RouteCall([](routing::RoutesResult const & result)
  {
    TEST_EQUAL(result.m_routes.size(), 1, ());
    TEST_EQUAL(result.m_activeIdx, 0, ());
    TEST_EQUAL(result.GetActive().GetDiffMidpoint(), std::optional<m2::PointD>({2, 5}),
               ("The selected route must be retained."));
  });
  while (!failed)
    guiThread->RunNext();
  TEST(bookmarks.GetUserMarkIds(UserMark::Type::ROUTE_ALT).empty(), ());

  bool recovered = false;
  session.RebuildRoute({1, 2}, [&recovered](routing::RoutesResult const &, routing::RouterResultCode) {
    recovered = true;
  }, nullptr, nullptr, routing::RouterDelegate::kNoTimeout, routing::SessionState::RouteRebuilding, false);
  while (!recovered)
    guiThread->RunNext();
  TEST(session.SwapActiveAlternative(1), ("Fresh alternatives must become selectable."));
}
}  // namespace routing_manager_tests
