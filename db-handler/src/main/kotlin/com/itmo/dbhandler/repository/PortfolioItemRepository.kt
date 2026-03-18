package com.itmo.dbhandler.repository

import com.itmo.dbhandler.entity.PortfolioItem
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.math.BigDecimal

@Repository
interface PortfolioItemRepository : JpaRepository<PortfolioItem, Long> {
    fun findByUserId(userId: Long): List<PortfolioItem>
    fun findByUserIdAndStockSymbol(userId: Long, stockSymbol: String): PortfolioItem?

    @Query("SELECT SUM(p.quantity * p.averageBuyPrice) FROM PortfolioItem p WHERE p.userId = :userId")
    fun getTotalInvestedValue(@Param("userId") userId: Long): BigDecimal?

    @Query("SELECT SUM(p.quantity * s.currentPrice) FROM PortfolioItem p JOIN Stock s ON p.stockSymbol = s.symbol WHERE p.userId = :userId")
    fun getCurrentTotalValue(@Param("userId") userId: Long): BigDecimal?
}