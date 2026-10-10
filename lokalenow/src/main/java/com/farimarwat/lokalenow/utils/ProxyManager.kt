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
 * Scans free proxies against the Google Translate endpoint and keeps the fastest ones.
 * Requests stick to the fastest proxy until it fails, then switch to the next fastest.
 * Failed proxies are replaced from a tested backup pool, so the build never waits.
 */
class ProxyManager(
    private val baseClient: OkHttpClient,
    topCount: Int = TOP_COUNT
) {
    private data class ProxyItem(val type: Proxy.Type, val host: String, val port: Int) {
        fun toProxy(): Proxy = Proxy(type, InetSocketAddress(host, port))
    }

    /** A tested proxy and how fast it answered the test request. */
    private class Entry(val proxy: Proxy, val latencyMs: Long)

    private val topCount = topCount.coerceIn(1, MAX_COUNT)

    // Fastest first. The first one is the proxy in use.
    private val active = mutableListOf<Entry>()
    // Tested spares, fastest first. Rate-limited ones are put at the end.
    private val backup = mutableListOf<Entry>()
    private val candidates = ArrayDeque<ProxyItem>()

    private val refilling = AtomicBoolean(false)
    @Volatile
    private var closed = false

    private val viaProxy = AtomicInteger(0)
    private val switches = AtomicInteger(0)
    private val rateLimitedCount = AtomicInteger(0)
    private val refills = AtomicInteger(0)

    /** Scans until [SCAN_TARGET] proxies work (or time runs out) and picks the fastest. */
    fun load(): Int {
        val fetched = fetchProxies().shuffled()
        if (fetched.isEmpty()) return 0

        synchronized(this) {
            candidates.clear()
            candidates.addAll(fetched)
            active.clear()
            backup.clear()
        }

        println("LokaleNow: Scanning proxies...")
        val found = mutableListOf<Entry>()
        val tested = findWorking(SCAN_TARGET, MAX_TEST_TIME_SEC, THREADS) { entry ->
            synchronized(found) { found.add(entry) }
        }
        val sorted = synchronized(found) { found.sortedBy { it.latencyMs } }

        val count = synchronized(this) {
            active.addAll(sorted.take(topCount))
            backup.addAll(sorted.drop(topCount))
            active.size
        }
        println("LokaleNow: Using $count proxies, ${sorted.size - count} in backup (tested $tested of ${fetched.size})")
        return count
    }

    /** The proxy to use now (the fastest active one), or null if none are left. */
    @Synchronized
    fun current(): Proxy? = active.firstOrNull()?.proxy

    @Synchronized
    fun hasProxies(): Boolean = active.isNotEmpty()

    fun markSuccess(proxy: Proxy) {
        viaProxy.incrementAndGet()
    }

    /**
     * Removes the failed proxy and fills its place from backup.
     * Rate-limited proxies go to the end of backup, since they usually recover.
     */
    fun markFailed(proxy: Proxy, rateLimited: Boolean) {
        val needRefill = synchronized(this) {
            val i = active.indexOfFirst { it.proxy == proxy }
            if (i >= 0) {
                active.removeAt(i)
                if (rateLimited) backup.add(Entry(proxy, Long.MAX_VALUE))
            }
            while (active.size < topCount) {
                val next = backup.removeFirstOrNull() ?: break
                insertSorted(active, next)
            }
            backup.isEmpty() && candidates.isNotEmpty()
        }
        switches.incrementAndGet()
        if (rateLimited) rateLimitedCount.incrementAndGet()
        if (needRefill) refillInBackground()
    }

    /** Stops any background testing. Call when the build is done. */
    fun close() {
        closed = true
    }

    fun summary(): String {
        val (left, spare) = synchronized(this) { active.size to backup.size }
        return "LokaleNow: Proxies: ${viaProxy.get()} translated via proxy, " +
                "${switches.get()} switches (${rateLimitedCount.get()} rate-limited), " +
                "${refills.get()} refills, $left active, $spare backup"
    }

    private fun refillInBackground() {
        if (closed || !refilling.compareAndSet(false, true)) return
        refills.incrementAndGet()
        Thread {
            try {
                findWorking(REFILL_TARGET, REFILL_TIME_SEC, REFILL_THREADS) { entry ->
                    synchronized(this@ProxyManager) {
                        if (active.size < topCount) insertSorted(active, entry) else insertSorted(backup, entry)
                    }
                }
            } finally {
                refilling.set(false)
            }
        }.apply {
            isDaemon = true
            name = "lokalenow-proxy-refill"
        }.start()
    }

    /** Tests candidates in parallel until [target] pass, time runs out, or candidates run out. */
    private fun findWorking(
        target: Int,
        timeLimitSec: Long,
        threads: Int,
        onFound: (Entry) -> Unit
    ): Int {
        val tested = AtomicInteger(0)
        val found = AtomicInteger(0)
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeLimitSec)
        fun keepGoing(): Boolean = !closed && System.nanoTime() < deadline && found.get() < target

        val pool = Executors.newFixedThreadPool(threads, DAEMON_THREADS)
        repeat(threads) {
            pool.execute {
                while (keepGoing()) {
                    val item = nextCandidate() ?: break
                    tested.incrementAndGet()
                    val ms = testProxy(item) ?: continue
                    if (found.incrementAndGet() <= target) onFound(Entry(item.toProxy(), ms))
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

    private fun insertSorted(list: MutableList<Entry>, entry: Entry) {
        val i = list.indexOfFirst { it.latencyMs > entry.latencyMs }
        if (i < 0) list.add(entry) else list.add(i, entry)
    }

    @Synchronized
    private fun nextCandidate(): ProxyItem? = candidates.removeFirstOrNull()

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

    /** Sends a real translate request through the proxy. Returns latency in ms if it worked. */
    private fun testProxy(item: ProxyItem): Long? {
        val client = baseClient.newBuilder()
            .proxy(item.toProxy())
            .connectTimeout(CONNECT_TIMEOUT_SEC, TimeUnit.SECONDS)
            .readTimeout(TEST_TIMEOUT_SEC, TimeUnit.SECONDS)
            .callTimeout(TEST_TIMEOUT_SEC + 2, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .build()
        val request = Request.Builder().url(TEST_URL).build()

        return try {
            val start = System.nanoTime()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string()
                if (response.isSuccessful && body != null && isValidTranslation(body)) {
                    (System.nanoTime() - start) / 1_000_000
                } else {
                    null
                }
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
        private const val SCAN_TARGET = 100
        private const val REFILL_TARGET = 30

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