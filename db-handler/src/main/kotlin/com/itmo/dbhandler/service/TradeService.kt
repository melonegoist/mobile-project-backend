package com.itmo.dbhandler.service

import com.itmo.dbhandler.entity.Trade
import com.itmo.dbhandler.model.*
import com.itmo.dbhandler.repository.TradeRepository
import com.itmo.dbhandler.util.SecurityUtils
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.math.BigDecimal
import java.time.OffsetDateTime

@Service
class TradeService(
    private val tradeRepository: TradeRepository,
    private val portfolioService: PortfolioService,
    private val accountService: AccountService,
    private val stockRepository: com.itmo.dbhandler.repository.StockRepository,
    private val userStatsRepository: com.itmo.dbhandler.repository.UserStatsRepository
) {

    @Transactional
    fun executeTrade(request: TradeRequest): TradeResponse {
        val userId = SecurityUtils.getCurrentUserId()
        val symbol = request.symbol.uppercase()

        val stock = stockRepository.findById(symbol)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Stock not found") }

        val totalAmount = stock.currentPrice.multiply(BigDecimal(request.quantity))

        if (request.tradeType == TradeRequest.TradeType.buy) {
            val account = accountService.getAccountEntity(userId)
            if (account.balance < totalAmount) {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Insufficient funds")
            }
            account.balance = account.balance.subtract(totalAmount)
            accountService.saveAccount(account)

            portfolioService.addToPortfolio(userId, symbol, request.quantity, stock.currentPrice)
        } else {
            val holding = portfolioService.getHoldingEntity(userId, symbol)
            if (holding.quantity < request.quantity) {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Not enough shares to sell")
            }

            val account = accountService.getAccountEntity(userId)
            account.balance = account.balance.add(totalAmount)
            accountService.saveAccount(account)

            portfolioService.removeFromPortfolio(userId, symbol, request.quantity, stock.currentPrice)
        }

        val trade = Trade(
            userId = userId,
            stockSymbol = symbol,
            tradeType = request.tradeType.value,
            quantity = request.quantity,
            price = stock.currentPrice,
            orderType = request.orderType?.value ?: "market",
            limitPrice = request.limitPrice?.let { BigDecimal(it) },
            status = "completed",
            message = "Trade executed successfully"
        )

        val savedTrade = tradeRepository.save(trade)
        updateUserStats(userId, savedTrade)

        return TradeResponse(
            tradeId = savedTrade.id,
            symbol = savedTrade.stockSymbol,
            quantity = savedTrade.quantity,
            price = savedTrade.price.toDouble(),
            totalAmount = savedTrade.totalAmount.toDouble(),
            status = TradeResponse.Status.completed,
            timestamp = savedTrade.tradeDate,
            message = savedTrade.message
        )
    }

    fun getTradeHistory(
        from: OffsetDateTime?,
        to: OffsetDateTime?,
        symbol: String?,
        tradeType: String?,
        limit: Int
    ): TradesHistoryGet200Response {
        val userId = SecurityUtils.getCurrentUserId()
        val pageable = PageRequest.of(0, limit, Sort.by("tradeDate").descending())

        val tradesPage = when {
            symbol != null && tradeType != null ->
                tradeRepository.findByUserIdAndStockSymbolAndTradeType(userId, symbol.uppercase(), tradeType, pageable)
            symbol != null ->
                tradeRepository.findByUserIdAndStockSymbol(userId, symbol.uppercase(), pageable)
            tradeType != null ->
                tradeRepository.findByUserIdAndTradeType(userId, tradeType, pageable)
            from != null && to != null ->
                tradeRepository.findByUserIdAndTradeDateBetween(userId, from, to, pageable)
            else ->
                tradeRepository.findByUserIdOrderByTradeDateDesc(userId, pageable)
        }

        val tradeData = tradesPage.content.map { trade ->
            TradeResponse(
                tradeId = trade.id,
                symbol = trade.stockSymbol,
                quantity = trade.quantity,
                price = trade.price.toDouble(),
                totalAmount = trade.totalAmount.toDouble(),
                status = when (trade.status) {
                    "completed" -> TradeResponse.Status.completed
                    "pending" -> TradeResponse.Status.pending
                    "rejected" -> TradeResponse.Status.rejected
                    else -> TradeResponse.Status.completed
                },
                timestamp = trade.tradeDate,
                message = trade.message
            )
        }

        return TradesHistoryGet200Response(
            data = tradeData,
            page = tradesPage.number,
            propertySize = tradesPage.size,
            totalElements = tradesPage.totalElements.toInt(),
            totalPages = tradesPage.totalPages,
            hasNext = tradesPage.hasNext(),
            hasPrevious = tradesPage.hasPrevious()
        )
    }

    private fun updateUserStats(userId: Long, trade: Trade) {
        var stats = userStatsRepository.findByUserId(userId)
        if (stats == null) {
            stats = com.itmo.dbhandler.entity.UserStats(userId = userId)
        }

        stats.totalTrades++
        if (trade.status == "completed") {
            stats.successfulTrades++
        }
        stats.totalVolume = stats.totalVolume.add(trade.totalAmount)
        stats.winRate = if (stats.totalTrades > 0) {
            BigDecimal(stats.successfulTrades)
                .multiply(BigDecimal(100))
                .divide(BigDecimal(stats.totalTrades), 2, java.math.RoundingMode.HALF_UP)
        } else {
            BigDecimal.ZERO
        }
        stats.lastUpdated = OffsetDateTime.now()

        userStatsRepository.save(stats)
    }
}