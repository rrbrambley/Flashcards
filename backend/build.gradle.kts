plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}

kotlin {
    jvmToolchain(11)
}

dependencies {
    implementation(project(":shared"))

    // Ktor server
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.server.auth)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.server.call.logging)
    implementation(libs.ktor.server.cors)

    // Serialization + coroutines
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.coroutines.core)

    // Exposed + DB
    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.postgresql)
    implementation(libs.hikaricp)

    // Auth
    implementation(libs.bcrypt)
    implementation(libs.google.api.client)

    // Storage (S3 / CloudFront)
    implementation(libs.aws.s3)

    // Logging
    implementation(libs.logback.classic)

    // Tests
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.testcontainers.postgresql)
}

application {
    mainClass.set("com.rrbrambley.flashcards.backend.ApplicationKt")
}

tasks.named<JavaExec>("run") {
    // Let `./gradlew :backend:run` pick up the Google Web client ID from gradle.properties
    // (an explicit env var still wins for prod). Enables Sign in with Google locally.
    providers.gradleProperty("GOOGLE_WEB_CLIENT_ID").orNull?.let {
        environment("GOOGLE_WEB_CLIENT_ID", it)
    }
}

tasks.test {
    useJUnitPlatform()
    // Forward Docker/Testcontainers env (e.g. a Colima or rootless socket) into the
    // forked test workers, which don't otherwise inherit it.
    listOf(
        "DOCKER_HOST",
        "TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE",
        "TESTCONTAINERS_RYUK_DISABLED",
    ).forEach { key -> System.getenv(key)?.let { environment(key, it) } }
    // docker-java reads the negotiated API version from the `api.version` system property,
    // not an env var; map DOCKER_API_VERSION through for engines that pin a minimum (e.g. Colima).
    System.getenv("DOCKER_API_VERSION")?.let { systemProperty("api.version", it) }
}
