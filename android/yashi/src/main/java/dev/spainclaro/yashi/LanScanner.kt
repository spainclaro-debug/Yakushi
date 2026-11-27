package dev.spainclaro.yashi

import java.io.IOException
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/**
 * Best-effort discovery of Yakushi servers on the current Wi-Fi/LAN.
 *
 * The app assumes the usual /24 home network: it probes every host on the
 * local IPv4 subnet on the server's default ports (8080 and the legacy 18084)
 * and checks that the HTTP response looks like the Yakushi frontend. This
 * keeps discovery dependency-free; servers on a custom port can still be
 * connected with the address bar or QR scanner.
 */
object LanScanner {
    private val DEFAULT_PORTS = listOf(8080, 18084)

    suspend fun scan(): List<ServerAddress> = withContext(Dispatchers.IO) {
        val localIp = localIpv4() ?: return@withContext emptyList()
        val prefix = localIp.substringBeforeLast('.', missingDelimiterValue = "")
        if (prefix.isEmpty()) return@withContext emptyList()

        val candidates = (1..254).flatMap { host ->
            DEFAULT_PORTS.map { port -> ServerAddress("$prefix.$host", port) }
        }
        val semaphore = Semaphore(48)
        coroutineScope {
            candidates
                .map { address ->
                    async {
                        semaphore.withPermit {
                            if (probe(address)) address else null
                        }
                    }
                }
                .awaitAll()
                .filterNotNull()
                .distinctBy { it.url }
                .sortedBy { it.display }
        }
    }

    private fun localIpv4(): String? = runCatching {
        NetworkInterface.getNetworkInterfaces()
            .asSequence()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.asSequence() }
            .filterIsInstance<Inet4Address>()
            .firstOrNull { it.isSiteLocalAddress && !it.isLoopbackAddress }
            ?.hostAddress
    }.getOrNull()

    private fun probe(address: ServerAddress): Boolean {
        val connection = try {
            URL(address.url).openConnection() as HttpURLConnection
        } catch (_: IOException) {
            return false
        }
        return try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 350
            connection.readTimeout = 700
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", "Yashi")
            val code = connection.responseCode
            if (code !in 200..399) {
                false
            } else {
                val stream = connection.inputStream
                val buffer = ByteArray(4096)
                val read = stream.use { it.read(buffer) }
                val body = if (read > 0) String(buffer, 0, read) else ""
                body.contains("yakushi", ignoreCase = true) ||
                    body.contains("tanoshi", ignoreCase = true) ||
                    body.contains("<!doctype", ignoreCase = true)
            }
        } catch (_: Exception) {
            false
        } finally {
            connection.disconnect()
        }
    }
}
