package com.itmo.dbhandler.repository

import com.itmo.dbhandler.entity.PriceHistory
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime

@Repository
interface PriceHistoryRepository : JpaRepository<PriceHistory, Long> {
    fun findByStockSymbolAndCandleIntervalAndTimeFromBetweenOrderByTimeFromAsc(
        stockSymbol: String,
        candleInterval: String,
        from: OffsetDateTime,
        to: OffsetDateTime
    ): List<PriceHistory>

    @Query("SELECT MAX(p.timeFrom) FROM PriceHistory p WHERE p.stockSymbol = :symbol AND p.candleInterval = :interval")
    fun findLatestTimestamp(@Param("symbol") symbol: String, @Param("interval") interval: String): OffsetDateTime?
}