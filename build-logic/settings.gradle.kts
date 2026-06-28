// Standalone build for the project's Gradle convention plugins (included by the root settings via
// `pluginManagement { includeBuild("build-logic") }`). Shares the root version catalog so plugin
// versions stay defined once in gradle/libs.versions.toml.
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
