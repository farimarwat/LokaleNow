package com.farimarwat.lokalenow.models

import com.farimarwat.lokalenow.utils.calculateFileHash
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
class PrimaryStringDocument private constructor(builder: Builder) {
    private val mProjDir: File
    private val mStringsFile: File
    private val mDocument: Document?
    private val mInputStream: InputStream?
    private var mAllNodes: List<LNode>? = null
    private val mNodeHashes: Map<String, Int>
    private val mModifiedNodes: List<LNode>
    private var mNodesToDelete: List<String>? = null

    class Builder(val projDir: File) {
        fun build(): PrimaryStringDocument = PrimaryStringDocument(this)
    }

    init {
        mProjDir = builder.projDir
        mStringsFile = File("${mProjDir}$VALUES_PATH$STRINGS_XML_FILE_NAME")

        mDocument = try {
            val factory = DocumentBuilderFactory.newInstance()
            val documentBuilder = factory.newDocumentBuilder()
            FileInputStream(mStringsFile).use { stream ->
                documentBuilder.parse(stream)
            }
        } catch (ex: Exception) {
            println("Error creating LDocument: $ex")
            null
        }
        mNodeHashes = loadNodeHashes()
        mAllNodes = loadStringXmlNodes()
        mModifiedNodes = listModifiedNodes()
        mNodesToDelete = listDeletedNodes()
        mInputStream = null
    }

    private fun loadStringXmlNodes(): List<LNode> {
        val list = mutableListOf<LNode>()
        try {
            mDocument?.let { doc ->
                val nodes = doc.documentElement.childNodes
                for (i in 0 until nodes.length) {
                    val node = nodes.item(i) as? Element ?: continue
                    val name = node.getAttribute("name")
                    val translatable = if (node.hasAttribute("translatable")) {
                        node.getAttribute("translatable").toBoolean()
                    } else {
                        true
                    }

                    when (node.tagName) {
                        "string" -> {
                            val isCData = hasCDataChild(node)
                            val value = if (isCData) getCDataContent(node) else getInnerXml(node)
                            list.add(
                                LNode(
                                    name = name,
                                    value = value,
                                    translatable = translatable,
                                    type = NodeType.STRING,
                                    isCData = isCData
                                )
                            )
                        }
                        "plurals" -> {
                            val items = linkedMapOf<String, String>()
                            val children = node.childNodes
                            for (j in 0 until children.length) {
                                val child = children.item(j) as? Element ?: continue
                                if (child.tagName == "item") {
                                    val quantity = child.getAttribute("quantity")
                                    val isCData = hasCDataChild(child)
                                    val value = if (isCData) getCDataContent(child) else getInnerXml(child)
                                    items[quantity] = value
                                }
                            }
                            list.add(
                                LNode(
                                    name = name,
                                    value = "",
                                    translatable = translatable,
                                    type = NodeType.PLURALS,
                                    pluralItems = items
                                )
                            )
                        }
                        "string-array" -> {
                            val items = mutableListOf<String>()
                            val children = node.childNodes
                            for (j in 0 until children.length) {
                                val child = children.item(j) as? Element ?: continue
                                if (child.tagName == "item") {
                                    items.add(getInnerXml(child))
                                }
                            }
                            list.add(
                                LNode(
                                    name = name,
                                    value = "",
                                    translatable = translatable,
                                    type = NodeType.STRING_ARRAY,
                                    arrayItems = items
                                )
                            )
                        }
                    }
                }
            }
        } catch (ex: Exception) {
            println("Error listing elements: $ex")
        }
        return list
    }

    private fun hasCDataChild(element: Element): Boolean {
        val children = element.childNodes
        for (i in 0 until children.length) {
            if (children.item(i).nodeType == Node.CDATA_SECTION_NODE) return true
        }
        return false
    }

