package com.farimarwat.lokalenow.utils

import com.farimarwat.lokalenow.models.LNode
import com.farimarwat.lokalenow.models.NodeType
import com.google.gson.JsonParser
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.net.Proxy
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class Translator private constructor(builder: Builder) {
    private val nodes: List<LNode>
    private val failedNames = mutableSetOf<String>()

    class Builder {
        val nodes = mutableListOf<LNode>()
        fun addNodes(nodeList: List<LNode>): Builder {
            nodes.addAll(nodeList)
            return this
        }

        fun build(): Translator = Translator(this)
    }

    init {
        nodes = builder.nodes
    }

    /**
     * Names of resources that could not be translated in the last translate() calls.
     * They are left out of the result so they get retried on the next build.
     */
    fun getFailedNames(): Set<String> = failedNames

    /**
     * Translates only translatable nodes. A node is returned only if all of its parts
     * translated successfully; failed nodes are recorded in [getFailedNames].
     */
    fun translate(lang: String): List<LNode> {
        val translatedNodes = mutableListOf<LNode>()
        val translatableNodes = nodes.filter { it.translatable }
        var totalItems = 0
        for (node in translatableNodes) {
            totalItems += when (node.type) {
                NodeType.STRING -> 1
                NodeType.PLURALS -> node.pluralItems.size
                NodeType.STRING_ARRAY -> node.arrayItems.size
            }
        }

        var done = 0
        fun progress() {
            done++
            System.out.print("\r  [${(done * 100) / totalItems}%] Translating for: $lang    ")
            System.out.flush()
        }

        for (node in translatableNodes) {
            when (node.type) {
                NodeType.STRING -> {
                    progress()
                    val translatedValue = NetworkHelper.getTranslation(lang, node.value, node.isCData)
                    if (translatedValue == null) {
                        failedNames.add(node.name)
                    } else {
                        translatedNodes.add(node.copy(value = translatedValue))
                    }
                }
                NodeType.PLURALS -> {
                    val translatedItems = linkedMapOf<String, String>()
                    var failed = false
                    for ((quantity, value) in node.pluralItems) {
                        progress()
                        val result = NetworkHelper.getTranslation(lang, value, quantity in node.cdataItems)
                        if (result == null) failed = true else translatedItems[quantity] = result
                    }
                    if (failed) {
                        failedNames.add(node.name)
                    } else {
                        translatedNodes.add(node.copy(pluralItems = translatedItems))
                    }
                }
                NodeType.STRING_ARRAY -> {
                    val translatedItems = mutableListOf<String>()
                    var failed = false
                    for ((index, value) in node.arrayItems.withIndex()) {
                        progress()
                        val result = NetworkHelper.getTranslation(lang, value, index.toString() in node.cdataItems)
                        if (result == null) failed = true else translatedItems.add(result)
                    }
                    if (failed) {
                        failedNames.add(node.name)
                    } else {
                        translatedNodes.add(node.copy(arrayItems = translatedItems))
                    }
                }
            }
        }
        if (totalItems > 0) System.out.println()
        return translatedNodes
    }
}

object NetworkHelper {
    internal val client = OkHttpClient()
    private const val MAX_ATTEMPTS = 3
    private const val MAX_PROXY_SWITCHES = 5
    private const val PROXY_CALL_TIMEOUT_SEC = 15L

    /** Set by the task when useProxy is on. Null means direct connection only. */
    @Volatile
    var proxyManager: ProxyManager? = null

    private val proxyClients = ConcurrentHashMap<Proxy, OkHttpClient>()

    /**
     * Translates one Android string value (as written inside the XML element) and returns it
     * ready to write back into strings.xml, or null if translation failed.
     */
    fun getTranslation(lang: String, value: String, isCData: Boolean): String? {
        val prepared = AndroidText.prepare(value, isCData)
        if (prepared.skip) return value
        val translated = requestTranslation(toGoogleLanguage(lang), prepared.text) ?: return null
        return AndroidText.finish(translated, prepared, isCData)
    }

    private fun requestTranslation(lang: String, text: String): String? {
        val urlEncodedString = URLEncoder.encode(text, StandardCharsets.UTF_8)
        val urlString =
            "https://translate.googleapis.com/translate_a/t?client=gtx&dt=t&sl=en&tl=$lang&q=$urlEncodedString"
        val request = Request.Builder()
            .url(urlString)
            .build()

        // Sticky: use the fastest proxy until it fails, then switch and retry the same text
        val manager = proxyManager
        var proxyTries = 0
        while (manager != null && proxyTries < MAX_PROXY_SWITCHES) {
            val proxy = manager.current() ?: break
            proxyTries++
            val result = execute(clientFor(proxy), request)
            if (result.text != null) {
                manager.markSuccess(proxy)
                return result.text
            }
            manager.markFailed(proxy, rateLimited = result.code == 429)
            proxyClients.remove(proxy)
        }

        // Direct: one last try after proxies, or normal retries when no proxy was used
        val directAttempts = if (proxyTries > 0) 1 else MAX_ATTEMPTS
        for (attempt in 1..directAttempts) {
            val result = execute(client, request)
            if (result.text != null) return result.text
            println("\n  LokaleNow: ${result.error} for '$lang' (attempt $attempt/$directAttempts)")
            if (attempt < directAttempts) Thread.sleep(1000L * attempt)
        }
        return null
    }

