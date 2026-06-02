plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    id("kotlin-parcelize")
    jacoco
}

android {
    namespace = "com.rrbrambley.flashcards"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.rrbrambley.flashcards"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // The Android emulator reaches the host machine at 10.0.2.2.
        buildConfigField("String", "BACKEND_BASE_URL", "\"http://10.0.2.2:8080\"")

        // Google OAuth Web client ID (server client ID) for Sign in with Google.
        // Set GOOGLE_WEB_CLIENT_ID in gradle.properties or the environment; blank = Google disabled.
        buildConfigField(
            "String",
            "GOOGLE_WEB_CLIENT_ID",
            "\"${providers.gradleProperty("GOOGLE_WEB_CLIENT_ID").getOrElse("")}\"",
        )
    }

    buildTypes {
        debug {
            // Produce Jacoco coverage data from testDebugUnitTest.
            enableUnitTestCoverage = true
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
//    kotlinOptions {
//        jvmTarget = "11"
//    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(project(":shared"))

    implementation(libs.hilt.android)
    ksp(libs.hilt.ksp.compiler)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.coil.svg)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.auth)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)

    implementation(libs.coroutines.android)

    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.ktor.client.mock)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}

jacoco {
    toolVersion = libs.versions.jacoco.get()
}

// AGP doesn't emit a usable Jacoco report for unit tests on its own; this reads the exec
// data produced by `enableUnitTestCoverage` (debug) against the compiled debug classes.
tasks.register<JacocoReport>("jacocoTestReport") {
    dependsOn("testDebugUnitTest")
    group = "verification"
    description = "Generates a Jacoco coverage report from the debug unit tests."

    reports {
        xml.required.set(true) // consumed by the CI coverage comment
        html.required.set(true) // for humans
        csv.required.set(false)
    }

    val coverageExclusions = listOf(
        "**/R.class", "**/R$*.class", "**/BuildConfig.*", "**/Manifest*.*",
        // Hilt / Dagger generated
        "**/*_Hilt*.*", "hilt_aggregated_deps/**", "**/*_Factory.*", "**/Dagger*.*",
        "**/*_MembersInjector.*", "**/*_Provide*Factory*.*", "**/*_GeneratedInjector.*",
        // Room generated
        "**/*_Impl.*",
        // Compose tooling/preview noise
        "**/*ComposableSingletons*.*", "**/*Preview*.*",
    )

    classDirectories.setFrom(
        files(
            // AGP 9 built-in Kotlin compiler output; tmp/kotlin-classes is the older AGP location.
            fileTree(layout.buildDirectory.dir("intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes")) {
                exclude(coverageExclusions)
            },
            fileTree(layout.buildDirectory.dir("tmp/kotlin-classes/debug")) { exclude(coverageExclusions) },
            fileTree(layout.buildDirectory.dir("intermediates/javac/debug/compileDebugJavaWithJavac/classes")) {
                exclude(coverageExclusions)
            },
        ),
    )
    sourceDirectories.setFrom(files("src/main/java", "src/main/kotlin"))
    executionData.setFrom(
        fileTree(layout.buildDirectory) {
            include(
                "outputs/unit_test_code_coverage/debugUnitTest/testDebugUnitTest.exec",
                "jacoco/testDebugUnitTest.exec",
                "outputs/code_coverage/debugUnitTest/**/*.ec",
            )
        },
    )
}
