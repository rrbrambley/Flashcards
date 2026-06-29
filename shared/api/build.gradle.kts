plugins {
    id("flashcards.kmp.library")
    alias(libs.plugins.kotlin.serialization)
}

// The HTTP API contract — @Serializable DTOs + the Ktor `FlashcardApiClient` + token/refresh
// plumbing. Deliberately Room-free so `:backend` (which only needs the contract) doesn't compile
// against the mobile persistence layer. `:shared` depends on this with `api(...)` and re-exports it
// into the iOS framework (so Swift still sees the DTOs + client). See FLA-161.
kotlin {
    android {
        // compileSdk / minSdk come from the flashcards.kmp.library convention.
        namespace = "com.rrbrambley.flashcards.shared.api"
    }

    jvm()

    iosX64()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.client.auth)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.coroutines.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.ktor.client.mock)
            implementation(libs.coroutines.test)
        }
    }
}
