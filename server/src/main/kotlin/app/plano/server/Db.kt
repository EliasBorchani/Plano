package app.plano.server

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.flywaydb.core.Flyway
import java.sql.Connection
import java.sql.ResultSet
import javax.sql.DataSource

/** Plain JDBC: a handful of queries does not justify an ORM. */
class Db(private val dataSource: DataSource) {
    suspend fun <T> tx(block: Connection.() -> T): T = withContext(Dispatchers.IO) {
        dataSource.connection.use { connection ->
            connection.autoCommit = false
            try {
                connection.block().also { connection.commit() }
            } catch (e: Throwable) {
                connection.rollback()
                throw e
            }
        }
    }

    companion object {
        fun connect(url: String, user: String, password: String): Db {
            val dataSource = HikariDataSource(
                HikariConfig().apply {
                    jdbcUrl = url
                    username = user
                    this.password = password
                    maximumPoolSize = 10
                },
            )
            Flyway.configure().dataSource(dataSource).load().migrate()
            return Db(dataSource)
        }
    }
}

fun <T> Connection.query(sql: String, vararg args: Any?, row: (ResultSet) -> T): List<T> =
    prepareStatement(sql).use { statement ->
        args.forEachIndexed { index, arg -> statement.setObject(index + 1, arg) }
        statement.executeQuery().use { result ->
            buildList { while (result.next()) add(row(result)) }
        }
    }

fun Connection.update(sql: String, vararg args: Any?): Int =
    prepareStatement(sql).use { statement ->
        args.forEachIndexed { index, arg -> statement.setObject(index + 1, arg) }
        statement.executeUpdate()
    }
