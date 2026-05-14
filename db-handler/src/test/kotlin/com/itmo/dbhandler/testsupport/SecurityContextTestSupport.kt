package com.itmo.dbhandler.testsupport

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.userdetails.User

object SecurityContextTestSupport {

    fun authenticate(userId: Long, username: String = "test-user") {
        val userDetails = User(
            username,
            "password-hash",
            listOf(SimpleGrantedAuthority("ROLE_USER"))
        )
        val auth = UsernamePasswordAuthenticationToken(userDetails, "", userDetails.authorities)
        auth.details = userId
        SecurityContextHolder.getContext().authentication = auth
    }

    fun clear() {
        SecurityContextHolder.clearContext()
    }
}
