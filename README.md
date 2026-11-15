# PdfCraft for Android

在安卓手机和平板上本地阅读 PDF。文件不离开设备，不需要账号，不上传任何内容。

A local PDF reader for Android phones and tablets. Nothing is uploaded, no account is required,
and files never leave the device.

---

## 这个项目是什么

它把 [storytold/pdfcraft](https://github.com/storytold/pdfcraft) 的 **Rust 渲染引擎**装进一个
安卓应用，界面用 Kotlin + Jetpack Compose 自己写。

目标的起点很清楚：**手机能舒服地读，平板上能舒服地读**，而不是把桌面版界面塞进小屏。

### 与上游的关系（请先读这段）

| 部分 | 来源 | 许可 |
|---|---|---|
| `crates/`、`vendor/`、`xtask/` —— PDF 解析、渲染、编辑引擎 | 原样取自 `storytold/pdfcraft` v0.21.1 | MIT OR Apache-2.0 |
| `apps/pdfcraft-mobile/` —— Rust 与 Kotlin 之间的绑定层 | 本项目编写 | MIT OR Apache-2.0 |
| `android/` —— Gradle 工程、Compose 界面、中文文案、图标 | 本项目编写 | MIT OR Apache-2.0 |

引擎是按上游许可**合规复用**，不是复制粘贴：`LICENSE-MIT`、`LICENSE-APACHE`、`NOTICE`、
`ATTRIBUTION.md` 全部保留，署名在下方。

**安卓层不取自任何第三方移植**，没有抄用其他人的 Kotlin 代码或界面文案。

### 为什么引擎随仓库一起放

上游只提供桌面端（egui）、命令行和 Web，没有安卓层；而引擎的公开 API 才是稳定的那一层。
把引擎固定在仓库里，版本就是可复现的——不会出现"今天构建成功、明天上游改了签名就编不过"。

---

## 现在能做什么

- 通过系统文件选择器打开 PDF（也可以从其他应用的"打开方式"进入）
- 连续滚动阅读，按页懒加载、按需渲染，几百页的文档打开也是瞬间
- 双指缩放（1×–4×），放大后可平移
- 平板/大屏：≥ 840 dp 时左侧出现页码侧栏，点击跳转，正文区自动加宽
- 深色模式跟随系统
- 界面中文优先（英文备用）

## 还不能做什么

这些是明确的待办，不是隐藏的坑：

- 没有文本选择、搜索、目录（书签）面板
- 没有批注、表单填写、签名
- **没有中文 OCR**：引擎自带的光学识别模型只覆盖无重音拉丁字母
- 没有"最近打开"列表：每次启动从选择文件开始
- 只有阅读，还不会修改后保存

---

## 自己构建

需要 JDK 17+、Android SDK（compileSdk 35）、NDK 27.2.12479018、Rust stable（1.95+，
`rust-toolchain.toml` 已声明两个安卓 target）、`cargo-ndk`、Gradle 8.11.1。

```bash
cd android
gradle assembleRelease -Ppdfcraft.abis=arm64-v8a
# 产物：android/app/build/outputs/apk/release/app-release.apk
```

一次构建会依次做三件事，顺序由 Gradle 保证：

1. `cargoBuild` —— 用 cargo-ndk 把引擎交叉编译成 `libpdfcraft_mobile.so`（最慢的一步）
2. `generateBindings` —— 用 UniFFI 从**刚编译出来的库**生成 Kotlin 绑定，保证绑定与库同源
3. 编译 Kotlin、打包 APK

`-Ppdfcraft.abis` 接受逗号分隔的多个 ABI（`arm64-v8a,armeabi-v7a,x86_64`）。

签名：设置环境变量 `PDFCRAFT_KEYSTORE`（keystore 路径）、`PDFCRAFT_KEYSTORE_PASSWORD`、
`PDFCRAFT_KEY_ALIAS`、`PDFCRAFT_KEY_PASSWORD` 即可用正式签名；不设置则退回调试签名（能装，
但无法覆盖升级已发布的正式包）。

没有 Gradle wrapper？在该目录跑一次 `gradle wrapper` 生成，或用你系统里的 Gradle。

## 发布

`.github/workflows/release.yml`：

- 手动运行 → 产出 artifact，不发版
- 推送 `v*` 标签 → 构建并创建预发布

版本号在 `android/gradle.properties` 的 `pdfcraft.version` / `pdfcraft.versionCode`，
标签必须与它一致，否则工作流会直接报错退出（避免打出来的包和标签对不上）。

需要的仓库 Secrets（可选的正式签名）：

```
PDFCRAFT_KEYSTORE_BASE64     # keystore 文件的 base64
PDFCRAFT_KEYSTORE_PASSWORD
PDFCRAFT_KEY_ALIAS
PDFCRAFT_KEY_PASSWORD
```

## 许可与署名

本项目以 MIT OR Apache-2.0 双许可发布，与上游一致。

- 引擎与文档处理的全部功劳属于 [storytold/pdfcraft](https://github.com/storytold/pdfcraft)
  及其贡献者。
- 完整版权与第三方组件声明见 `NOTICE`、`ATTRIBUTION.md`、`LICENSES.toml`。
- "PdfCraft" 是上游项目的名字，本项目是它的一个非官方安卓前端，与原项目无隶属关系。

---

# English

## What this is

A PDF reader for Android phones and tablets, built on the Rust engine from
[storytold/pdfcraft](https://github.com/storytold/pdfcraft) with a Kotlin + Jetpack Compose UI
written for touch screens rather than ported from a desktop window.

### What comes from where

| Part | Origin | Licence |
|---|---|---|
| `crates/`, `vendor/`, `xtask/` — parsing, rendering, editing | `storytold/pdfcraft` v0.21.1, unmodified | MIT OR Apache-2.0 |
| `apps/pdfcraft-mobile/` — the Rust↔Kotlin boundary | this project | MIT OR Apache-2.0 |
| `android/` — Gradle, Compose UI, Chinese strings, icon | this project | MIT OR Apache-2.0 |

The engine is reused under its licence, with `LICENSE-MIT`, `LICENSE-APACHE`, `NOTICE` and
`ATTRIBUTION.md` kept intact. The Android layer is **not** taken from any third-party port: no
Kotlin code or UI copy was copied from another project.

### Why the engine is vendored here

Upstream ships a desktop app (egui), a CLI and the web build — no Android layer. Its stable
surface is the engine API. Pinning the engine in this repository makes a build reproducible
instead of depending on what upstream's `main` looks like today.

## What works

- Open a PDF through the system picker, or through another app's "open with"
- Continuous scrolling with per-page lazy rendering — a 900-page manual opens immediately
- Pinch to zoom (1×–4×), pan while zoomed
- Tablets: a page rail appears beside the pages at ≥ 840 dp
- Dark mode follows the system
- Chinese first, English as the fallback

## What does not

Text selection, search, bookmarks, annotations, forms, signing, and Chinese OCR (the bundled
recognition model covers unaccented Latin script only). There is no "recently opened" list yet,
and the app only reads: it does not save changes.

## Building

JDK 17+, Android SDK 35, NDK 27.2.12479018, Rust stable 1.95+ with the two Android targets,
`cargo-ndk`, Gradle 8.11.1.

```bash
cd android
gradle assembleRelease -Ppdfcraft.abis=arm64-v8a
```

One build runs three steps in order: `cargoBuild` cross-compiles the engine with cargo-ndk,
`generateBindings` runs UniFFI against that freshly built library so the Kotlin bindings cannot
describe an API the library does not have, and the APK is assembled from both.

## Licence

MIT OR Apache-2.0, as upstream. All credit for the engine goes to
[storytold/pdfcraft](https://github.com/storytold/pdfcraft) and its contributors. This is an
unofficial Android front end and is not affiliated with that project.
