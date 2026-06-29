// Convention: an Android application module's shared baseline — SDK levels + Java 11. The module
// keeps its namespace/applicationId, buildConfig, buildTypes, buildFeatures, and feature plugins
// (Compose/KSP/Hilt). Single source of truth for compile/min/target SDK across the app.
plugins {
    id("com.android.application")
}

android {
    compileSdk = FlashcardsSdk.COMPILE

    defaultConfig {
        minSdk = FlashcardsSdk.MIN
        targetSdk = FlashcardsSdk.TARGET
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}
