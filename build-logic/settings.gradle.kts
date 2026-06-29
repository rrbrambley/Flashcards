// Standalone build for the project's Gradle convention plugins (included by the root settings via
// `pluginManagement { includeBuild("build-logic") }`). Shares the root version catalog so plugin
// versions stay defined once in gradle/libs.versions.toml.
pluginManagement {
    // Explicit repos so the `kotlin-dsl` plugin + convention-plugin markers resolve reliably from a
    // clean cache (a missing block left this to flake on the CI macOS runner — FLA-161).
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "build-logic"
