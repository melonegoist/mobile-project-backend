package com.itmo.dbhandler.controller

import com.itmo.dbhandler.api.WatchlistApi
import com.itmo.dbhandler.model.Stock
import com.itmo.dbhandler.model.WatchlistPostRequest
import com.itmo.dbhandler.service.WatchlistService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController

@RestController
class WatchlistController(
    private val watchlistService: WatchlistService
) : WatchlistApi {

    override fun watchlistGet(): ResponseEntity<List<Stock>> {
        return ResponseEntity.ok(watchlistService.getWatchlist())
    }

    override fun watchlistPost(watchlistPostRequest: WatchlistPostRequest): ResponseEntity<Unit> {
        watchlistService.addToWatchlist(watchlistPostRequest)
        return ResponseEntity.ok().build()
    }

    override fun watchlistSymbolDelete(symbol: String): ResponseEntity<Unit> {
        watchlistService.removeFromWatchlist(symbol)
        return ResponseEntity.ok().build()
    }
}