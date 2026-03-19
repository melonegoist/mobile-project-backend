package com.itmo.dbhandler.entity

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.OffsetDateTime

@Entity
@Table(name = "account_transactions")
data class AccountTransaction(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Column(nullable = false)
    var type: String,

    @Column(nullable = false)
    var amount: BigDecimal,

    @Column(nullable = false)
    var currency: String = "USD",

    @Column(nullable = false)
    var status: String = "completed",

    var description: String? = null,

    @Column(name = "transaction_date")
    var transactionDate: OffsetDateTime = OffsetDateTime.now(),

    @Column(name = "created_at")
    var createdAt: OffsetDateTime = OffsetDateTime.now()
)