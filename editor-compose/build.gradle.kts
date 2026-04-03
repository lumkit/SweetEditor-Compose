import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.*

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    iosArm64()
    iosSimulatorArm64()

    jvm()

    js {
        browser()
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }

    sourceSets {
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
        }
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.ui)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jvmMain.dependencies {
            implementation(libs.compose.desktop.common)
        }
    }
}

android {
    namespace = "com.qiplat.compose.sweeteditor"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
        consumerProguardFiles("proguard-rules.pro")
        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++17"
            }
        }
    }
    sourceSets["main"].jniLibs.srcDir("src/androidMain/jniLibs")
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            proguardFiles("proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    externalNativeBuild {
        cmake {
            path = file("src/androidMain/cpp/CMakeLists.txt")
        }
    }
}

dependencies {
    debugImplementation(libs.compose.uiTooling)
}

val isMacOs = System.getProperty("os.name").contains("Mac", ignoreCase = true)
val isIdeaSync = System.getProperty("idea.sync.active") == "true"
val desktopPlatformDir = when {
    System.getProperty("os.name").contains("Mac", ignoreCase = true) -> "osx"
    System.getProperty("os.name").contains("Linux", ignoreCase = true) -> "linux"
    System.getProperty("os.name").contains("Windows", ignoreCase = true) -> "windows"
    else -> "unsupported"
}
val desktopArchDir = when {
    System.getProperty("os.arch").contains("aarch64", ignoreCase = true) -> "arm64"
    System.getProperty("os.arch").contains("arm64", ignoreCase = true) -> "arm64"
    else -> "x86_64"
}
val rootNativeLibraryDir = rootProject.layout.projectDirectory.dir("editor-core")
val androidModuleNativeDir = layout.projectDirectory.dir("src/androidMain/jniLibs")
val jvmModuleNativeDir = layout.projectDirectory.dir("src/jvmMain/resources/native")
val iosModuleNativeDir = layout.projectDirectory.dir("src/iosMain/resources/native")
val desktopBridgeOutputDir = layout.buildDirectory.dir("native/jvm/$desktopArchDir")
val desktopBridgeBuildDir = layout.buildDirectory.dir("native/jvm/cmake/$desktopArchDir")
val generatedJvmResourceDir = layout.buildDirectory.dir("generated/resources/jvm/main")
val generatedDesktopBridgeResourceDir = layout.buildDirectory.dir("generated/resources/jvm/main/native/$desktopPlatformDir/$desktopArchDir")
val desktopBridgeLibraryName = System.mapLibraryName("sweeteditor_desktop_bridge")
val javaHome = System.getenv("JAVA_HOME") ?: System.getProperty("java.home")
val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use(::load)
    }
}
val androidSdkDir = localProperties.getProperty("sdk.dir")
    ?: System.getenv("ANDROID_SDK_ROOT")
    ?: System.getenv("ANDROID_HOME")
val cmakeExecutable = androidSdkDir
    ?.let { sdkDir ->
        file(sdkDir)
            .resolve("cmake")
            .listFiles()
            ?.sortedByDescending { it.name }
            ?.firstOrNull()
            ?.resolve("bin/cmake")
            ?.absolutePath
    }
    ?: "cmake"

fun syncEditorComposeNativeLibraries() {
    copy {
        from(rootNativeLibraryDir.dir("android"))
        into(androidModuleNativeDir)
        include("**/libsweeteditor.so")
        includeEmptyDirs = false
    }
    copy {
        from(rootNativeLibraryDir.dir("osx"))
        into(jvmModuleNativeDir.dir("osx"))
        include("**/libsweeteditor.dylib")
        includeEmptyDirs = false
    }
    copy {
        from(rootNativeLibraryDir.dir("linux"))
        into(jvmModuleNativeDir.dir("linux"))
        include("**/libsweeteditor.so")
        includeEmptyDirs = false
    }
    copy {
        from(rootNativeLibraryDir.dir("windows"))
        into(jvmModuleNativeDir.dir("windows"))
        include("**/sweeteditor.dll")
        includeEmptyDirs = false
    }
    copy {
        from(rootNativeLibraryDir.dir("ios"))
        into(iosModuleNativeDir)
        include("**/libsweeteditor.dylib")
        includeEmptyDirs = false
    }
}

val syncEditorComposeNativeLibraries by tasks.registering {
    group = "sweeteditor"
    description = "Syncs editor-core native libraries into editor-compose platform library folders."
    inputs.dir(rootNativeLibraryDir)
    outputs.dir(androidModuleNativeDir)
    outputs.dir(jvmModuleNativeDir)
    outputs.dir(iosModuleNativeDir)
    doLast {
        syncEditorComposeNativeLibraries()
    }
}

if (isIdeaSync) {
    syncEditorComposeNativeLibraries()
}

val configureDesktopBridge by tasks.registering(Exec::class) {
    onlyIf { isMacOs }
    dependsOn(syncEditorComposeNativeLibraries)
    inputs.file(file("src/jvmMain/cpp/CMakeLists.txt"))
    inputs.file(file("src/jvmMain/cpp/desktop_bridge.cpp"))
    outputs.dir(desktopBridgeBuildDir)
    doFirst {
        desktopBridgeBuildDir.get().asFile.mkdirs()
        commandLine(
            cmakeExecutable,
            "-S",
            file("src/jvmMain/cpp").absolutePath,
            "-B",
            desktopBridgeBuildDir.get().asFile.absolutePath,
            "-DSWEETEDITOR_ARCH_DIR=$desktopArchDir",
            "-DJAVA_HOME=$javaHome",
            "-DCMAKE_LIBRARY_OUTPUT_DIRECTORY=${desktopBridgeOutputDir.get().asFile.absolutePath}",
        )
    }
}

tasks.register<Exec>("buildDesktopBridge") {
    onlyIf { isMacOs }
    dependsOn(configureDesktopBridge)
    outputs.dir(desktopBridgeOutputDir)
    doFirst {
        desktopBridgeOutputDir.get().asFile.mkdirs()
        commandLine(
            cmakeExecutable,
            "--build",
            desktopBridgeBuildDir.get().asFile.absolutePath,
            "--target",
            "sweeteditor_desktop_bridge",
        )
    }
}

val copyDesktopBridgeToJvmResources by tasks.registering(Copy::class) {
    onlyIf { isMacOs }
    dependsOn("buildDesktopBridge")
    from(desktopBridgeOutputDir)
    include(desktopBridgeLibraryName)
    into(generatedDesktopBridgeResourceDir)
}

tasks.named<ProcessResources>("jvmProcessResources") {
    dependsOn(copyDesktopBridgeToJvmResources)
    from(generatedJvmResourceDir)
}

tasks.withType<ProcessResources>().configureEach {
    dependsOn(syncEditorComposeNativeLibraries)
}

tasks.matching {
    it.name == "preBuild" ||
        it.name == "compileKotlinJvm" ||
        it.name.contains("KotlinIdeaImport", ignoreCase = true) ||
        it.name.contains("IdeaSync", ignoreCase = true)
}.configureEach {
    dependsOn(syncEditorComposeNativeLibraries)
}