    private class CallResult(val text: String?, val code: Int, val error: String)

    private fun execute(callClient: OkHttpClient, request: Request): CallResult = try {
        callClient.newCall(request).execute().use { response ->
            if (response.isSuccessful) {
                val result = response.body?.string()?.let { parseResponse(it) }
                if (result != null) CallResult(result, response.code, "")
                else CallResult(null, response.code, "empty response")
            } else {
                CallResult(null, response.code, "HTTP ${response.code}")
            }
        }
    } catch (e: Exception) {
        CallResult(null, 0, e.message ?: e.javaClass.simpleName)
    }

    private fun clientFor(proxy: Proxy): OkHttpClient = proxyClients.getOrPut(proxy) {
        client.newBuilder()
            .proxy(proxy)
            .connectTimeout(ProxyManager.CONNECT_TIMEOUT_SEC, TimeUnit.SECONDS)
            .callTimeout(PROXY_CALL_TIMEOUT_SEC, TimeUnit.SECONDS)
            .build()
    }

    private fun parseResponse(body: String): String? {
        val first = JsonParser.parseString(body).asJsonArray.firstOrNull() ?: return null
        return if (first.isJsonArray) first.asJsonArray.firstOrNull()?.asString else first.asString
    }

    /**
     * Converts an Android values folder qualifier (values-in, values-zh-rCN, values-b+sr+Latn)
     * to the language code Google Translate expects.
     */
    fun toGoogleLanguage(lang: String): String {
        if (lang.startsWith("b+")) {
            return toGoogleLanguage(lang.removePrefix("b+").split("+").first())
        }
        val parts = lang.split("-r")
        val base = when (parts[0]) {
            "in" -> "id"
            "iw" -> "he"
            "ji" -> "yi"
            "nb" -> "no"
            else -> parts[0]
        }
        if (parts.size > 1 && base == "zh") return "zh-${parts[1]}"
        return base
    }
}

/**
 * Converts Android string resource text to plain text for translation and back,
 * protecting placeholders, HTML tags and escape sequences from being changed.
 */
object AndroidText {
    class Prepared(val text: String, val tokens: List<String>, val skip: Boolean = false)

    private val TAG_REGEX = Regex("""<xliff:g[^>]*>.*?</xliff:g>|<[^>]+>""", RegexOption.DOT_MATCHES_ALL)
    private val ESCAPE_REGEX = Regex("""\\[nt]|\\u[0-9a-fA-F]{4}""")
    private val FORMAT_REGEX = Regex("""%(\d+\$)?[-#+0,(<]*\d*(\.\d+)?([tT][a-zA-Z]|[a-zA-Z%])""")
    private val NAMED_ARG_REGEX = Regex("""\{[a-zA-Z_][a-zA-Z0-9_]*\}""")
    private val REFERENCE_REGEX = Regex("""^[@?][\w:/.+]+$""")
    private val TOKEN_REGEX = Regex("""[#＃]\s*[{｛]\s*(\p{Nd}+)\s*[}｝]""")

    fun prepare(value: String, isCData: Boolean): Prepared {
        val tokens = mutableListOf<String>()
        fun protect(input: String, regex: Regex): String = regex.replace(input) { match ->
            tokens.add(match.value)
            "#{${tokens.size - 1}}"
        }

        var text = protect(value, TAG_REGEX)
        text = protect(text, ESCAPE_REGEX)
        if (!isCData) text = decodeXmlEntities(text)
        text = unescapeAndroid(text)
        text = protect(text, FORMAT_REGEX)
        text = protect(text, NAMED_ARG_REGEX)

        val skip = text.isBlank() || REFERENCE_REGEX.matches(text.trim())
        return Prepared(text, tokens, skip)
    }

    /**
     * Returns the translated text ready for strings.xml, or null if any placeholder was lost.
     */
    fun finish(translated: String, prepared: Prepared, isCData: Boolean): String? {
        var text = escapeAndroid(decodeXmlEntities(translated))
        if (!isCData) text = escapeXml(text)

        val used = mutableSetOf<Int>()
        var broken = false
        text = TOKEN_REGEX.replace(text) { match ->
            val index = match.groupValues[1].map { Character.digit(it, 10) }.joinToString("").toIntOrNull()
            val original = index?.let { prepared.tokens.getOrNull(it) }
            if (original == null) {
                broken = true
                match.value
            } else {
                used.add(index)
                original
            }
        }
        if (broken || used.size != prepared.tokens.size) return null
        return text
    }

    private fun decodeXmlEntities(input: String): String {
        return input
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&#39;", "'")
            .replace("&amp;", "&")
    }

    private fun unescapeAndroid(input: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < input.length) {
            val c = input[i]
            if (c == '\\' && i + 1 < input.length) {
                sb.append(input[i + 1])
                i += 2
                continue
            }
            // Unescaped double quotes are removed by Android, so drop them here too
            if (c != '"') sb.append(c)
            i++
        }
        return sb.toString()
    }

    private fun escapeAndroid(input: String): String {
        val escaped = input
            .replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\"", "\\\"")
        return if (escaped.startsWith("@") || escaped.startsWith("?")) "\\$escaped" else escaped
    }

    private fun escapeXml(input: String): String {
        return input
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
    }
}