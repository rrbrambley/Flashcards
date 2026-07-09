// Convention: a Kotlin Multiplatform library's shared baseline — SDK levels, JVM toolchain 17, and
// the expect/actual-classes opt-in (Room-KMP uses an expect object whose actual is KSP-generated).
// The module declares its own namespace, targets (jvm/iOS), framework, source sets, and Room/KSP.
plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
}

kotlin {
    jvmToolchain(17)

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    // SDK levels shared with the app convention (single source of truth); the module sets namespace.
    android {
        compileSdk = FlashcardsSdk.COMPILE
        minSdk = FlashcardsSdk.MIN
    }
}
