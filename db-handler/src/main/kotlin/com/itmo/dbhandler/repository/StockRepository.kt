package com.itmo.dbhandler.repository

import com.itmo.dbhandler.entity.Stock
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface StockRepository : JpaRepository<Stock, String> {
    fun findBySector(sector: String, pageable: Pageable): Page<Stock>
    fun findAllByOrderBySymbolAsc(pageable: Pageable): Page<Stock>
    fun findAllByOrderByCurrentPriceDesc(pageable: Pageable): Page<Stock>
    fun findAllByOrderByChangePercentDesc(pageable: Pageable): Page<Stock>
    fun findAllByOrderByVolumeDesc(pageable: Pageable): Page<Stock>
}