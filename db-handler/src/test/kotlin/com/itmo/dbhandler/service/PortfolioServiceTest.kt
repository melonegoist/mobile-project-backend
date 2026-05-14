package com.itmo.dbhandler.service

import com.itmo.dbhandler.entity.PortfolioItem
import com.itmo.dbhandler.entity.Stock
import com.itmo.dbhandler.repository.PortfolioItemRepository
import com.itmo.dbhandler.repository.StockRepository
import com.itmo.dbhandler.testsupport.SecurityContextTestSupport
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.math.BigDecimal
import java.util.Optional

class PortfolioServiceTest {

    private val portfolioRepo = mockk<PortfolioItemRepository>(relaxed = true)
    private val stockRepo = mockk<StockRepository>()
    private val service = PortfolioService(portfolioRepo, stockRepo)

    private val userId = 11L

    @BeforeEach
    fun setUp() {
        SecurityContextTestSupport.authenticate(userId)
    }

    @AfterEach
    fun tearDown() {
        SecurityContextTestSupport.clear()
    }

    private fun apple(price: BigDecimal = BigDecimal("200.00")): Stock = Stock(
        symbol = "AAPL",
        name = "Apple Inc.",
        sector = "Technology",
        currentPrice = price,
        changePercent = BigDecimal.ZERO
    )

    @Test
    fun `addToPortfolio creates new position`() {
        every { portfolioRepo.findByUserIdAndStockSymbol(userId, "AAPL") } returns null

        service.addToPortfolio(userId, "AAPL", 5, BigDecimal("100.00"))

        val saved = slot<PortfolioItem>()
        verify { portfolioRepo.save(capture(saved)) }
        assertEquals(5, saved.captured.quantity)
        assertEquals(BigDecimal("100.00"), saved.captured.averageBuyPrice)
    }

    @Test
    fun `addToPortfolio averages buy price when position exists`() {
        val existing = PortfolioItem(
            userId = userId,
            stockSymbol = "AAPL",
            quantity = 10,
            averageBuyPrice = BigDecimal("100.0000")
        )
        every { portfolioRepo.findByUserIdAndStockSymbol(userId, "AAPL") } returns existing

        service.addToPortfolio(userId, "AAPL", 10, BigDecimal("200.0000"))

        val saved = slot<PortfolioItem>()
        verify { portfolioRepo.save(capture(saved)) }
        assertEquals(20, saved.captured.quantity)
        // (10*100 + 10*200) / 20 = 150
        assertEquals(BigDecimal("150.0000"), saved.captured.averageBuyPrice)
    }

    @Test
    fun `removeFromPortfolio partial sale updates quantity`() {
        val existing = PortfolioItem(
            id = 1L,
            userId = userId,
            stockSymbol = "AAPL",
            quantity = 10,
            averageBuyPrice = BigDecimal("100.00")
        )
        every { portfolioRepo.findByUserIdAndStockSymbol(userId, "AAPL") } returns existing

        service.removeFromPortfolio(userId, "AAPL", 3, BigDecimal("120.00"))

        val saved = slot<PortfolioItem>()
        verify { portfolioRepo.save(capture(saved)) }
        assertEquals(7, saved.captured.quantity)
    }

    @Test
    fun `removeFromPortfolio full sale deletes the position`() {
        val existing = PortfolioItem(
            id = 1L,
            userId = userId,
            stockSymbol = "AAPL",
            quantity = 10,
            averageBuyPrice = BigDecimal("100.00")
        )
        every { portfolioRepo.findByUserIdAndStockSymbol(userId, "AAPL") } returns existing

        service.removeFromPortfolio(userId, "AAPL", 10, BigDecimal("120.00"))

        verify { portfolioRepo.delete(existing) }
    }

    @Test
    fun `removeFromPortfolio rejects when not enough shares`() {
        val existing = PortfolioItem(
            id = 1L,
            userId = userId,
            stockSymbol = "AAPL",
            quantity = 2,
            averageBuyPrice = BigDecimal("100.00")
        )
        every { portfolioRepo.findByUserIdAndStockSymbol(userId, "AAPL") } returns existing

        val ex = assertThrows(ResponseStatusException::class.java) {
            service.removeFromPortfolio(userId, "AAPL", 5, BigDecimal("120.00"))
        }
        assertEquals(HttpStatus.BAD_REQUEST, ex.statusCode)
    }

    @Test
    fun `removeFromPortfolio throws when position missing`() {
        every { portfolioRepo.findByUserIdAndStockSymbol(userId, "ZZZ") } returns null

        val ex = assertThrows(ResponseStatusException::class.java) {
            service.removeFromPortfolio(userId, "ZZZ", 1, BigDecimal("10.00"))
        }
        assertEquals(HttpStatus.BAD_REQUEST, ex.statusCode)
    }

    @Test
    fun `getPortfolio computes profit and loss per position`() {
        val item = PortfolioItem(
            id = 1L,
            userId = userId,
            stockSymbol = "AAPL",
            quantity = 5,
            averageBuyPrice = BigDecimal("100.00")
        )
        every { portfolioRepo.findByUserId(userId) } returns listOf(item)
        every { stockRepo.findById("AAPL") } returns Optional.of(apple(BigDecimal("200.00")))

        val list = service.getPortfolio()

        assertEquals(1, list.size)
        val pos = list.first()
        // totalValue = 5 * 200 = 1000; invested = 500; pl = 500; pl% = 100
        assertEquals(1000.0, pos.totalValue)
        assertEquals(500.0, pos.profitLoss)
        assertEquals(100.0, pos.profitLossPercent)
    }

    @Test
    fun `getHolding throws 404 when not found`() {
        every { portfolioRepo.findByUserIdAndStockSymbol(userId, "ZZZ") } returns null

        val ex = assertThrows(ResponseStatusException::class.java) { service.getHolding("ZZZ") }
        assertEquals(HttpStatus.NOT_FOUND, ex.statusCode)
    }

    @Test
    fun `calculateProfitLoss handles zero invested without division by zero`() {
        every { portfolioRepo.getTotalInvestedValue(userId) } returns null
        every { portfolioRepo.getCurrentTotalValue(userId) } returns null

        val summary = service.calculateProfitLoss(userId)

        assertEquals(0, summary.totalProfitLoss.signum())
        assertEquals(0, summary.totalProfitLossPercent.signum())
    }

    @Test
    fun `calculateProfitLoss returns percent when invested gt zero`() {
        every { portfolioRepo.getTotalInvestedValue(userId) } returns BigDecimal("1000.00")
        every { portfolioRepo.getCurrentTotalValue(userId) } returns BigDecimal("1500.00")

        val summary = service.calculateProfitLoss(userId)

        assertEquals(BigDecimal("500.00"), summary.totalProfitLoss)
        assertTrue(summary.totalProfitLossPercent > BigDecimal("49.99"))
    }
}
