package com.itmo.dbhandler.service

import com.itmo.dbhandler.entity.Stock as StockEntity
import com.itmo.dbhandler.model.*
import com.itmo.dbhandler.repository.PriceHistoryRepository
import com.itmo.dbhandler.repository.StockRepository
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset

@Service
class MarketService(
    private val stockRepository: StockRepository,
    private val priceHistoryRepository: PriceHistoryRepository
) {

    fun getStocks(
        limit: Int,
        offset: Int,
        sortBy: String,
        order: String,
        sector: String?
    ): MarketStocksGet200Response {
        val pageable = when (order.lowercase()) {
            "asc" -> PageRequest.of(offset / limit, limit, Sort.by(sortBy).ascending())
            "desc" -> PageRequest.of(offset / limit, limit, Sort.by(sortBy).descending())
            else -> PageRequest.of(offset / limit, limit, Sort.by(sortBy).ascending())
        }

        val stocksPage = if (sector != null) {
            stockRepository.findBySector(sector, pageable)
        } else {
            when (sortBy) {
                "price" -> stockRepository.findAllByOrderByCurrentPriceDesc(pageable)
                "changePercent" -> stockRepository.findAllByOrderByChangePercentDesc(pageable)
                "volume" -> stockRepository.findAllByOrderByVolumeDesc(pageable)
                else -> stockRepository.findAllByOrderBySymbolAsc(pageable)
            }
        }

        val stockData = stocksPage.content.map { stock ->
            mapToApiStock(stock)
        }

        return MarketStocksGet200Response(
            data = stockData,
            page = stocksPage.number,
            propertySize = stocksPage.size,
            totalElements = stocksPage.totalElements.toInt(),
            totalPages = stocksPage.totalPages,
            hasNext = stocksPage.hasNext(),
            hasPrevious = stocksPage.hasPrevious()
        )
    }

    fun getStockBySymbol(symbol: String): Stock {
        val stock = stockRepository.findById(symbol.uppercase())
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Stock not found") }

        return mapToApiStock(stock)
    }

    fun getPriceHistory(
        symbol: String,
        interval: String,
        from: LocalDate?,
        to: LocalDate?
    ): PriceHistory {
        stockRepository.findById(symbol.uppercase())
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Stock not found") }

        val endDate = to?.atStartOfDay()?.atOffset(ZoneOffset.UTC) ?: OffsetDateTime.now()
        val startDate = from?.atStartOfDay()?.atOffset(ZoneOffset.UTC)
            ?: endDate.minusDays(30)

        val candleInterval = when (interval) {
            "1d" -> "1d"
            "1w" -> "1w"
            "1m" -> "1M"
            "3m" -> "1M"
            "1y" -> "1M"
            else -> "1d"
        }

        val historyData = priceHistoryRepository.findByStockSymbolAndCandleIntervalAndTimeFromBetweenOrderByTimeFromAsc(
            stockSymbol = symbol.uppercase(),
            candleInterval = candleInterval,
            from = startDate,
            to = endDate
        )

        val data = historyData.map { candle ->
            PriceHistoryDataInner(
                timestamp = candle.timeFrom,
                open = candle.openPrice,
                high = candle.highPrice,
                low = candle.lowPrice,
                close = candle.closePrice,
                volume = candle.volume.toInt()
            )
        }

        val apiInterval = when (candleInterval) {
            "1d" -> PriceHistory.Interval._1d
            "1w" -> PriceHistory.Interval._1w
            "1M" -> PriceHistory.Interval._1m
            else -> PriceHistory.Interval._1d
        }

        return PriceHistory(
            symbol = symbol.uppercase(),
            interval = apiInterval,
            data = data
        )
    }

    fun getMarketSummary(): MarketSummary {
        val allStocks = stockRepository.findAll()

        val gainers = allStocks
            .sortedByDescending { it.changePercent }
            .take(10)
            .map { mapToApiStock(it) }

        val losers = allStocks
            .sortedBy { it.changePercent }
            .take(10)
            .map { mapToApiStock(it) }

        val mostActive = allStocks
            .sortedByDescending { it.volume ?: 0 }
            .take(10)
            .map { mapToApiStock(it) }

        return MarketSummary(
            totalStocks = allStocks.size,
            gainers = gainers,
            losers = losers,
            mostActive = mostActive,
            marketStatus = MarketSummary.MarketStatus.open,
            lastUpdated = OffsetDateTime.now()
        )
    }

    fun mapToApiStock(stock: StockEntity): Stock {
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