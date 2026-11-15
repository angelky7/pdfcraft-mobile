<p align="center">
  <img src="docs/banner-en.svg" alt="PdfCraft for Android" width="720">
</p>

<p align="center">
  <a href="README.md">中文</a> · <strong>English</strong>
</p>

A PDF reader for Android phones and tablets, with a stylus that writes on the page. Nothing is
uploaded, no account is required, and files never leave the device.

---

## What this is

A PDF reader for Android, built on the Rust engine from
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

---

## What works

- Open a PDF from the system picker; the app **remembers the last eight**, and reopens each where
  you stopped reading
- Continuous scrolling or one page at a time; pages render as they are reached, so a 900-page
  manual opens immediately
- Pinch to zoom (1x–3x), double-tap to zoom, drag the bar at the bottom to move through the
  document, or type a page number
- **Writing and marking**: pen, highlighter, box, arrow, eraser — the line follows the pressure
- **A finger writes too**, for tablets that have no pen
- **Writing works while zoomed in**, because the tip and the page agree on where the ink goes
- **Annotations already in the file are drawn**: the highlights, underlines, strike-throughs,
  boxes and notes somebody added in Acrobat
- **Navigation panel**: thumbnails, the document's outline, its comments, full-text search
- Tablets: two-column layout and a page rail at 840 dp and wider
- Dark mode follows the system; Chinese first, English second

### Writing and marking

| Gesture | What happens |
|---|---|
| A stylus touches the page | Writes; the line thickens and thins with pressure |
| A finger drags (finger writing off) | Scrolls the page |
| A finger touches the page (finger writing on) | Writes |
| Two fingers | Zooms, including while writing |
| A palm rests on the glass | Ignored while a mark is being drawn, so it cannot scroll the page away |
| The stylus's side button, its tail, or the eraser tool | Erases what it passes over |
| Undo | Takes back the last mark, on any page |
| Save a copy | Writes a new PDF wherever you choose; the original is untouched |

Five tools: **pen**, **highlighter** (38% and three times as wide, so the words show through),
**box**, **arrow** (which grows as you drag it), and the **eraser**.

The stylus-versus-finger split is the app's decision, not the system's: Android reports which tool
a touch came from (`MotionEvent.getToolType`); what each tool does is up to the app. That is why
"a finger writes too" is a switch rather than a different device.

What gets written are real PDF annotations, not pixels baked into the page:

| Tool | What lands in the file |
|---|---|
| Pen | `/Subtype /Ink` |
| Highlighter | Highlight-intent ink (`/IT /FreehandHighlight`) |
| Box | `/Subtype /Square` |
| Arrow | `/Subtype /Line`, open arrowhead at the end |

Another reader shows the same marks and can still edit them.

Stated plainly:

- **No writing on rotated pages**: the crop box and the displayed coordinates differ by a
  rotation, and refusing beats drawing somewhere the tip is not.
- **One width per annotation in the file**: PDF ink carries a single `/BS /W`, so the pressure
  variation visible on screen is flattened to the stroke's nominal width on save.
- **The deepest zoom is slightly soft**: rendering has a resolution ceiling (a bitmap over about
  40 MB is more than a phone will give this app), past which the raster is scaled rather than
  re-rendered.

### The navigation panel

Opened from the menu button in the top bar. All four tabs answer the same question — where do I
want to be — and all four answer it by going to a page:

| Tab | What it shows |
|---|---|
| Pages | Thumbnails, rendered as they scroll into view, the current page marked |
| Contents | The document's own bookmarks, indented by depth; an entry that leaves the file is shown greyed and is not clickable |
| Comments | The annotations already in the file, **including the ink this app wrote**, each jumping to its page |
| Search | The text layer, one row per page with the context around the first match and how many that page holds |

Search runs off the main thread and waits for a pause in typing: re-interpreting the document on
every keystroke is unusable on a long manual.

## What does not work yet

- No text selection (search jumps to a page but does not highlight the match on it)
- The highlighter is freehand, **not "select a passage and highlight it"** — PDF's `/Highlight`
  with `/QuadPoints` is not wired up
- No note icons, text boxes, form fields, or signing
- **No Chinese OCR**: the engine's optical model covers unaccented Latin script only
- Save a copy only; the original is never overwritten

## Building

JDK 17+, Android SDK 35, NDK 27.2.12479018, Rust stable 1.95+ with the two Android targets
(`rust-toolchain.toml` declares them), `cargo-ndk`, Gradle 8.11.1.

```bash
cd android
gradle assembleRelease -Ppdfcraft.abis=arm64-v8a
# android/app/build/outputs/apk/release/app-release.apk
```

One build runs three steps in order:

1. `cargoBuild` — cross-compiles the engine with cargo-ndk into `libpdfcraft_mobile.so` (the slow
   one)
2. `generateBindings` — runs UniFFI against that freshly built library, so the Kotlin bindings
   cannot describe an API the library does not have
3. Kotlin compiles and the APK is assembled from both

`-Ppdfcraft.abis` takes a comma-separated list (`arm64-v8a,armeabi-v7a,x86_64`).

Signing: set `PDFCRAFT_KEYSTORE` (path), `PDFCRAFT_KEYSTORE_PASSWORD`, `PDFCRAFT_KEY_ALIAS` and
`PDFCRAFT_KEY_PASSWORD` to sign with a release key. Without them the build falls back to Android's
debug key — installable, but it cannot upgrade a release-signed install.

### Tests

```bash
cargo test -p pdfcraft-mobile --lib
```

These open a real PDF, write ink, save, reopen the file and assert an `Ink` annotation is there.
An APK cannot prove that, so the tests do.

## Releasing

`.github/workflows/release.yml`:

- A manual run produces an artifact and releases nothing
- A `v*` tag builds and creates a prerelease

The version lives in `android/gradle.properties` (`pdfcraft.version` / `pdfcraft.versionCode`) and a
tag that disagrees with it fails the workflow rather than shipping a mismatched build.

Repository secrets, for release signing:

```
PDFCRAFT_KEYSTORE_BASE64     # base64 of the keystore
PDFCRAFT_KEYSTORE_PASSWORD
PDFCRAFT_KEY_ALIAS
PDFCRAFT_KEY_PASSWORD
```

## Licence and credit

MIT OR Apache-2.0, as upstream.

- All credit for the engine and the document handling goes to
  [storytold/pdfcraft](https://github.com/storytold/pdfcraft) and its contributors.
- Full copyright and third-party notices are in `NOTICE`, `ATTRIBUTION.md` and `LICENSES.toml`.
- "PdfCraft" is the upstream project's name. This is an unofficial Android front end and is not
  affiliated with that project.
