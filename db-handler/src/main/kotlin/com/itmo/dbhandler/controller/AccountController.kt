package com.itmo.dbhandler.controller

import com.itmo.dbhandler.api.AccountApi
import com.itmo.dbhandler.model.Account
import com.itmo.dbhandler.model.AccountDepositPostRequest
import com.itmo.dbhandler.model.AccountTransactionsGet200Response
import com.itmo.dbhandler.model.AccountWithdrawPostRequest
import com.itmo.dbhandler.service.AccountService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController
import java.time.OffsetDateTime

@RestController
class AccountController(
    private val accountService: AccountService
) : AccountApi {

    override fun accountGet(): ResponseEntity<Account> {
        return ResponseEntity.ok(accountService.getAccount())
    }

    override fun accountDepositPost(accountDepositPostRequest: AccountDepositPostRequest): ResponseEntity<Account> {
        return ResponseEntity.ok(accountService.deposit(accountDepositPostRequest))
    }

    override fun accountWithdrawPost(accountWithdrawPostRequest: AccountWithdrawPostRequest): ResponseEntity<Account> {
        return ResponseEntity.ok(accountService.withdraw(accountWithdrawPostRequest))
    }

    override fun accountTransactionsGet(type: String?, from: OffsetDateTime?): ResponseEntity<AccountTransactionsGet200Response> {
        val response = accountService.getTransactions(type, from)
        return ResponseEntity.ok(response)
    }
}