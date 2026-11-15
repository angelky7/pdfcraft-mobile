//! The engine as the Android app sees it: open a document, ask about its pages, get rasters,
//! and write pen strokes into it as ink annotations.
//!
//! Everything expensive — parsing, interpreting a page, writing an edit — happens in Rust. The
//! Kotlin side owns no PDF knowledge: it asks for a page at a scale, receives premultiplied
//! BGRA8, and draws it; it hands back strokes in page-relative coordinates and says where to save.
//! UniFFI generates the Kotlin bindings from this crate (see `bindgen/`), so the surface here is
//! deliberately small and flat: one object per open document, plus records for what crosses the
//! boundary.
//!
//! `PageRenderer` is not `Sync` (it holds interpreter state and mutates per request), so a
//! document owns it behind a `Mutex`. Renders are therefore serialized per document. That is the
//! right trade for a phone: one page at a time is what a reader shows, and a second worker would
//! need a second parser and a second copy of the file's buffers.
//!
//! Edits go through the engine's session, which keeps the undo stack and produces the saved bytes.
//! Rendering deliberately does *not* go through it: the page on screen is drawn from the file as
//! it was opened, and strokes are shown by the app's own overlay until the document is reopened.
//! Redrawing a page on every finished stroke would cost a full re-parse and re-interpret for a
//! change the overlay is already showing.

use std::sync::{Arc, Mutex};

use pdfcraft_annot::{LineEnding, NewAnnotation, Shape, Style};
use pdfcraft_engine::{Edit, Session};
use pdfcraft_render::{OpenError, PageRenderer, RenderConfig, RenderRequest, RequestKind, inspect};

uniffi::setup_scaffolding!();

/// What can go wrong, in terms the UI can show.
#[derive(Debug, thiserror::Error, uniffi::Error)]
pub enum MobileError {
    /// The file could not be read at all.
    #[error("{path} could not be read: {detail}")]
    Io { path: String, detail: String },

    /// The document is encrypted and no password was given.
    #[error("the document needs a password")]
    NeedsPassword,

    /// A password was given and does not open the document.
    #[error("the password is incorrect")]
    WrongPassword,

    /// The bytes are not a PDF this engine can read.
    #[error("this file is not a readable PDF: {detail}")]
    Invalid { detail: String },

    /// The renderer gave up on this page.
    #[error("page {page} could not be drawn: {detail}")]
    Render { page: i32, detail: String },

    /// A page number past the last page.
    #[error("there is no page {page}: the document has {count}")]
    NoSuchPage { page: i32, count: i32 },

    /// The engine refused the change: a permission bit, a damaged page, a full disk.
    #[error("the change could not be written: {detail}")]
    Edit { detail: String },
}

/// What a mark is: which annotation the engine is asked to write.
#[derive(Clone, Copy, Debug, PartialEq, Eq, uniffi::Enum)]
pub enum MarkKind {
    /// Freehand strokes in the pen's colour, opaque.
    Ink,
    /// Freehand strokes that let the page show through, written as a highlight-intent ink so a
    /// reader that only knows `/Ink` still shows it.
    Highlighter,
    /// A rectangle drawn between the first two points.
    Rectangle,
    /// A line from the first point to the second, arrowheaded at the second.
    Arrow,
}

/// One thing drawn on a page.
///
/// Points are normalised to the page as the reader displays it: `0..1` from the left edge, and
/// `0..1` from the *top* edge downward, which is how a touch screen reports them. Converting to
/// PDF user space (origin bottom-left, y up, offset by the page's crop box) happens here, so the
/// Kotlin side never has to know that PDF's y axis runs the other way.
///
/// Widths stay in points for the same reason the coordinates are normalised: they describe the
/// mark, not the screen showing it.
#[derive(Clone, Debug, uniffi::Record)]
pub struct Mark {
    pub kind: MarkKind,
    /// Flattened x,y pairs. Strokes use as many as were sampled; a rectangle and an arrow use the
    /// first two, as two opposite corners / the two ends. A trailing value with no partner and
    /// anything that is not a finite number is ignored.
    pub points: Vec<f32>,
    /// Colour as `0xAARRGGBB`. Alpha becomes the annotation's opacity: PDF ink has no alpha.
    pub color: i32,
    /// Nominal width in PDF points. PDF ink carries one width per annotation, so a stroke written
    /// at varying pressure is stored at its nominal width; the overlay on screen keeps the
    /// variation, which is the part the eye actually reads.
    pub width: f32,
}

/// One line of the document's outline (its bookmarks, in reading order).
#[derive(Clone, Debug, uniffi::Record)]
pub struct OutlineRow {
    pub title: String,
    /// 0-based page the bookmark goes to, or -1 when it goes nowhere in this document.
    pub page: i32,
    /// Nesting depth: 0 at the top level.
    pub depth: i32,
}

