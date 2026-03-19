package com.itmo.dbhandler.util

import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.userdetails.UserDetails

object SecurityUtils {

    fun getCurrentUserId(): Long {
        val authentication = SecurityContextHolder.getContext().authentication
        val principal = authentication?.principal

        return when (principal) {
            is UserDetails -> {
                val userId = authentication.details as? Long
                userId ?: throw IllegalStateException("User ID not found in authentication details")
            }
            else -> throw IllegalStateException("No authenticated user found")
        }
    }

    fun getCurrentUsername(): String {
        val authentication = SecurityContextHolder.getContext().authentication
        return authentication?.name ?: throw IllegalStateException("No authenticated user found")
    }
}