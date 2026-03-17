package com.itmo.dbhandler.service

import com.itmo.dbhandler.entity.PortfolioItem as PortfolioItemEntity
import com.itmo.dbhandler.entity.Stock as StockEntity
import com.itmo.dbhandler.model.*
import com.itmo.dbhandler.repository.PortfolioItemRepository
import com.itmo.dbhandler.repository.StockRepository
import com.itmo.dbhandler.util.SecurityUtils
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.OffsetDateTime

@Service
class PortfolioService(
    private val portfolioItemRepository: PortfolioItemRepository,
    private val stockRepository: StockRepository
) {

    fun getPortfolio(): List<PortfolioItem> {
        val userId = SecurityUtils.getCurrentUserId()
        val items = portfolioItemRepository.findByUserId(userId)

        return items.map { item ->
            val stock = stockRepository.findById(item.stockSymbol)
                .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Stock not found") }

            val currentPrice = stock.currentPrice
            val totalValue = currentPrice.multiply(BigDecimal(item.quantity))
            val investedValue = item.averageBuyPrice.multiply(BigDecimal(item.quantity))
            val profitLoss = totalValue.subtract(investedValue)
            val profitLossPercent = if (investedValue.compareTo(BigDecimal.ZERO) > 0) {
                profitLoss.multiply(BigDecimal(100)).divide(investedValue, 2, RoundingMode.HALF_UP)
            } else {
                BigDecimal.ZERO
            }

            PortfolioItem(
                stock = mapToApiStock(stock),
                quantity = item.quantity,
                averageBuyPrice = item.averageBuyPrice.toDouble(),
                totalValue = totalValue.toDouble(),
                profitLoss = profitLoss.toDouble(),
                profitLossPercent = profitLossPercent.toDouble()
            )
        }
    }

    fun getPortfolioValue(): PortfolioValueGet200Response {
        val userId = SecurityUtils.getCurrentUserId()

        val totalInvested = portfolioItemRepository.getTotalInvestedValue(userId) ?: BigDecimal.ZERO
        val currentTotal = portfolioItemRepository.getCurrentTotalValue(userId) ?: BigDecimal.ZERO
        val change = currentTotal.subtract(totalInvested)
        val changePercent = if (totalInvested.compareTo(BigDecimal.ZERO) > 0) {
            change.multiply(BigDecimal(100)).divide(totalInvested, 2, RoundingMode.HALF_UP)
        } else {
            BigDecimal.ZERO
        }

        return PortfolioValueGet200Response(
            totalValue = currentTotal,
            change = change,
            changePercent = changePercent,
            currency = "USD",
            lastUpdated = OffsetDateTime.now()
        )
    }

    fun getHolding(symbol: String): PortfolioItem {
        val userId = SecurityUtils.getCurrentUserId()
        val item = portfolioItemRepository.findByUserIdAndStockSymbol(userId, symbol)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Position not found")

        val stock = stockRepository.findById(symbol)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Stock not found") }

        val currentPrice = stock.currentPrice
        val totalValue = currentPrice.multiply(BigDecimal(item.quantity))
        val investedValue = item.averageBuyPrice.multiply(BigDecimal(item.quantity))
        val profitLoss = totalValue.subtract(investedValue)
        val profitLossPercent = if (investedValue.compareTo(BigDecimal.ZERO) > 0) {
            profitLoss.multiply(BigDecimal(100)).divide(investedValue, 2, RoundingMode.HALF_UP)
        } else {
            BigDecimal.ZERO
        }

        return PortfolioItem(
            stock = mapToApiStock(stock),
            quantity = item.quantity,
            averageBuyPrice = item.averageBuyPrice.toDouble(),
            totalValue = totalValue.toDouble(),
            profitLoss = profitLoss.toDouble(),
            profitLossPercent = profitLossPercent.toDouble()
        )
    }

    @Transactional
    fun addToPortfolio(userId: Long, symbol: String, quantity: Int, price: BigDecimal) {
        val existingItem = portfolioItemRepository.findByUserIdAndStockSymbol(userId, symbol)

        if (existingItem != null) {
            val totalQuantity = existingItem.quantity + quantity
            val totalCost = existingItem.averageBuyPrice.multiply(BigDecimal(existingItem.quantity))
                .add(price.multiply(BigDecimal(quantity)))
            val newAveragePrice = totalCost.divide(BigDecimal(totalQuantity), 4, RoundingMode.HALF_UP)

            existingItem.quantity = totalQuantity
            existingItem.averageBuyPrice = newAveragePrice
            existingItem.updatedAt = OffsetDateTime.now()
            portfolioItemRepository.save(existingItem)
        } else {
            val newItem = PortfolioItemEntity(
                userId = userId,
                stockSymbol = symbol,
                quantity = quantity,
                averageBuyPrice = price
            )
            portfolioItemRepository.save(newItem)
        }
    }

    @Transactional
    fun removeFromPortfolio(userId: Long, symbol: String, quantity: Int, price: BigDecimal) {
        val item = portfolioItemRepository.findByUserIdAndStockSymbol(userId, symbol)
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Position not found")

        if (item.quantity < quantity) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Not enough shares to sell")
        }

        if (item.quantity == quantity) {
            portfolioItemRepository.delete(item)
        } else {
            item.quantity -= quantity
            item.updatedAt = OffsetDateTime.now()
            portfolioItemRepository.save(item)
        }
    }

    fun calculateProfitLoss(userId: Long): PortfolioSummary {
        val totalInvested = portfolioItemRepository.getTotalInvestedValue(userId) ?: BigDecimal.ZERO
        val currentTotal = portfolioItemRepository.getCurrentTotalValue(userId) ?: BigDecimal.ZERO
        val profitLoss = currentTotal.subtract(totalInvested)
        val profitLossPercent = if (totalInvested.compareTo(BigDecimal.ZERO) > 0) {
            profitLoss.multiply(BigDecimal(100)).divide(totalInvested, 2, RoundingMode.HALF_UP)
        } else {
            BigDecimal.ZERO
        }

        return PortfolioSummary(profitLoss, profitLossPercent)
    }

    private fun mapToApiStock(stock: StockEntity): Stock {
        return Stock(
            symbol = stock.symbol,
            name = stock.name,
            price = stock.currentPrice.toDouble(),
            changePercent = stock.changePercent.toDouble(),
            volume = stock.volume?.toInt(),
            marketCap = stock.marketCap?.toDouble()
        )
    }
}

data class PortfolioSummary(
    val totalProfitLoss: BigDecimal,
    val totalProfitLossPercent: BigDecimal
)
