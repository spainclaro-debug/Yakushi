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
    val subnets: List<String> = emptyList(),
)

object LanScanner {
    private val DEFAULT_PORTS = listOf(8080, 18084, 8000, 3000)

    suspend fun scan(
        context: Context,
        preferredPorts: List<Int> = emptyList(),
        knownAddresses: List<ServerAddress> = emptyList(),
    ): LanScanResult = withContext(Dispatchers.IO) {
        val ports = (preferredPorts + DEFAULT_PORTS)
            .filter { it in 1..65535 }
            .distinct()
            .take(6)
        val localIps = localIpv4s(context)
        val localIp = localIps.firstOrNull()

        // Knowing an address from the saved list/history is the most reliable
        // hint: probe it first, even if the phone is on a VPN or a different
        // interface than the one NetworkInterface reports first.
        val knownServers = knownAddresses
            .mapNotNull { AddressParser.parse(it.url) }
            .distinctBy { it.url }
            .filter { probe(it) }
        if (knownServers.isNotEmpty()) {
            return@withContext LanScanResult(
                servers = knownServers.sortedBy { it.display },
                localIp = localIp,
                ports = ports,
                subnets = knownAddresses.mapNotNull { it.display.substringBeforeLast('.') }.distinct(),
            )
        }

        val prefixes = LinkedHashSet<String>()
        localIps.forEach { ip ->
            ip.substringBeforeLast('.', missingDelimiterValue = "")
                .takeIf { it.isNotEmpty() }
                ?.let(prefixes::add)
        }
        knownAddresses.forEach { address ->
            if (address.host.count { it == '.' } == 3) {
                address.host.substringBeforeLast('.')
                    .takeIf { it.isNotEmpty() }
                    ?.let(prefixes::add)
            }
        }
        val scanPrefixes = prefixes.take(4)
        if (scanPrefixes.isEmpty()) {
            return@withContext LanScanResult(emptyList(), localIp, ports)
        }

        val candidates = scanPrefixes.flatMap { prefix ->
            (1..254).flatMap { host ->
                ports.map { port -> ServerAddress("$prefix.$host", port) }
            }
        }.distinctBy { it.url }
        val semaphore = Semaphore(64)
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
        }.let { LanScanResult(it, localIp, ports, scanPrefixes) }
    }

    private fun localIpv4s(context: Context): List<String> {
        val result = LinkedHashSet<String>()
        runCatching {
            val connectivity = context.getSystemService(ConnectivityManager::class.java)
            // Prefer a real Wi-Fi/Ethernet network over a VPN's active network.
            val networks = connectivity?.allNetworks.orEmpty().filter { network ->
                val caps = connectivity?.getNetworkCapabilities(network) ?: return@filter false
                caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) ||
                    caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET)
            } + listOfNotNull(connectivity?.activeNetwork)
            networks.forEach { network ->
                connectivity?.getLinkProperties(network)
                    ?.linkAddresses
                    ?.map { it.address }
                    ?.filterIsInstance<Inet4Address>()
                    ?.filter { it.isSiteLocalAddress && !it.isLoopbackAddress && !it.isLinkLocalAddress }
                    ?.mapTo(result) { it.hostAddress.orEmpty() }
            }
        }
        if (result.isNotEmpty()) return result.toList()

        runCatching {
            NetworkInterface.getNetworkInterfaces()
                .asSequence()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.asSequence() }
                .filterIsInstance<Inet4Address>()
                .filter { it.isSiteLocalAddress && !it.isLoopbackAddress && !it.isLinkLocalAddress }
                .mapNotNullTo(result) { it.hostAddress }
        }
        return result.toList()
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
