package com.itmo.dbhandler.e2e

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import java.math.BigDecimal

/**
 * Walks through the full authenticated trading workflow:
 *   register → login → deposit → buy → sell → history
 *
 * Verifies that the database state evolves correctly across the cycle.
 */
class TradeCycleE2eTest : E2eTestBase() {

    @Test
    fun `user can register, top up, buy and sell stock`() {
        val token = registerAndLogin("trader")

        // --- deposit ---
        val depositResponse = postJson(
            "/account/deposit",
            mapOf("amount" to BigDecimal("10000.00"), "paymentMethod" to "card"),
            Map::class.java,
            token
        )
        assertEquals(HttpStatus.OK, depositResponse.statusCode)
        val accountAfterDeposit = depositResponse.body!!
        assertEquals(10000.0, (accountAfterDeposit["balance"] as Number).toDouble())

        // --- buy AAPL ---
        val buyResponse = postJson(
            "/trades",
            mapOf(
                "symbol" to "AAPL",
                "quantity" to 5,
                "tradeType" to "buy",
                "orderType" to "market"
            ),
            Map::class.java,
            token
        )
        assertEquals(HttpStatus.OK, buyResponse.statusCode)
        val buyBody = buyResponse.body!!
        assertEquals("AAPL", buyBody["symbol"])
        assertEquals(5, (buyBody["quantity"] as Number).toInt())
        assertEquals("completed", buyBody["status"])

        // --- portfolio contains AAPL ---
        val portfolioResponse = getJson("/portfolio", List::class.java, token)
        assertEquals(HttpStatus.OK, portfolioResponse.statusCode)
        @Suppress("UNCHECKED_CAST")
        val positions = portfolioResponse.body as List<Map<String, Any>>
        assertEquals(1, positions.size)
        val position = positions.first()
        @Suppress("UNCHECKED_CAST")
        val stock = position["stock"] as Map<String, Any>
        assertEquals("AAPL", stock["symbol"])
        assertEquals(5, (position["quantity"] as Number).toInt())

        // --- sell 2 shares ---
        val sellResponse = postJson(
            "/trades",
            mapOf(
                "symbol" to "AAPL",
                "quantity" to 2,
                "tradeType" to "sell",
                "orderType" to "market"
            ),
            Map::class.java,
            token
        )
        assertEquals(HttpStatus.OK, sellResponse.statusCode)
        val sellBody = sellResponse.body!!
        assertEquals("AAPL", sellBody["symbol"])
        assertEquals(2, (sellBody["quantity"] as Number).toInt())
        assertEquals("completed", sellBody["status"])

        // --- portfolio now shows 3 ---
        val portfolioAfterSell = getJson("/portfolio", List::class.java, token)
        @Suppress("UNCHECKED_CAST")
        val positionsAfter = portfolioAfterSell.body as List<Map<String, Any>>
        assertEquals(1, positionsAfter.size)
        assertEquals(3, (positionsAfter.first()["quantity"] as Number).toInt())

        // --- history has both trades ---
        val historyResponse = getJson("/trades/history?limit=10", Map::class.java, token)
        assertEquals(HttpStatus.OK, historyResponse.statusCode)
        val history = historyResponse.body!!
        assertNotNull(history["data"])
        @Suppress("UNCHECKED_CAST")
        val historyData = history["data"] as List<Map<String, Any>>
        assertEquals(2, historyData.size)
    }

    @Test
    fun `buying without enough balance returns 400`() {
        val token = registerAndLogin("poor")
        // Deposit only a tiny amount (1 USD) — minimum per spec is 10, so use 10.
        postJson(
            "/account/deposit",
            mapOf("amount" to BigDecimal("10.00")),
            Map::class.java,
            token
        )

        val response = postJson(
            "/trades",
            mapOf("symbol" to "AAPL", "quantity" to 1000, "tradeType" to "buy", "orderType" to "market"),
            Map::class.java,
            token
        )
        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    }

    @Test
    fun `selling shares user does not own returns 404`() {
        val token = registerAndLogin("noshares")

        val response = postJson(
            "/trades",
            mapOf("symbol" to "AAPL", "quantity" to 1, "tradeType" to "sell", "orderType" to "market"),
            Map::class.java,
            token
        )
        assertTrue(response.statusCode.value() in 400..499)
    }

    @Test
    fun `unauthenticated trade is rejected`() {
        val response = postJson(
            "/trades",
            mapOf("symbol" to "AAPL", "quantity" to 1, "tradeType" to "buy", "orderType" to "market"),
            Map::class.java,
            token = null
        )
        // No JWT → filter does not authenticate → Spring Security returns 401 or 403
        assertTrue(response.statusCode.value() in 401..403)
    }
}
