import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.provider.Provider
import org.gradle.language.jvm.tasks.ProcessResources
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

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
    val androidModuleNativeDir = layout.projectDirectory.dir("src/androidMain/jniLibs")
    val jvmModuleNativeDir = layout.projectDirectory.dir("src/jvmMain/resources/native")
    val iosModuleNativeDir = layout.projectDirectory.dir("src/nativeInterop/libs/ios")

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

    // Desktop JNI bridge removed: FFM (Foreign Function & Memory) API is now used instead of JNI
    // The native library is loaded directly via SymbolLookup.libraryLookup()

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