/// One annotation already in the document.
#[derive(Clone, Debug, uniffi::Record)]
pub struct AnnotationRow {
    pub page: i32,
    /// PDF subtype, such as `Ink`, `Highlight` or `Text`.
    pub subtype: String,
    /// The comment's text, empty when it has none.
    pub contents: String,
    /// The author the file records, empty when it records none.
    pub author: String,
    /// Colour as `0xAARRGGBB`, fully opaque when the file gives none.
    pub color: i32,
    /// The annotation's rectangle, normalised like a mark's points: x0, y0, x1, y1.
    pub rect: Vec<f32>,
    /// Text-markup quads, normalised, eight numbers each (four corners).
    pub quads: Vec<f32>,
    /// A line's, polygon's or callout's vertices, normalised x,y pairs; empty for other subtypes.
    pub points: Vec<f32>,
}

/// A page that contains the search term, with the text around the first match on it.
#[derive(Clone, Debug, uniffi::Record)]
pub struct SearchHit {
    pub page: i32,
    /// The sentence-ish context around the first match, for the result list.
    pub snippet: String,
    /// How many times the term appears on this page.
    pub count: i32,
}

/// Everything drawn on one page.
#[derive(Clone, Debug, uniffi::Record)]
pub struct PageMark {
    /// 0-based page index.
    pub page: i32,
    pub marks: Vec<Mark>,
}

/// A page's displayed geometry, which is what the reader needs to lay pages out.
#[derive(Clone, Debug, uniffi::Record)]
pub struct PageSize {
    /// Displayed width in PDF points, after the page's rotation and user unit.
    pub width: f32,
    /// Displayed height in PDF points.
    pub height: f32,
    /// The page's own label (a Roman numeral, say), or its number when it has none.
    pub label: String,
    /// Clockwise rotation in degrees the reader should assume is already applied: the width and
    /// height above are post-rotation, so the UI draws the raster as-is and never rotates it.
    pub rotation: i32,
}

/// A rasterized page.
#[derive(Debug, uniffi::Record)]
pub struct RenderedImage {
    pub width: i32,
    pub height: i32,
    /// Premultiplied BGRA8, row-major: the byte order Android's `ARGB_8888` `Bitmap` holds in
    /// memory, so `copyPixelsFromBuffer` takes it as-is. The engine produces RGBA; the swap
    /// happens here once rather than in a per-pixel loop on the UI thread.
    pub pixels: Vec<u8>,
    /// How long the render took, for the UI to decide whether a page needs a cheaper preview.
    pub millis: i32,
}

/// An open document.
///
/// Holding this keeps the file's bytes and the parser alive; the app should drop it when the
/// document is closed so a large PDF's memory is returned.
#[derive(uniffi::Object)]
pub struct Document {
    /// The file as it was opened. A save starts from these bytes and replays the ink, so what is
    /// written is always exactly the strokes the reader currently sees: an undo or an eraser is
    /// simply a stroke that is no longer in the list, instead of a counter-edit to walk back.
    original: Arc<Vec<u8>>,
    password: Option<String>,
    renderer: Mutex<PageRenderer>,
    pages: Vec<PageSize>,
    /// Each page's effective crop box in points, the rectangle that normalised stroke points map
    /// onto. Kept beside `pages` because the reader never needs it.
    crops: Vec<[f32; 4]>,
    /// The outline, flattened once at open: a tree is what the file holds, a list is what a
    /// navigation panel draws.
    outline: Vec<OutlineRow>,
    /// Annotations the document arrived with, for the comments panel.
    annotations: Vec<AnnotationRow>,
    title: Option<String>,
    /// Whether a password opened it, so the UI can say so.
    encrypted: bool,
}

