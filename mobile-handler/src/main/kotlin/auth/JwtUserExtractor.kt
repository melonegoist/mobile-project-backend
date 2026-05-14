package com.itmo.auth

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.Base64

/**
 * Best-effort extraction of the user identifier from a Bearer token's JWT payload.
 *
 * The token signature is intentionally NOT verified here — the mobile-handler is a
 * proxy in front of db-handler, which is the actual authority. We only need the
 * userId so we can route the trade_notification message to the right WebSocket
 * session after a successful POST /trades response.
 */
fun extractUserIdFromBearerToken(authHeader: String?): String? {
    if (authHeader == null || !authHeader.startsWith("Bearer ")) return null
    return try {
        val token = authHeader.removePrefix("Bearer ").trim()
        val payloadB64 = token.split(".").getOrNull(1) ?: return null
        val padded = payloadB64.padEnd((payloadB64.length + 3) / 4 * 4, '=')
        val payload = Base64.getUrlDecoder().decode(padded).decodeToString()
        val json = Json.parseToJsonElement(payload).jsonObject
        json["userId"]?.jsonPrimitive?.contentOrNull
            ?: json["sub"]?.jsonPrimitive?.contentOrNull
    } catch (_: Exception) {
        null
    }
}
