package com.itmo.dbhandler.service

import com.itmo.dbhandler.entity.PriceHistory
import com.itmo.dbhandler.entity.Stock
import com.itmo.dbhandler.messaging.QuoteEvent
import com.itmo.dbhandler.repository.PriceHistoryRepository
import com.itmo.dbhandler.repository.StockRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.Optional

class QuoteServiceTest {

    private val stockRepository = mockk<StockRepository>(relaxed = true)
    private val priceHistoryRepository = mockk<PriceHistoryRepository>(relaxed = true)

    private val service = QuoteService(stockRepository, priceHistoryRepository)

    @Test
    fun `processQuote ignores unknown ticker`() {
        every { stockRepository.findById("ZZZ") } returns Optional.empty()

        service.processQuote(QuoteEvent("ZZZ", 10f, OffsetDateTime.now().toString()))

        verify(exactly = 0) { stockRepository.save(any()) }
        verify(exactly = 0) { priceHistoryRepository.save(any()) }
    }

    @Test
    fun `processQuote updates stock price and writes new candle`() {
        val stock = Stock(
            symbol = "AAPL",
            name = "Apple Inc.",
            sector = "Technology",
            currentPrice = BigDecimal("100.0000"),
            changePercent = BigDecimal.ZERO
        )
        every { stockRepository.findById("AAPL") } returns Optional.of(stock)
        every {
            priceHistoryRepository.findByStockSymbolAndCandleIntervalAndTimeFromBetweenOrderByTimeFromAsc(
                any(), any(), any(), any()
            )
        } returns emptyList()

        service.processQuote(QuoteEvent("aapl", 110f, OffsetDateTime.now().toString()))

        val stockSlot = slot<Stock>()
        verify { stockRepository.save(capture(stockSlot)) }
        assertEquals(0, BigDecimal("110.0000").compareTo(stockSlot.captured.currentPrice))
        assertTrue(stockSlot.captured.changePercent.signum() > 0)

        val candleSlot = slot<PriceHistory>()
        verify { priceHistoryRepository.save(capture(candleSlot)) }
        assertEquals("AAPL", candleSlot.captured.stockSymbol)
        assertEquals(1L, candleSlot.captured.volume)
        assertEquals(candleSlot.captured.openPrice, candleSlot.captured.closePrice)
    }

    @Test
    fun `processQuote updates existing candle high low close and volume`() {
        val stock = Stock(
            symbol = "AAPL",
            name = "Apple Inc.",
            sector = "Technology",
            currentPrice = BigDecimal("100.0000"),
            changePercent = BigDecimal.ZERO
        )
        val existingCandle = PriceHistory(
            id = 1L,
            stockSymbol = "AAPL",
            candleInterval = "1m",
            timeFrom = OffsetDateTime.now().withSecond(0).withNano(0),
            openPrice = BigDecimal("100.0000"),
            highPrice = BigDecimal("105.0000"),
            lowPrice = BigDecimal("99.0000"),
            closePrice = BigDecimal("103.0000"),
            volume = 4L
        )
        every { stockRepository.findById("AAPL") } returns Optional.of(stock)
        every {
            priceHistoryRepository.findByStockSymbolAndCandleIntervalAndTimeFromBetweenOrderByTimeFromAsc(
                any(), any(), any(), any()
            )
        } returns listOf(existingCandle)

        service.processQuote(QuoteEvent("AAPL", 110f, OffsetDateTime.now().toString()))

        val candleSlot = slot<PriceHistory>()
        verify { priceHistoryRepository.save(capture(candleSlot)) }
        assertEquals(0, BigDecimal("110.0000").compareTo(candleSlot.captured.highPrice))
        assertEquals(0, BigDecimal("99.0000").compareTo(candleSlot.captured.lowPrice))
        assertEquals(0, BigDecimal("110.0000").compareTo(candleSlot.captured.closePrice))
        assertEquals(5L, candleSlot.captured.volume)
    }

    @Test
    fun `processQuote handles zero old price without divide by zero`() {
        val stock = Stock(
            symbol = "NEW",
            name = "New Co.",
            currentPrice = BigDecimal.ZERO,
            changePercent = BigDecimal.ZERO
        )
        every { stockRepository.findById("NEW") } returns Optional.of(stock)
        every {
            priceHistoryRepository.findByStockSymbolAndCandleIntervalAndTimeFromBetweenOrderByTimeFromAsc(
                any(), any(), any(), any()
            )
        } returns emptyList()

        service.processQuote(QuoteEvent("NEW", 50f, OffsetDateTime.now().toString()))

        val stockSlot = slot<Stock>()
        verify { stockRepository.save(capture(stockSlot)) }
        assertEquals(BigDecimal.ZERO, stockSlot.captured.changePercent)
    }

    @Test
    fun `QuoteEvent falls back to now on bad timestamp`() {
        val event = QuoteEvent("AAPL", 1f, "not-a-date")
        val result = event.getTimestampAsOffsetDateTime()
        assertTrue(result.isAfter(OffsetDateTime.now().minusMinutes(1)))
    }
}
