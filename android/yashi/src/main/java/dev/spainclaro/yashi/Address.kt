package dev.spainclaro.yashi

import java.net.URI

/** A Yakushi server address (host + port). Scheme defaults to http. */
data class ServerAddress(
    val host: String,
    val port: Int,
    val scheme: String = "http",
) {
    val display: String
        get() = if (host.contains(':')) "[$host]:$port" else "$host:$port"

    val url: String
        get() = "$scheme://$display"

    companion object {
        fun fromUrl(url: String): ServerAddress? = AddressParser.parse(url)
    }
}

/**
 * Parses the many shapes a user may paste/scan: `192.168.1.5`,
 * `192.168.1.5:8080`, `http://192.168.1.5:8080`, a full URL with a path, etc.
 * The Yakushi server speaks plain HTTP, so a missing scheme defaults to http.
 */
object AddressParser {
    const val DEFAULT_PORT = 8080

    fun parse(raw: String): ServerAddress? {
        var input = raw.trim().trimEnd('/')
        if (input.isEmpty()) return null
        if (!input.contains("://")) input = "http://$input"

        val uri = runCatching { URI(input) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase() ?: "http"
        if (scheme != "http" && scheme != "https") return null
        val host = uri.host?.trim().orEmpty()
        if (host.isEmpty()) return null
        val port = when {
            uri.port in 1..65535 -> uri.port
            scheme == "https" -> 443
            else -> DEFAULT_PORT
        }
        if (port !in 1..65535) return null
        return ServerAddress(host = host, port = port, scheme = scheme)
    }
}
