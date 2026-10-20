"""Compile real Android EGL owners against a deterministic EGL/native-window boundary.

This validates lifecycle/ownership decisions, not a PowerVR driver or GPU workload.
"""
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[3]
SDK = ROOT / 'android/sdk/src/main/cpp'
OGL = SDK / 'app/organicmaps/sdk/opengl'


class AndroidEglOwnersTest(unittest.TestCase):
    def test_resize_failed_attachment_cleanup_and_swap_quarantine(self):
        compiler = shutil.which('c++')
        if not compiler:
            self.skipTest('C++ compiler unavailable')
        files = {
            'base/assert.hpp': '#pragma once\n#include <cassert>\n#define ASSERT(x,...) assert(x)\n#define CHECK(x,...) assert(x)\n',
            'base/logging.hpp': '#pragma once\n#define LOG(...) ((void)0)\n',
            'base/src_point.hpp': '#pragma once\nnamespace base {struct SrcPoint{};}\n#define SRC() base::SrcPoint{}\n',
            'app/organicmaps/sdk/core/jni_helper.hpp': '#pragma once\nstruct JNIEnv {}; using jobject=void*;\n',
            'app/organicmaps/sdk/platform/AndroidPlatform.hpp': '#pragma once\n',
            'android/api-level.h': '#pragma once\n',
            'android/native_window.h': '''#pragma once
struct ANativeWindow {};
extern int releases;
inline void ANativeWindow_release(ANativeWindow*){++releases;}
inline int ANativeWindow_setBuffersGeometry(ANativeWindow*,int,int,int){return 0;}
''',
            'android/native_window_jni.h': '''#pragma once
#include "android/native_window.h"
#include "app/organicmaps/sdk/core/jni_helper.hpp"
inline ANativeWindow* ANativeWindow_fromSurface(JNIEnv*,jobject){static ANativeWindow w;return &w;}
''',
            'drape/gl_includes.hpp': '''#pragma once
#include "EGL/egl.h"
using GLuint=unsigned;
constexpr int GL_FRAMEBUFFER=0;
inline void glBindFramebuffer(int,int){}
inline bool gl3stubInit(){return true;}
''',
            'drape/oglcontext.hpp': '''#pragma once
template<class T>using ref_ptr=T*;
namespace dp {class BaseFramebuffer {public:void Bind(){}};
class GraphicsContext {public:virtual ~GraphicsContext()=default;
virtual void MakeCurrent(){} virtual void DoneCurrent(){} virtual void Present(){}
virtual void SetFramebuffer(ref_ptr<BaseFramebuffer>){} virtual void SetRenderingEnabled(bool){}
virtual void SetPresentAvailable(bool){} virtual bool Validate(){return true;}};
class OGLContext:public GraphicsContext {};}
''',
            'drape/graphics_context_factory.hpp': '''#pragma once
#include "drape/oglcontext.hpp"
namespace dp {class GraphicsContextFactory {public:virtual ~GraphicsContextFactory()=default;
virtual GraphicsContext* GetDrawContext()=0;virtual GraphicsContext* GetResourcesUploadContext()=0;
virtual bool IsDrawContextCreated()const{return false;}virtual bool IsUploadContextCreated()const{return false;}
virtual void WaitForInitialization(GraphicsContext*){}virtual void SetPresentAvailable(bool){} };}
''',
            'EGL/egl.h': '''#pragma once
#include <cstddef>
struct ANativeWindow;
using EGLint=int;using EGLDisplay=int;using EGLSurface=int;using EGLContext=int;using EGLConfig=int;
constexpr int EGL_NO_CONTEXT=0,EGL_NO_SURFACE=0,EGL_NO_DISPLAY=0,EGL_DEFAULT_DISPLAY=1;
constexpr int EGL_FALSE=0,EGL_TRUE=1,EGL_CONTEXT_CLIENT_VERSION=2,EGL_NONE=3,EGL_RED_SIZE=4,
EGL_GREEN_SIZE=5,EGL_BLUE_SIZE=6,EGL_ALPHA_SIZE=7,EGL_STENCIL_SIZE=8,EGL_DEPTH_SIZE=9,
EGL_RENDERABLE_TYPE=10,EGL_SURFACE_TYPE=11,EGL_PBUFFER_BIT=12,EGL_WINDOW_BIT=13,
EGL_WIDTH=14,EGL_HEIGHT=15,EGL_NATIVE_VISUAL_ID=16,EGL_RENDER_BUFFER=17,EGL_BACK_BUFFER=18,EGL_DRAW=19;
extern int queries,destroys,terminates,swaps;extern bool failQuery,failWindow,failSwap;
inline int eglGetDisplay(int){return 1;}inline int eglInitialize(int,int*,int*){return 1;}
inline int eglTerminate(int){++terminates;return 1;}
inline int eglChooseConfig(int,int*,int* c,int,int* n){*n=1;*c=1;return 1;}
inline int eglGetConfigAttrib(int,int,int,int* r){*r=1;return 1;}
inline int eglCreateWindowSurface(int,int,void*,int*){return failWindow?0:2;}
inline int eglCreatePbufferSurface(int,int,int*){return 3;}
inline int eglDestroySurface(int,int){++destroys;return 1;}
inline int eglQuerySurface(int,int,int attr,int* r){++queries;*r=attr==EGL_WIDTH?1280:720;return !failQuery;}
inline int eglCreateContext(int,int,int,int*){return 4;}
inline int eglDestroyContext(int,int){return 1;}inline int eglMakeCurrent(int,int,int,int){return 1;}
inline int eglGetCurrentDisplay(){return 1;}inline int eglGetCurrentSurface(int){return 2;}
inline int eglGetCurrentContext(){return 4;}
inline int eglSwapBuffers(int,int){++swaps;return !failSwap;}
''',
            'owner_test.cpp': '''#include <cassert>
#include "app/organicmaps/sdk/opengl/androidoglcontextfactory.hpp"
#include "app/organicmaps/sdk/opengl/android_gl_utils.hpp"
int queries=0,destroys=0,terminates=0,releases=0,swaps=0;
bool failQuery=false,failWindow=false,failSwap=false;
namespace android {
ConfigComparator::ConfigComparator(EGLDisplay d):m_display(d){}
bool ConfigComparator::operator()(EGLConfig const&,EGLConfig const&)const{return false;}
void CheckEGL(base::SrcPoint const&){}
}
int main(){
 JNIEnv env;
 {android::AndroidOGLContextFactory f(&env,(void*)1);
  assert(f.IsValid() && queries==2);f.UpdateSurfaceSize(800,400);
  assert(queries==2 && f.GetWidth()==800 && f.GetHeight()==400);
  f.UpdateSurfaceSize(0,0);assert(f.GetWidth()==800);
  auto* draw=f.GetDrawContext();failSwap=true;draw->Present();draw->Present();
  assert(swaps==1 && !draw->Validate());draw->SetPresentAvailable(true);draw->Present();assert(swaps==1);
  f.ResetSurface();f.ResetSurface();assert(releases==1 && destroys==1);
  failWindow=true;f.SetSurface(&env,(void*)1);assert(!f.IsValid() && releases==2 && terminates==0);
  failWindow=false;failQuery=true;f.SetSurface(&env,(void*)1);
  assert(!f.IsValid() && releases==3 && destroys==2 && terminates==0);
  failQuery=false;failSwap=false;f.SetSurface(&env,(void*)1);assert(f.IsValid());
  draw->Present();assert(swaps==2 && draw->Validate());
 }
 assert(releases==4 && destroys==4 && terminates==1);
}
''',
        }
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            for name, content in files.items():
                path = root / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(content)
            binary = root / 'owners'
            result = subprocess.run([compiler, '-std=c++17', '-pthread', '-I', tmp, '-I', str(SDK),
                                     str(OGL / 'androidoglcontextfactory.cpp'), str(OGL / 'androidoglcontext.cpp'),
                                     str(root / 'owner_test.cpp'), '-o', str(binary)],
                                    capture_output=True, text=True, timeout=60)
            self.assertEqual(0, result.returncode, result.stderr)
            subprocess.run([str(binary)], check=True, timeout=10)
