package com.itmo

import com.itmo.routing.configureProxyRoutes
import com.itmo.routing.configureWebSocketRoute
import io.ktor.server.application.Application

fun Application.configureRouting() {
    configureProxyRoutes()
    configureWebSocketRoute()
}
