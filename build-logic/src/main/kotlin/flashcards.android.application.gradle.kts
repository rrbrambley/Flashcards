// Convention: an Android application module's shared baseline — SDK levels + Java 11. The module
// keeps its namespace/applicationId, buildConfig, buildTypes, buildFeatures, and feature plugins
// (Compose/KSP/Hilt). Single source of truth for compile/min/target SDK across the app.
plugins {
    id("com.android.application")
}

android {
    compileSdk = 36

    defaultConfig {
        minSdk = 26
        targetSdk = 36
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}
