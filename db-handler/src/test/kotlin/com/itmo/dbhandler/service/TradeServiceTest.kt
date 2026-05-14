package com.itmo.dbhandler.service

import com.itmo.dbhandler.entity.Account
import com.itmo.dbhandler.entity.PortfolioItem
import com.itmo.dbhandler.entity.Stock
import com.itmo.dbhandler.entity.Trade
import com.itmo.dbhandler.entity.UserStats
import com.itmo.dbhandler.model.TradeRequest
import com.itmo.dbhandler.repository.StockRepository
import com.itmo.dbhandler.repository.TradeRepository
import com.itmo.dbhandler.repository.UserStatsRepository
import com.itmo.dbhandler.testsupport.SecurityContextTestSupport
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.Optional

class TradeServiceTest {

    private val tradeRepository = mockk<TradeRepository>(relaxed = true)
    private val portfolioService = mockk<PortfolioService>(relaxed = true)
    private val accountService = mockk<AccountService>(relaxed = true)
    private val stockRepository = mockk<StockRepository>()
    private val userStatsRepository = mockk<UserStatsRepository>(relaxed = true)

    private val service = TradeService(
        tradeRepository,
        portfolioService,
        accountService,
        stockRepository,
        userStatsRepository
    )

    private val userId = 42L

    @BeforeEach
    fun setUp() {
        SecurityContextTestSupport.authenticate(userId)
    }

    @AfterEach
    fun tearDown() {
        SecurityContextTestSupport.clear()
    }

    private fun apple(price: BigDecimal = BigDecimal("100.00")): Stock = Stock(
        symbol = "AAPL",
        name = "Apple Inc.",
        sector = "Technology",
        currentPrice = price,
        changePercent = BigDecimal.ZERO
    )

    private fun account(balance: BigDecimal): Account =
        Account(id = 1, userId = userId, balance = balance, currency = "USD")

    private fun savedTrade(
        type: String,
        quantity: Int,
        price: BigDecimal
    ): Trade = Trade(
        id = 100L,
        userId = userId,
        stockSymbol = "AAPL",
        tradeType = type,
        quantity = quantity,
        price = price,
        totalAmount = price.multiply(BigDecimal(quantity)),
        status = "completed",
        message = "Trade executed successfully",
        tradeDate = OffsetDateTime.now()
    )

    @Test
    fun `executeTrade buys stock, debits account and grows portfolio`() {
        val stock = apple(BigDecimal("150.00"))
        every { stockRepository.findById("AAPL") } returns Optional.of(stock)
        every { accountService.getAccountEntity(userId) } returns account(BigDecimal("1000.00"))
        every { tradeRepository.save(any<Trade>()) } answers { savedTrade("buy", 5, BigDecimal("150.00")) }
        every { userStatsRepository.findByUserId(userId) } returns null

        val response = service.executeTrade(
            TradeRequest(symbol = "AAPL", quantity = 5, tradeType = TradeRequest.TradeType.buy)
        )

        assertEquals("AAPL", response.symbol)
        assertEquals(5, response.quantity)
        assertEquals(750.0, response.totalAmount)

        val accountSlot = slot<Account>()
        verify { accountService.saveAccount(capture(accountSlot)) }
        assertEquals(BigDecimal("250.00"), accountSlot.captured.balance)

        verify { portfolioService.addToPortfolio(userId, "AAPL", 5, BigDecimal("150.00")) }

        val statsSlot = slot<UserStats>()
        verify { userStatsRepository.save(capture(statsSlot)) }
        assertEquals(1, statsSlot.captured.totalTrades)
        assertEquals(1, statsSlot.captured.successfulTrades)
        assertEquals(BigDecimal("100.00"), statsSlot.captured.winRate)
    }

    @Test
    fun `executeTrade rejects buy when insufficient funds`() {
        every { stockRepository.findById("AAPL") } returns Optional.of(apple(BigDecimal("150.00")))
        every { accountService.getAccountEntity(userId) } returns account(BigDecimal("100.00"))

        val ex = assertThrows(ResponseStatusException::class.java) {
            service.executeTrade(
                TradeRequest(symbol = "AAPL", quantity = 5, tradeType = TradeRequest.TradeType.buy)
            )
        }
        assertEquals(HttpStatus.BAD_REQUEST, ex.statusCode)
    }

