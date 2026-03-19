package com.itmo.dbhandler.dto

import com.itmo.dbhandler.model.User

data class AuthResponse(
    val token: String,
    val tokenType: String = "Bearer",
    val expiresIn: Long,
    val refreshToken: String?,
    val user: User
)