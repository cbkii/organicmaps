<div align="center">

# 🚗 Organic Maps In-Car

### Offline navigation, adapted for Android car head units

**A vehicle-focused Android edition built on the excellent [Organic Maps Project](https://organicmaps.app/).**

[![Latest Release](https://img.shields.io/github/v/release/cbkii/organicmaps?include_prereleases&label=In-Car%20Release)](https://github.com/cbkii/organicmaps/releases)
[![Android](https://img.shields.io/badge/Android-10%2B-3DDC84?logo=android&logoColor=white)](#-device-support)
[![Architecture](https://img.shields.io/badge/ABI-arm64--v8a-blue)](#-download)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue)](LICENSE)
[![Upstream](https://img.shields.io/badge/Upstream-Organic%20Maps-orange)](https://github.com/organicmaps/organicmaps)

**[⬇️ Download](https://github.com/cbkii/organicmaps/releases)** ·
**[🗺️ Organic Maps](https://organicmaps.app/)** ·
**[📖 Build](docs/BUILDING.md)** ·
**[🔧 Contribute](docs/CONTRIBUTING.md)**

</div>

---

> [!IMPORTANT]
> ### 💚 Built on Organic Maps
>
> **Organic Maps In-Car is an independent derivative of the open-source [Organic Maps Project](https://organicmaps.app/).**
>
> The underlying navigation platform and a substantial part of this application's code are the work of the **Organic Maps maintainers, contributors and wider open-source community**.
>
> This fork is **not an official Organic Maps release and is not affiliated with or endorsed by the Organic Maps Project**.

---

## 🚘 What is Organic Maps In-Car?

Organic Maps In-Car adapts Organic Maps for **fixed-display Android vehicle head units**.

It keeps the privacy-friendly, offline-first navigation foundation of Organic Maps while focusing on:

- 👆 larger, clearer touchscreen controls;
- 👀 better glanceability and readability;
- 🌗 improved day and night visibility;
- 🪟 better full-screen, split/compact-window layouts;
- 🔊 offline navigation audio;
- 🧭 simpler navigation interactions;
- ⚡ reliable startup and embedded-device behaviour.

The dedicated **`inCar`** variant is the primary product published by this fork.

---

## ✨ Highlights

| | Feature |
|---|---|
| 🚗 | Simpler route controls and interactions |
| 🗺️ | Fully offline maps, search and navigation |
| 🚦 | Turn-by-turn driving navigation |
| ⭐ | Quick buttons for commonly used destinations |
| 👆 | Optimised controls for vehicle touchscreens |
| 🌙 | Improved day/night readability (map theme) |
| 🧭 | Automatic Follow-and-Rotate behaviour |
| 🔊 | Offline audio (TTS fallback) in-built |
| 🪟 | Improved full-screen, split/windowed layouts |
| 🚧 | Traffic support |
| 🔖 | Bookmarks and map downloads |
| 🥾 | Strong gating for track-recording auto-resume |

The In-Car build also removes or reduces application surfaces that are unnecessary for a dedicated vehicle installation.

---

## ⬇️ Download

<div align="center">

### **[📦 Download Organic Maps In-Car releases](https://github.com/cbkii/organicmaps/releases)**

</div>

**Release package**: `app.organicmaps.incar`
**Current release architecture**: `arm64-v8a`

> [!NOTE]
> In-Car builds will be published as **pre-releases** while development and physical head-unit validation continue.

---

## 📱🚘 Device support

>[!WARNING]
> Organic Maps In-Car is designed for **direct-display Android head units and other fixed vehicle displays**.
>
>It is **not an Android Auto projection application**.

Development specifically considers **Android 10 / API 29** embedded hardware, testing on the **Topway TS10/TS18** devices, while keeping the implementation based on standard Android and Organic Maps components wherever practical.

See **[Head-Unit Validation](docs/TS18_VALIDATION.md)** for the physical validation model.

---

## 💚 Organic Maps upstream

This project would not exist without **Organic Maps**.

The upstream project provides the core mapping and navigation foundation, including major parts of the:

- 🗺️ map engine;
- 🚗 routing and navigation system;
- 🔎 offline search;
- 🎨 map rendering;
- 📦 offline map infrastructure;
- 📱 Android application;
- 🧩 shared native runtime.

Organic Maps is a **privacy-focused, free and open-source offline maps and GPS application**, powered by **OpenStreetMap** data.

### 🫶 Please support the upstream project

🌐 **Website:** [organicmaps.app](https://organicmaps.app/)  
💻 **Source:** [github.com/organicmaps/organicmaps](https://github.com/organicmaps/organicmaps)  
❤️ **Donate:** [organicmaps.app/donate](https://organicmaps.app/donate/)  

> **This fork adds and maintains vehicle-focused changes. It does not claim ownership of upstream Organic Maps work.**

Where practical, generally useful improvements should remain compatible with upstream architecture and suitable for contribution or adaptation upstream.

### 🔄 Upstream updates

Active upstream development continues at:

### **[🌿 organicmaps/organicmaps](https://github.com/organicmaps/organicmaps)**

Upstream changes are imported **selectively** so this fork can benefit from Organic Maps fixes and improvements without restoring platforms or repository content deliberately removed from the Android-focused tree.

Imported upstream changes should retain clear and traceable provenance.

See:

- [UPSTREAM.md](docs/UPSTREAM.md)
- [PROVENANCE.md](docs/PROVENANCE.md)

---

## 🙏 Attribution

The **Organic Maps Project** requests derivative applications using its source code, user interface or binary data to include a visible, human-readable acknowledgement of the project and a link to **[organicmaps.app](https://organicmaps.app/)**.

Accordingly:

> **Organic Maps In-Car is based on the [Organic Maps Project](https://organicmaps.app/) and the work of its maintainers and open-source contributors.**

This attribution should also remain visible in appropriate **user-facing application locations**, such as the **About** or **Main Menu** screens.

This README does not replace any attribution required within the application itself.

Organic Maps, OpenStreetMap and retained third-party components remain subject to their respective licences, copyright notices and attribution requirements.

---

## 🏗️ Repository scope

This repository is **Android-focused, but not In-Car-only.**

The `inCar` variant is the primary release product.

Other Android flavours remain where useful for **compatibility, compilation and regression testing**:

| Variant | Purpose |
|---|---|
| `inCar` | 🚗 Primary direct-display vehicle product |
| `web` | Android compatibility/regression surface |
| `fdroid` | Android compatibility/regression surface |
| `google` | Android compatibility/regression surface |
| `huawei` | Android compatibility/regression surface |

> [!NOTE]
> The presence of a build flavour does **not** mean this fork publishes through the corresponding application store.

Shared C++ code, JNI/NDK components and required Android runtime data intentionally remain outside `android/` where required by the application.

iOS, Qt/desktop packaging and large map-generation or unrelated test inputs are outside the maintained scope of this Android-focused fork.

### 📚 Repository documentation

- 🏛️ [Architecture](docs/ARCHITECTURE.md)
- 📦 [Repository Scope](docs/REPOSITORY_SCOPE.md)
- 🔨 [Building](docs/BUILDING.md)
- 🚀 [Releasing](docs/RELEASING.md)
- 🚗 [TS18 Validation](docs/TS18_VALIDATION.md)
- 🔄 [Upstream Integration](docs/UPSTREAM.md)
- 🧬 [Provenance](docs/PROVENANCE.md)

---

## 🔨 Building

From a fresh clone:

```bash
git submodule sync --recursive
git submodule update --init --recursive --depth 1

./configure.sh

cd android
./gradlew -Parm64 assembleWebDebug
./gradlew -Parm64 assembleInCarProfileable
```

See **[docs/BUILDING.md](docs/BUILDING.md)** for the current supported build and validation commands.

### 🔍 Repository scope validation

```bash
python3 tools/ci/verify_android_repo_scope.py
```

This validates the repository's Android-focused path boundary and related repository-scope requirements.

---

## 🚀 Releases

The supported publication surface of this fork is the **Organic Maps In-Car APK**, via:

```text
.github/workflows/manual-in-car-release.yml
```

See **[docs/RELEASING.md](docs/RELEASING.md)** for technical release details.

---

## 🛠️ Contributing

Contributions are welcome where they align with the project's goals.

Changes should aim to:

- 🚗 keep the In-Car product simple and reliable;
- 🪅 minimise driver distraction;
- 👁️‍🗨️ optimise readibility and simplify interaction;
- 📱 retain Android 10/API 29 compatibility;
- 🧩 avoid unnecessary device-specific coupling;
- 💚 preserve Organic Maps attribution and licensing;
- 🌿 be compatible with upstream where practical;
- 🧪 distinguish CI/emulator results from physical-device validation.

See **[docs/CONTRIBUTING.md](docs/CONTRIBUTING.md)** for development guidance.

---

## 📜 Licence

Retained Organic Maps source code is licensed under the **Apache License, Version 2.0**, together with applicable notices and third-party licence requirements. See:

- [LICENSE](LICENSE)
- [NOTICE](NOTICE)
- [COPYING](COPYING)
- [.reuse/dep5](.reuse/dep5)
- [data/copyright.html](data/copyright.html)

Binary data files, including map data, are subject to separate terms:

- [DATA_LICENSE.txt](DATA_LICENSE.txt)

---

## 🧡 Credits & acknowledgements

<div align="center">

### Built with enormous thanks to the  
## **[Organic Maps Project](https://organicmaps.app/)**

and its maintainers, contributors and open-source community.

Organic Maps itself builds upon **[OpenStreetMap](https://www.openstreetmap.org/)** and the work of OpenStreetMap contributors, alongside numerous open-source projects and datasets documented in the repository's licence and attribution files.

**Thank you to everyone contributing to free, open and privacy-respecting mapping.**

---

### 🌍 [Official Organic Maps Website](https://organicmaps.app/)

### 💻 [Official Organic Maps Source Repository](https://github.com/organicmaps/organicmaps)

### ❤️ [Support Organic Maps](https://organicmaps.app/donate/)

</div>

---

> [!IMPORTANT]
> **Organic Maps In-Car is an independent derivative project.**
>
> For the official Organic Maps application, downloads, documentation and community, visit **[organicmaps.app](https://organicmaps.app/)**.
