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
    private val mLayout: List<LayoutEntry>

    private sealed class LayoutEntry {
        data class Resource(val name: String) : LayoutEntry()
        data class Comment(val text: String) : LayoutEntry()
        object BlankLine : LayoutEntry()
    }

    class Builder(val projDir: File) {
        fun build(): PrimaryStringDocument = PrimaryStringDocument(this)
    }

    init {
        mProjDir = builder.projDir
        mStringsFile = File("${mProjDir}$VALUES_PATH$STRINGS_XML_FILE_NAME")

        mDocument = parseXmlFile(mStringsFile)
        mNodeHashes = loadNodeHashes()
        mAllNodes = loadStringXmlNodes()
        mModifiedNodes = listModifiedNodes()
        mNodesToDelete = listDeletedNodes()
        mLayout = loadLayout()
        mInputStream = null
    }

    private fun parseXmlFile(file: File): Document? {
        return try {
            val factory = DocumentBuilderFactory.newInstance()
            val documentBuilder = factory.newDocumentBuilder()
            FileInputStream(file).use { stream ->
                documentBuilder.parse(stream)
            }
        } catch (ex: Exception) {
            println("Error reading ${file.path}: $ex")
            null
        }
    }

    private fun loadStringXmlNodes(): List<LNode> = parseNodes(mDocument)

    private fun parseNodes(document: Document?): List<LNode> {
        val list = mutableListOf<LNode>()
        try {
            document?.let { doc ->
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

    /**
     * Records the order of resources, comments and blank lines in the original strings.xml,
     * so every language file can be written in the same layout.
     */
    private fun loadLayout(): List<LayoutEntry> {
        val layout = mutableListOf<LayoutEntry>()
        val doc = mDocument ?: return layout
        val children = doc.documentElement.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            when (child.nodeType) {
                Node.TEXT_NODE -> {
                    val text = child.nodeValue
                    if (text.isBlank()) {
                        val blankLines = text.count { it == '\n' } - 1
                        val isEdge = i == 0 || i == children.length - 1
                        if (!isEdge) repeat(blankLines.coerceAtLeast(0)) { layout.add(LayoutEntry.BlankLine) }
                    }
                }
                Node.COMMENT_NODE -> layout.add(LayoutEntry.Comment(child.nodeValue))
                Node.ELEMENT_NODE -> {
                    val element = child as Element
                    if (element.tagName in RESOURCE_TAGS) {
                        layout.add(LayoutEntry.Resource(element.getAttribute("name")))
                    }
                }
            }
        }
        return layout
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

    /**
     * Names of resources whose English text changed since the last run.
     * Empty when there are no saved hashes yet (first run or after clean),
     * so existing translations are kept instead of translating everything again.
     */
    fun getChangedNodeNames(): Set<String> {
        if (mNodeHashes.isEmpty()) return emptySet()
        return mModifiedNodes.map { it.name }.toSet()
    }

    /**
     * Reads the existing values-[lang]/strings.xml, if any, keyed by resource name.
     */
    fun readLocalized(lang: String): Map<String, LNode> {
        val file = getLocalizedFile(lang)
        if (!file.exists() || file.length() == 0L) return emptyMap()
        return parseNodes(parseXmlFile(file)).associateBy { it.name }
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

    private fun getLocalizedFile(lang: String): File {
        val localizedValuesDir = File(
            mProjDir,
            "src${File.separator}main${File.separator}res${File.separator}values-$lang"
        )
        return File(localizedValuesDir, STRINGS_XML_FILE_NAME)
    }

    /**
     * Writes values-[lang]/strings.xml in the same order as the original strings.xml,
     * including comments and blank lines. Resources missing from [nodesByName] are skipped.
     */
    fun saveLocalized(lang: String, nodesByName: Map<String, LNode>) {
        val translatedXmlFile = getLocalizedFile(lang)
        translatedXmlFile.parentFile.mkdirs()
        saveXmlFile(nodesByName, translatedXmlFile)
    }

    private fun saveXmlFile(nodesByName: Map<String, LNode>, outputFile: File) {
        val sb = StringBuilder()
        sb.appendLine("<?xml version=\"1.0\" encoding=\"utf-8\"?>")
        sb.appendLine("<!--$GENERATED_MARKER-->")
        sb.appendLine("<resources>")

        for (entry in mLayout) {
            when (entry) {
                is LayoutEntry.BlankLine -> sb.appendLine()
                is LayoutEntry.Comment -> sb.appendLine("    <!--${entry.text}-->")
                is LayoutEntry.Resource -> {
                    val node = nodesByName[entry.name] ?: continue
                    appendNode(sb, node)
                }
            }
        }

        sb.appendLine("</resources>")
        val content = sb.toString()
        if (outputFile.exists() && outputFile.readText() == content) return
        outputFile.writeText(content)
    }

    private fun appendNode(sb: StringBuilder, node: LNode) {
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
        const val GENERATED_MARKER = " Generated by LokaleNow "
        private val RESOURCE_TAGS = setOf("string", "plurals", "string-array")
        val VALUES_PATH =
            "${File.separator}src${File.separator}main${File.separator}res${File.separator}values${File.separator}"
        val PATH_RES =
            "${File.separator}src${File.separator}main${File.separator}res${File.separator}"

        /**
         * True only for strings.xml files this plugin wrote, so hand-made files are never deleted.
         */
        fun isGeneratedFile(file: File): Boolean {
            if (!file.exists()) return false
            return file.useLines { lines -> lines.take(3).any { it.contains(GENERATED_MARKER.trim()) } }
        }
    }
}