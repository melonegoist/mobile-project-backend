package com.itmo.dbhandler.controller

import com.itmo.dbhandler.api.TradesApi
import com.itmo.dbhandler.model.TradeRequest
import com.itmo.dbhandler.model.TradeResponse
import com.itmo.dbhandler.model.TradesHistoryGet200Response
import com.itmo.dbhandler.service.TradeService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController
import java.time.OffsetDateTime

@RestController
class TradeController(
    private val tradeService: TradeService
) : TradesApi {

    override fun tradesPost(tradeRequest: TradeRequest): ResponseEntity<TradeResponse> {
        val response = tradeService.executeTrade(tradeRequest)
        return ResponseEntity.ok(response)
    }

    override fun tradesHistoryGet(
        from: OffsetDateTime?,
        to: OffsetDateTime?,
        symbol: String?,
        tradeType: String?,
        limit: Int
    ): ResponseEntity<TradesHistoryGet200Response> {
        val response = tradeService.getTradeHistory(from, to, symbol, tradeType, limit)
        return ResponseEntity.ok(response)
    }
}