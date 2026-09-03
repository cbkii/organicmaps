import tempfile
import unittest
from pathlib import Path
from unittest import mock

from maps_generator.generator import stages as stages_module


class MwmStage(stages_module.Stage):
    def apply(self, *args, **kwargs):
        pass


class CountryStage(stages_module.Stage):
    def apply(self, *args, **kwargs):
        pass


class RegularStage(stages_module.Stage):
    def apply(self, *args, **kwargs):
        pass


class HelperStage(stages_module.Stage):
    def apply(self, *args, **kwargs):
        pass


class TestStages(unittest.TestCase):
    def test_is_valid_stage_name_checks_all_stage_groups(self):
        registry = stages_module.Stages()
        registry.set_mwm_stage(MwmStage)
        registry.add_country_stage(CountryStage)
        registry.add_stage(RegularStage)
        registry.add_helper_stage(HelperStage)

        for name in ("Mwm", "Country", "Regular", "Helper"):
            with self.subTest(name=name):
                self.assertTrue(registry.is_valid_stage_name(name))
        self.assertFalse(registry.is_valid_stage_name("Missing"))

    def test_test_stage_runs_pretests_body_and_posttests_in_order(self):
        events = []

        def pretest(env, logger, *args, **kwargs):
            events.append("pre")
            return True

        def posttest(env, logger, *args, **kwargs):
            events.append("post")
            return True

        pre = stages_module.Test(pretest, is_pretest=True)
        post = stages_module.Test(posttest)

        @stages_module.test_stage(pre, post)
        class DecoratedStage(stages_module.Stage):
            def apply(self, env, *args, **kwargs):
                events.append("body")

        DecoratedStage()(object())
        self.assertEqual(events, ["pre", "body", "post"])

    def test_outer_stage_registers_normal_and_mwm_stages(self):
        registry = stages_module.Stages()
        with mock.patch.object(stages_module, "stages", registry):
            @stages_module.outer_stage
            class Outer(stages_module.Stage):
                def apply(self, *args, **kwargs):
                    pass

            @stages_module.outer_stage
            @stages_module.mwm_stage
            class Mwm(stages_module.Stage):
                def apply(self, *args, **kwargs):
                    pass

        self.assertIn(Outer, registry.stages)
        self.assertIn(Mwm, registry.stages)
        self.assertIs(registry.mwm_stage, Mwm)

    def test_depends_from_internal_downloads_resolved_dependency_once(self):
        class Paths:
            def __init__(self, root):
                self.status_path = root
                self._artifact = str(Path(root) / "artifact.dat")

            @property
            def artifact(self):
                return self._artifact

        class Env:
            def __init__(self, root):
                self.paths = Paths(root)
                self.production = True
                self.force_download_files = False

        class FakeStatus:
            instances = []

            def __init__(self, path):
                self.path = path
                self.finished = False
                self.__class__.instances.append(self)

            def is_finished(self):
                return False

            def finish(self):
                self.finished = True

        dependency = stages_module.InternalDependency(
            "https://example.invalid/artifact.dat", Paths.artifact
        )
        events = []

        @stages_module.depends_from_internal(dependency)
        class DecoratedStage(stages_module.Stage):
            def apply(self, env, *args, **kwargs):
                events.append("body")

        with tempfile.TemporaryDirectory() as root, \
             mock.patch.object(stages_module.status, "Status", FakeStatus), \
             mock.patch.object(
                 stages_module,
                 "normalize_url_to_path_dict",
                 side_effect=lambda urls: urls,
             ) as normalize, \
             mock.patch.object(stages_module, "download_files") as download:
            env = Env(root)
            DecoratedStage()(env)

        expected = {"https://example.invalid/artifact.dat": env.paths.artifact}
        normalize.assert_called_once_with(expected)
        download.assert_called_once_with(expected, False)
        self.assertEqual(events, ["body"])
        self.assertTrue(FakeStatus.instances[-1].finished)


if __name__ == "__main__":
    unittest.main()
