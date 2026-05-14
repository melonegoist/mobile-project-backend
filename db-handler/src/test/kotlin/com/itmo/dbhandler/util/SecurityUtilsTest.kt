package com.itmo.dbhandler.util

import com.itmo.dbhandler.testsupport.SecurityContextTestSupport
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder

class SecurityUtilsTest {

    @AfterEach
    fun tearDown() {
        SecurityContextTestSupport.clear()
    }

    @Test
    fun `getCurrentUserId returns details from authentication`() {
        SecurityContextTestSupport.authenticate(123L, "alice")
        assertEquals(123L, SecurityUtils.getCurrentUserId())
        assertEquals("alice", SecurityUtils.getCurrentUsername())
    }

    @Test
    fun `getCurrentUserId throws when context is empty`() {
        SecurityContextTestSupport.clear()
        assertThrows(IllegalStateException::class.java) { SecurityUtils.getCurrentUserId() }
    }

    @Test
    fun `getCurrentUserId throws when principal is not UserDetails`() {
        val auth = UsernamePasswordAuthenticationToken("anonymous", null, emptyList())
        SecurityContextHolder.getContext().authentication = auth
        assertThrows(IllegalStateException::class.java) { SecurityUtils.getCurrentUserId() }
    }
}
