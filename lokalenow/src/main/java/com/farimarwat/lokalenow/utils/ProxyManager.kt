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
import java.util.concurrent.ExecutorCompletionService
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
/**
 * Fetches free proxies, tests them against the Google Translate endpoint,
 * and hands out working ones in rotation. When too many die during a build,
 * it quietly tests more in the background.
 */
class ProxyManager(
    private val baseClient: OkHttpClient,
    topCount: Int = TOP_COUNT
) {
    private data class ProxyItem(val type: Proxy.Type, val host: String, val port: Int) {
        fun toProxy(): Proxy = Proxy(type, InetSocketAddress(host, port))
    }

    private val topCount = topCount.coerceIn(1, MAX_COUNT)

    private val active = mutableListOf<Proxy>()
    private val strikes = mutableMapOf<Proxy, Int>()
    private val candidates = ArrayDeque<ProxyItem>()
    private var index = 0

    private val refilling = AtomicBoolean(false)
    @Volatile
    private var closed = false

    private val viaProxy = AtomicInteger(0)
    private val dropped = AtomicInteger(0)
    private val refills = AtomicInteger(0)

    /** Finds working proxies. Blocks only for the first load. Returns how many are ready. */
    fun load(): Int {
        val fetched = fetchProxies().shuffled()
        if (fetched.isEmpty()) return 0

        synchronized(this) {
            candidates.clear()
            candidates.addAll(fetched)
            active.clear()
            strikes.clear()
            index = 0
        }

        println("LokaleNow: Finding $topCount working proxies...")
        val tested = findWorking(MAX_TEST_TIME_SEC, THREADS)
        val count = synchronized(this) { active.size }
        println("LokaleNow: Using $count proxies (tested $tested of ${fetched.size})")
        return count
    }

    /** Next proxy in rotation, or null if none are left. */
    @Synchronized
    fun next(): Proxy? {
        if (active.isEmpty()) return null
        if (index >= active.size) index = 0
        return active[index++]
    }

    @Synchronized
    fun hasProxies(): Boolean = active.isNotEmpty()

    fun markSuccess(proxy: Proxy) {
        synchronized(this) { strikes.remove(proxy) }
        viaProxy.incrementAndGet()
    }

    /** Records a failure. Returns true if the proxy was dropped (after [MAX_STRIKES] failures in a row). */
    fun markFailed(proxy: Proxy): Boolean {
        val removed = synchronized(this) {
            val count = (strikes[proxy] ?: 0) + 1
            if (count < MAX_STRIKES) {
                strikes[proxy] = count
                false
            } else {
                strikes.remove(proxy)
                val i = active.indexOf(proxy)
                if (i >= 0) {
                    active.removeAt(i)
                    if (i < index) index--
                }
                true
            }
        }
        if (removed) {
            dropped.incrementAndGet()
            refillIfLow()
        }
        return removed
    }

    /** Stops any background testing. Call when the build is done. */
    fun close() {
        closed = true
    }

    fun summary(): String {
        val left = synchronized(this) { active.size }
        return "LokaleNow: Proxies: ${viaProxy.get()} translated via proxy, " +
                "${dropped.get()} dropped, ${refills.get()} refills, $left active"
    }

    private fun refillIfLow() {
        val low = synchronized(this) {
            active.size < (topCount / 2).coerceAtLeast(1) && candidates.isNotEmpty()
        }
        if (!low || closed || !refilling.compareAndSet(false, true)) return

        refills.incrementAndGet()
        Thread {
            try {
                findWorking(REFILL_TIME_SEC, REFILL_THREADS)
            } finally {
                refilling.set(false)
            }
        }.apply {
            isDaemon = true
            name = "lokalenow-proxy-refill"
        }.start()
    }

    /** Tests candidates in parallel until the list is full, time runs out, or candidates run out. */
    private fun findWorking(timeLimitSec: Long, threads: Int): Int {
        val tested = AtomicInteger(0)
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeLimitSec)
        fun keepGoing(): Boolean =
            !closed && System.nanoTime() < deadline && synchronized(this) { active.size < topCount }

        val pool = Executors.newFixedThreadPool(threads, DAEMON_THREADS)
        repeat(threads) {
            pool.execute {
                while (keepGoing()) {
                    val item = nextCandidate() ?: break
                    tested.incrementAndGet()
                    if (testProxy(item)) addActive(item.toProxy())
                }
            }
        }
        pool.shutdown()
        while (!pool.isTerminated && keepGoing()) {
            pool.awaitTermination(100, TimeUnit.MILLISECONDS)
        }
        pool.shutdownNow()
        return tested.get()
    }

    @Synchronized
    private fun nextCandidate(): ProxyItem? = candidates.removeFirstOrNull()

    @Synchronized
    private fun addActive(proxy: Proxy) {
        if (active.size < topCount && proxy !in active) active.add(proxy)
    }

    private fun fetchProxies(): List<ProxyItem> {
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

        return all.toList()
    }

    /** Sends a real translate request through the proxy. True if it returned a valid translation. */
    private fun testProxy(item: ProxyItem): Boolean {
        val client = baseClient.newBuilder()
            .proxy(item.toProxy())
            .connectTimeout(CONNECT_TIMEOUT_SEC, TimeUnit.SECONDS)
            .readTimeout(TEST_TIMEOUT_SEC, TimeUnit.SECONDS)
            .callTimeout(TEST_TIMEOUT_SEC + 2, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .build()
        val request = Request.Builder().url(TEST_URL).build()

        return try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string()
                response.isSuccessful && body != null && isValidTranslation(body)
            }
        } catch (e: Exception) {
            false
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
        private const val MAX_STRIKES = 2

        private const val THREADS = 200
        private const val REFILL_THREADS = 50
        private const val FETCH_TIMEOUT_SEC = 10L
        const val CONNECT_TIMEOUT_SEC = 4L
        private const val TEST_TIMEOUT_SEC = 6L
        private const val MAX_TEST_TIME_SEC = 60L
        private const val REFILL_TIME_SEC = 60L

        private val DAEMON_THREADS = ThreadFactory { r -> Thread(r).apply { isDaemon = true } }

        private const val PROXYSCRAPE_URL =
            "https://api.proxyscrape.com/v4/free-proxy-list/get?request=display_proxies&protocol=http&format=json"
        private const val PROXIFLY_URL =
            "https://raw.githubusercontent.com/proxifly/free-proxy-list/main/proxies/all/data.json"
        private const val TEST_URL =
            "https://translate.googleapis.com/translate_a/t?client=gtx&dt=t&sl=en&tl=de&q=hello"
    }
}