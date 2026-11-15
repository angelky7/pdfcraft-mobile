<p align="center">
  <img src="docs/banner.svg" alt="PdfCraft for Android" width="720">
</p>

<p align="center">
  <strong>中文</strong> · <a href="README.en.md">English</a>
</p>

在安卓手机和平板上本地阅读 PDF，并用手写笔在页面上批注。文件不离开设备，不需要账号，不上传任何内容。

---

## 这个项目是什么

它把 [storytold/pdfcraft](https://github.com/storytold/pdfcraft) 的 **Rust 渲染引擎**装进一个安卓应用，界面用 Kotlin + Jetpack Compose 自己写。

目标很清楚：**手机能舒服地读，平板上能舒服地读**，而不是把桌面版界面塞进小屏。

### 与上游的关系（请先读这段）

| 部分 | 来源 | 许可 |
|---|---|---|
| `crates/`、`vendor/`、`xtask/` —— PDF 解析、渲染、编辑引擎 | 原样取自 `storytold/pdfcraft` v0.21.1 | MIT OR Apache-2.0 |
| `apps/pdfcraft-mobile/` —— Rust 与 Kotlin 之间的绑定层 | 本项目编写 | MIT OR Apache-2.0 |
| `android/` —— Gradle 工程、Compose 界面、中文文案、图标 | 本项目编写 | MIT OR Apache-2.0 |

引擎是按上游许可**合规复用**，不是复制粘贴：`LICENSE-MIT`、`LICENSE-APACHE`、`NOTICE`、`ATTRIBUTION.md` 全部保留，署名在下方。

**安卓层不取自任何第三方移植**，没有抄用其他人的 Kotlin 代码或界面文案。

### 为什么引擎随仓库一起放

上游只提供桌面端（egui）、命令行和 Web，没有安卓层；而引擎的公开 API 才是稳定的那一层。把引擎固定在仓库里，版本就是可复现的——不会出现"今天构建成功、明天上游改了签名就编不过"。

---

## 现在能做什么

- 通过系统文件选择器打开 PDF（也可以从其他应用的"打开方式"进入）
- 连续滚动阅读，按页懒加载、按需渲染，几百页的文档打开也是瞬间
- 双指缩放（1×–4×），放大后可平移
- **手写笔书写**：笔在页面上写字，手指照常滚动
- 平板/大屏：≥ 840 dp 时左侧出现页码侧栏，点击跳转，正文区自动加宽
- 深色模式跟随系统
- 界面中文优先（英文备用）

### 手写笔怎么用

| 动作 | 效果 |
|---|---|
| 触控笔落在页面上 | 书写，线条粗细随压感变化 |
| 手指滑动 | 滚动页面（不会被当成书写） |
| 手掌压在屏幕上 | 笔在书写时忽略，不会把页面滑走 |
| 笔的侧键 / 笔尾橡皮 | 擦除（划过即删整笔） |
| 工具栏「撤销」 | 撤销上一笔（可跨页） |
| 工具栏「保存副本」 | 通过系统选择器另存为新 PDF，原文件不动 |

「笔写字、手指滚动」不是系统功能，而是这个 app 自己判断的：Android 只告诉应用这一笔来自触控笔还是手指（`MotionEvent.getToolType`），用什么工具做什么由应用决定。

写入的是**真正的 PDF 墨迹批注**（`/Subtype /Ink`），不是把像素烤进页面。用别的阅读器打开保存后的文件，能看到同样的笔迹，也能继续编辑或删除。

三个明确的限制：

- **放大状态下不能写**：缩放是绘制时缩放，笔尖落点和页面坐标会对不上，所以书写只在 100% 时开放
- **旋转过的页面不能写**：页面裁剪框与显示坐标差一个旋转，映射需要额外推导，暂时直接拒绝而不是画错位置
- **PDF 里的墨迹是单一线宽**：PDF 的 ink 批注一条只能有一个线宽，屏幕上的压感变化在保存后会被压平为该笔的标称宽度

## 还不能做什么

这些是明确的待办，不是隐藏的坑：

- 没有文本选择、搜索、目录（书签）面板
- 没有高亮、便签、表单填写、签名（手写是唯一的批注）
- **没有中文 OCR**：引擎自带的光学识别模型只覆盖无重音拉丁字母
- 没有"最近打开"列表：每次启动从选择文件开始
- 只能另存副本，不能直接覆盖原文件

---

## 自己构建

需要 JDK 17+、Android SDK（compileSdk 35）、NDK 27.2.12479018、Rust stable（1.95+，`rust-toolchain.toml` 已声明两个安卓 target）、`cargo-ndk`、Gradle 8.11.1。

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

签名：设置环境变量 `PDFCRAFT_KEYSTORE`（keystore 路径）、`PDFCRAFT_KEYSTORE_PASSWORD`、`PDFCRAFT_KEY_ALIAS`、`PDFCRAFT_KEY_PASSWORD` 即可用正式签名；不设置则退回调试签名（能装，但无法覆盖升级已发布的正式包）。

### 测试

```bash
cargo test -p pdfcraft-mobile --lib
```

这组测试会真的打开一个 PDF、写入墨迹、保存、再打开，断言文件里出现一个 `Ink` 批注——APK 本身证明不了这件事，所以由它来证明。

## 发布

`.github/workflows/release.yml`：

- 手动运行 → 产出 artifact，不发版
- 推送 `v*` 标签 → 构建并创建预发布

版本号在 `android/gradle.properties` 的 `pdfcraft.version` / `pdfcraft.versionCode`，标签必须与它一致，否则工作流会直接报错退出。

需要的仓库 Secrets（可选的正式签名）：

```
PDFCRAFT_KEYSTORE_BASE64     # keystore 文件的 base64
PDFCRAFT_KEYSTORE_PASSWORD
PDFCRAFT_KEY_ALIAS
PDFCRAFT_KEY_PASSWORD
```

## 许可与署名

本项目以 MIT OR Apache-2.0 双许可发布，与上游一致。

- 引擎与文档处理的全部功劳属于 [storytold/pdfcraft](https://github.com/storytold/pdfcraft) 及其贡献者。
- 完整版权与第三方组件声明见 `NOTICE`、`ATTRIBUTION.md`、`LICENSES.toml`。
- "PdfCraft" 是上游项目的名字，本项目是它的一个非官方安卓前端，与原项目无隶属关系。
