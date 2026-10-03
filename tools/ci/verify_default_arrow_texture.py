#!/usr/bin/env python3
"""Pin the default 3D-arrow texture fallback without inventing a packaged asset."""

from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[2]
TEXTURE_MANAGER = (ROOT / "libs/drape/texture_manager.cpp").read_text(encoding="utf-8")
ARROW3D = (ROOT / "libs/drape_frontend/arrow3d.cpp").read_text(encoding="utf-8")
STATIC_TEXTURE = (ROOT / "libs/drape/static_texture.hpp").read_text(encoding="utf-8")

# Mask literals, rather than deleting them: a stray string is still executable syntax and
# must not turn into an acceptable default return. Raw strings may contain quotes/braces.
CPP_NON_CODE = re.compile(
    r"//[^\n]*|/\*[\s\S]*?\*/"
    r'|(?:u8|u|U|L)?R"(?P<delimiter>[^()\s\\]{0,16})\([\s\S]*?\)(?P=delimiter)"'
    r'|"(?:\\[\s\S]|[^"\\])*"'
    r"|'(?:\\[\s\S]|[^'\\])*'"
)


def executable_source(source: str) -> str:
    return CPP_NON_CODE.sub(
        lambda match: " " if match.group().startswith(("//", "/*")) else "__cpp_literal__",
        source,
    )


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(f"ERROR: {message}")


def extract_between(source: str, start_anchor: str, end_anchor: str, label: str) -> str:
    start = source.find(start_anchor)
    end = source.find(end_anchor, start + len(start_anchor)) if start >= 0 else -1
    require(start >= 0 and end > start, f"{label} anchors missing or reordered")
    return source[start:end]


def extract_braced_block(source: str, anchor: str, label: str) -> str:
    start = source.find(anchor)
    require(start >= 0, f"{label} guard is missing")
    opening = source.find("{", start + len(anchor))
    require(opening >= 0, f"{label} opening brace is missing")
    depth = 0
    for index in range(opening, len(source)):
        char = source[index]
        if char == "{":
            depth += 1
        elif char == "}":
            depth -= 1
            if depth == 0:
                return source[opening + 1:index]
    raise SystemExit(f"ERROR: {label} closing brace is missing")


def verify_sources(texture_manager: str, arrow3d: str, static_texture: str) -> None:
    create_arrow = extract_between(
        executable_source(texture_manager),
        "drape_ptr<Texture> CreateArrowTexture",
        "class SimpleTexturePool",
        "CreateArrowTexture / SimpleTexturePool",
    )
    body = extract_braced_block(create_arrow, "drape_ptr<Texture> CreateArrowTexture", "CreateArrowTexture")
    # Pin the complete executable body, not a sentinel mention anywhere in the region.
    # This also rejects loaders/early returns outside the non-empty-path branch.
    contract = re.fullmatch(
        r"\s*if\s*\(\s*!texturePath\.empty\(\)\s*\)\s*\{"
        r"\s*return\s+make_unique_dp<StaticTexture>\(\s*context\s*,\s*texturePath\s*,"
        r"\s*useDefaultResourceFolder\s*\?\s*StaticTexture::kDefaultResource\s*:\s*std::string\(\)\s*,"
        r"\s*dp::TextureFormat::RGBA8\s*,\s*textureAllocator\s*,\s*true\s*\)\s*;\s*\}"
        r"\s*return\s+make_unique_dp<StaticTexture>\(\)\s*;\s*",
        body,
    )
    require(contract is not None,
            "CreateArrowTexture must load an optional custom texture exactly once inside the non-empty-path "
            "branch, then return only the unloaded sentinel on the default path")
    require(
        "bool m_isLoadingCorrect = false;" in executable_source(static_texture),
        "the unloaded StaticTexture sentinel must report loading failure",
    )
    require(
        "arrowTexture->IsLoadingCorrect() && !texCoords.empty()" in executable_source(arrow3d),
        "Arrow3d must gate texturing on successful texture loading",
    )
    require(
        "data.m_arrowMeshTexturingEnabled = false;" in executable_source(arrow3d),
        "Arrow3d must preserve the untextured mesh fallback",
    )


def main() -> int:
    verify_sources(TEXTURE_MANAGER, ARROW3D, STATIC_TEXTURE)
    print("Default arrow texture fallback verification PASSED")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