impl Document {
    /// Open from bytes already in memory, with an optional password.
    fn build(bytes: Vec<u8>, password: Option<String>) -> Result<Arc<Self>, MobileError> {
        let bytes = Arc::new(bytes);
        // `inspect` is the cheap pass: it reads the catalogue and the page tree, not the content
        // streams, so it costs the same on a 3,000-page manual as on a receipt.
        let info = inspect(bytes.clone(), password.as_deref()).map_err(open_error)?;

        let pages = info
            .pages
            .iter()
            .map(|p| PageSize { width: p.width, height: p.height, label: p.label.clone(), rotation: i32::from(p.rotation) })
            .collect();
        let crops = info.pages.iter().map(|p| p.crop).collect();

        let mut outline = Vec::new();
        flatten_outline(&info.outline, 0, &mut outline);
        let annotations = info
            .annotations
            .iter()
            .map(|a| {
                // A rectangle and a quad are both in user space; the panel draws in the space the
                // page is displayed in, which is why everything crossing the boundary is
                // normalised here rather than in Kotlin.
                let crop = info.pages.get(a.page).map(|p| p.crop).unwrap_or([0.0, 0.0, 612.0, 792.0]);
                AnnotationRow {
                    page: a.page as i32,
                    subtype: a.subtype.clone(),
                    contents: a.contents.clone().unwrap_or_default(),
                    author: a.author.clone().unwrap_or_default(),
                    color: argb_of(a.color),
                    rect: vec![
                        norm_x(a.rect[0], crop),
                        norm_y(a.rect[3], crop),
                        norm_x(a.rect[2], crop),
                        norm_y(a.rect[1], crop),
                    ],
                    quads: a
                        .quads
                        .iter()
                        .flat_map(|q| {
                            q.chunks_exact(2)
                                .flat_map(|p| [norm_x(p[0], crop), norm_y(p[1], crop)])
                                .collect::<Vec<f32>>()
                        })
                        .collect(),
                    points: a
                        .points
                        .iter()
                        .flat_map(|p| [norm_x(p[0], crop), norm_y(p[1], crop)])
                        .collect(),
                }
            })
            .collect();

        let config = RenderConfig {
            password: password.as_deref().map(Arc::from),
            // Installed CJK faces may draw fonts the PDF fails to embed. Screen only: nothing
            // this app renders is saved or printed, so the exception cannot escape into a file.
            ..RenderConfig::default().on_screen()
        };

        // The session is built at save time, not here: an open document has nothing to keep in
        // one until there are strokes to write, and a second parser over a large file is memory
        // a phone would rather spend on the page.

        Ok(Arc::new(Self {
            original: bytes.clone(),
            password: password.clone(),
            renderer: Mutex::new(PageRenderer::new(bytes, config)),
            pages,
            crops,
            outline,
            annotations,
            title: info.title.clone(),
            encrypted: info.encrypted,
        }))
    }

    /// The renderer's lock, taken without failing on a poisoned mutex: a panic in one render must
    /// not make the whole document unreadable, and the renderer's own state is a parser that the
    /// next request rebuilds anyway.
    fn renderer(&self) -> std::sync::MutexGuard<'_, PageRenderer> {
        self.renderer.lock().unwrap_or_else(|poisoned| poisoned.into_inner())
    }
}

#[uniffi::export]
impl Document {
    /// Open a document from a path in the app's own storage.
    ///
    /// The app copies a picked file into its sandbox first: a `content://` URI is not a path, and
    /// Android may revoke read access to one once the activity that picked it finishes.
    #[uniffi::constructor]
    pub fn open(path: String) -> Result<Arc<Self>, MobileError> {
        Self::open_with_password(path, String::new())
    }

    /// Open a password-protected document; an empty password means "none given".
    #[uniffi::constructor]
    pub fn open_with_password(path: String, password: String) -> Result<Arc<Self>, MobileError> {
        let bytes = std::fs::read(&path).map_err(|e| MobileError::Io { path, detail: e.to_string() })?;
        let password = (!password.is_empty()).then_some(password);
        Self::build(bytes, password)
    }

    /// Open bytes the app already read (a stream from a content provider, a download, a test).
    #[uniffi::constructor]
    pub fn from_bytes(bytes: Vec<u8>) -> Result<Arc<Self>, MobileError> {
        Self::build(bytes, None)
    }

    /// How many pages the document has.
    pub fn page_count(&self) -> i32 {
        self.pages.len() as i32
    }

    /// Every page's geometry, for laying the document out before anything is drawn.
    pub fn pages(&self) -> Vec<PageSize> {
        self.pages.clone()
    }

    /// One page's geometry; `None` past the end.
    pub fn page(&self, index: i32) -> Option<PageSize> {
        self.pages.get(index as usize).cloned()
    }

    /// The document's title, when it declares one worth showing.
    pub fn title(&self) -> Option<String> {
        self.title.clone()
    }

    /// Whether a password was needed to open the document.
    pub fn is_encrypted(&self) -> bool {
        self.encrypted
    }

    /// The document's bookmarks in reading order, flattened, each with its nesting depth.
    pub fn outline(&self) -> Vec<OutlineRow> {
        self.outline.clone()
    }

    /// The annotations the document arrived with.
    pub fn annotations(&self) -> Vec<AnnotationRow> {
        self.annotations.clone()
    }

