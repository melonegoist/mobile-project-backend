package com.itmo.dbhandler.service

import com.itmo.dbhandler.entity.Account
import com.itmo.dbhandler.entity.RefreshToken
import com.itmo.dbhandler.entity.User
import com.itmo.dbhandler.model.LoginRequest
import com.itmo.dbhandler.model.RefreshTokenRequest
import com.itmo.dbhandler.model.RegisterRequest
import com.itmo.dbhandler.repository.AccountRepository
import com.itmo.dbhandler.repository.RefreshTokenRepository
import com.itmo.dbhandler.repository.UserRepository
import com.itmo.dbhandler.security.JwtTokenProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.web.server.ResponseStatusException
import java.time.OffsetDateTime
import java.util.Optional

class AuthServiceTest {

    private val userRepository = mockk<UserRepository>(relaxed = true)
    private val accountRepository = mockk<AccountRepository>(relaxed = true)
    private val refreshTokenRepository = mockk<RefreshTokenRepository>(relaxed = true)
    private val passwordEncoder = mockk<PasswordEncoder>()
    private val jwtTokenProvider = mockk<JwtTokenProvider>()

    private val service = AuthService(
        userRepository,
        accountRepository,
        refreshTokenRepository,
        passwordEncoder,
        jwtTokenProvider
    )

    private fun newUser(id: Long = 1L, username: String = "alice") = User(
        id = id,
        username = username,
        email = "$username@example.com",
        passwordHash = "hashed",
        firstName = "Alice",
        lastName = "Smith"
    )

    @Test
    fun `register creates user, account and returns auth tokens`() {
        val request = RegisterRequest(
            username = "alice",
            email = "alice@example.com",
            password = "secret123",
            firstName = "Alice",
            lastName = "Smith"
        )
        every { userRepository.existsByUsername("alice") } returns false
        every { userRepository.existsByEmail("alice@example.com") } returns false
        every { passwordEncoder.encode("secret123") } returns "hashed"
        val savedUser = newUser()
        every { userRepository.save(any<User>()) } returns savedUser
        every { jwtTokenProvider.generateToken(1L, "alice") } returns "access-token"
        every { jwtTokenProvider.generateRefreshToken(1L, "alice") } returns "refresh-token"

        val response = service.register(request)

        assertEquals("access-token", response.token)
        assertEquals("refresh-token", response.refreshToken)
        assertEquals(3600L, response.expiresIn)
        assertEquals("alice", response.user.username)

        val accountSlot = slot<Account>()
        verify { accountRepository.save(capture(accountSlot)) }
        assertEquals(1L, accountSlot.captured.userId)
        assertEquals(0, accountSlot.captured.balance.signum())
        assertEquals("USD", accountSlot.captured.currency)

        verify { refreshTokenRepository.save(any<RefreshToken>()) }
    }

    @Test
    fun `register rejects duplicate username`() {
        val request = RegisterRequest("alice", "alice@example.com", "secret123", null, null)
        every { userRepository.existsByUsername("alice") } returns true

        val ex = assertThrows(ResponseStatusException::class.java) { service.register(request) }
        assertEquals(HttpStatus.CONFLICT, ex.statusCode)
    }

    @Test
    fun `register rejects duplicate email`() {
        val request = RegisterRequest("alice", "alice@example.com", "secret123", null, null)
        every { userRepository.existsByUsername("alice") } returns false
        every { userRepository.existsByEmail("alice@example.com") } returns true

        val ex = assertThrows(ResponseStatusException::class.java) { service.register(request) }
        assertEquals(HttpStatus.CONFLICT, ex.statusCode)
    }

    @Test
    fun `login returns tokens for valid credentials`() {
        val user = newUser()
        every { userRepository.findByUsername("alice") } returns user
        every { passwordEncoder.matches("secret123", "hashed") } returns true
        every { jwtTokenProvider.generateToken(1L, "alice") } returns "access"
        every { jwtTokenProvider.generateRefreshToken(1L, "alice") } returns "refresh"

        val response = service.login(LoginRequest("alice", "secret123"))

        assertEquals("access", response.token)
        assertEquals("refresh", response.refreshToken)
        assertNotNull(response.user)
    }

    @Test
    fun `login throws 401 when user not found`() {
        every { userRepository.findByUsername("ghost") } returns null

        val ex = assertThrows(ResponseStatusException::class.java) {
            service.login(LoginRequest("ghost", "any"))
        }
        assertEquals(HttpStatus.UNAUTHORIZED, ex.statusCode)
    }

    @Test
    fun `login throws 401 when password mismatch`() {
        every { userRepository.findByUsername("alice") } returns newUser()
        every { passwordEncoder.matches("wrong", "hashed") } returns false

        val ex = assertThrows(ResponseStatusException::class.java) {
            service.login(LoginRequest("alice", "wrong"))
        }
        assertEquals(HttpStatus.UNAUTHORIZED, ex.statusCode)
    }

    @Test
    fun `refresh rotates token`() {
        val user = newUser()
        val storedToken = RefreshToken(
            userId = 1L,
            token = "old-refresh",
            expiryDate = OffsetDateTime.now().plusHours(1)
        )
        every { refreshTokenRepository.findByToken("old-refresh") } returns storedToken
        every { userRepository.findById(1L) } returns Optional.of(user)
        every { jwtTokenProvider.generateToken(1L, "alice") } returns "new-access"
        every { jwtTokenProvider.generateRefreshToken(1L, "alice") } returns "new-refresh"

        val response = service.refresh(RefreshTokenRequest("old-refresh"))

        verify { refreshTokenRepository.delete(storedToken) }
        assertEquals("new-access", response.token)
        assertEquals("new-refresh", response.refreshToken)
    }

    @Test
    fun `refresh rejects expired token and deletes it`() {
        val expired = RefreshToken(
            userId = 1L,
            token = "old",
            expiryDate = OffsetDateTime.now().minusMinutes(1)
        )
        every { refreshTokenRepository.findByToken("old") } returns expired

        val ex = assertThrows(ResponseStatusException::class.java) {
            service.refresh(RefreshTokenRequest("old"))
        }
        assertEquals(HttpStatus.UNAUTHORIZED, ex.statusCode)
        verify { refreshTokenRepository.delete(expired) }
    }

    @Test
    fun `refresh rejects unknown token`() {
        every { refreshTokenRepository.findByToken("ghost") } returns null

        val ex = assertThrows(ResponseStatusException::class.java) {
            service.refresh(RefreshTokenRequest("ghost"))
        }
        assertEquals(HttpStatus.UNAUTHORIZED, ex.statusCode)
    }

    @Test
    fun `logout deletes all refresh tokens for user`() {
        every { jwtTokenProvider.getUserIdFromToken("access-token") } returns 7L

        service.logout("access-token")

        verify { refreshTokenRepository.deleteByUserId(7L) }
    }
}
