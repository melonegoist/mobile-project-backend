package com.itmo.dbhandler.entity

import jakarta.persistence.*
import java.time.OffsetDateTime

@Entity
@Table(name = "watchlist", uniqueConstraints = [UniqueConstraint(columnNames = ["user_id", "stock_symbol"])])
data class WatchlistItem(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Column(name = "stock_symbol", nullable = false)
    val stockSymbol: String,

    @Column(name = "added_at")
    var addedAt: OffsetDateTime = OffsetDateTime.now()
)