    /// Find a term in the document's text layer.
    ///
    /// Each page is asked for its text glyphs and the string is searched as-is, so a term split
    /// across a line break or a ligature can be missed — the engine's text layer is what it is.
    /// Returns one hit per page, with the context around the first match, and stops after
    /// `max_pages` hits so a common word in a long manual cannot freeze the UI.
    ///
    /// Call from a background dispatcher: this interprets every page it searches.
    pub fn search(&self, query: String, max_pages: i32) -> Vec<SearchHit> {
        let needle = query.trim().to_lowercase();
        if needle.is_empty() {
            return Vec::new();
        }
        let limit = max_pages.max(1) as usize;

        let mut hits = Vec::new();
        let mut renderer = self.renderer();
        for page in 0..self.pages.len() {
            let request = RenderRequest { page, kind: RequestKind::Text, ..RenderRequest::default() };
            let rendered = renderer.render(request);
            let Some(text) = rendered.text else { continue };

            let haystack = page_text(&text);
            let haystack_lower = haystack.to_lowercase();
            let count = haystack_lower.matches(&needle).count();
            if count == 0 {
                continue;
            }
            if let Some(at) = haystack_lower.find(&needle) {
                hits.push(SearchHit {
                    page: page as i32,
                    snippet: context(&haystack, at, needle.len()),
                    count: count as i32,
                });
            }
            if hits.len() >= limit {
                break;
            }
        }
        hits
    }

    /// Rasterize a page.
    ///
    /// `scale` is device pixels per PDF point: the UI passes its density times its zoom, so a
    /// page at 2× density and 1.5× zoom asks for `3.0`. Requests are clamped by the renderer's
    /// own pixel budget, so an absurd scale comes back as a smaller raster rather than an
    /// allocation the device cannot serve.
    pub fn render_page(&self, index: i32, scale: f32) -> Result<RenderedImage, MobileError> {
        let count = self.pages.len() as i32;
        // `index < 0` as well as past the end: `as usize` on a negative would wrap to a huge
        // number and the renderer would be asked for a page that cannot exist.
        if index < 0 || index >= count {
            return Err(MobileError::NoSuchPage { page: index, count });
        }
        // A caller with a zero, negative or NaN scale would otherwise get a blank page and no
        // explanation; 1:1 is the only sensible reading of "no scale".
        let scale = if scale.is_finite() && scale > 0.0 { scale } else { 1.0 };

        let request = RenderRequest { page: index as usize, scale, ..RenderRequest::default() };
        let rendered = self.renderer().render(request);

        if let Some(detail) = rendered.error {
            return Err(MobileError::Render { page: index, detail });
        }

        // RGBA from the engine, BGRA in memory for the Bitmap: swap the outer bytes of every
        // pixel. `chunks_exact_mut` leaves a trailing partial pixel alone, which cannot occur for
        // a raster the renderer sized itself.
        let mut pixels = rendered.rgba.to_vec();
        for pixel in pixels.chunks_exact_mut(4) {
            pixel.swap(0, 2);
        }
        Ok(RenderedImage {
            width: rendered.width as i32,
            height: rendered.height as i32,
            pixels,
            millis: rendered.millis as i32,
        })
    }

    /// The raster scale that fits a page inside a viewport, without the page ever exceeding it.
    ///
    /// The UI uses this for the initial zoom: it needs the page's geometry and its own size, and
    /// doing the arithmetic here keeps one definition of "fit" for phones, tablets and split
    /// screen.
    pub fn fit_scale(&self, index: i32, viewport_width: f32, viewport_height: f32, density: f32) -> f32 {
        let Some(page) = self.pages.get(index as usize) else { return density.max(1.0) };
        if page.width <= 0.0 || page.height <= 0.0 || viewport_width <= 0.0 || viewport_height <= 0.0 {
            return density.max(1.0);
        }
        let sx = viewport_width / page.width;
        let sy = viewport_height / page.height;
        // The viewport is in device pixels; the scale the renderer wants is device pixels per
        // point, which is the same number.
        sx.min(sy).max(0.01)
    }

