//! The engine as the Android app sees it: open a document, ask about its pages, get rasters.
//!
//! Everything expensive — parsing, interpreting a page — happens in Rust. The Kotlin side owns
//! no PDF knowledge: it asks for a page at a scale, receives premultiplied RGBA8, and draws it.
//! UniFFI generates the Kotlin bindings from this crate (see `bindgen/`), so the surface here is
//! deliberately small and flat: one object per open document, plus records for what crosses the
//! boundary.
//!
//! `PageRenderer` is not `Sync` (it holds interpreter state and mutates per request), so a
//! document owns it behind a `Mutex`. Renders are therefore serialized per document. That is the
//! right trade for a phone: one page at a time is what a reader shows, and a second worker would
//! need a second parser and a second copy of the file's buffers.

use std::sync::{Arc, Mutex};

use pdfcraft_render::{OpenError, PageRenderer, RenderConfig, RenderRequest, inspect};

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
    renderer: Mutex<PageRenderer>,
    pages: Vec<PageSize>,
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

        let config = RenderConfig {
            password: password.as_deref().map(Arc::from),
            // Installed CJK faces may draw fonts the PDF fails to embed. Screen only: nothing
            // this app renders is saved or printed, so the exception cannot escape into a file.
            ..RenderConfig::default().on_screen()
        };

        Ok(Arc::new(Self {
            renderer: Mutex::new(PageRenderer::new(bytes, config)),
            pages,
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

    #[test]
    fn fit_scales_to_the_narrower_side() {
        let document = Document::build(minimal_pdf(), None).expect("the fixture opens");
        // A 612×792 page in a 1000×792 viewport: width allows 1.63, height allows 1.00, so the
        // page fits at 1.00 and is centred by the UI.
        let fit = document.fit_scale(0, 1000.0, 792.0, 2.0);
        assert!((fit - 1.0).abs() < 1e-4, "fit {fit} should be the height's 1.00");

        // A narrower viewport is then the limit: 612 → 306 is half.
        let narrow = document.fit_scale(0, 306.0, 792.0, 2.0);
        assert!((narrow - 0.5).abs() < 1e-4, "fit {narrow} should be the width's 0.50");
    }
}
