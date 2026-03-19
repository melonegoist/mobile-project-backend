package com.itmo.dbhandler.entity

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.OffsetDateTime

@Entity
@Table(name = "portfolio_items", uniqueConstraints = [UniqueConstraint(columnNames = ["user_id", "stock_symbol"])])
data class PortfolioItem(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Column(name = "stock_symbol", nullable = false)
    var stockSymbol: String,

    @Column(nullable = false)
    var quantity: Int,

    @Column(name = "average_buy_price", nullable = false)
    var averageBuyPrice: BigDecimal,

    @Column(name = "created_at")
    var createdAt: OffsetDateTime = OffsetDateTime.now(),

    @Column(name = "updated_at")
    var updatedAt: OffsetDateTime = OffsetDateTime.now()
)