    private fun getCDataContent(element: Element): String {
        val children = element.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeType == Node.CDATA_SECTION_NODE) return child.nodeValue
        }
        return element.textContent
    }

    private fun getInnerXml(element: Element): String {
        val transformer = TransformerFactory.newInstance().newTransformer()
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes")
        val sb = StringBuilder()
        val children = element.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            val writer = java.io.StringWriter()
            transformer.transform(DOMSource(child), StreamResult(writer))
            sb.append(writer.toString())
        }
        return sb.toString()
    }

    fun getAllNodes(): List<LNode>? = mAllNodes

    fun getModifiedNodes(): List<LNode>? {
        return if (mModifiedNodes.isNotEmpty()) mModifiedNodes else null
    }

    private fun loadNodeHashes(): Map<String, Int> {
        val nodeHashFile = getHashFile(NODES_HASH_FILE_NAME)
        val map = mutableMapOf<String, Int>()
        if (nodeHashFile.exists()) {
            nodeHashFile.forEachLine { line ->
                val parts = line.split(":")
                if (parts.size == 2) {
                    val key = parts[0]
                    val value = parts[1].toIntOrNull()
                    if (value != null) {
                        map[key] = value
                    }
                }
            }
        }
        return map
    }

    private fun listModifiedNodes(): List<LNode> {
        val list = mutableListOf<LNode>()
        mAllNodes?.let { allNodes ->
            for (item in allNodes) {
                val hash = mNodeHashes[item.name]
                val currentHash = when (item.type) {
                    NodeType.STRING -> item.value.hashCode()
                    NodeType.PLURALS -> item.pluralItems.hashCode()
                    NodeType.STRING_ARRAY -> item.arrayItems.hashCode()
                }
                if (hash != currentHash) {
                    list.add(item)
                }
            }
        }
        return list
    }

    private fun listDeletedNodes(): List<String>? {
        val list = mutableListOf<String>()
        mAllNodes?.let { allNodes ->
            for (item in mNodeHashes) {
                if (allNodes.find { it.name == item.key } == null) {
                    list.add(item.key)
                }
            }
            return list
        }
        return null
    }

    fun isModified(): Boolean {
        val hashFile = getHashFile(STRINGS_XML_HASH_FILE_NAME)
        val currentHash = mStringsFile.calculateFileHash()
        if (!hashFile.exists()) return true
        val storedHash = hashFile.readText().trim()
        return storedHash != currentHash
    }

    fun saveHashes() {
        val hashFile = getHashFile(STRINGS_XML_HASH_FILE_NAME)
        val currentHash = mStringsFile.calculateFileHash()
        hashFile.writeText(currentHash)
        saveNodeHashes()
    }

    private fun saveNodeHashes() {
        val nodeHashFile = getHashFile(NODES_HASH_FILE_NAME)
        val nodes = loadStringXmlNodes()
        val content = nodes.joinToString("\n") { node ->
            val hash = when (node.type) {
                NodeType.STRING -> node.value.hashCode()
                NodeType.PLURALS -> node.pluralItems.hashCode()
                NodeType.STRING_ARRAY -> node.arrayItems.hashCode()
            }
            "${node.name}:$hash"
        }
        try {
            nodeHashFile.writeText(content)
        } catch (e: Exception) {
            println("Error writing to file: ${e.message}")
        }
    }

    private fun getHashFile(hashFileName: String): File {
        val hashDir = getHashDirectory()
        return File(hashDir, hashFileName)
    }

    private fun getHashDirectory(): File {
        val buildDir = File(mProjDir, "build")
        buildDir.mkdirs()
        val hashDir = File(buildDir, "hashes")
        hashDir.mkdirs()
        return hashDir
    }

    fun saveLocalized(lang: String, translatedNodes: List<LNode>) {
        val localizedValuesDir = File(
            mProjDir,
            "src${File.separator}main${File.separator}res${File.separator}values-$lang"
        )
        localizedValuesDir.mkdirs()
        val translatedXmlFile = File(localizedValuesDir, STRINGS_XML_FILE_NAME)
        saveXmlFile(translatedNodes, translatedXmlFile)
    }

    private fun saveXmlFile(nodes: List<LNode>, outputFile: File) {
        val sb = StringBuilder()
        sb.appendLine("<?xml version=\"1.0\" encoding=\"utf-8\"?>")
        sb.appendLine("<resources>")

        for (node in nodes) {
            when (node.type) {
                NodeType.STRING -> {
                    val value = if (node.isCData) {
                        "<![CDATA[${node.value}]]>"
                    } else {
                        node.value
                    }
                    sb.appendLine("    <string name=\"${node.name}\">$value</string>")
                }
                NodeType.PLURALS -> {
                    sb.appendLine("    <plurals name=\"${node.name}\">")
                    for ((quantity, value) in node.pluralItems) {
                        sb.appendLine("        <item quantity=\"$quantity\">$value</item>")
                    }
                    sb.appendLine("    </plurals>")
                }
                NodeType.STRING_ARRAY -> {
                    sb.appendLine("    <string-array name=\"${node.name}\">")
                    for (value in node.arrayItems) {
                        sb.appendLine("        <item>$value</item>")
                    }
                    sb.appendLine("    </string-array>")
                }
            }
        }

        sb.appendLine("</resources>")
        outputFile.writeText(sb.toString())
    }

    private fun removeWhitespaceNodes(node: Node) {
        val children = node.childNodes
        for (i in children.length - 1 downTo 0) {
            val child = children.item(i)
            if (child.nodeType == Node.TEXT_NODE && child.textContent.trim().isEmpty()) {
                node.removeChild(child)
            } else if (child.nodeType == Node.ELEMENT_NODE) {
                removeWhitespaceNodes(child)
            }
        }
    }

    companion object {
        const val STRINGS_XML_FILE_NAME = "strings.xml"
        const val STRINGS_XML_HASH_FILE_NAME = "strings.hash"
        const val NODES_HASH_FILE_NAME = "nodes.hash"
        val VALUES_PATH =
            "${File.separator}src${File.separator}main${File.separator}res${File.separator}values${File.separator}"
        val PATH_RES =
            "${File.separator}src${File.separator}main${File.separator}res${File.separator}"
    }
}