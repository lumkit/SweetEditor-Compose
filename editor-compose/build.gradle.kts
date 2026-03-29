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
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.activity.compose)
        }
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jvmMain.dependencies {
            implementation(libs.compose.desktop.common)
            implementation(libs.kotlinx.coroutinesSwing)
        }
    }
}

android {
    namespace = "com.qiplat.compose.sweeteditor"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++17"
            }
        }
    }
    sourceSets["main"].jniLibs.srcDir("../editor-core/include/android")
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
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
val desktopArchDir = when {
    System.getProperty("os.arch").contains("aarch64", ignoreCase = true) -> "arm64"
    System.getProperty("os.arch").contains("arm64", ignoreCase = true) -> "arm64"
    else -> "x86_64"
}
val desktopBridgeOutputDir = layout.buildDirectory.dir("native/jvm/$desktopArchDir")
val desktopBridgeBuildDir = layout.buildDirectory.dir("native/jvm/cmake/$desktopArchDir")
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

val configureDesktopBridge by tasks.registering(Exec::class) {
    onlyIf { isMacOs }
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
