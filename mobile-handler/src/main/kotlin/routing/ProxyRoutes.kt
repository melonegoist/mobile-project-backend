package com.itmo.routing

import com.itmo.plugins.httpClient
import com.itmo.websocket.WebSocketManager
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.slf4j.LoggerFactory
import java.time.Instant

private val logger = LoggerFactory.getLogger("ProxyRoutes")

private val hopByHopHeaders = setOf(
    "Connection", "Keep-Alive", "Transfer-Encoding", "TE",
    "Trailers", "Upgrade", "Proxy-Authorization", "Proxy-Authenticate"
)

fun Application.configureProxyRoutes() {
    val dbHandlerUrl = System.getenv("DB_HANDLER_URL")
        ?: environment.config.propertyOrNull("dbhandler.url")?.getString()
        ?: "http://localhost:8080"

    routing {
        get("/health") {
            call.respond(
                HttpStatusCode.OK,
                mapOf(
                    "status" to "healthy",
                    "service" to "mobile-handler",
                    "version" to "1.0.0",
                    "timestamp" to Instant.now().toString(),
                    "websocketSessions" to WebSocketManager.getTotalSessions(),
                    "connectedUsers" to WebSocketManager.getConnectedUserCount()
                )
            )
        }

        // Forward all other requests to db-handler
        route("{...}") {
            handle {
                proxyRequest(call, dbHandlerUrl)
            }
        }
    }
}

private suspend fun proxyRequest(call: ApplicationCall, targetBase: String) {
    val path = call.request.uri
    val targetUrl = "$targetBase$path"

    try {
        val isTradePost = call.request.httpMethod == HttpMethod.Post
                && call.request.path() == "/trades"

        // Buffer body for POST/PUT/PATCH
        val bodyBytes: ByteArray? = if (call.request.httpMethod in listOf(
                HttpMethod.Post, HttpMethod.Put, HttpMethod.Patch
            )
        ) {
            call.receive()
        } else null

        val upstream: HttpResponse = httpClient.request(targetUrl) {
            method = call.request.httpMethod

            call.request.headers.forEach { name, values ->
                if (name.equals("Host", ignoreCase = true)) return@forEach
                if (hopByHopHeaders.any { it.equals(name, ignoreCase = true) }) return@forEach
                values.forEach { value -> headers.append(name, value) }
            }

            bodyBytes?.let {
                val ct = call.request.contentType()
                setBody(ByteArrayContent(it, ct))
            }
        }

        upstream.headers.forEach { name, values ->
            if (name.equals("Content-Length", ignoreCase = true)) return@forEach
            if (hopByHopHeaders.any { it.equals(name, ignoreCase = true) }) return@forEach
            values.forEach { value ->
                call.response.headers.append(name, value, safeOnly = false)
            }
        }

        val responseBody = upstream.bodyAsBytes()
        call.respond(upstream.status, ByteArrayContent(responseBody, upstream.contentType()))

        // After a successful trade, push a trade_notification via WebSocket
        if (isTradePost && upstream.status.isSuccess()) {
            val authHeader = call.request.headers["Authorization"]
            val userId = extractUserIdFromBearerToken(authHeader)
            if (userId != null) {
                val tradeJson = responseBody.decodeToString()
                val wsMsg = """{"type":"trade_notification","data":$tradeJson,"timestamp":"${Instant.now()}"}"""
                WebSocketManager.sendToUser(userId, wsMsg)
            }
        }
    } catch (e: Exception) {
        logger.error("Proxy error for $targetUrl: ${e.message}")
        call.respond(
            HttpStatusCode.BadGateway,
            mapOf("code" to 502, "message" to "Backend service unreachable", "details" to e.message)
        )
    }
}

private fun extractUserIdFromBearerToken(authHeader: String?): String? {
    if (authHeader == null || !authHeader.startsWith("Bearer ")) return null
    return try {
        val token = authHeader.removePrefix("Bearer ").trim()
        val payloadB64 = token.split(".").getOrNull(1) ?: return null
        val payload = java.util.Base64.getUrlDecoder()
            .decode(payloadB64.padEnd((payloadB64.length + 3) / 4 * 4, '='))
            .decodeToString()
        val json = kotlinx.serialization.json.Json.parseToJsonElement(payload).jsonObject
        json["userId"]?.jsonPrimitive?.contentOrNull
            ?: json["sub"]?.jsonPrimitive?.contentOrNull
    } catch (_: Exception) {
        null
    }
}
