package com.farimarwat.lokalenow.utils

import com.farimarwat.lokalenow.models.LNode
import okhttp3.OkHttpClient
import okhttp3.Request
import com.google.gson.JsonParser

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
            val translatedValue = NetworkHelper.getTranslation(lang, node.value)
            translatedNodes.add(LNode(node.name, translatedValue ?: node.value))
        }
        return translatedNodes
    }


}


object NetworkHelper {
    private val client = OkHttpClient()

    fun getTranslation(lang: String, text: String): String? {
        val urlString = "https://translate.googleapis.com/translate_a/t?client=gtx&dt=t&sl=en&tl=$lang&q=$text"
        val request = Request.Builder()
            .url(urlString)
            .build()
        try {
            val response = client.newCall(request).execute()
            val jsonResponse = response.body?.string()

            // Parse JSON using Gson JsonParser
            val jsonArray = JsonParser.parseString(jsonResponse).asJsonArray
            return sanitizeXmlString(jsonArray.firstOrNull()?.asString)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    private fun sanitizeXmlString(input: String?): String? {
        return input?.replace("&", "&amp;")
            ?.replace("<", " &lt;")
            ?.replace(">", " &gt;")
            ?.replace("\"", " &quot;")
            ?.replace("'", " &apos;")
            ?.replace("\u0022", " &quot;") // Unicode escape for double quote
            ?.replace("\u0027", " &apos;") // Unicode escape for single quote
    }
}
