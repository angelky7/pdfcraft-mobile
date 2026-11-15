import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// The cargo workspace: <repo>/android is this project, so the workspace is its parent.
val workspaceDir: File = rootDir.parentFile

val appVersionName: String = providers.gradleProperty("pdfcraft.version").get()
val appVersionCode: Int = providers.gradleProperty("pdfcraft.versionCode").get().toInt()

/** The ABIs to build, from `pdfcraft.abis`: arm64-v8a by default. */
val rustAbis: List<String> = (providers.gradleProperty("pdfcraft.abis").orNull ?: "arm64-v8a")
    .split(',')
    .map(String::trim)
    .filter(String::isNotEmpty)

/** rustup's cargo (Android Studio does not inherit a shell's PATH), else PATH's; `CARGO` wins. */
val cargoExecutable: String = run {
    val exe = if (System.getProperty("os.name").lowercase().startsWith("windows")) "cargo.exe" else "cargo"
    val rustup = File(System.getProperty("user.home"), ".cargo/bin/$exe")
    providers.environmentVariable("CARGO").orNull ?: if (rustup.isFile) rustup.absolutePath else exe
}

/** Where cargo-ndk drops the shared libraries, as a jniLibs source set. */
val jniLibsDir = layout.buildDirectory.dir("rustJniLibs")

/** The library the bindings are generated from: any ABI's build carries the same metadata. */
val bindingsLibrary = jniLibsDir.map { it.file("${rustAbis.first()}/libpdfcraft_mobile.so") }

val bindingsDir = layout.buildDirectory.dir("generated/uniffi")

/**
 * Cross-compiles the Rust engine into `libpdfcraft_mobile.so` per ABI.
 *
 * This is the slow step of a clean build (the engine is a large workspace), which is why it is a
 * task of its own with declared inputs: Gradle skips it when nothing under crates/ or apps/
 * changed.
 */
val cargoBuild = tasks.register<Exec>("cargoBuild") {
    group = "rust"
    description = "Builds libpdfcraft_mobile.so for ${rustAbis.joinToString(", ")}"

    workingDir = workspaceDir
    val command = buildList {
        add(cargoExecutable)
        add("ndk")
        rustAbis.forEach { abi ->
            add("-t")
            add(abi)
        }
        add("-o")
        add(jniLibsDir.get().asFile.absolutePath)
        add("build")
        add("--release")
        add("-p")
        add("pdfcraft-mobile")
        add("--lib")
    }
    commandLine(command)

    // $CARGO_HOME and the workspace path end up in the binary; keep the build reproducible and
    // the paths out of the artifact.
    val cargoHome = providers.environmentVariable("CARGO_HOME").orNull
        ?: File(System.getProperty("user.home"), ".cargo").absolutePath
    environment("CARGO_ENCODED_RUSTFLAGS", "--remap-path-prefix=$cargoHome=/cargo")
    environment("CARGO_TERM_COLOR", "always")

    inputs.property("abis", rustAbis)
    inputs.dir(File(workspaceDir, "crates")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.dir(File(workspaceDir, "apps")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(File(workspaceDir, "Cargo.toml"))
    inputs.file(File(workspaceDir, "Cargo.lock"))
    outputs.dir(jniLibsDir)
}

/** Generates `pdfcraft_mobile.kt` from the library cargo just built. */
val generateBindings = tasks.register<Exec>("generateBindings") {
    group = "rust"
    description = "Generates the Kotlin bindings for the engine"
    dependsOn(cargoBuild)

    workingDir = workspaceDir
    commandLine(
        cargoExecutable, "run", "--quiet", "-p", "pdfcraft-mobile-bindgen", "--",
        "generate",
        "--library", bindingsLibrary.get().asFile.absolutePath,
        "--language", "kotlin",
        "--out-dir", bindingsDir.get().asFile.absolutePath,
    )

    inputs.dir(jniLibsDir)
    outputs.dir(bindingsDir)
}

/** The release keystore, when the build was given one; otherwise the build falls back to debug. */
val releaseKeystore: File? = providers.environmentVariable("PDFCRAFT_KEYSTORE").orNull
    ?.let(::File)
    ?.takeIf { it.isFile }

android {
    namespace = "io.github.angelky7.pdfcraft"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.angelky7.pdfcraft"
        minSdk = 29
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName

        // Ship only the languages the app actually has, so the APK does not carry dead strings.
        resourceConfigurations += listOf("en", "zh", "zh-rCN")

        ndk {
            abiFilters += rustAbis
        }
    }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = releaseKeystore
                storePassword = System.getenv("PDFCRAFT_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("PDFCRAFT_KEY_ALIAS")
                keyPassword = System.getenv("PDFCRAFT_KEY_PASSWORD")
                // The keystore is PKCS12, whose key password is the store's.
                enableV1Signing = true
                enableV2Signing = true
            }
        }
    }

    buildTypes {
        release {
            // UniFFI and JNA both reach their code reflectively or by JNI name, so obfuscation
            // would need rules that are easy to get subtly wrong. The app is small; leave it on.
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    sourceSets.getByName("main") {
        // The generated bindings are sources like any other; the directory only exists after
        // generateBindings has run, which the Kotlin compile depends on below.
        kotlin.srcDir(bindingsDir)
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/DEPENDENCIES",
                "/META-INF/LICENSE*",
                "/META-INF/NOTICE*",
            )
        }
    }

    lint {
        // A release build should not fail on a lint nit in generated or vendored code.
        checkReleaseBuilds = false
        abortOnError = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

// The bindings must exist before Kotlin compiles them, and the libraries before the APK's jniLibs
// are merged: both are generated, so neither is on disk when Gradle configures the module.
tasks.withType<KotlinCompile>().configureEach {
    dependsOn(generateBindings)
}

tasks.matching { it.name.startsWith("merge") && it.name.contains("JniLibFolders") }.configureEach {
    dependsOn(cargoBuild)
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)

    // UniFFI's Kotlin runtime calls into the library through JNA, and the `aar` variant is the
    // one that carries the per-ABI native JNA libraries an Android app needs.
    implementation("net.java.dev.jna:jna:${libs.versions.jna.get()}@aar")

    debugImplementation(libs.compose.ui.tooling)
}
