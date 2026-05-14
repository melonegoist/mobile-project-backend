package com.itmo.dbhandler.security

import com.itmo.dbhandler.service.UserDetailsServiceImpl
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.User
import org.springframework.test.util.ReflectionTestUtils

class JwtTokenProviderTest {

    private val userDetailsService = mockk<UserDetailsServiceImpl>()
    private lateinit var provider: JwtTokenProvider

    @BeforeEach
    fun setUp() {
        provider = JwtTokenProvider(userDetailsService)
        ReflectionTestUtils.setField(
            provider,
            "secretKey",
            "test-secret-key-must-be-long-enough-for-hs256-signing-1234"
        )
        ReflectionTestUtils.setField(provider, "expirationInMs", 60_000L)
        ReflectionTestUtils.setField(provider, "refreshExpirationInMs", 600_000L)
    }

    @Test
    fun `generate and parse access token`() {
        val token = provider.generateToken(7L, "alice")

        assertNotNull(token)
        assertTrue(provider.validateToken(token))
        assertEquals("alice", provider.getUsernameFromToken(token))
        assertEquals(7L, provider.getUserIdFromToken(token))
    }

    @Test
    fun `generated refresh token differs from access token`() {
        val access = provider.generateToken(1L, "bob")
        val refresh = provider.generateRefreshToken(1L, "bob")
        assertNotEquals(access, refresh)
    }

    @Test
    fun `validateToken returns false on garbage`() {
        assertFalse(provider.validateToken("definitely-not-a-jwt"))
    }

    @Test
    fun `validateToken returns false for expired token`() {
        ReflectionTestUtils.setField(provider, "expirationInMs", -1L)
        val expired = provider.generateToken(1L, "alice")
        assertFalse(provider.validateToken(expired))
    }

    @Test
    fun `getAuthentication wires user id into details`() {
        every { userDetailsService.loadUserByUsername("alice") } returns User(
            "alice",
            "hash",
            listOf(SimpleGrantedAuthority("ROLE_USER"))
        )

        val token = provider.generateToken(42L, "alice")
        val auth = provider.getAuthentication(token)

        assertEquals(42L, auth.details)
        assertEquals("alice", auth.name)
    }
}
