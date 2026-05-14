package com.itmo.dbhandler.service

import com.itmo.dbhandler.entity.Account
import com.itmo.dbhandler.entity.AccountTransaction
import com.itmo.dbhandler.model.AccountDepositPostRequest
import com.itmo.dbhandler.model.AccountWithdrawPostRequest
import com.itmo.dbhandler.repository.AccountRepository
import com.itmo.dbhandler.repository.AccountTransactionRepository
import com.itmo.dbhandler.testsupport.SecurityContextTestSupport
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.math.BigDecimal

class AccountServiceTest {

    private val accountRepository = mockk<AccountRepository>(relaxed = true)
    private val accountTxRepository = mockk<AccountTransactionRepository>(relaxed = true)
    private val portfolioService = mockk<PortfolioService>()

    private val service = AccountService(accountRepository, accountTxRepository, portfolioService)

    private val userId = 5L

    @BeforeEach
    fun setUp() {
        SecurityContextTestSupport.authenticate(userId)
        every { portfolioService.calculateProfitLoss(userId) } returns
            PortfolioSummary(BigDecimal.ZERO, BigDecimal.ZERO)
    }

    @AfterEach
    fun tearDown() {
        SecurityContextTestSupport.clear()
    }

    private fun account(balance: BigDecimal = BigDecimal.ZERO) =
        Account(id = 1, userId = userId, balance = balance, currency = "USD")

    @Test
    fun `deposit increases balance and saves transaction`() {
        val acc = account(BigDecimal("100.00"))
        every { accountRepository.findByUserId(userId) } returns acc

        service.deposit(AccountDepositPostRequest(amount = BigDecimal("250.00")))

        val accSlot = slot<Account>()
        verify { accountRepository.save(capture(accSlot)) }
        assertEquals(BigDecimal("350.00"), accSlot.captured.balance)

        val txSlot = slot<AccountTransaction>()
        verify { accountTxRepository.save(capture(txSlot)) }
        assertEquals("deposit", txSlot.captured.type)
        assertEquals(BigDecimal("250.00"), txSlot.captured.amount)
    }

    @Test
    fun `withdraw decreases balance when funds are sufficient`() {
        val acc = account(BigDecimal("500.00"))
        every { accountRepository.findByUserId(userId) } returns acc

        service.withdraw(AccountWithdrawPostRequest(amount = BigDecimal("200.00")))

        val accSlot = slot<Account>()
        verify { accountRepository.save(capture(accSlot)) }
        assertEquals(BigDecimal("300.00"), accSlot.captured.balance)

        val txSlot = slot<AccountTransaction>()
        verify { accountTxRepository.save(capture(txSlot)) }
        assertEquals("withdraw", txSlot.captured.type)
    }

    @Test
    fun `withdraw rejects when funds are insufficient`() {
        every { accountRepository.findByUserId(userId) } returns account(BigDecimal("50.00"))

        val ex = assertThrows(ResponseStatusException::class.java) {
            service.withdraw(AccountWithdrawPostRequest(amount = BigDecimal("100.00")))
        }
        assertEquals(HttpStatus.BAD_REQUEST, ex.statusCode)
        verify(exactly = 0) { accountRepository.save(any()) }
    }

    @Test
    fun `getAccount throws 404 when account is missing`() {
        every { accountRepository.findByUserId(userId) } returns null

        val ex = assertThrows(ResponseStatusException::class.java) { service.getAccount() }
        assertEquals(HttpStatus.NOT_FOUND, ex.statusCode)
    }

    @Test
    fun `getAccount returns balance and computed profit summary`() {
        every { accountRepository.findByUserId(userId) } returns account(BigDecimal("1234.56"))
        every { portfolioService.calculateProfitLoss(userId) } returns
            PortfolioSummary(BigDecimal("100.00"), BigDecimal("8.10"))

        val result = service.getAccount()

        assertEquals(1234.56, result.balance)
        assertEquals(100.0, result.totalProfitLoss)
        assertEquals(8.10, result.totalProfitLossPercent)
    }
}