    /// Write the document, with the given marks, to `path`. Returns the number of bytes written.
    ///
    /// The marks arrive as an argument rather than being remembered as edits, and the save replays
    /// them onto the file as it was opened. That is what keeps the file honest: the app's model is
    /// the source of truth, so a mark that was undone or erased is one that is simply not in the
    /// list, and what is written cannot drift from what is on screen.
    ///
    /// Strokes are grouped per page by kind, colour and width, because an ink annotation carries
    /// one of each: a page written with a single pen becomes a single annotation rather than a
    /// thousand. Rectangles and arrows are one object each, because they are one each.
    pub fn save_with_marks(&self, path: String, pages: Vec<PageMark>) -> Result<i64, MobileError> {
        let mut session = Session::new();
        let id = session
            .open("document.pdf", None, self.original.clone(), self.password.as_deref())
            .map_err(open_error)?;

        let count = self.pages.len() as i32;
        for page_marks in &pages {
            let index = page_marks.page;
            if index < 0 || index >= count {
                return Err(MobileError::NoSuchPage { page: index, count });
            }
            let page = index as usize;
            // The crop box is in user space and the mark's coordinates are in the space the
            // reader shows; mapping one onto the other has to undo the rotation. Refusing is
            // better than drawing in the wrong place and calling it saved.
            if self.pages[page].rotation != 0 {
                return Err(MobileError::Edit { detail: "a rotated page cannot be written on yet".into() });
            }

            let crop = self.crops[page];

            // (kind, colour, width in quarter points) → the strokes drawn with it. Quantising the
            // width keeps a slider that moved by a rounding error from splitting one page in two.
            let mut freehand: Vec<(MarkKind, i32, i32, Vec<Vec<[f64; 2]>>)> = Vec::new();
            let mut shapes: Vec<(Shape, Style, String)> = Vec::new();

            for mark in &page_marks.marks {
                let points = to_user(&mark.points, crop);
                match mark.kind {
                    MarkKind::Ink | MarkKind::Highlighter => {
                        // One point is a tap, which ink cannot express; two are a stroke.
                        if points.len() < 2 {
                            continue;
                        }
                        let quarter = (f64::from(mark.width).clamp(0.1, 40.0) * 4.0).round() as i32;
                        match freehand
                            .iter_mut()
                            .find(|(k, c, w, _)| *k == mark.kind && *c == mark.color && *w == quarter)
                        {
                            Some((_, _, _, paths)) => paths.push(points),
                            None => freehand.push((mark.kind, mark.color, quarter, vec![points])),
                        }
                    }

                    MarkKind::Rectangle => {
                        if points.len() < 2 {
                            continue;
                        }
                        let (a, b) = (points[0], points[1]);
                        let rect = [
                            a[0].min(b[0]),
                            a[1].min(b[1]),
                            a[0].max(b[0]),
                            a[1].max(b[1]),
                        ];
                        // Under a point it is a stray tap, not a shape; PDF would keep it as an
                        // invisible object forever.
                        if rect[2] - rect[0] < 1.0 || rect[3] - rect[1] < 1.0 {
                            continue;
                        }
                        shapes.push((
                            Shape::Rectangle { rect },
                            style_of(mark.color, mark.width, opacity_of(mark.color)),
                            String::new(),
                        ));
                    }

                    MarkKind::Arrow => {
                        if points.len() < 2 {
                            continue;
                        }
                        let (from, to) = (points[0], points[1]);
                        if (to[0] - from[0]).abs() < 1.0 && (to[1] - from[1]).abs() < 1.0 {
                            continue;
                        }
                        shapes.push((
                            Shape::Line {
                                from,
                                to,
                                start: LineEnding::None,
                                end: LineEnding::OpenArrow,
                            },
                            style_of(mark.color, mark.width, opacity_of(mark.color)),
                            String::new(),
                        ));
                    }
                }
            }

            for (kind, color, quarter, paths) in freehand {
                let shape = if kind == MarkKind::Highlighter {
                    Shape::FreehandHighlight { strokes: paths }
                } else {
                    Shape::Ink { strokes: paths }
                };
                // A highlighter is paper that lets the text through; ink is ink. The width grows
                // with it because a highlighter that is as fine as a pen is just a pen.
                let (opacity, scale) = if kind == MarkKind::Highlighter {
                    (0.38, 3.0)
                } else {
                    (opacity_of(color), 1.0)
                };
                let width = (f64::from(quarter) / 4.0) * scale;
                shapes.push((shape, style_of(color, width as f32, opacity), String::new()));
            }

            for (shape, style, contents) in shapes {
                let annotation = NewAnnotation {
                    page,
                    shape,
                    style,
                    contents,
                    author: "PdfCraft".into(),
                };
                session
                    .apply(id, Edit::AddAnnotation(annotation))
                    .map_err(|e| MobileError::Edit { detail: e.to_string() })?;
            }
        }

        let bytes = session.save_bytes(id).map_err(|e| MobileError::Edit { detail: e.to_string() })?;
        std::fs::write(&path, &*bytes).map_err(|e| MobileError::Io { path, detail: e.to_string() })?;
        Ok(bytes.len() as i64)
    }
}

/// A mark's points, mapped from the page as displayed to the page as stored.
///
/// Screen coordinates run right and down; the document's run right and up from the bottom-left of
/// the crop box. This is the only place that difference is dealt with.
fn to_user(points: &[f32], crop: [f32; 4]) -> Vec<[f64; 2]> {
    let [x0, _, x1, y1] = crop;
    let width = f64::from(x1 - x0);
    let height = f64::from(y1 - crop[1]);

    points
        .chunks_exact(2)
        .filter_map(|pair| {
            let (nx, ny) = (pair[0], pair[1]);
            if !nx.is_finite() || !ny.is_finite() {
                return None;
            }
            Some([
                f64::from(x0) + f64::from(nx.clamp(0.0, 1.0)) * width,
                f64::from(y1) - f64::from(ny.clamp(0.0, 1.0)) * height,
            ])
        })
        .collect()
}

