package com.itmo.plugins

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import kotlinx.serialization.json.Json

val httpClient = HttpClient(CIO) {
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true; isLenient = true })
    }
    install(HttpTimeout) {
        requestTimeoutMillis = 30_000
        connectTimeoutMillis = 5_000
    }
    engine {
        maxConnectionsCount = 20_000
        endpoint {
            maxConnectionsPerRoute = 5_000
            connectTimeout = 5_000
            requestTimeout = 30_000
        }
    }
}

fun Application.configureHTTPClient() {
    environment.monitor.subscribe(ApplicationStopped) {
        httpClient.close()
    }
}
