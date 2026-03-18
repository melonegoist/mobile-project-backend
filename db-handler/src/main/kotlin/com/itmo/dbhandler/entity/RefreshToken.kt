package com.itmo.dbhandler.entity

import jakarta.persistence.*
import java.time.OffsetDateTime

@Entity
@Table(name = "refresh_tokens")
data class RefreshToken(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Column(unique = true, nullable = false)
    var token: String,

    @Column(name = "expiry_date", nullable = false)
    var expiryDate: OffsetDateTime,

    @Column(name = "created_at")
    var createdAt: OffsetDateTime = OffsetDateTime.now()
)