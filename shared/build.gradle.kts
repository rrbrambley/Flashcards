plugins {
    id("flashcards.kmp.library")
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// Embed the canonical practice-grading golden fixture (FLA-81) as a Kotlin string so the parity test
// can run in `commonTest` on Kotlin/Native (iOS) too — Native has no classpath resources (#344). The
// repo-root JSON stays the single source of truth (web reads it via node:fs; this is derived at
// build time, never hand-edited).
val generateGradingFixture = tasks.register("generateGradingFixtureSource") {
    val fixture = rootDir.resolve("testFixtures/practice-grading/grading-fixtures.json")
    val outputDir = layout.buildDirectory.dir("generated/gradingFixture/commonTest/kotlin")
    inputs.file(fixture)
    outputs.dir(outputDir)
    doLast {
        val escaped = fixture.readText()
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\$", "\\\$")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
        val pkgDir = outputDir.get().dir("com/rrbrambley/flashcards/practice/grading").asFile
        pkgDir.mkdirs()
        pkgDir.resolve("GradingFixtureData.kt").writeText(
            buildString {
                appendLine("package com.rrbrambley.flashcards.practice.grading")
                appendLine()
                appendLine("// GENERATED from testFixtures/practice-grading/grading-fixtures.json — do not edit.")
                appendLine("internal const val GRADING_FIXTURES_JSON: String = \"$escaped\"")
            },
        )
    }
}

kotlin {
    // jvmToolchain(11), the -Xexpect-actual-classes opt-in, and compileSdk/minSdk come from the
    // flashcards.kmp.library convention.
    android {
        namespace = "com.rrbrambley.flashcards.shared"
    }

    jvm()

    val xcfName = "Shared"
    listOf(
        // iosX64 (Intel-Mac simulator) dropped: androidx.sqlite:sqlite-bundled 2.7+ (pulled by the
        // Kotlin 2.4 bump) no longer publishes an ios_x64 variant. Apple Silicon uses
        // iosSimulatorArm64; devices use iosArm64; CI is ARM — so Intel-sim support is moot.
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = xcfName
            isStatic = true
            // Re-export the API contract so Swift still sees the DTOs + FlashcardApiClient (FLA-161).
            export(project(":shared:api"))
        }
    }

    sourceSets {
        commonMain.dependencies {
            // The HTTP API contract (DTOs + client) lives in :shared:api; `api(...)` re-exposes it to
            // consumers (androidApp) and exports it into the iOS framework (FLA-161).
            api(project(":shared:api"))
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.client.auth)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.coroutines.core)
            // Room-KMP: the offline-first store, shared across Android + iOS (run on the bundled
            // SQLite driver). `api` so consumers (androidApp / iosApp) can reference the DB + DAOs.
            api(libs.androidx.room.runtime)
            implementation(libs.androidx.sqlite.bundled)
        }
        iosMain.dependencies {
            // Darwin (NSURLSession) HTTP engine so iOS uses the platform networking stack.
            implementation(libs.ktor.client.darwin)
        }
        commonTest {
            // The grading golden fixture, embedded as a Kotlin string by generateGradingFixtureSource
            // so GradingParityFixtureTest runs on both JVM and iOS Native (#344).
            kotlin.srcDir(generateGradingFixture)
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.ktor.client.mock)
                implementation(libs.coroutines.test)
            }
        }
        // The shuffle golden fixture (FLA-200) is still loaded as a jvmTest classpath resource by
        // ShuffleParityFixtureTest (JVM-only), shared with the web Vitest suite.
        jvmTest {
            resources.srcDir(rootDir.resolve("testFixtures/practice-shuffle"))
        }
    }
}

// Export Room schemas to a versioned, checked-in dir (the Room Gradle plugin can't yet configure a
// KMP module, so use the KSP arg — same approach androidApp uses).
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

// Run the Room KSP compiler for every target's compilation (the generated DB impl is per-platform).
// Needs KSP 2.3+ (decoupled KSP2), which is the first to support AGP's KMP-library android target.
dependencies {
    add("kspAndroid", libs.androidx.room.compiler)
    add("kspJvm", libs.androidx.room.compiler)
    add("kspIosArm64", libs.androidx.room.compiler)
    add("kspIosSimulatorArm64", libs.androidx.room.compiler)
}
