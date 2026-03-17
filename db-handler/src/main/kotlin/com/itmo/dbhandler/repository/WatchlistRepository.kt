package com.itmo.dbhandler.repository

import com.itmo.dbhandler.entity.WatchlistItem
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface WatchlistRepository : JpaRepository<WatchlistItem, Long> {
    fun findByUserIdOrderByAddedAtDesc(userId: Long): List<WatchlistItem>
    fun findByUserIdAndStockSymbol(userId: Long, stockSymbol: String): WatchlistItem?
    fun deleteByUserIdAndStockSymbol(userId: Long, stockSymbol: String)
}