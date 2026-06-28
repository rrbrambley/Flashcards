// Convention: a Kotlin Multiplatform library's shared baseline — JVM toolchain 11 and the
// expect/actual-classes opt-in (Room-KMP uses an expect object whose actual is KSP-generated). The
// module declares its own targets (android/jvm/iOS), framework, source sets, and Room/KSP wiring.
plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
}

kotlin {
    jvmToolchain(11)

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }
}
