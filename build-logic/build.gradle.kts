plugins {
    `kotlin-dsl`
}

repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation(libs.android.gradle.plugin)
    implementation(libs.kotlin.gradle.plugin)
    implementation(libs.compose.gradle.plugin)
}

gradlePlugin {
    plugins {
        register("editorComposeConvention") {
            id = "sweeteditor.editor-compose-convention"
            implementationClass = "SweetEditorEditorComposeConventionPlugin"
        }
    }
}