/// A user-space x, as a fraction of the page's width from its left edge.
fn norm_x(value: f32, crop: [f32; 4]) -> f32 {
    let width = crop[2] - crop[0];
    if width.abs() < f32::EPSILON {
        0.0
    } else {
        ((value - crop[0]) / width).clamp(-0.2, 1.2)
    }
}

/// A user-space y, as a fraction of the page's height from its *top* edge — the direction a screen
/// counts in, and the one every consumer of a normalised point here expects.
fn norm_y(value: f32, crop: [f32; 4]) -> f32 {
    let height = crop[3] - crop[1];
    if height.abs() < f32::EPSILON {
        0.0
    } else {
        ((crop[3] - value) / height).clamp(-0.2, 1.2)
    }
}

/// `0xAARRGGBB` from the file's three floats, opaque when the file gives no colour.
fn argb_of(color: Option<[f32; 3]>) -> i32 {
    let Some([r, g, b]) = color else { return 0xFF00_0000u32 as i32 };
    let channel = |v: f32| (v.clamp(0.0, 1.0) * 255.0).round() as u32;
    (0xFF00_0000 | (channel(r) << 16) | (channel(g) << 8) | channel(b)) as i32
}

/// The alpha an `0xAARRGGBB` asks for, floored so nothing is written invisible.
fn opacity_of(color: i32) -> f64 {
    let alpha = f64::from((color as u32 >> 24) & 0xFF) / 255.0;
    alpha.clamp(0.05, 1.0)
}

/// A stroke style for a mark: its colour, its opacity, its width.
fn style_of(color: i32, width: f32, opacity: f64) -> Style {
    let argb = color as u32;
    Style {
        color: [
            f64::from((argb >> 16) & 0xFF) / 255.0,
            f64::from((argb >> 8) & 0xFF) / 255.0,
            f64::from(argb & 0xFF) / 255.0,
        ],
        opacity: opacity.clamp(0.05, 1.0),
        width: f64::from(width.clamp(0.1, 60.0)),
        ..Style::default()
    }
}

/// The page's text as one string, with the engine's word breaks turned back into spaces.
///
/// Glyph order is the text layer's own order, which is reading order per line; without the spaces
/// a search for "the document" would only match when the words happen to be adjacent glyphs.
fn page_text(text: &pdfcraft_render::PageText) -> String {
    let mut out = String::with_capacity(text.glyphs.len() + 16);
    for (index, glyph) in text.glyphs.iter().enumerate() {
        if text.space_before.get(index).copied().unwrap_or(false) {
            out.push(' ');
        }
        out.push_str(&glyph.text);
    }
    out
}

/// The text around a match, for a result row: a little before, a little after.
///
/// The match position comes from a lower-cased copy of the page, which can be a different length
/// than the page itself (a German ß lower-cases to two characters, for one), so both ends are
/// pulled back to a character boundary before slicing: a slightly-off snippet beats a panic.
fn context(text: &str, at: usize, len: usize) -> String {
    let at = char_boundary(text, at);
    let end = char_boundary(text, at.saturating_add(len));
    let start = char_boundary(text, at.saturating_sub(40));
    let stop = char_boundary(text, (end + 60).min(text.len()));
    text[start..stop].trim().to_string()
}

/// `index`, moved back to the nearest character boundary of `text`.
fn char_boundary(text: &str, index: usize) -> usize {
    let mut index = index.min(text.len());
    while index > 0 && !text.is_char_boundary(index) {
        index -= 1;
    }
    index
}

/// Flatten the outline tree into the rows a panel draws.
fn flatten_outline(items: &[pdfcraft_render::OutlineItem], depth: i32, out: &mut Vec<OutlineRow>) {
    for item in items {
        out.push(OutlineRow {
            title: item.title.clone(),
            page: item.page.map_or(-1, |page| page as i32),
            depth,
        });
        flatten_outline(&item.children, depth + 1, out);
    }
}

