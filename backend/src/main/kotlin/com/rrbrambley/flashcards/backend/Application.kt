package com.rrbrambley.flashcards.backend

import com.rrbrambley.flashcards.backend.auth.GoogleTokenVerifier
import com.rrbrambley.flashcards.backend.db.DatabaseFactory
import com.rrbrambley.flashcards.backend.db.DbConfig
import com.rrbrambley.flashcards.backend.plugins.configureCors
import com.rrbrambley.flashcards.backend.plugins.configureMonitoring
import com.rrbrambley.flashcards.backend.plugins.configureRequestLimits
import com.rrbrambley.flashcards.backend.plugins.configureRouting
import com.rrbrambley.flashcards.backend.plugins.configureSecurity
import com.rrbrambley.flashcards.backend.plugins.configureSerialization
import com.rrbrambley.flashcards.backend.plugins.configureStatusPages
import com.rrbrambley.flashcards.backend.storage.S3StorageService
import com.rrbrambley.flashcards.backend.storage.Storage
import com.typesafe.config.ConfigFactory
import io.ktor.server.application.Application
import io.ktor.server.config.HoconApplicationConfig
import io.ktor.server.engine.applicationEnvironment
import io.ktor.server.engine.connector
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty

fun main() {
    val config = HoconApplicationConfig(ConfigFactory.load())

    DatabaseFactory.init(
        DbConfig(
            jdbcUrl = config.property("db.jdbcUrl").getString(),
            user = config.property("db.user").getString(),
            password = config.property("db.password").getString(),
            maxPoolSize = config.property("db.maxPoolSize").getString().toInt(),
        ),
    )

    GoogleTokenVerifier.configure(config.propertyOrNull("auth.googleWebClientId")?.getString())

    val bucket = config.propertyOrNull("storage.bucket")?.getString()
    val cdnBaseUrl = config.propertyOrNull("storage.cdnBaseUrl")?.getString()
    if (!bucket.isNullOrBlank() && !cdnBaseUrl.isNullOrBlank()) {
        Storage.service = S3StorageService(
            bucket = bucket,
            cdnBaseUrl = cdnBaseUrl,
            region = config.propertyOrNull("storage.region")?.getString() ?: "us-east-1",
            endpoint = config.propertyOrNull("storage.endpoint")?.getString(),
        )
    }

    val port = config.property("ktor.deployment.port").getString().toInt()
    // Hand the loaded config to the server environment so module() (e.g. configureSecurity's
    // jwt block) can read environment.config — embeddedServer(port = …) alone leaves it empty.
    embeddedServer(
        Netty,
        environment = applicationEnvironment { this.config = config },
        configure = { connector { this.port = port } },
    ) { module() }.start(wait = true)
}

/** Wires plugins and routes. The database must be initialized before this runs. */
fun Application.module() {
    configureSerialization()
    configureMonitoring()
    configureCors()
    configureStatusPages()
    configureRequestLimits()
    configureSecurity()
    configureRouting()
}
