package dev.spainclaro.yashi

import android.content.Context
import android.net.ConnectivityManager
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
data class LanScanResult(
    val servers: List<ServerAddress>,
    val localIp: String?,
    val ports: List<Int>,
)

object LanScanner {
    private val DEFAULT_PORTS = listOf(8080, 18084, 8000, 3000)

    suspend fun scan(
        context: Context,
        preferredPorts: List<Int> = emptyList(),
    ): LanScanResult = withContext(Dispatchers.IO) {
        val ports = (preferredPorts + DEFAULT_PORTS)
            .filter { it in 1..65535 }
            .distinct()
        val localIp = localIpv4(context)
        if (localIp == null) return@withContext LanScanResult(emptyList(), null, ports)
        val prefix = localIp.substringBeforeLast('.', missingDelimiterValue = "")
        if (prefix.isEmpty()) {
            return@withContext LanScanResult(emptyList(), localIp, ports)
        }

        val candidates = (1..254).flatMap { host ->
            ports.map { port -> ServerAddress("$prefix.$host", port) }
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
        }.let { LanScanResult(it, localIp, ports) }
    }

    private fun localIpv4(context: Context): String? {
        // Prefer the active network's IPv4 address (works on Wi-Fi and avoids
        // picking a VPN/loopback interface from NetworkInterface ordering).
        runCatching {
            val connectivity = context.getSystemService(ConnectivityManager::class.java)
            val props = connectivity?.activeNetwork?.let { connectivity.getLinkProperties(it) }
            props?.linkAddresses
                ?.map { it.address }
                ?.filterIsInstance<Inet4Address>()
                ?.firstOrNull { !it.isLoopbackAddress && !it.isLinkLocalAddress }
                ?.hostAddress
                ?.let { return it }
        }
        return runCatching {
            NetworkInterface.getNetworkInterfaces()
                .asSequence()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.asSequence() }
                .filterIsInstance<Inet4Address>()
                .firstOrNull { !it.isLoopbackAddress && !it.isLinkLocalAddress }
                ?.hostAddress
        }.getOrNull()
    }

    private fun probe(address: ServerAddress): Boolean =
        probePath(address, "/health", health = true) ||
            probePath(address, "/", health = false)

    private fun probePath(address: ServerAddress, path: String, health: Boolean): Boolean {
        val connection = try {
            URL(address.url + path).openConnection() as HttpURLConnection
        } catch (_: IOException) {
            return false
        }
        return try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 400
            connection.readTimeout = 900
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", "Yashi")
            val code = connection.responseCode
            if (code !in 200..399) {
                false
            } else if (health) {
                // The server's /health endpoint is unauthenticated and returns
                // 200 for a running Yakushi/Tanoshi server. This is much more
                // reliable than guessing from the SPA HTML.
                true
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
