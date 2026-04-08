import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.Exec
import org.gradle.language.jvm.tasks.ProcessResources
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import java.util.*

class SweetEditorEditorComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.multiplatform")
            pluginManager.apply("com.android.library")
            pluginManager.apply("org.jetbrains.compose")
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")

            val libs = extensions.getByType(VersionCatalogsExtension::class.java).named("libs")
            fun library(name: String) = libs.findLibrary(name).get()
            fun intVersion(name: String) = libs.findVersion(name).get().requiredVersion.toInt()

            val editorCoreDirectory = rootProject.layout.projectDirectory.dir("editor-core")
            val iosCinteropDefFileByTargetName = mapOf(
                "iosArm64" to "sweeteditor-iosArm64.def",
                "ios_arm64" to "sweeteditor-iosArm64.def",
                "iosSimulatorArm64" to "sweeteditor-iosSimulatorArm64.def",
                "ios_simulator_arm64" to "sweeteditor-iosSimulatorArm64.def",
            )

            configureKotlin(iosCinteropDefFileByTargetName, ::library)
            configureAndroid(intVersion("android-compileSdk"), intVersion("android-minSdk"))
            configureNativeSyncAndDesktopBridge(editorCoreDirectory)
        }
    }
}

private fun Project.configureAndroid(
    compileSdkVersion: Int,
    minSdkVersion: Int,
) {
    extensions.configure(LibraryExtension::class.java) {
        namespace = "com.qiplat.compose.sweeteditor"
        compileSdk = compileSdkVersion

        defaultConfig {
            minSdk = minSdkVersion
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
        sourceSets.getByName("main").jniLibs.srcDir("src/androidMain/jniLibs")
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
        publishing {
            singleVariant("release") {
                withSourcesJar()
            }
        }
    }
}

@OptIn(ExperimentalWasmDsl::class)
private fun Project.configureKotlin(
    iosCinteropDefFileByTargetName: Map<String, String>,
    libraryProvider: (String) -> Provider<MinimalExternalModuleDependency>,
) {
    extensions.configure(KotlinMultiplatformExtension::class.java) {
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
        wasmJs {
            browser()
        }

        targets.withType(KotlinNativeTarget::class.java).configureEach {
            if (konanTarget.family != org.jetbrains.kotlin.konan.target.Family.IOS) {
                return@configureEach
            }
            binaries {
                framework {
                    baseName = "SweetEditor"
                }
            }
            compilations.getByName("main").cinterops.create("sweeteditor") {
                val defFileName = requireNotNull(iosCinteropDefFileByTargetName[konanTarget.name]) {
                    "Missing cinterop def mapping for iOS target ${konanTarget.name}"
                }
                val iosHeaderDirectory = project.layout.projectDirectory.dir("src/nativeInterop/libs/ios/headers").asFile.absolutePath
                defFile(project.file("src/nativeInterop/cinterop/$defFileName"))
                includeDirs(iosHeaderDirectory)
                compilerOpts("-I$iosHeaderDirectory")
            }
        }

    }

    dependencies.add("androidMainImplementation", libraryProvider("androidx-activity-compose"))
    dependencies.add("commonMainImplementation", libraryProvider("compose-runtime"))
    dependencies.add("commonMainImplementation", libraryProvider("compose-foundation"))
    dependencies.add("commonMainImplementation", libraryProvider("compose-ui"))
    dependencies.add("commonTestImplementation", libraryProvider("kotlin-test"))
    dependencies.add("jvmMainImplementation", libraryProvider("compose-desktop-common"))
    dependencies.add("debugImplementation", libraryProvider("compose-uiTooling"))
}

private fun Project.configureNativeSyncAndDesktopBridge(editorCoreDirectory: org.gradle.api.file.Directory) {
    val osName = System.getProperty("os.name")
    val osArch = System.getProperty("os.arch")
    val isMacOs = osName.contains("Mac", ignoreCase = true)
    val desktopPlatformDir = when {
        osName.contains("Mac", ignoreCase = true) -> "osx"
        osName.contains("Linux", ignoreCase = true) -> "linux"
        osName.contains("Windows", ignoreCase = true) -> "windows"
        else -> "unsupported"
    }
    val desktopArchDir = when {
        osArch.contains("aarch64", ignoreCase = true) -> "arm64"
        osArch.contains("arm64", ignoreCase = true) -> "arm64"
        else -> "x86_64"
    }
    val androidModuleNativeDir = layout.projectDirectory.dir("src/androidMain/jniLibs")
    val jvmModuleNativeDir = layout.projectDirectory.dir("src/jvmMain/resources/native")
    val iosModuleNativeDir = layout.projectDirectory.dir("src/nativeInterop/libs/ios")
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

    val syncEditorComposeNativeLibraries = tasks.register("syncEditorComposeNativeLibraries") {
        group = "sweeteditor"
        description = "Syncs editor-core native libraries into editor-compose platform library folders."
        inputs.dir(editorCoreDirectory)
        outputs.dir(androidModuleNativeDir)
        outputs.dir(jvmModuleNativeDir)
        outputs.dir(iosModuleNativeDir)
        outputs.dir(iosModuleNativeDir.dir("headers"))
        doLast {
            val nativeCopyMappings = listOf(
                Triple(editorCoreDirectory.dir("android"), androidModuleNativeDir, "**/libsweeteditor.so"),
                Triple(editorCoreDirectory.dir("osx"), jvmModuleNativeDir.dir("osx"), "**/libsweeteditor.dylib"),
                Triple(editorCoreDirectory.dir("linux"), jvmModuleNativeDir.dir("linux"), "**/libsweeteditor.so"),
                Triple(editorCoreDirectory.dir("windows"), jvmModuleNativeDir.dir("windows"), "**/sweeteditor.dll"),
                Triple(editorCoreDirectory.dir("ios"), iosModuleNativeDir, "**/libsweeteditor_static.a"),
            )
            nativeCopyMappings.forEach { (sourceDirectory, targetDirectory, includePattern) ->
                copy {
                    from(sourceDirectory)
                    into(targetDirectory)
                    include(includePattern)
                    includeEmptyDirs = false
                }
            }
            copy {
                from(editorCoreDirectory)
                into(iosModuleNativeDir.dir("headers"))
                include("c_api.h")
                includeEmptyDirs = false
            }
        }
    }

    val configureDesktopBridge = tasks.register("configureDesktopBridge", Exec::class.java) {
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

    val buildDesktopBridge = tasks.register("buildDesktopBridge", Exec::class.java) {
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

    val copyDesktopBridgeToJvmResources = tasks.register("copyDesktopBridgeToJvmResources", Copy::class.java) {
        onlyIf { isMacOs }
        dependsOn(buildDesktopBridge)
        from(desktopBridgeOutputDir)
        include(desktopBridgeLibraryName)
        into(generatedDesktopBridgeResourceDir)
    }

    tasks.named("jvmProcessResources", ProcessResources::class.java).configure {
        dependsOn(copyDesktopBridgeToJvmResources)
        from(generatedJvmResourceDir)
    }

    tasks.withType(ProcessResources::class.java).configureEach {
        dependsOn(syncEditorComposeNativeLibraries)
    }

    tasks.matching {
        it.name == "preBuild" ||
            it.name == "compileKotlinJvm" ||
            it.name.startsWith("cinteropSweeteditor", ignoreCase = true) ||
            it.name.contains("KotlinIdeaImport", ignoreCase = true) ||
            it.name.contains("IdeaSync", ignoreCase = true)
    }.configureEach {
        dependsOn(syncEditorComposeNativeLibraries)
    }
}
