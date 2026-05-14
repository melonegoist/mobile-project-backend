package com.itmo.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class JwtUserExtractorTest {

    private val secret = "test-secret-not-validated-since-signature-is-ignored"

    private fun token(userId: Long? = null, subject: String? = null): String {
        val builder = JWT.create()
        if (subject != null) builder.withSubject(subject)
        if (userId != null) builder.withClaim("userId", userId)
        return builder.sign(Algorithm.HMAC256(secret))
    }

    @Test
    fun `returns null when header missing`() {
        assertNull(extractUserIdFromBearerToken(null))
    }

    @Test
    fun `returns null when header is not Bearer`() {
        assertNull(extractUserIdFromBearerToken("Basic dXNlcjpwYXNz"))
    }

    @Test
    fun `returns userId claim when present`() {
        val t = token(userId = 42L, subject = "alice")
        assertEquals("42", extractUserIdFromBearerToken("Bearer $t"))
    }

    @Test
    fun `falls back to sub when userId missing`() {
        val t = token(subject = "alice")
        assertEquals("alice", extractUserIdFromBearerToken("Bearer $t"))
    }

    @Test
    fun `returns null when payload malformed`() {
        assertNull(extractUserIdFromBearerToken("Bearer not.a.real-jwt"))
    }

    @Test
    fun `returns null when token has no payload section`() {
        assertNull(extractUserIdFromBearerToken("Bearer onlyheader"))
    }
}
