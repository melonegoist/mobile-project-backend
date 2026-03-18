package com.itmo.dbhandler.repository

import com.itmo.dbhandler.entity.Trade
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime

@Repository
interface TradeRepository : JpaRepository<Trade, Long> {
    fun findByUserIdOrderByTradeDateDesc(userId: Long, pageable: Pageable): Page<Trade>
    fun findByUserIdAndStockSymbol(userId: Long, stockSymbol: String, pageable: Pageable): Page<Trade>
    fun findByUserIdAndTradeType(userId: Long, tradeType: String, pageable: Pageable): Page<Trade>
    fun findByUserIdAndStockSymbolAndTradeType(userId: Long, stockSymbol: String, tradeType: String, pageable: Pageable): Page<Trade>
    fun findByUserIdAndTradeDateBetween(userId: Long, from: OffsetDateTime, to: OffsetDateTime, pageable: Pageable): Page<Trade>
}