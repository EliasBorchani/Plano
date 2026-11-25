package app.plano.server.links

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Dns
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.slf4j.LoggerFactory
import java.io.IOException
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.UnknownHostException
import java.time.Duration

private val log = LoggerFactory.getLogger(SafeFetcher::class.java)

/** Which addresses the server may reach for a user-supplied link. Tests relax it to reach a local fake server. */
data class AddressPolicy(
    val allowPrivate: Boolean = false,
    val allowAnyPort: Boolean = false,
) {
    fun allows(address: InetAddress): Boolean = allowPrivate || isPublic(address)

    fun allowsPort(url: HttpUrl): Boolean = allowAnyPort || url.port == 80 || url.port == 443

    companion object {
        /** Loopback, private, link-local (cloud metadata at 169.254.169.254), carrier-grade NAT, multicast, ULA. */
        fun isPublic(address: InetAddress): Boolean {
            if (address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress ||
                address.isSiteLocalAddress || address.isMulticastAddress
            ) {
                return false
            }
            val bytes = address.address.map { it.toInt() and 0xff }
            return when (address) {
                is Inet4Address -> !(bytes[0] == 0 || (bytes[0] == 100 && bytes[1] in 64..127) || bytes[0] >= 240)
                is Inet6Address -> (bytes[0] and 0xfe) != 0xfc
                else -> false
            }
        }
    }
}

data class FetchedPage(
    val url: String,
    val html: ByteArray,
    /** From the Content-Type header, when it names one. */
    val charset: String?,
)

/**
 * Fetches user-supplied addresses without letting them reach the server's own network (PRD, Link handling): web
 * schemes and ports only, every hop's addresses checked (OkHttp connects to exactly the addresses our [Dns]
 * returned, so a second DNS answer cannot swap in a private one), a few redirects at most, a size limit and a timeout.
 */
class SafeFetcher(
    private val policy: AddressPolicy = AddressPolicy(),
    dns: Dns = Dns.SYSTEM,
    timeout: Duration = Duration.ofSeconds(5),
    private val maxBytes: Int = 512 * 1024,
    private val maxRedirects: Int = 5,
) {
    private val client = OkHttpClient.Builder()
        .dns(CheckedDns(dns, policy))
        .followRedirects(false)
        .followSslRedirects(false)
        .callTimeout(timeout)
        .build()

    /**
     * Follows redirects from [start] without reading any page, while each address passes [allowHost], and stops at
     * the first address [isDone] accepts (that address itself is not requested). Null when it does not get there.
     */
    suspend fun followRedirects(start: String, allowHost: (String) -> Boolean, isDone: (String) -> Boolean): String? {
        var url = start.toHttpUrlOrNull() ?: return null
        repeat(maxRedirects + 1) {
            if (isDone(url.toString())) return url.toString()
            if (!allowHost(url.host)) return null
            url = call(url) { response -> response.redirectTarget() } ?: return null
        }
        return null
    }

    /** The HTML at [start] (redirects followed, each hop checked), or null for anything else or any failure. */
    suspend fun fetchHtml(start: String): FetchedPage? {
        var url = start.toHttpUrlOrNull() ?: return null
        repeat(maxRedirects + 1) {
            var next: HttpUrl? = null
            val page = call(url) { response ->
                next = response.redirectTarget()
                if (next != null || !response.isSuccessful) return@call null
                val type = response.body.contentType()
                if (type == null || type.subtype !in setOf("html", "xhtml+xml")) return@call null
                val source = response.body.source()
                source.request(maxBytes.toLong() + 1)
                // A huge page is cut, not refused: the title is near the top.
                val bytes = source.buffer.readByteArray(minOf(source.buffer.size, maxBytes.toLong()))
                FetchedPage(url.toString(), bytes, type.charset()?.name())
            }
            if (page != null) return page
            url = next ?: return null
        }
        return null
    }

    private suspend fun <T> call(url: HttpUrl, read: (Response) -> T?): T? {
        if (!url.isHttps && url.scheme != "http") return null
        if (!policy.allowsPort(url)) return null
        // OkHttp does not ask the Dns for literal addresses (`http://127.0.0.1/`): check those here.
        if (url.host.isIpLiteral() && !policy.allows(InetAddress.getByName(url.host))) return null
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).header("Accept", "text/html").build()
        return withContext(Dispatchers.IO) {
            try {
                client.newCall(request).execute().use(read)
            } catch (e: IOException) {
                log.debug("Link fetch failed for {}: {}", url.host, e.message)
                null
            }
        }
    }

    private fun Response.redirectTarget(): HttpUrl? =
        if (isRedirect) header("Location")?.let { request.url.resolve(it) } else null

    private fun String.isIpLiteral() = ':' in this || all { it.isDigit() || it == '.' }

    private class CheckedDns(private val delegate: Dns, private val policy: AddressPolicy) : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            val addresses = delegate.lookup(hostname)
            // One private address is enough to refuse: the connection may pick any of them.
            if (addresses.isEmpty() || addresses.any { !policy.allows(it) }) throw UnknownHostException("Refused address for $hostname")
            return addresses
        }
    }

    companion object {
        // Generic on purpose: no contact details of the owner in requests to third-party sites.
        const val USER_AGENT = "Mozilla/5.0 (compatible; PlanoLinkPreview/1.0)"
    }
}
