#!/usr/bin/env python3
"""Pin the default 3D-arrow texture fallback without inventing a packaged asset."""

from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
TEXTURE_MANAGER = (ROOT / "libs/drape/texture_manager.cpp").read_text(encoding="utf-8")
ARROW3D = (ROOT / "libs/drape_frontend/arrow3d.cpp").read_text(encoding="utf-8")
STATIC_TEXTURE = (ROOT / "libs/drape/static_texture.hpp").read_text(encoding="utf-8")


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


def main() -> int:
    create_arrow = extract_between(
        TEXTURE_MANAGER,
        "drape_ptr<Texture> CreateArrowTexture",
        "class SimpleTexturePool",
        "CreateArrowTexture / SimpleTexturePool",
    )
    custom_path = extract_braced_block(
        create_arrow,
        "if (!texturePath.empty())",
        "non-empty custom arrow texture path",
    )
    loader = "make_unique_dp<StaticTexture>(context, texturePath"

    require(
        custom_path.count(loader) == 1 and create_arrow.count(loader) == 1,
        "custom arrow texture loading must occur exactly once and remain inside the non-empty texturePath branch",
    )
    require(
        "return make_unique_dp<StaticTexture>();" in create_arrow,
        "the empty/default arrow-texture path must use an unloaded sentinel",
    )
    require(
        "return make_unique_dp<StaticTexture>();" not in custom_path,
        "the unloaded sentinel must remain the default path, outside the custom-path branch",
    )
    require(
        'make_unique_dp<StaticTexture>(context, "arrow-texture.png"' not in create_arrow,
        "the default path must not probe the APK for known-absent arrow-texture.png",
    )
    require(
        "bool m_isLoadingCorrect = false;" in STATIC_TEXTURE,
        "the unloaded StaticTexture sentinel must report loading failure",
    )
    require(
        "arrowTexture->IsLoadingCorrect() && !texCoords.empty()" in ARROW3D,
        "Arrow3d must gate texturing on successful texture loading",
    )
    require(
        "data.m_arrowMeshTexturingEnabled = false;" in ARROW3D,
        "Arrow3d must preserve the untextured mesh fallback",
    )

    print("Default arrow texture fallback verification PASSED")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
