package com.itmo.dbhandler.entity

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.OffsetDateTime

@Entity
@Table(name = "trades")
data class Trade(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Column(name = "stock_symbol", nullable = false)
    val stockSymbol: String,

    @Column(name = "trade_type", nullable = false)
    var tradeType: String,

    @Column(nullable = false)
    var quantity: Int,

    @Column(nullable = false)
    var price: BigDecimal,

    @Column(name = "total_amount", insertable = false, updatable = false)
    var totalAmount: BigDecimal = BigDecimal.ZERO,

    @Column(name = "order_type")
    var orderType: String = "market",

    @Column(name = "limit_price")
    var limitPrice: BigDecimal? = null,

    @Column(nullable = false)
    var status: String = "pending",

    var message: String? = null,

    @Column(name = "trade_date")
    var tradeDate: OffsetDateTime = OffsetDateTime.now(),

    @Column(name = "created_at")
    var createdAt: OffsetDateTime = OffsetDateTime.now()
)