package com.itmo.dbhandler.service

import com.itmo.dbhandler.dto.AuthResponse
import com.itmo.dbhandler.entity.Account
import com.itmo.dbhandler.entity.RefreshToken
import com.itmo.dbhandler.entity.User
import com.itmo.dbhandler.model.LoginRequest
import com.itmo.dbhandler.model.RefreshTokenRequest
import com.itmo.dbhandler.model.RegisterRequest
import com.itmo.dbhandler.model.User as ApiUser
import com.itmo.dbhandler.repository.AccountRepository
import com.itmo.dbhandler.repository.RefreshTokenRepository
import com.itmo.dbhandler.repository.UserRepository
import com.itmo.dbhandler.security.JwtTokenProvider
import org.springframework.http.HttpStatus
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.*

@Service
class AuthService(
    private val userRepository: UserRepository,
    private val accountRepository: AccountRepository,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtTokenProvider: JwtTokenProvider
) {

    @Transactional
    fun register(request: RegisterRequest): AuthResponse {
        if (userRepository.existsByUsername(request.username)) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Username already exists")
        }
        if (userRepository.existsByEmail(request.email)) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Email already exists")
        }

        val user = User(
            username = request.username,
            email = request.email,
            passwordHash = passwordEncoder.encode(request.password)!!,
            firstName = request.firstName,
            lastName = request.lastName
        )
        val savedUser = userRepository.save(user)

        val account = Account(
            userId = savedUser.id,
            balance = BigDecimal.ZERO,
            currency = "USD"
        )
        accountRepository.save(account)

        return generateAuthResponse(savedUser)
    }

    fun login(request: LoginRequest): AuthResponse {
        val user = userRepository.findByUsername(request.username)
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials")

        if (!passwordEncoder.matches(request.password, user.passwordHash)) {
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials")
        }

        return generateAuthResponse(user)
    }

    @Transactional
    fun refresh(request: RefreshTokenRequest): AuthResponse {
        val refreshToken = refreshTokenRepository.findByToken(request.refreshToken)
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token")

        if (refreshToken.expiryDate.isBefore(OffsetDateTime.now())) {
            refreshTokenRepository.delete(refreshToken)
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token expired")
        }

        val user = userRepository.findById(refreshToken.userId)
            .orElseThrow { ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found") }

        refreshTokenRepository.delete(refreshToken)

        return generateAuthResponse(user)
    }

    @Transactional
    fun logout(token: String) {
        val userId = jwtTokenProvider.getUserIdFromToken(token)
        refreshTokenRepository.deleteByUserId(userId)
    }

    private fun generateAuthResponse(user: User): AuthResponse {
        val token = jwtTokenProvider.generateToken(user.id, user.username)
        val refreshToken = jwtTokenProvider.generateRefreshToken(user.id, user.username)

        val refreshTokenEntity = RefreshToken(
            userId = user.id,
            token = refreshToken,
            expiryDate = OffsetDateTime.now().plusDays(1)
        )
        refreshTokenRepository.save(refreshTokenEntity)

        return AuthResponse(
            token = token,
            expiresIn = 3600,
            refreshToken = refreshToken,
            user = mapToApiUser(user)
        )
    }

    private fun mapToApiUser(user: User): ApiUser {
        return ApiUser(
            id = UUID.randomUUID(),
            username = user.username,
            email = user.email,
            firstName = user.firstName,
            lastName = user.lastName,
            avatarUrl = user.avatarUrl?.let { java.net.URI(it) },
            registrationDate = user.registrationDate,
            accountType = ApiUser.AccountType.entries.find { it.value == user.accountType } ?: ApiUser.AccountType.investor
        )
    }
}