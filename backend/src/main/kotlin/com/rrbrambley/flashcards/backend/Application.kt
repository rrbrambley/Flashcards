package com.rrbrambley.flashcards.backend

import com.rrbrambley.flashcards.backend.db.DatabaseFactory
import com.rrbrambley.flashcards.backend.db.DbConfig
import com.rrbrambley.flashcards.backend.plugins.configureMonitoring
import com.rrbrambley.flashcards.backend.plugins.configureRouting
import com.rrbrambley.flashcards.backend.plugins.configureSecurity
import com.rrbrambley.flashcards.backend.plugins.configureSerialization
import com.rrbrambley.flashcards.backend.plugins.configureStatusPages
import com.typesafe.config.ConfigFactory
import io.ktor.server.application.Application
import io.ktor.server.config.HoconApplicationConfig
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

    val port = config.property("ktor.deployment.port").getString().toInt()
    embeddedServer(Netty, port = port) { module() }.start(wait = true)
}

/** Wires plugins and routes. The database must be initialized before this runs. */
fun Application.module() {
    configureSerialization()
    configureMonitoring()
    configureStatusPages()
    configureSecurity()
    configureRouting()
}
