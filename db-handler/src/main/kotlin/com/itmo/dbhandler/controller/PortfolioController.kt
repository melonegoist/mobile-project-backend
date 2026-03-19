package com.itmo.dbhandler.controller

import com.itmo.dbhandler.api.PortfolioApi
import com.itmo.dbhandler.model.PortfolioItem
import com.itmo.dbhandler.model.PortfolioValueGet200Response
import com.itmo.dbhandler.service.PortfolioService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController

@RestController
class PortfolioController(
    private val portfolioService: PortfolioService
) : PortfolioApi {

    override fun portfolioGet(): ResponseEntity<List<PortfolioItem>> {
        return ResponseEntity.ok(portfolioService.getPortfolio())
    }

    override fun portfolioValueGet(): ResponseEntity<PortfolioValueGet200Response> {
        return ResponseEntity.ok(portfolioService.getPortfolioValue())
    }

    override fun portfolioHoldingsSymbolGet(symbol: String): ResponseEntity<PortfolioItem> {
        return ResponseEntity.ok(portfolioService.getHolding(symbol))
    }
}