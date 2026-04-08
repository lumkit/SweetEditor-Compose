import com.vanniktech.maven.publish.DeploymentValidation
import com.vanniktech.maven.publish.KotlinMultiplatform

plugins {
    id("sweeteditor.editor-compose-convention")
    alias(libs.plugins.maven.publish)
}

fun Project.findPropertyOrEnvironment(name: String): String? =
    (findProperty(name) as? String)?.takeIf { it.isNotBlank() }
        ?: System.getenv("ORG_GRADLE_PROJECT_$name")?.takeIf { it.isNotBlank() }

val signingInMemoryKeyFile = findPropertyOrEnvironment("signingInMemoryKeyFile")
val resolvedPublishingProperties = buildMap {
    findPropertyOrEnvironment("mavenCentralUsername")?.let { put("mavenCentralUsername", it) }
    findPropertyOrEnvironment("mavenCentralPassword")?.let { put("mavenCentralPassword", it) }
    findPropertyOrEnvironment("signingInMemoryKeyPassword")?.let { put("signingInMemoryKeyPassword", it) }
    (
            findPropertyOrEnvironment("signingInMemoryKey")
                ?: signingInMemoryKeyFile?.let { keyPath ->
                    file(keyPath)
                        .takeIf { it.exists() }
                        ?.readText()
                        ?.takeIf { it.isNotBlank() }
                }
            )?.let { put("signingInMemoryKey", it) }
}
resolvedPublishingProperties.forEach { (name, value) ->
    extra.set(name, value)
}

plugins.withId("signing") {
    extensions.configure(SigningExtension::class.java) {
        val signingInMemoryKey = resolvedPublishingProperties["signingInMemoryKey"]
        val signingInMemoryKeyPassword = resolvedPublishingProperties["signingInMemoryKeyPassword"]
        if (!signingInMemoryKey.isNullOrBlank()) {
            useInMemoryPgpKeys(signingInMemoryKey, signingInMemoryKeyPassword)
        }
    }
}

mavenPublishing {
    publishToMavenCentral(automaticRelease = true, DeploymentValidation.PUBLISHED)
    signAllPublications()

    configure(
        KotlinMultiplatform(
            androidVariantsToPublish = listOf("release")
        )
    )

    coordinates(
        groupId = "io.github.lumkit",
        artifactId = "sweet-editor-compose",
        version = findProperty("editor.publish.versionName") as? String ?: "0.0.1"
    )

    pom {
        name = "Sweet Editor Compose"
        description = "A Multifunctional code editor library for compose multiplatfrom（It is not the BasicTextField enhancement）"
        inceptionYear = "2026"
        url = "https://github.com/lumkit/SweetEditor-Compose"
        licenses {
            license {
                name = "GNU Lesser General Public License, Version 2.1"
                url = "https://www.gnu.org/licenses/old-licenses/lgpl-2.1.txt"
                distribution = "repo"
            }
        }
        developers {
            developer {
                id = "lumkit"
                name = "LumYuan"
                url = "https://github.com/lumkit/"
                email = "lumkit@163.com"
            }
        }
        scm {
            url = "https://github.com/lumkit/SweetEditor-Compose/"
            connection = "scm:git:git://github.com/lumkit/SweetEditor-Compose.git"
            developerConnection = "scm:git:ssh://git@github.com/lumkit/SweetEditor-Compose.git"
        }
    }
}
