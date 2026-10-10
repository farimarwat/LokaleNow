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
        for (node in nodes) {
            if (!node.translatable) {
                translatedNodes.add(node)
                continue
            }

            when (node.type) {
                NodeType.STRING -> {
                    val translatedValue = if (node.isCData) {
                        NetworkHelper.getTranslationRaw(lang, node.value)
                    } else {
                        NetworkHelper.getTranslation(lang, node.value)
                    }
                    translatedNodes.add(
                        node.copy(value = translatedValue ?: node.value)
                    )
                }
                NodeType.PLURALS -> {
                    val translatedItems = linkedMapOf<String, String>()
                    for ((quantity, value) in node.pluralItems) {
                        val translated = NetworkHelper.getTranslation(lang, value)
                        translatedItems[quantity] = translated ?: value
                    }
                    translatedNodes.add(
                        node.copy(pluralItems = translatedItems)
                    )
                }
                NodeType.STRING_ARRAY -> {
                    val translatedItems = mutableListOf<String>()
                    for (value in node.arrayItems) {
                        val translated = NetworkHelper.getTranslation(lang, value)
                        translatedItems.add(translated ?: value)
                    }
                    translatedNodes.add(
                        node.copy(arrayItems = translatedItems)
                    )
                }
            }
        }
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