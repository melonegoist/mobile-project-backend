package com.itmo.dbhandler.repository

import com.itmo.dbhandler.entity.AccountTransaction
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime

@Repository
interface AccountTransactionRepository : JpaRepository<AccountTransaction, Long> {
    fun findByUserIdOrderByTransactionDateDesc(userId: Long, pageable: Pageable): Page<AccountTransaction>
    fun findByUserIdAndTypeOrderByTransactionDateDesc(userId: Long, type: String, pageable: Pageable): Page<AccountTransaction>
    fun findByUserIdAndTransactionDateBetweenOrderByTransactionDateDesc(
        userId: Long,
        from: OffsetDateTime,
        to: OffsetDateTime,
        pageable: Pageable
    ): Page<AccountTransaction>
}