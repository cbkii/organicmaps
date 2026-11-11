"""Compile entire production owners, rather than extracting/reimplementing methods.

The queue and course tests use their real headers and retained tests. AsyncRouter uses
controlled domain/platform boundaries to execute its real worker, mutexes, request and
callback lifecycle. This is not a full map-engine, driver or bookmark integration test.
"""
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[3]
SUPPORT = ROOT / 'android/tools/native_owner_tests'


class NativeOwnersTest(unittest.TestCase):
    def build(self, root, sources, extra=()):
        compiler = shutil.which('c++') or shutil.which('g++')
        if not compiler:
            self.skipTest('NOT_RUN: C++23 compiler unavailable')
        output = root / 'tests'
        result = subprocess.run([compiler, '-std=c++23', '-DRELEASE', '-pthread',
                                 '-I', str(root), '-I', str(SUPPORT), '-I', str(ROOT / 'libs'),
                                 *extra, *map(str, sources), '-o', str(output)],
                                capture_output=True, text=True, timeout=90)
        self.assertEqual(result.returncode, 0, result.stderr)
        subprocess.run([str(output)], check=True, timeout=30)

    def boundaries(self, root):
        for path, content in {
            'testing/testing.hpp': '#include "test_support.hpp"',
            'base/assert.hpp': '''#pragma once
#include <cassert>
#include <cstdint>
#define CHECK_EQUAL(x,y,...) assert((x)==(y))
#define ASSERT_GREATER(x,y,...) assert((x)>(y))
#define ASSERT(x,...) assert(x)
#define ASSERT_EQUAL(x,y,...) assert((x)==(y))
#define CHECK(x,...) assert(x)
#define VERIFY(x,...) assert(x)
#define UNREACHABLE() std::terminate()
''',
        }.items():
            file = root / path
            file.parent.mkdir(parents=True, exist_ok=True)
            file.write_text(content)

    def test_complete_message_queue_owner_and_retained_course_tests(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            self.boundaries(root)
            self.build(root, [SUPPORT / 'runner.cpp', ROOT / 'libs/drape_frontend/message_queue.cpp',
                             ROOT / 'libs/drape_frontend/drape_frontend_tests/message_queue_tests.cpp',
                             ROOT / 'libs/drape_frontend/drape_frontend_tests/my_position_driving_policy_tests.cpp'],
                       ['-O2', '-ffast-math'])

    def test_complete_async_router_worker_owner(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            self.boundaries(root)
            for header in ('absent_regions_finder', 'checkpoints', 'route', 'router', 'router_delegate', 'routing_callbacks'):
                path = root / 'routing' / (header + '.hpp')
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text('#include "router_contracts.hpp"')
            for name, text in {
                'platform/platform.hpp': '#include "router_contracts.hpp"',
                'base/logging.hpp': '#define LOG(...) ((void)0)',
                'base/thread.hpp': '#pragma once\n#include <thread>\nnamespace threads {using SimpleThread=std::thread;}',
            }.items():
                path = root / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(text)
            self.build(root, [SUPPORT / 'async_router_owner.cpp', ROOT / 'libs/routing/async_router.cpp'])

    def test_complete_render_read_and_queue_owners(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            self.boundaries(root)
            headers = {
                'drape/graphics_context.hpp': """#pragma once
namespace dp {class GraphicsContext;enum class BackgroundMode{Default};}""",
                'drape/texture_manager.hpp': '#pragma once\nnamespace dp {class TextureManager;}',
                'drape_frontend/custom_features_context.hpp': """#pragma once
#include <memory>
namespace df {struct CustomFeaturesContext {};using CustomFeaturesContextWeakPtr=std::weak_ptr<CustomFeaturesContext>;}""",
                'drape_frontend/tile_key.hpp': """#pragma once
#include "geometry/point2d.hpp"
namespace df {struct TileKey {static constexpr int kNoZoom=-1;int m_zoomLevel=15;
m2::PointI GetTileCoords()const{return {};}double GetTileXOffset()const{return 0;}
static m2::PointI NoCoordinates(){return {};}bool operator<(TileKey const&)const{return false;}
int GetWrappedDataRect()const{return 0;}};}""",
                'drape_frontend/traffic_generator.hpp': '#pragma once\n#include <vector>\nnamespace df {using TrafficSegmentsGeometry=std::vector<int>;}',
                'drape_frontend/message_subclasses.hpp': """#pragma once
#include "drape_frontend/map_shape.hpp"
#include "drape_frontend/traffic_generator.hpp"
namespace df {struct FlushTrafficGeometryMessage:Message {FlushTrafficGeometryMessage(TileKey const&,TrafficSegmentsGeometry&&){}Type GetType()const override{return Type::FlushTrafficGeometry;}};}""",
                'indexer/feature_decl.hpp': """#pragma once
struct MwmSet{using MwmId=int;};struct FeatureID{int m_mwmId=0;bool operator<(FeatureID const&o)const{return m_mwmId<o.m_mwmId;}};class FeatureType {};""",
                'drape_frontend/metaline_manager.hpp': '#pragma once\nnamespace df {struct MetalineManager {template<class T> void Update(T const&){} };}',
                'drape_frontend/map_data_provider.hpp': """#pragma once
#include "indexer/feature_decl.hpp"
#include <stdexcept>
namespace df {struct MapDataProvider {bool fail=false,m_isCountryLoadedByName=false;
template<class Fn>void ReadFeaturesID(Fn,int,int)const{if(fail)throw std::runtime_error("provider failure");}
template<class Fn,class V>void ReadFeatures(Fn,V const&)const{} };}""",
                'drape_frontend/rule_drawer.hpp': '#pragma once\nnamespace df {struct RuleDrawer {template<class T>RuleDrawer(bool,T,int){}void operator()(FeatureType&){} };}',
                'drape_frontend/tile_utils.hpp': '#pragma once\nnamespace df {inline int ClipTileZoomByMaxDataZoom(int z){return z;}}',
            }
            for name, text in headers.items():
                path = root / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(text)
            self.build(root, [SUPPORT / 'render_owner.cpp',
                             ROOT / 'libs/drape_frontend/engine_context.cpp',
                             ROOT / 'libs/drape_frontend/tile_info.cpp',
                             ROOT / 'libs/drape_frontend/message_queue.cpp'])
