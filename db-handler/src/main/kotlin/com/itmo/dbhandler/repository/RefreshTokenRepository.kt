package com.itmo.dbhandler.repository

import com.itmo.dbhandler.entity.RefreshToken
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime

@Repository
interface RefreshTokenRepository : JpaRepository<RefreshToken, Long> {
    fun findByToken(token: String): RefreshToken?
    fun deleteByUserId(userId: Long)
    fun deleteAllByExpiryDateBefore(now: OffsetDateTime)
}