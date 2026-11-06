package app.plano.server

import java.sql.DriverManager
import java.util.UUID

/** A fresh database per test on the compose Postgres (`docker compose up -d postgres`), dropped afterwards. */
class TestDatabase : AutoCloseable {
    private val adminUrl = System.getenv("TEST_DATABASE_URL") ?: "jdbc:postgresql://localhost:5434/plano"
    private val user = System.getenv("DATABASE_USER") ?: "plano"
    private val password = checkNotNull(System.getenv("POSTGRES_PASSWORD")) { "POSTGRES_PASSWORD missing: create .env" }
    private val name = "plano_test_" + UUID.randomUUID().toString().replace("-", "").take(12)

    init {
        admin { createStatement().execute("create database $name") }
    }

    val db: Db = Db.connect(adminUrl.substringBeforeLast('/') + "/" + name, user, password)

    override fun close() {
        admin { createStatement().execute("drop database if exists $name with (force)") }
    }

    private fun admin(block: java.sql.Connection.() -> Unit) {
        DriverManager.getConnection(adminUrl, user, password).use(block)
    }
}
