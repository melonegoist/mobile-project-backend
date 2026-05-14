package com.itmo.dbhandler.service

import com.itmo.dbhandler.entity.Stock
import com.itmo.dbhandler.entity.WatchlistItem
import com.itmo.dbhandler.model.WatchlistPostRequest
import com.itmo.dbhandler.repository.StockRepository
import com.itmo.dbhandler.repository.WatchlistRepository
import com.itmo.dbhandler.testsupport.SecurityContextTestSupport
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.math.BigDecimal
import java.util.Optional

class WatchlistServiceTest {

    private val watchlistRepository = mockk<WatchlistRepository>(relaxed = true)
    private val stockRepository = mockk<StockRepository>()
    private val marketService = mockk<MarketService>()

    private val service = WatchlistService(watchlistRepository, stockRepository, marketService)

    private val userId = 99L

    @BeforeEach
    fun setUp() {
        SecurityContextTestSupport.authenticate(userId)
    }

    @AfterEach
    fun tearDown() {
        SecurityContextTestSupport.clear()
    }

    private fun apple() = Stock(
        symbol = "AAPL",
        name = "Apple Inc.",
        sector = "Technology",
        currentPrice = BigDecimal("150.00"),
        changePercent = BigDecimal.ZERO
    )

    @Test
    fun `addToWatchlist saves new item with uppercased symbol`() {
        every { stockRepository.existsById("AAPL") } returns true
        every { watchlistRepository.findByUserIdAndStockSymbol(userId, "AAPL") } returns null

        service.addToWatchlist(WatchlistPostRequest(symbol = "aapl"))

        val saved = slot<WatchlistItem>()
        verify { watchlistRepository.save(capture(saved)) }
        assertEquals("AAPL", saved.captured.stockSymbol)
        assertEquals(userId, saved.captured.userId)
    }

    @Test
    fun `addToWatchlist throws 404 when stock unknown`() {
        every { stockRepository.existsById("ZZZ") } returns false

        val ex = assertThrows(ResponseStatusException::class.java) {
            service.addToWatchlist(WatchlistPostRequest(symbol = "zzz"))
        }
        assertEquals(HttpStatus.NOT_FOUND, ex.statusCode)
    }

    @Test
    fun `addToWatchlist rejects duplicates`() {
        every { stockRepository.existsById("AAPL") } returns true
        every { watchlistRepository.findByUserIdAndStockSymbol(userId, "AAPL") } returns
            WatchlistItem(userId = userId, stockSymbol = "AAPL")

        val ex = assertThrows(ResponseStatusException::class.java) {
            service.addToWatchlist(WatchlistPostRequest(symbol = "aapl"))
        }
        assertEquals(HttpStatus.CONFLICT, ex.statusCode)
    }

    @Test
    fun `removeFromWatchlist deletes existing item`() {
        val item = WatchlistItem(id = 1, userId = userId, stockSymbol = "AAPL")
        every { watchlistRepository.findByUserIdAndStockSymbol(userId, "AAPL") } returns item

        service.removeFromWatchlist("aapl")

        verify { watchlistRepository.delete(item) }
    }

    @Test
    fun `removeFromWatchlist throws 404 when missing`() {
        every { watchlistRepository.findByUserIdAndStockSymbol(userId, "ZZZ") } returns null

        val ex = assertThrows(ResponseStatusException::class.java) {
            service.removeFromWatchlist("ZZZ")
        }
        assertEquals(HttpStatus.NOT_FOUND, ex.statusCode)
    }

    @Test
    fun `getWatchlist returns mapped stocks`() {
        val item = WatchlistItem(id = 1, userId = userId, stockSymbol = "AAPL")
        every { watchlistRepository.findByUserIdOrderByAddedAtDesc(userId) } returns listOf(item)
        every { stockRepository.findById("AAPL") } returns Optional.of(apple())
        every { marketService.mapToApiStock(any()) } returns
            com.itmo.dbhandler.model.Stock(
                symbol = "AAPL",
                name = "Apple Inc.",
                price = 150.0,
                changePercent = 0.0
            )

        val result = service.getWatchlist()

        assertEquals(1, result.size)
        assertEquals("AAPL", result.first().symbol)
    }
}
