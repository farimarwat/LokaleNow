package com.farimarwat.lokalenow.utils

import com.farimarwat.lokalenow.models.LNode
import com.farimarwat.lokalenow.models.NodeType
import com.google.gson.JsonParser
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
class Translator private constructor(builder: Builder) {
    private val nodes: List<LNode>

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

    fun translate(lang: String): List<LNode> {
        val translatedNodes = mutableListOf<LNode>()
        var totalItems = 0
        for (node in nodes) {
            if (!node.translatable) continue
            totalItems += when (node.type) {
                NodeType.STRING -> 1
                NodeType.PLURALS -> node.pluralItems.size
                NodeType.STRING_ARRAY -> node.arrayItems.size
            }
        }

        var done = 0

        for (node in nodes) {
            if (!node.translatable) {
                translatedNodes.add(node)
                continue
            }

            when (node.type) {
                NodeType.STRING -> {
                    done++
                    System.out.print("\r  [${ (done * 100) / totalItems }%] Translating for: $lang    ")
                    System.out.flush()
                    val translatedValue = if (node.isCData) {
                        NetworkHelper.getTranslationRaw(lang, node.value)
                    } else {
                        NetworkHelper.getTranslation(lang, node.value)
                    }
                    translatedNodes.add(node.copy(value = translatedValue ?: node.value))
                }
                NodeType.PLURALS -> {
                    val translatedItems = linkedMapOf<String, String>()
                    for ((quantity, value) in node.pluralItems) {
                        done++
                        System.out.print("\r  [${ (done * 100) / totalItems }%] Translating for: $lang    ")
                        System.out.flush()
                        val result = NetworkHelper.getTranslation(lang, value)
                        translatedItems[quantity] = result ?: value
                    }
                    translatedNodes.add(node.copy(pluralItems = translatedItems))
                }
                NodeType.STRING_ARRAY -> {
                    val translatedItems = mutableListOf<String>()
                    for (value in node.arrayItems) {
                        done++
                        System.out.print("\r  [${ (done * 100) / totalItems }%] Translating for: $lang    ")
                        System.out.flush()
                        val result = NetworkHelper.getTranslation(lang, value)
                        translatedItems.add(result ?: value)
                    }
                    translatedNodes.add(node.copy(arrayItems = translatedItems))
                }
            }
        }
        System.out.println()
        return translatedNodes
    }
}

object NetworkHelper {
    private val client = OkHttpClient()

    fun getTranslation(lang: String, text: String): String? {
        val raw = getRawTranslation(lang, text) ?: return null
        return escapeForXml(raw)
    }

    fun getTranslationRaw(lang: String, text: String): String? {
        return getRawTranslation(lang, text)
    }

    private fun getRawTranslation(lang: String, text: String): String? {
        val plainText = decodeXmlEntities(text)
        val urlEncodedString = URLEncoder.encode(plainText, StandardCharsets.UTF_8)
        val urlString =
            "https://translate.googleapis.com/translate_a/t?client=gtx&dt=t&sl=en&tl=$lang&q=$urlEncodedString"
        val request = Request.Builder()
            .url(urlString)
            .build()
        try {
            val response = client.newCall(request).execute()
            val jsonResponse = response.body?.string()
            val jsonArray = JsonParser.parseString(jsonResponse).asJsonArray
            val translated = jsonArray.firstOrNull()?.asString ?: return null
            return decodeXmlEntities(translated)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    private fun decodeXmlEntities(input: String): String {
        return input
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&#39;", "'")
    }

    private fun escapeForXml(input: String): String {
        return input
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("'", "\\'")
    }
}