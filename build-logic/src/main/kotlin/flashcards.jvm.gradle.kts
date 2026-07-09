// Convention: a plain Kotlin/JVM module's shared baseline — JVM toolchain 17. The module keeps its
// own plugins (application, serialization, jacoco, test-retry), dependencies, and tasks.
plugins {
    id("org.jetbrains.kotlin.jvm")
}

kotlin {
    jvmToolchain(17)
}
