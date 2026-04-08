package com.itmo.dbhandler.service

import com.itmo.dbhandler.messaging.QuoteEvent
import com.itmo.dbhandler.repository.PriceHistoryRepository
import com.itmo.dbhandler.repository.StockRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.OffsetDateTime

@Service
class QuoteService(
    private val stockRepository: StockRepository,
    private val priceHistoryRepository: PriceHistoryRepository
) {

    private val log = LoggerFactory.getLogger(QuoteService::class.java)

    @Transactional
    fun processQuote(quote: QuoteEvent) {
        val symbol = quote.ticker.uppercase()

        val stock = stockRepository.findById(symbol).orElse(null)
        if (stock == null) {
            log.warn("Stock not found for ticker: {}", symbol)
            return
        }

        val oldPrice = stock.currentPrice
        val newPrice = BigDecimal(quote.price.toDouble()).setScale(4, RoundingMode.HALF_UP)

        stock.currentPrice = newPrice
        stock.changePercent = calculateChangePercent(oldPrice, newPrice)
        stock.lastUpdated = OffsetDateTime.now()

        if (stock.volume == null) {
            stock.volume = 0L
        }

        stockRepository.save(stock)
        log.info("Updated stock {} price: {} -> {} ({}%)", symbol, oldPrice, newPrice, stock.changePercent)

        savePriceHistory(symbol, newPrice, quote.getTimestampAsOffsetDateTime())
    }

    private fun calculateChangePercent(oldPrice: BigDecimal, newPrice: BigDecimal): BigDecimal {
        if (oldPrice.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO
        }
        val change = newPrice.subtract(oldPrice)
        val percent = change.multiply(BigDecimal(100)).divide(oldPrice, 2, RoundingMode.HALF_UP)
        return percent
    }

    private fun savePriceHistory(symbol: String, price: BigDecimal, timestamp: OffsetDateTime) {
        val interval = "1m"
        val roundedTimestamp = timestamp.withSecond(0).withNano(0)

        val existingHistory = priceHistoryRepository.findByStockSymbolAndCandleIntervalAndTimeFromBetweenOrderByTimeFromAsc(
            stockSymbol = symbol,
            candleInterval = interval,
            from = roundedTimestamp,
            to = roundedTimestamp.plusMinutes(1)
        )

        if (existingHistory.isNotEmpty()) {
            val history = existingHistory.first()
            history.highPrice = if (price > history.highPrice) price else history.highPrice
            history.lowPrice = if (price < history.lowPrice) price else history.lowPrice
            history.closePrice = price
            history.volume = history.volume + 1
            priceHistoryRepository.save(history)
        } else {
            val priceHistory = com.itmo.dbhandler.entity.PriceHistory(
                stockSymbol = symbol,
                candleInterval = interval,
                timeFrom = roundedTimestamp,
                openPrice = price,
                highPrice = price,
                lowPrice = price,
                closePrice = price,
                volume = 1L
            )
            priceHistoryRepository.save(priceHistory)
        }
    }
}