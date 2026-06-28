import org.gradle.api.provider.Provider
import org.gradle.plugin.use.PluginDependency

plugins {
    `kotlin-dsl`
}

// Put the Gradle plugin *markers* on the convention-plugin classpath so the precompiled scripts in
// src/main/kotlin can apply them by id. Versions come from the root catalog — the single source of
// truth — by mapping each `[plugins]` entry to its `<id>:<id>.gradle.plugin:<version>` marker.
dependencies {
    fun plugin(p: Provider<PluginDependency>) =
        p.map { "${it.pluginId}:${it.pluginId}.gradle.plugin:${it.version}" }

    implementation(plugin(libs.plugins.android.application))
    implementation(plugin(libs.plugins.android.kotlin.multiplatform.library))
    implementation(plugin(libs.plugins.kotlin.multiplatform))
    implementation(plugin(libs.plugins.kotlin.jvm))
}
