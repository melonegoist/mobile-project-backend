package com.itmo.dbhandler

import com.itmo.dbhandler.e2e.E2eTestBase
import org.junit.jupiter.api.Test

/**
 * Smoke test — verifies the Spring context boots against ephemeral containers.
 * Uses the same shared infrastructure as the rest of the e2e suite.
 */
class DbHandlerApplicationTests : E2eTestBase() {

    @Test
    fun contextLoads() {
    }
}
