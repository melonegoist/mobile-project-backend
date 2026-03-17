package com.itmo.dbhandler.entity

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.math.BigDecimal
import java.time.Duration
import java.time.OffsetDateTime

@Entity
@Table(name = "user_stats")
data class UserStats(
    @Id
    @Column(name = "user_id")
    val userId: Long = 0,

    @Column(name = "total_trades")
    var totalTrades: Int = 0,

    @Column(name = "successful_trades")
    var successfulTrades: Int = 0,

    @Column(name = "win_rate")
    var winRate: BigDecimal = BigDecimal.ZERO,

    @Column(name = "total_volume")
    var totalVolume: BigDecimal = BigDecimal.ZERO,

    @Column(name = "best_trade")
    var bestTrade: String? = null,

    @Column(name = "worst_trade")
    var worstTrade: String? = null,

    @Column(name = "average_holding_time")
    @JdbcTypeCode(SqlTypes.INTERVAL_SECOND)
    var averageHoldingTime: Duration? = null,

    @Column(name = "last_updated")
    var lastUpdated: OffsetDateTime = OffsetDateTime.now()
)