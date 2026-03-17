package com.itmo.dbhandler.entity

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.OffsetDateTime

@Entity
@Table(name = "price_history", uniqueConstraints = [UniqueConstraint(columnNames = ["stock_symbol", "candle_interval", "time_from"])])
data class PriceHistory(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "stock_symbol", nullable = false)
    val stockSymbol: String,

    @Column(name = "candle_interval", nullable = false)
    val candleInterval: String,

    @Column(name = "time_from", nullable = false)
    val timeFrom: OffsetDateTime,

    @Column(name = "open_price", nullable = false)
    var openPrice: BigDecimal,

    @Column(name = "high_price", nullable = false)
    var highPrice: BigDecimal,

    @Column(name = "low_price", nullable = false)
    var lowPrice: BigDecimal,

    @Column(name = "close_price", nullable = false)
    var closePrice: BigDecimal,

    @Column(nullable = false)
    var volume: Long,

    @Column(name = "created_at")
    var createdAt: OffsetDateTime = OffsetDateTime.now()
)