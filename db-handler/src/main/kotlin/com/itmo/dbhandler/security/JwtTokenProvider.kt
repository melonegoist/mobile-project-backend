package com.itmo.dbhandler.security

import com.itmo.dbhandler.service.UserDetailsServiceImpl
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.SignatureAlgorithm
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.stereotype.Component
import java.security.Key
import java.util.*

@Component
class JwtTokenProvider(
    private val userDetailsService: UserDetailsServiceImpl
) {
    @Value("\${jwt.secret:defaultSecretKeyForJWTGeneration12345678901234567890}")
    private lateinit var secretKey: String

    @Value("\${jwt.expiration:3600000}")
    private var expirationInMs: Long = 3600000

    @Value("\${jwt.refreshExpiration:86400000}")
    private var refreshExpirationInMs: Long = 86400000

    private val key: Key
        get() = Keys.hmacShaKeyFor(secretKey.toByteArray())

    fun generateToken(userId: Long, username: String): String {
        val claims = Jwts.claims().setSubject(username)
        claims["userId"] = userId

        return Jwts.builder()
            .setClaims(claims)
            .setIssuedAt(Date())
            .setExpiration(Date(System.currentTimeMillis() + expirationInMs))
            .signWith(key, SignatureAlgorithm.HS256)
            .compact()
    }

    fun generateRefreshToken(userId: Long, username: String): String {
        val claims = Jwts.claims().setSubject(username)
        claims["userId"] = userId
        claims["tokenType"] = "refresh"

        return Jwts.builder()
            .setClaims(claims)
            .setIssuedAt(Date())
            .setExpiration(Date(System.currentTimeMillis() + refreshExpirationInMs))
            .signWith(key, SignatureAlgorithm.HS256)
            .compact()
    }

    fun getUsernameFromToken(token: String): String {
        return Jwts.parserBuilder()
            .setSigningKey(key)
            .build()
            .parseClaimsJws(token)
            .body
            .subject
    }

    fun getUserIdFromToken(token: String): Long {
        return Jwts.parserBuilder()
            .setSigningKey(key)
            .build()
            .parseClaimsJws(token)
            .body
            .get("userId", Long::class.javaObjectType)
    }

    fun validateToken(token: String): Boolean {
        return try {
            val claims = Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
            !claims.body.expiration.before(Date())
        } catch (e: Exception) {
            println(e)
            false
        }
    }

    fun getAuthentication(token: String): Authentication {
        val username = getUsernameFromToken(token)
        val userId = getUserIdFromToken(token)
        val userDetails: UserDetails = userDetailsService.loadUserByUsername(username)
        val authentication = UsernamePasswordAuthenticationToken(userDetails, "", userDetails.authorities)
        authentication.details = userId
        return authentication
    }
}