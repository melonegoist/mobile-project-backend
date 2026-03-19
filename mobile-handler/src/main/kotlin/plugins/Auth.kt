package com.itmo.plugins

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*

fun Application.configureAuth() {
    val jwtSecret = System.getenv("JWT_SECRET")
        ?: environment.config.propertyOrNull("jwt.secret")?.getString()
        ?: "default-secret-key-change-in-production-min-32-chars"

    install(Authentication) {
        jwt("auth-jwt") {
            realm = "BrokerApp"
            verifier(
                JWT.require(Algorithm.HMAC256(jwtSecret)).build()
            )
            validate { credential ->
                if (credential.payload.subject != null) {
                    JWTPrincipal(credential.payload)
                } else null
            }
        }
    }
}
