package com.itmo

import com.itmo.routing.configureProxyRoutes
import com.itmo.routing.configureWebSocketRoute
import org.gradle.internal.code.UserCodeApplicationContext

fun UserCodeApplicationContext.Application.configureRouting() {
    configureProxyRoutes()
    configureWebSocketRoute()
}
