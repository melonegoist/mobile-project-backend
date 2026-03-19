package com.itmo.plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*

fun Application.configureStatusPages() {
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            call.respond(
                HttpStatusCode.InternalServerError,
                mapOf(
                    "code" to 500,
                    "message" to (cause.message ?: "Internal server error")
                )
            )
        }
        status(HttpStatusCode.NotFound) { call, status ->
            call.respond(status, mapOf("code" to 404, "message" to "Not found"))
        }
        status(HttpStatusCode.Unauthorized) { call, status ->
            call.respond(status, mapOf("code" to 401, "message" to "Unauthorized"))
        }
    }
}