/// Map the engine's open errors onto the ones the UI can act on: a password prompt, or a message.
fn open_error(error: OpenError) -> MobileError {
    match error {
        OpenError::NeedsPassword => MobileError::NeedsPassword,
        OpenError::WrongPassword => MobileError::WrongPassword,
        OpenError::Invalid(detail) => MobileError::Invalid { detail },
        OpenError::Unsupported(detail) => MobileError::Invalid { detail },
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    /// A one-page PDF built by hand, so the tests need no fixture file.
    fn minimal_pdf() -> Vec<u8> {
        let mut out = Vec::new();
        let mut offsets = Vec::new();
        out.extend_from_slice(b"%PDF-1.7\n");
        let objects = [
            "1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n",
            "2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n",
            "3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Contents 4 0 R >>\nendobj\n",
            "4 0 obj\n<< /Length 0 >>\nstream\n\nendstream\nendobj\n",
        ];
        for object in objects {
            offsets.push(out.len());
            out.extend_from_slice(object.as_bytes());
        }
        let xref = out.len();
        out.extend_from_slice(format!("xref\n0 {}\n0000000000 65535 f \n", objects.len() + 1).as_bytes());
        for offset in &offsets {
            out.extend_from_slice(format!("{offset:010} 00000 n \n").as_bytes());
        }
        out.extend_from_slice(
            format!(
                "trailer\n<< /Size {} /Root 1 0 R >>\nstartxref\n{xref}\n%%EOF\n",
                objects.len() + 1
            )
            .as_bytes(),
        );
        out
    }

    #[test]
    fn reads_a_page_tree_and_renders() {
        let document = Document::build(minimal_pdf(), None).expect("the fixture opens");
        assert_eq!(document.page_count(), 1);
        let page = document.page(0).expect("page zero exists");
        assert!((page.width - 612.0).abs() < 0.01, "width {} is the MediaBox's", page.width);
        assert!((page.height - 792.0).abs() < 0.01, "height {} is the MediaBox's", page.height);

        let image = document.render_page(0, 1.0).expect("an empty page still rasterizes");
        assert_eq!((image.width, image.height), (612, 792));
        assert_eq!(image.pixels.len(), 612 * 792 * 4);
    }

    #[test]
    fn refuses_a_page_past_the_end() {
        let document = Document::build(minimal_pdf(), None).expect("the fixture opens");
        match document.render_page(7, 1.0) {
            Err(MobileError::NoSuchPage { page, count }) => {
                assert_eq!((page, count), (7, 1));
            }
            other => panic!("expected NoSuchPage, got {other:?}"),
        }
    }

    #[test]
    fn rejects_bytes_that_are_not_a_pdf() {
        assert!(matches!(Document::build(b"not a pdf".to_vec(), None), Err(MobileError::Invalid { .. })));
    }

    /// The whole writing path, end to end: a mark in, a file out, and the file re-opened to see
    /// that what is there is a real annotation rather than pixels baked into the page.
    #[test]
    fn ink_reaches_the_saved_file_as_an_annotation() {
        let document = Document::build(minimal_pdf(), None).expect("the fixture opens");
        let stroke = Mark {
            kind: MarkKind::Ink,
            points: vec![0.2, 0.2, 0.6, 0.6, 0.8, 0.3],
            // Opaque dark green, in the 0xAARRGGBB form the UI passes.
            color: 0xFF1B_5E20u32 as i32,
            width: 3.0,
        };

        let path = temp_path("ink");
        document
            .save_with_marks(path.clone(), vec![PageMark { page: 0, marks: vec![stroke] }])
            .expect("the document saves");

        let reopened = reopen(&path);
        assert_eq!(reopened.annotations.len(), 1, "exactly one annotation was written");
        assert_eq!(reopened.annotations[0].subtype, "Ink", "it is an ink annotation");
        assert_eq!(reopened.annotations[0].page, 0);
    }

    #[test]
    fn strokes_from_one_pen_share_an_annotation() {
        let document = Document::build(minimal_pdf(), None).expect("the fixture opens");
        let stroke = |y: f32| Mark {
            kind: MarkKind::Ink,
            points: vec![0.1, y, 0.9, y],
            color: -0x1000000,
            width: 2.0,
        };

        let path = temp_path("ink-grouped");
        document
            .save_with_marks(
                path.clone(),
                vec![PageMark { page: 0, marks: vec![stroke(0.2), stroke(0.4), stroke(0.6)] }],
            )
            .expect("the document saves");

        // One pen, one width, one colour: one annotation holding three strokes, not three
        // annotations — which is what keeps a page of handwriting from becoming 200 objects.
        assert_eq!(reopen(&path).annotations.len(), 1);
    }

    #[test]
    fn a_different_width_is_its_own_annotation() {
        let document = Document::build(minimal_pdf(), None).expect("the fixture opens");
        let mark = |y: f32, width: f32| Mark {
            kind: MarkKind::Ink,
            points: vec![0.1, y, 0.9, y],
            color: -0x1000000,
            width,
        };

        let path = temp_path("ink-widths");
        document
            .save_with_marks(
                path.clone(),
                vec![PageMark { page: 0, marks: vec![mark(0.2, 1.0), mark(0.6, 6.0)] }],
            )
            .expect("the document saves");

        assert_eq!(reopen(&path).annotations.len(), 2);
    }

    #[test]
    fn saving_with_no_marks_writes_no_annotation() {
        let document = Document::build(minimal_pdf(), None).expect("the fixture opens");
        let path = temp_path("ink-none");
        document.save_with_marks(path.clone(), Vec::new()).expect("the document saves");
        assert!(reopen(&path).annotations.is_empty(), "an untouched page stays untouched");
    }

    #[test]
    fn a_mark_outside_the_page_is_refused() {
        let document = Document::build(minimal_pdf(), None).expect("the fixture opens");
        let stroke = Mark {
            kind: MarkKind::Ink,
            points: vec![0.0, 0.0, 1.0, 1.0],
            color: -0x1000000,
            width: 2.0,
        };
        let path = temp_path("ink-range");

        for page in [4, -1] {
            let result = document
                .save_with_marks(path.clone(), vec![PageMark { page, marks: vec![stroke.clone()] }]);
            assert!(matches!(result, Err(MobileError::NoSuchPage { .. })), "page {page} is out of range");
        }
    }

    #[test]
    fn a_stroke_with_one_point_does_not_error() {
        let document = Document::build(minimal_pdf(), None).expect("the fixture opens");
        // A tap: the UI may hand over a single sample, which ink cannot express.
        let dot = Mark {
            kind: MarkKind::Ink,
            points: vec![0.5, 0.5],
            color: -0x1000000,
            width: 2.0,
        };
        let path = temp_path("ink-dot");
        document
            .save_with_marks(path.clone(), vec![PageMark { page: 0, marks: vec![dot] }])
            .expect("a dot is ignored, not an error");
        assert!(reopen(&path).annotations.is_empty());
    }

    #[test]
    fn a_rectangle_becomes_a_square_annotation() {
        let document = Document::build(minimal_pdf(), None).expect("the fixture opens");
        let box_shape = Mark {
            kind: MarkKind::Rectangle,
            // Two opposite corners, which is how the UI reports a drag.
            points: vec![0.2, 0.2, 0.7, 0.5],
            color: -0x1000000,
            width: 2.0,
        };

        let path = temp_path("rect");
        document
            .save_with_marks(path.clone(), vec![PageMark { page: 0, marks: vec![box_shape] }])
            .expect("the document saves");

        let reopened = reopen(&path);
        assert_eq!(reopened.annotations.len(), 1);
        assert_eq!(reopened.annotations[0].subtype, "Square", "a rectangle is /Square in PDF");
        // It occupies the area that was dragged over, not the whole page.
        let width = reopened.annotations[0].rect[2] - reopened.annotations[0].rect[0];
        assert!(width > 200.0 && width < 400.0, "rect width {width} is half the page, give or take");
    }

    #[test]
    fn an_arrow_becomes_a_line_annotation() {
        let document = Document::build(minimal_pdf(), None).expect("the fixture opens");
        let arrow = Mark {
            kind: MarkKind::Arrow,
            points: vec![0.15, 0.15, 0.85, 0.4],
            color: -0x1000000,
            width: 2.0,
        };

        let path = temp_path("arrow");
        document
            .save_with_marks(path.clone(), vec![PageMark { page: 0, marks: vec![arrow] }])
            .expect("the document saves");

        let reopened = reopen(&path);
        assert_eq!(reopened.annotations.len(), 1);
        assert_eq!(reopened.annotations[0].subtype, "Line");
        assert_eq!(reopened.annotations[0].points.len(), 2, "a line has two ends");
    }

    #[test]
    fn a_highlighter_is_its_own_annotation() {
        let document = Document::build(minimal_pdf(), None).expect("the fixture opens");
        let pen = Mark {
            kind: MarkKind::Ink,
            points: vec![0.1, 0.1, 0.9, 0.1],
            color: -0x1000000,
            width: 2.0,
        };
        let marker = Mark {
            kind: MarkKind::Highlighter,
            points: vec![0.1, 0.5, 0.9, 0.5],
            color: 0x66FFE066u32 as i32,
            width: 2.0,
        };

        let path = temp_path("highlighter");
        document
            .save_with_marks(path.clone(), vec![PageMark { page: 0, marks: vec![pen, marker] }])
            .expect("the document saves");

        // A pen and a highlighter are different tools, so they cannot be merged into one object
        // even though they share a colour width: two annotations, not one.
        assert_eq!(reopen(&path).annotations.len(), 2);
    }

    /// A path under the temp directory, unique per test so a parallel run cannot collide.
    fn temp_path(name: &str) -> String {
        let path = std::env::temp_dir().join(format!("pdfcraft-{name}-{}.pdf", std::process::id()));
        path.to_string_lossy().into_owned()
    }

    /// Open what a save wrote, as a reader would.
    fn reopen(path: &str) -> pdfcraft_render::DocInfo {
        let bytes = std::fs::read(path).expect("the saved file is there");
        inspect(Arc::new(bytes), None).expect("the saved file opens again")
    }
}
