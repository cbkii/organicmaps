"""Execute the production compact readiness state against presentation failure generations."""
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[3]


class PresentationReadinessTest(unittest.TestCase):
    def test_failed_swap_revokes_readiness_and_only_current_presentation_restores_it(self):
        compiler = [shutil.which('javac')] if shutil.which('javac') else [shutil.which('java'), '-m', 'jdk.compiler/com.sun.tools.javac.Main']
        if compiler[0] is None:
            self.skipTest('Java compiler unavailable')
        harness = '''package app.organicmaps.incar;
public class PresentationTest {
  static void check(boolean value) {if(!value)throw new AssertionError();}
  public static void main(String[] args) {
    var state=new InCarCompactStartupState();
    state.created(7,42);state.coreReady();state.nativeReady(7);
    state.attached(7,true);state.surface(7,800,480);state.activity(7,42,true);
    state.presentationRequired(7);check(!state.ready());
    state.presentation(7,true,2);check(state.ready());
    state.presentation(7,false,2);check(!state.ready());
    state.activity(7,42,true);check(!state.ready());
    state.presentation(7,true,1);check(!state.ready());
    state.presentation(7,true,3);check(state.ready());
    state.attached(7,false);check(!state.ready());
    state.presentation(7,true,3);check(!state.ready());
    state.created(8,43);state.nativeReady(8);state.attached(8,true);
    state.surface(8,800,480);state.activity(8,43,true);state.presentationRequired(8);
    state.presentation(7,true,100);check(!state.ready());
    state.presentation(8,true,1);check(state.ready());
    // A new observation lease can belong to a recreated native factory.
    state.presentationRequired(8);check(!state.ready());
    state.presentation(8,true,0);check(state.ready());
  }
}'''
        with tempfile.TemporaryDirectory() as tmp:
            source = Path(tmp) / 'PresentationTest.java'
            source.write_text(harness)
            production = ROOT / 'android/app/src/main/java/app/organicmaps/incar/InCarCompactStartupState.java'
            subprocess.run([*compiler, '-d', tmp, str(source), str(production)], check=True, timeout=30)
            subprocess.run(['java', '-cp', tmp, 'app.organicmaps.incar.PresentationTest'], check=True, timeout=10)


if __name__ == '__main__':
    unittest.main()
