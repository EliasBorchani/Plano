package app.plano.server

import java.security.SecureRandom
import java.sql.ResultSet
import java.time.Clock
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.Base64
import kotlin.time.Instant
import kotlin.time.toJavaInstant
import kotlin.time.toKotlinInstant

private val random = SecureRandom()

/** A secret for a link (group invite, personal invitation): 256 bits, URL-safe. */
fun newLinkToken(): String =
    ByteArray(32).also(random::nextBytes).let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }

fun Clock.now(): Instant = instant().toKotlinInstant()

/** The JDBC driver binds `OffsetDateTime`, not instants. */
fun Instant.toSql(): OffsetDateTime = toJavaInstant().atOffset(ZoneOffset.UTC)

fun ResultSet.instant(column: Int): Instant? = getObject(column, OffsetDateTime::class.java)?.toInstant()?.toKotlinInstant()
