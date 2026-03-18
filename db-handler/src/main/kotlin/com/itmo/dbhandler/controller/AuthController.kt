package com.itmo.dbhandler.controller

import com.itmo.dbhandler.api.AuthApi
import com.itmo.dbhandler.model.AuthLogoutPost200Response
import com.itmo.dbhandler.model.LoginRequest
import com.itmo.dbhandler.model.LoginResponse
import com.itmo.dbhandler.model.RefreshTokenRequest
import com.itmo.dbhandler.model.RegisterRequest
import com.itmo.dbhandler.service.AuthService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes

@RestController
class AuthController(
    private val authService: AuthService
) : AuthApi {

    override fun authRegisterPost(registerRequest: RegisterRequest): ResponseEntity<LoginResponse> {
        val response = authService.register(registerRequest)
        return ResponseEntity(
            LoginResponse(
                token = response.token,
                tokenType = response.tokenType,
                expiresIn = response.expiresIn.toInt(),
                refreshToken = response.refreshToken,
                user = response.user
            ),
            HttpStatus.CREATED
        )
    }

    override fun authLoginPost(loginRequest: LoginRequest): ResponseEntity<LoginResponse> {
        val response = authService.login(loginRequest)
        return ResponseEntity.ok(
            LoginResponse(
                token = response.token,
                tokenType = response.tokenType,
                expiresIn = response.expiresIn.toInt(),
                refreshToken = response.refreshToken,
                user = response.user
            )
        )
    }

    override fun authRefreshPost(refreshTokenRequest: RefreshTokenRequest): ResponseEntity<LoginResponse> {
        val response = authService.refresh(refreshTokenRequest)
        return ResponseEntity.ok(
            LoginResponse(
                token = response.token,
                tokenType = response.tokenType,
                expiresIn = response.expiresIn.toInt(),
                refreshToken = response.refreshToken,
                user = response.user
            )
        )
    }

    override fun authLogoutPost(): ResponseEntity<AuthLogoutPost200Response> {
        val request = (RequestContextHolder.currentRequestAttributes() as ServletRequestAttributes).request
        val authHeader = request.getHeader("Authorization")
        val token = authHeader?.substringAfter("Bearer ")
        if (token != null) {
            authService.logout(token)
        }
        return ResponseEntity.ok(
            AuthLogoutPost200Response(
                message = "Logged out successfully"
            )
        )
    }
}