plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

kotlin {
    jvmToolchain(11)

    // Room-KMP uses an `expect object … : RoomDatabaseConstructor` whose actual is KSP-generated;
    // opt into the (stable-in-practice) expect/actual-classes feature to silence the Beta warning.
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    android {
        namespace = "com.rrbrambley.flashcards.shared"
        compileSdk = 36
        minSdk = 26
    }

    jvm()

    val xcfName = "Shared"
    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = xcfName
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
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
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.ktor.client.mock)
            implementation(libs.coroutines.test)
        }
        // The canonical practice-grading golden fixture (FLA-81) lives at the repo root so both this
        // module and the web app share one copy. Expose it on the jvmTest classpath as a resource so
        // GradingParityFixtureTest can load it; the web reads the same file directly via node:fs.
        jvmTest {
            resources.srcDir(rootDir.resolve("testFixtures/practice-grading"))
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
    add("kspIosX64", libs.androidx.room.compiler)
    add("kspIosArm64", libs.androidx.room.compiler)
    add("kspIosSimulatorArm64", libs.androidx.room.compiler)
}
