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


def main() -> int:
    create_arrow = TEXTURE_MANAGER.split("drape_ptr<Texture> CreateArrowTexture", 1)[1].split(
        "class SimpleTexturePool", 1
    )[0]

    require(
        "make_unique_dp<StaticTexture>(context, texturePath" in create_arrow,
        "custom arrow texture paths must still use StaticTexture loading",
    )
    require(
        "return make_unique_dp<StaticTexture>();" in create_arrow,
        "the empty/default arrow-texture path must use an unloaded sentinel",
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
