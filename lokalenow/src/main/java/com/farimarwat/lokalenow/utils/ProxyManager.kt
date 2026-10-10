package com.farimarwat.lokalenow.utils

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Fetches free proxies, tests them against the Google Translate endpoint,
 * keeps the fastest ones and hands them out in rotation.
 */
class ProxyManager(
    private val baseClient: OkHttpClient,
    topCount: Int = TOP_COUNT
) {
    private val topCount = topCount.coerceIn(1, MAX_COUNT)
    private data class ProxyItem(val type: Proxy.Type, val host: String, val port: Int) {
        fun toProxy(): Proxy = Proxy(type, InetSocketAddress(host, port))
        override fun toString(): String =
            "${if (type == Proxy.Type.SOCKS) "socks" else "http"}://$host:$port"
    }

    private val active = mutableListOf<Proxy>()
    private var index = 0

    /** Fetches, tests and keeps the top proxies. Returns how many are ready to use. */
    fun load(): Int {
        val candidates = fetchProxies()
        if (candidates.isEmpty()) return 0

        println("LokaleNow: Testing ${candidates.size} proxies against translator...")
        val pool = Executors.newFixedThreadPool(THREADS)
        val working = try {
            pool.invokeAll(candidates.map { item -> Callable { testProxy(item) } })
                .mapNotNull { runCatching { it.get() }.getOrNull() }
                .sortedBy { it.first }
        } finally {
            pool.shutdownNow()
        }

        val best = working.take(topCount)
        println("LokaleNow: Working: ${working.size} | Using top ${best.size}")
        best.forEach { (ms, item) -> println("    ${ms}ms -> $item") }

        synchronized(this) {
            active.clear()
            active.addAll(best.map { it.second.toProxy() })
            index = 0
        }
        return best.size
    }

    /** Next proxy in rotation, or null if none are left. */
    @Synchronized
    fun next(): Proxy? {
        if (active.isEmpty()) return null
        if (index >= active.size) index = 0
        return active[index++]
    }

    /** Removes a proxy that failed, so it is not used again in this build. */
    @Synchronized
    fun markFailed(proxy: Proxy) {
        val i = active.indexOf(proxy)
        if (i < 0) return
        active.removeAt(i)
        if (i < index) index--
        println("\n  LokaleNow: Dropped proxy ${proxy.address()} (${active.size} left)")
    }

    @Synchronized
    fun hasProxies(): Boolean = active.isNotEmpty()

    private fun fetchProxies(): List<ProxyItem> {
        println("LokaleNow: Fetching proxies...")
        val all = LinkedHashSet<ProxyItem>()
        val fetchClient = baseClient.newBuilder()
            .callTimeout(FETCH_TIMEOUT_SEC, TimeUnit.SECONDS)
            .build()

        try {
            val json = getJson(fetchClient, PROXYSCRAPE_URL)
            val list: JsonArray? = when {
                json == null -> null
                json.isJsonObject -> json.asJsonObject.getAsJsonArray("proxies")
                json.isJsonArray -> json.asJsonArray
                else -> null
            }
            list?.forEach { el ->
                toItem(el.asObjectOrNull(), "http")?.let(all::add)
            }
        } catch (e: Exception) {
            println("  LokaleNow: ProxyScrape: ${e.message}")
        }

        try {
            val json = getJson(fetchClient, PROXIFLY_URL)
            if (json != null && json.isJsonArray) {
                json.asJsonArray.forEach { el ->
                    val obj = el.asObjectOrNull()
                    val protocol = obj?.get("protocol")?.asString ?: "http"
                    toItem(obj, protocol)?.let(all::add)
                }
            }
        } catch (e: Exception) {
            println("  LokaleNow: Proxifly: ${e.message}")
        }

        println("LokaleNow: Total unique proxies: ${all.size}")
        return all.toList()
    }

    /** Sends a real translate request through the proxy. Returns latency in ms if it worked. */
    private fun testProxy(item: ProxyItem): Pair<Long, ProxyItem>? {
        val client = baseClient.newBuilder()
            .proxy(item.toProxy())
            .connectTimeout(TEST_TIMEOUT_SEC, TimeUnit.SECONDS)
            .readTimeout(TEST_TIMEOUT_SEC, TimeUnit.SECONDS)
            .callTimeout(TEST_TIMEOUT_SEC + 2, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .build()
        val request = Request.Builder().url(TEST_URL).build()

        return try {
            val start = System.nanoTime()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                if (!isValidTranslation(body)) return null
                (System.nanoTime() - start) / 1_000_000 to item
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun isValidTranslation(body: String): Boolean = try {
        val first = JsonParser.parseString(body).asJsonArray.firstOrNull()
        val text = when {
            first == null -> null
            first.isJsonArray -> first.asJsonArray.firstOrNull()?.asString
            else -> first.asString
        }
        !text.isNullOrBlank()
    } catch (e: Exception) {
        false
    }

    private fun getJson(client: OkHttpClient, url: String): JsonElement? {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body?.string() ?: return null
            return JsonParser.parseString(body)
        }
    }

    private fun toItem(obj: JsonObject?, protocol: String): ProxyItem? {
        if (obj == null) return null
        val host = obj.get("ip")?.asString?.takeIf { it.isNotBlank() } ?: return null
        val port = obj.get("port")?.asString?.toIntOrNull() ?: return null
        val type = if (protocol.lowercase().startsWith("socks")) Proxy.Type.SOCKS else Proxy.Type.HTTP
        return ProxyItem(type, host, port)
    }

    private fun JsonElement.asObjectOrNull(): JsonObject? = if (isJsonObject) asJsonObject else null

    companion object {
        private const val TOP_COUNT = 10
        const val MAX_COUNT = 20
        private const val THREADS = 200
        private const val FETCH_TIMEOUT_SEC = 10L
        private const val TEST_TIMEOUT_SEC = 6L

        private const val PROXYSCRAPE_URL =
            "https://api.proxyscrape.com/v4/free-proxy-list/get?request=display_proxies&protocol=http&format=json"
        private const val PROXIFLY_URL =
            "https://raw.githubusercontent.com/proxifly/free-proxy-list/main/proxies/all/data.json"
        private const val TEST_URL =
            "https://translate.googleapis.com/translate_a/t?client=gtx&dt=t&sl=en&tl=de&q=hello"
    }
}