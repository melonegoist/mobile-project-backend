package com.itmo.dbhandler.entity

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.OffsetDateTime

@Entity
@Table(name = "stocks")
data class Stock(
    @Id
    @Column(length = 20)
    val symbol: String,

    @Column(nullable = false)
    var name: String,

    var sector: String? = null,

    @Column(name = "current_price", nullable = false)
    var currentPrice: BigDecimal,

    @Column(name = "change_percent", nullable = false)
    var changePercent: BigDecimal,

    var volume: Long? = null,

    @Column(name = "market_cap")
    var marketCap: BigDecimal? = null,

    @Column(name = "last_updated")
    var lastUpdated: OffsetDateTime = OffsetDateTime.now(),

    @Column(name = "created_at")
    var createdAt: OffsetDateTime = OffsetDateTime.now()
)