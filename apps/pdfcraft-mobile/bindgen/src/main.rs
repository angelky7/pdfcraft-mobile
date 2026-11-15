//! Generates the Kotlin bindings for `pdfcraft-mobile`.
//!
//! The bindings are generated from the *built library* rather than from the source, in UniFFI's
//! library mode. That way what Kotlin sees is what the compiled crate actually exports — the
//! same release's UniFFI version, the same conditional API — instead of a second reading of the
//! source that can drift from it.
//!
//! Invoked by the Android build (`android/app/build.gradle.kts`), which passes the `.so` it just
//! built:
//!
//! ```text
//! uniffi-bindgen generate --library libpdfcraft_mobile.so --language kotlin --out-dir <dir>
//! ```
fn main() {
    uniffi::uniffi_bindgen_main();
}
