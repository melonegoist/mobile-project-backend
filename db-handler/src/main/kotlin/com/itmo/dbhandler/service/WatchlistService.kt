package com.itmo.dbhandler.service

import com.itmo.dbhandler.entity.WatchlistItem
import com.itmo.dbhandler.model.Stock
import com.itmo.dbhandler.model.WatchlistPostRequest
import com.itmo.dbhandler.repository.StockRepository
import com.itmo.dbhandler.repository.WatchlistRepository
import com.itmo.dbhandler.util.SecurityUtils
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

@Service
class WatchlistService(
    private val watchlistRepository: WatchlistRepository,
    private val stockRepository: StockRepository,
    private val marketService: MarketService
) {

    fun getWatchlist(): List<Stock> {
        val userId = SecurityUtils.getCurrentUserId()
        val watchlistItems = watchlistRepository.findByUserIdOrderByAddedAtDesc(userId)

        return watchlistItems.map { item ->
            val stock = stockRepository.findById(item.stockSymbol)
                .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Stock not found") }
            marketService.mapToApiStock(stock)
        }
    }

    @Transactional
    fun addToWatchlist(request: WatchlistPostRequest) {
        val userId = SecurityUtils.getCurrentUserId()
        val symbol = request.symbol.uppercase()

        if (!stockRepository.existsById(symbol)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Stock not found")
        }

        val existing = watchlistRepository.findByUserIdAndStockSymbol(userId, symbol)
        if (existing != null) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Stock already in watchlist")
        }

        val watchlistItem = WatchlistItem(
            userId = userId,
            stockSymbol = symbol
        )
        watchlistRepository.save(watchlistItem)
    }

    @Transactional
    fun removeFromWatchlist(symbol: String) {
        val userId = SecurityUtils.getCurrentUserId()
        val item = watchlistRepository.findByUserIdAndStockSymbol(userId, symbol.uppercase())
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Item not found in watchlist")

        watchlistRepository.delete(item)
    }
}