    @Test
    fun `executeTrade throws 404 when stock not found`() {
        every { stockRepository.findById("ZZZ") } returns Optional.empty()

        val ex = assertThrows(ResponseStatusException::class.java) {
            service.executeTrade(
                TradeRequest(symbol = "ZZZ", quantity = 1, tradeType = TradeRequest.TradeType.buy)
            )
        }
        assertEquals(HttpStatus.NOT_FOUND, ex.statusCode)
    }

    @Test
    fun `executeTrade sells stock, credits account and reduces portfolio`() {
        val stock = apple(BigDecimal("200.00"))
        val holding = PortfolioItem(
            userId = userId,
            stockSymbol = "AAPL",
            quantity = 10,
            averageBuyPrice = BigDecimal("150.00")
        )
        every { stockRepository.findById("AAPL") } returns Optional.of(stock)
        every { portfolioService.getHoldingEntity(userId, "AAPL") } returns holding
        every { accountService.getAccountEntity(userId) } returns account(BigDecimal("500.00"))
        every { tradeRepository.save(any<Trade>()) } answers { savedTrade("sell", 4, BigDecimal("200.00")) }
        every { userStatsRepository.findByUserId(userId) } returns null

        service.executeTrade(
            TradeRequest(symbol = "AAPL", quantity = 4, tradeType = TradeRequest.TradeType.sell)
        )

        val accountSlot = slot<Account>()
        verify { accountService.saveAccount(capture(accountSlot)) }
        assertEquals(BigDecimal("1300.00"), accountSlot.captured.balance)

        verify { portfolioService.removeFromPortfolio(userId, "AAPL", 4, BigDecimal("200.00")) }
    }

    @Test
    fun `executeTrade rejects sell when not enough shares`() {
        val stock = apple(BigDecimal("200.00"))
        val holding = PortfolioItem(
            userId = userId,
            stockSymbol = "AAPL",
            quantity = 2,
            averageBuyPrice = BigDecimal("150.00")
        )
        every { stockRepository.findById("AAPL") } returns Optional.of(stock)
        every { portfolioService.getHoldingEntity(userId, "AAPL") } returns holding

        val ex = assertThrows(ResponseStatusException::class.java) {
            service.executeTrade(
                TradeRequest(symbol = "AAPL", quantity = 5, tradeType = TradeRequest.TradeType.sell)
            )
        }
        assertEquals(HttpStatus.BAD_REQUEST, ex.statusCode)
    }

    @Test
    fun `executeTrade uppercases ticker before lookup`() {
        every { stockRepository.findById("AAPL") } returns Optional.of(apple())
        every { accountService.getAccountEntity(userId) } returns account(BigDecimal("10000.00"))
        every { tradeRepository.save(any<Trade>()) } answers { savedTrade("buy", 1, BigDecimal("100.00")) }
        every { userStatsRepository.findByUserId(userId) } returns null

        service.executeTrade(
            TradeRequest(symbol = "aapl", quantity = 1, tradeType = TradeRequest.TradeType.buy)
        )

        verify { stockRepository.findById("AAPL") }
        verify { portfolioService.addToPortfolio(userId, "AAPL", 1, any()) }
    }

    @Test
    fun `getTradeHistory pages results`() {
        val trade = savedTrade("buy", 3, BigDecimal("100.00"))
        val page: Page<Trade> = PageImpl(listOf(trade), PageRequest.of(0, 10), 1L)
        every { tradeRepository.findByUserIdOrderByTradeDateDesc(userId, any()) } returns page

        val result = service.getTradeHistory(null, null, null, null, 10)

        assertEquals(1, result.totalElements)
        assertEquals(1, result.data?.size)
        assertEquals("AAPL", result.data?.first()?.symbol)
    }

    @Test
    fun `getTradeHistory filters by symbol and tradeType when both provided`() {
        val empty: Page<Trade> = PageImpl(emptyList(), PageRequest.of(0, 5), 0L)
        every {
            tradeRepository.findByUserIdAndStockSymbolAndTradeType(userId, "TSLA", "sell", any())
        } returns empty

        val result = service.getTradeHistory(null, null, "tsla", "sell", 5)

        assertNotNull(result)
        verify { tradeRepository.findByUserIdAndStockSymbolAndTradeType(userId, "TSLA", "sell", any()) }
    }
}
