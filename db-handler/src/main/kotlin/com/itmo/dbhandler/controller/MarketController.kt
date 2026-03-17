package com.itmo.dbhandler.controller

import com.itmo.dbhandler.api.MarketApi
import com.itmo.dbhandler.model.MarketStocksGet200Response
import com.itmo.dbhandler.model.MarketSummary
import com.itmo.dbhandler.model.PriceHistory
import com.itmo.dbhandler.model.Stock
import com.itmo.dbhandler.service.MarketService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

@RestController
class MarketController(
    private val marketService: MarketService
) : MarketApi {

    override fun marketStocksGet(
        limit: Int,
        offset: Int,
        sortBy: String,
        order: String,
        sector: String?
    ): ResponseEntity<MarketStocksGet200Response> {
        val response = marketService.getStocks(limit, offset, sortBy, order, sector)
        return ResponseEntity.ok(response)
    }

    override fun marketStocksSymbolGet(symbol: String): ResponseEntity<Stock> {
        val response = marketService.getStockBySymbol(symbol)
        return ResponseEntity.ok(response)
    }

    override fun marketStocksSymbolHistoryGet(
        symbol: String,
        interval: String,
        from: LocalDate?,
        to: LocalDate?
    ): ResponseEntity<PriceHistory> {
        val response = marketService.getPriceHistory(symbol, interval, from, to)
        return ResponseEntity.ok(response)
    }

    override fun marketSummaryGet(): ResponseEntity<MarketSummary> {
        val response = marketService.getMarketSummary()
        return ResponseEntity.ok(response)
    }
}