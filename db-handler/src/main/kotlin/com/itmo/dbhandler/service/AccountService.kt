package com.itmo.dbhandler.service

import com.itmo.dbhandler.entity.AccountTransaction
import com.itmo.dbhandler.model.*
import com.itmo.dbhandler.repository.AccountRepository
import com.itmo.dbhandler.repository.AccountTransactionRepository
import com.itmo.dbhandler.util.SecurityUtils
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.math.BigDecimal
import java.time.OffsetDateTime

@Service
class AccountService(
    private val accountRepository: AccountRepository,
    private val accountTransactionRepository: AccountTransactionRepository,
    private val portfolioService: PortfolioService,
) {

    fun getAccount(): Account {
        val userId = SecurityUtils.getCurrentUserId()
        val account = accountRepository.findByUserId(userId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found")
        val portfolioSummary = calculatePortfolioValue(userId)

        return Account(
            userId = userId,
            balance = account.balance.toDouble(),
            currency = when (account.currency) {
                "USD" -> Account.Currency.USD
                "EUR" -> Account.Currency.EUR
                "RUB" -> Account.Currency.RUB
                else -> Account.Currency.USD
            },
            availableBalance = account.balance.toDouble(),
            totalProfitLoss = portfolioSummary.totalProfitLoss.toDouble(),
            totalProfitLossPercent = portfolioSummary.totalProfitLossPercent.toDouble(),
        )
    }

    @Transactional
    fun deposit(request: AccountDepositPostRequest): Account {
        val userId = SecurityUtils.getCurrentUserId()
        val account = accountRepository.findByUserId(userId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found")

        account.balance += request.amount

        val transaction = AccountTransaction(
            userId = userId,
            type = "deposit",
            amount = request.amount,
            currency = account.currency,
            description = "Deposit via ${request.paymentMethod?.value ?: "card"}",
            status = "completed"
        )
        accountTransactionRepository.save(transaction)
        accountRepository.save(account)

        return getAccount()
    }

    @Transactional
    fun withdraw(request: AccountWithdrawPostRequest): Account {
        val userId = SecurityUtils.getCurrentUserId()
        val account = accountRepository.findByUserId(userId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found")

        if (account.balance < request.amount) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Insufficient funds")
        }

        account.balance -= request.amount

        val transaction = AccountTransaction(
            userId = userId,
            type = "withdraw",
            amount = request.amount,
            currency = account.currency,
            description = "Withdrawal",
            status = "completed"
        )
        accountTransactionRepository.save(transaction)
        accountRepository.save(account)

        return getAccount()
    }

    fun getTransactions(type: String?, from: OffsetDateTime?): AccountTransactionsGet200Response {
        val userId = SecurityUtils.getCurrentUserId()
        val pageable = PageRequest.of(0, 100, Sort.by("transactionDate").descending())

        val transactions = when {
            type != null -> accountTransactionRepository.findByUserIdAndTypeOrderByTransactionDateDesc(userId, type, pageable)
            from != null -> {
                val to = OffsetDateTime.now()
                accountTransactionRepository.findByUserIdAndTransactionDateBetweenOrderByTransactionDateDesc(userId, from, to, pageable)
            }
            else -> accountTransactionRepository.findByUserIdOrderByTransactionDateDesc(userId, pageable)
        }

        val transactionData = transactions.content.map { tx ->
            AccountTransactionsGet200ResponseAllOfDataInner(
                id = tx.id,
                type = tx.type,
                amount = tx.amount,
                timestamp = tx.transactionDate,
                status = tx.status
            )
        }

        return AccountTransactionsGet200Response(
            data = transactionData,
            page = transactions.number,
            propertySize = transactions.size,
            totalElements = transactions.totalElements.toInt(),
            totalPages = transactions.totalPages,
            hasNext = transactions.hasNext(),
            hasPrevious = transactions.hasPrevious()
        )
    }

    fun getAccountEntity(userId: Long): com.itmo.dbhandler.entity.Account {
        return accountRepository.findByUserId(userId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found")
    }

    fun saveAccount(account: com.itmo.dbhandler.entity.Account) {
        accountRepository.save(account)
    }

    private fun calculatePortfolioValue(userId: Long): PortfolioSummary {
        return portfolioService.calculateProfitLoss(userId)
    }

    private fun calculateLockedFunds(userId: Long): BigDecimal {
        // реализовать позже при добавлении ордеров
        return BigDecimal.ZERO
    }
}