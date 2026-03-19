package com.itmo.routing

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.itmo.websocket.WebSocketManager
import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import org.slf4j.LoggerFactory
import java.time.Instant

private val logger = LoggerFactory.getLogger("WebSocketRoute")

fun Application.configureWebSocketRoute() {
    val jwtSecret = System.getenv("JWT_SECRET")
        ?: environment.config.propertyOrNull("jwt.secret")?.getString()
        ?: "default-secret-key-change-in-production-min-32-chars"

    routing {
        webSocket("/ws") {
            // Accept token from query param (mobile-friendly) or Authorization header
            val token = call.request.queryParameters["token"]
                ?: call.request.headers["Authorization"]?.removePrefix("Bearer ")?.trim()

            if (token == null) {
                close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Missing authentication token"))
                return@webSocket
            }

            val userId = try {
                val verifier = JWT.require(Algorithm.HMAC256(jwtSecret)).build()
                val decoded = verifier.verify(token)
                decoded.getClaim("userId")?.asLong()?.toString()
                    ?: decoded.subject
                    ?: throw IllegalArgumentException("No user identifier in token")
            } catch (e: Exception) {
                logger.warn("WebSocket rejected – invalid token: ${e.message}")
                close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Invalid or expired token"))
                return@webSocket
            }

            WebSocketManager.addSession(userId, this)
            logger.info("WS connected user=$userId total=${WebSocketManager.getTotalSessions()}")

            try {
                send(
                    Frame.Text(
                        """{"type":"connected","data":{"userId":"$userId"},"timestamp":"${Instant.now()}"}"""
                    )
                )

                for (frame in incoming) {
                    when (frame) {
                        is Frame.Text -> {
                            val text = frame.readText()
                            logger.debug("WS message from user=$userId: $text")
                            // Echo back for liveness check
                            if (text == "ping") send(Frame.Text("""{"type":"pong","timestamp":"${Instant.now()}"}"""))
                        }
                        is Frame.Ping -> send(Frame.Pong(frame.data))
                        else -> {}
                    }
                }
            } catch (e: Exception) {
                logger.debug("WS closed for user=$userId: ${e.message}")
            } finally {
                WebSocketManager.removeSession(userId, this)
                logger.info("WS disconnected user=$userId total=${WebSocketManager.getTotalSessions()}")
            }
        }
    }
}
