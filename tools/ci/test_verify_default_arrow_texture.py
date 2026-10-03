#!/usr/bin/env python3
"""Mutation regressions for the optional default-arrow contract."""

import unittest

import verify_default_arrow_texture as verifier


class DefaultArrowTextureTest(unittest.TestCase):
    sentinel = "return make_unique_dp<StaticTexture>();"

    def verify(self, source: str) -> None:
        verifier.verify_sources(source, verifier.ARROW3D, verifier.STATIC_TEXTURE)

    def assert_rejected(self, source: str) -> None:
        with self.assertRaisesRegex(SystemExit, "ERROR:"):
            self.verify(source)

    def test_current_contract(self) -> None:
        self.verify(verifier.TEXTURE_MANAGER)

    def test_comment_cannot_replace_executable_sentinel(self) -> None:
        for mention in (f"// {self.sentinel}\n", f"/* {self.sentinel} }} {{ */"):
            with self.subTest(mention=mention):
                self.assert_rejected(verifier.TEXTURE_MANAGER.replace(
                    self.sentinel,
                    mention + 'return make_unique_dp<StaticTexture>(context, "other-texture.png");',
                    1,
                ))

    def test_string_or_raw_string_cannot_replace_executable_sentinel(self) -> None:
        for mention in (f'"{self.sentinel}"', f'R"check({self.sentinel} }} " {{)check"'):
            with self.subTest(mention=mention):
                self.assert_rejected(verifier.TEXTURE_MANAGER.replace(
                    self.sentinel,
                    f"auto ignored = {mention}; return make_unique_dp<StaticTexture>(context, texturePath);",
                    1,
                ))

    def test_default_loader_even_with_real_sentinel_is_rejected(self) -> None:
        self.assert_rejected(verifier.TEXTURE_MANAGER.replace(
            self.sentinel,
            'auto ignored = make_unique_dp<StaticTexture>(context, "other-texture.png"); ' + self.sentinel,
            1,
        ))

    def test_custom_guard_or_optional_loading_cannot_change(self) -> None:
        for original, replacement in (("!texturePath.empty()", "texturePath.empty()"),
                                      ("true /* allowOptional */", "false /* allowOptional */")):
            with self.subTest(original=original):
                self.assert_rejected(verifier.TEXTURE_MANAGER.replace(original, replacement, 1))

    def test_missing_function_anchor_reports_actionable_failure(self) -> None:
        self.assert_rejected(verifier.TEXTURE_MANAGER.replace("CreateArrowTexture", "RemovedArrowTexture"))

    def test_comments_with_braces_and_sentinel_remain_harmless(self) -> None:
        self.verify(verifier.TEXTURE_MANAGER.replace(
            self.sentinel, f"/* {{ }} {self.sentinel} */\n{self.sentinel}", 1))


if __name__ == "__main__":
    unittest.main()
