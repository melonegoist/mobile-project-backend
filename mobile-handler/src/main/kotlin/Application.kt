package com.itmo

import com.itmo.plugins.configureAuth
import com.itmo.plugins.configureHTTPClient
import com.itmo.plugins.configureSerialization
import com.itmo.plugins.configureStatusPages
import com.itmo.plugins.configureWebSockets
import com.itmo.redis.RedisSubscriber
import io.ktor.server.application.*

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    configureSerialization()
    configureWebSockets()
    configureAuth()
    configureStatusPages()
    configureHTTPClient()
    configureRouting()
    RedisSubscriber.start(this)
}
