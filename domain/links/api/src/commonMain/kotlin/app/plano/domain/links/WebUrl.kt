package app.plano.domain.links

/**
 * A small http(s) URL model. Written here rather than taken from Ktor so the rules module stays free of the
 * network stack. Query values stay percent-encoded as received; [decode] is for reading them.
 */
data class WebUrl(
    val scheme: String,
    val host: String,
    /** Null for the scheme's default port. */
    val port: Int?,
    /** Raw (still encoded), empty or starting with `/`. */
    val path: String,
    /** Raw (still encoded) pairs, in order. A value is null for `?flag` without `=`. */
    val query: List<Pair<String, String?>>,
) {
    val segments: List<String> get() = path.split('/').filter { it.isNotEmpty() }

    fun param(name: String): String? = query.firstOrNull { decode(it.first) == name }?.second?.let { decode(it, plusAsSpace = true) }

    override fun toString(): String = buildString {
        append(scheme).append("://").append(host)
        if (port != null) append(':').append(port)
        append(path)
        if (query.isNotEmpty()) {
            append('?')
            append(query.joinToString("&") { (key, value) -> if (value == null) key else "$key=$value" })
        }
    }

    companion object {
        private val SCHEME = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://")

        /** Null unless [text] is an http(s) address. A missing scheme means https (`maps.app.goo.gl/x`). */
        fun parse(text: String): WebUrl? {
            val trimmed = text.trim()
            if (trimmed.isEmpty() || trimmed.any { it.isWhitespace() }) return null
            val withScheme = if (SCHEME.containsMatchIn(trimmed)) trimmed else "https://$trimmed"
            val scheme = withScheme.substringBefore("://").lowercase()
            if (scheme != "http" && scheme != "https") return null

            val rest = withScheme.substringAfter("://").substringBefore('#')
            val authorityEnd = rest.indexOfFirst { it == '/' || it == '?' }.let { if (it < 0) rest.length else it }
            val authority = rest.substring(0, authorityEnd)
            // `https://google.com@evil.example` reads as google.com to a person but goes to evil.example.
            if ('@' in authority) return null
            val (host, port) = splitHostPort(authority) ?: return null

            val afterAuthority = rest.substring(authorityEnd)
            val path = afterAuthority.substringBefore('?')
            val rawQuery = if ('?' in afterAuthority) afterAuthority.substringAfter('?') else ""
            val query = rawQuery.split('&').filter { it.isNotEmpty() }.map {
                if ('=' in it) it.substringBefore('=') to it.substringAfter('=') else it to null
            }
            val defaultPort = if (scheme == "https") 443 else 80
            return WebUrl(scheme, host, port?.takeIf { it != defaultPort }, path, query)
        }

        private fun splitHostPort(authority: String): Pair<String, Int?>? {
            val ipv6 = authority.startsWith("[")
            val hostPart = if (ipv6) authority.substringBefore(']') + "]" else authority.substringBefore(':')
            val portText = authority.removePrefix(hostPart).removePrefix(":").takeIf { authority.length > hostPart.length }
            val port = portText?.let { it.toIntOrNull()?.takeIf { p -> p in 1..65535 } ?: return null }
            val host = hostPart.lowercase().trimEnd('.')
            if (host.isEmpty() || (!ipv6 && !host.all { it.isLetterOrDigit() || it == '.' || it == '-' })) return null
            // Public addresses always have a dot: `https://exa mple.com` in a message must not become `https://exa`.
            if (!ipv6 && '.' !in host) return null
            return host to port
        }

        /** Percent-decoding as UTF-8. Google writes spaces as `+` in place names. */
        fun decode(text: String, plusAsSpace: Boolean = false): String {
            if ('%' !in text && !(plusAsSpace && '+' in text)) return text
            val bytes = ArrayList<Byte>(text.length)
            var i = 0
            while (i < text.length) {
                val c = text[i]
                val hex = if (c == '%' && i + 2 <= text.lastIndex) text.substring(i + 1, i + 3).takeIf { it.all(::isHex) }?.toInt(16) else null
                when {
                    hex != null -> {
                        bytes += hex.toByte()
                        i += 3
                    }
                    c == '+' && plusAsSpace -> {
                        bytes += ' '.code.toByte()
                        i++
                    }
                    else -> {
                        c.toString().encodeToByteArray().forEach { bytes += it }
                        i++
                    }
                }
            }
            return bytes.toByteArray().decodeToString()
        }

        private fun isHex(c: Char) = c in '0'..'9' || c in 'a'..'f' || c in 'A'..'F'

        /** Form encoding (`+` for spaces), for building Google Maps link-outs. */
        fun encode(text: String): String = buildString {
            for (byte in text.encodeToByteArray()) {
                val c = (byte.toInt() and 0xff).toChar()
                when {
                    c.isLetterOrDigit() && c.code < 128 || c in "-._~" -> append(c)
                    c == ' ' -> append('+')
                    else -> append('%').append((byte.toInt() and 0xff).toString(16).uppercase().padStart(2, '0'))
                }
            }
        }
    }
}
