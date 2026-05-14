package com.itmo.dbhandler.e2e

import com.redis.testcontainers.RedisContainer
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

/**
 * Base class for e2e tests.
 *
 * Postgres and Redis are started once per JVM (singleton-container pattern) and
 * shared between every E2eTestBase subclass. Spring Boot is then booted on a
 * random port with @DynamicPropertySource wiring it to those containers.
 *
 * Docker must be available on the host. Without it, the static initialiser
 * below will throw on first reference and the suite will fail loudly.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "jwt.secret=test-jwt-secret-must-be-long-enough-for-hs256-signing-1234567890",
        "spring.kafka.bootstrap-servers=localhost:9092",
        "management.health.kafka.enabled=false",
        "spring.jpa.show-sql=false"
    ]
)
abstract class E2eTestBase {

    companion object {
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer(
            DockerImageName.parse("postgres:16-alpine")
        ).apply {
            withDatabaseName("brokerapp")
            withUsername("brokerapp")
            withPassword("brokerapp_password")
            withReuse(true)
            start()
        }

        @JvmStatic
        val redis: RedisContainer = RedisContainer(
            DockerImageName.parse("redis:7-alpine")
        ).apply {
            withReuse(true)
            start()
        }

        @JvmStatic
        @DynamicPropertySource
        fun overrideProps(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { postgres.jdbcUrl }
            registry.add("spring.datasource.username") { postgres.username }
            registry.add("spring.datasource.password") { postgres.password }
            registry.add("spring.flyway.url") { postgres.jdbcUrl }
            registry.add("spring.flyway.user") { postgres.username }
            registry.add("spring.flyway.password") { postgres.password }
            registry.add("spring.data.redis.host") { redis.host }
            registry.add("spring.data.redis.port") { redis.firstMappedPort }
            registry.add("quotation.redis.channel") { "quotes.ticks" }
        }
    }

    @Autowired
    protected lateinit var restTemplate: TestRestTemplate

    @LocalServerPort
    protected var port: Int = 0

    protected fun url(path: String): String = "http://localhost:$port$path"

    private fun jsonHeaders(token: String? = null): HttpHeaders = HttpHeaders().apply {
        contentType = MediaType.APPLICATION_JSON
        if (token != null) setBearerAuth(token)
    }

    protected fun <T> postJson(path: String, body: Any, type: Class<T>, token: String? = null): ResponseEntity<T> =
        restTemplate.exchange(url(path), HttpMethod.POST, HttpEntity(body, jsonHeaders(token)), type)

    protected fun <T> getJson(path: String, type: Class<T>, token: String? = null): ResponseEntity<T> =
        restTemplate.exchange(url(path), HttpMethod.GET, HttpEntity<Void>(jsonHeaders(token)), type)

    protected fun <T> deleteJson(path: String, type: Class<T>, token: String? = null): ResponseEntity<T> =
        restTemplate.exchange(url(path), HttpMethod.DELETE, HttpEntity<Void>(jsonHeaders(token)), type)

    /**
     * Registers a unique throw-away user and returns the issued JWT.
     * Each call yields a fresh username so tests stay isolated.
     */
    protected fun registerAndLogin(prefix: String = "user"): String {
        val unique = "$prefix${System.nanoTime()}"
        val body = mapOf(
            "username" to unique,
            "email" to "$unique@e2e.test",
            "password" to "Passw0rd!",
            "firstName" to "E2E",
            "lastName" to "Tester"
        )
        val response = postJson("/auth/register", body, Map::class.java)
        check(response.statusCode.is2xxSuccessful) {
            "register failed: ${response.statusCode} ${response.body}"
        }
        @Suppress("UNCHECKED_CAST")
        val map = response.body as Map<String, Any>
        return map["token"] as String
    }
}
