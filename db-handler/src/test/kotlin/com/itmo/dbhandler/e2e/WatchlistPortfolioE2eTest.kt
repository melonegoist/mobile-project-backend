package com.itmo.dbhandler.e2e

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import java.math.BigDecimal

class WatchlistPortfolioE2eTest : E2eTestBase() {

    @Test
    fun `add, list and remove from watchlist`() {
        val token = registerAndLogin("watcher")

        val addResponse = postJson("/watchlist", mapOf("symbol" to "AAPL"), Map::class.java, token)
        assertEquals(HttpStatus.OK, addResponse.statusCode)

        val getResponse = getJson("/watchlist", List::class.java, token)
        assertEquals(HttpStatus.OK, getResponse.statusCode)
        @Suppress("UNCHECKED_CAST")
        val list = getResponse.body as List<Map<String, Any>>
        assertEquals(1, list.size)
        assertEquals("AAPL", list.first()["symbol"])

        val deleteResponse = deleteJson("/watchlist/AAPL", Map::class.java, token)
        assertEquals(HttpStatus.OK, deleteResponse.statusCode)

        val afterDelete = getJson("/watchlist", List::class.java, token)
        @Suppress("UNCHECKED_CAST")
        val empty = afterDelete.body as List<Map<String, Any>>
        assertTrue(empty.isEmpty())
    }

    @Test
    fun `duplicate watchlist add is rejected with 409`() {
        val token = registerAndLogin("dupwatcher")

        postJson("/watchlist", mapOf("symbol" to "TSLA"), Map::class.java, token)
        val second = postJson("/watchlist", mapOf("symbol" to "TSLA"), Map::class.java, token)

        assertEquals(HttpStatus.CONFLICT, second.statusCode)
    }

    @Test
    fun `unknown symbol returns 404 when added to watchlist`() {
        val token = registerAndLogin("unknown")
        val response = postJson("/watchlist", mapOf("symbol" to "NOTREAL"), Map::class.java, token)
        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
    }

    @Test
    fun `portfolio value reflects pending trades`() {
        val token = registerAndLogin("portfolio")

        // Deposit so the user can trade
        postJson(
            "/account/deposit",
            mapOf("amount" to BigDecimal("5000.00")),
            Map::class.java,
            token
        )

        // Initial portfolio is empty
        val empty = getJson("/portfolio", List::class.java, token)
        @Suppress("UNCHECKED_CAST")
        val emptyList = empty.body as List<Map<String, Any>>
        assertTrue(emptyList.isEmpty())

        // Buy 3 GOOGL
        postJson(
            "/trades",
            mapOf("symbol" to "GOOGL", "quantity" to 3, "tradeType" to "buy", "orderType" to "market"),
            Map::class.java,
            token
        )

        // Portfolio now has GOOGL
        val withPosition = getJson("/portfolio", List::class.java, token)
        @Suppress("UNCHECKED_CAST")
        val positions = withPosition.body as List<Map<String, Any>>
        assertEquals(1, positions.size)

        // Portfolio value endpoint returns numbers
        val valueResp = getJson("/portfolio/value", Map::class.java, token)
        assertEquals(HttpStatus.OK, valueResp.statusCode)
        val value = valueResp.body!!
        assertTrue((value["totalValue"] as Number).toDouble() > 0)
    }
}
