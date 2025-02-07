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

/**
 * A class that provides functionality for handling XML documents related to localization,
 * specifically managing strings.xml files, hash calculations for modification detection,
 * and saving localized versions of XML files.
 *
 * @property mProjDir The project directory containing the XML files.
 * @property mStringsFile The localized strings.xml file.
 * @property mOriginalXmlFile The original strings.xml file.
 * @property mDocument The XML document object created from the strings.xml file.
 * @property mInputStream Input stream, not currently used in the class.
 */
class PrimaryStringDocument private constructor(builder: Builder) {
    private val mProjDir: File
    private val mStringsFile: File
    private val mDocument: Document?
    private val mInputStream: InputStream?
    private var mAllNodes:List<LNode>? = null
    private val mNodeHashes:Map<String,Int>
    private val mModifiedNodes:List<LNode>

    /**
     * Builder pattern for constructing an [PrimaryStringDocument] instance.
     *
     * @property projDir The project directory containing the XML files.
     */
    class Builder(val projDir: File) {
        /**
         * Builds and returns an instance of [PrimaryStringDocument].
         *
         * @return The created [PrimaryStringDocument] instance.
         */
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
        mInputStream = null
    }

    /**
     * Lists all elements in the XML document, converting them into [LNode] objects.
     * The elements include attributes like `name`, `value`, and `translatable`.
     *
     * @return A list of [LNode] objects representing the XML elements.
     */
    private fun loadStringXmlNodes(): List<LNode> {
        val list = mutableListOf<LNode>()
        try {
            mDocument?.let { doc ->
                val nodes = doc.documentElement.childNodes
                list.addAll((0 until nodes.length)
                    .mapNotNull { nodes.item(it) as? Element }
                    .map {
                        val name = it.getAttribute("name")
                        val value = it.textContent
                        val translatable = if (it.hasAttribute("translatable")) {
                            val translatableAtr = it.getAttributeNode("translatable")
                            translatableAtr.value.toBoolean()
                        } else {
                            true
                        }
                        LNode(name, value, translatable)
                    }
                )
            }
        } catch (ex: Exception) {
            println("Error listing elements: $ex")
        }
        return list
    }
    fun getAllNodes():List<LNode>?{
        return mAllNodes
    }
    fun getModifiedNodes():List<LNode>?{
        return if(mModifiedNodes.isNotEmpty()) mModifiedNodes else null
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

    private fun listModifiedNodes():List<LNode>{
        val list = mutableListOf<LNode>()
        mAllNodes?.let{ allNodes ->
            for(item in allNodes){
                val hash = mNodeHashes[item.name]
                if(hash != item.value.hashCode()){
                    list.add(item)
                }
            }
        }
        return list
    }

    /**
     * Determines whether the strings.xml file has been modified by comparing the current
     * file hash with the stored hash.
     *
     * @return `true` if the file has been modified, otherwise `false`.
     */
    fun isModified(): Boolean {
        val hashFile = getHashFile(STRINGS_XML_HASH_FILE_NAME)
        val currentHash = mStringsFile.calculateFileHash()
        if (!hashFile.exists()) {
            return true
        }
        val storedHash = hashFile.readText().trim() // Ensure any extra whitespace is removed
        return storedHash != currentHash
    }


    /**
     * Saves the current hash of the strings.xml file to detect modifications later.
     */
    fun saveHashes() {
        val hashFile = getHashFile(STRINGS_XML_HASH_FILE_NAME)
        val currentHash = mStringsFile.calculateFileHash()
        hashFile.writeText(currentHash)
        saveNodeHashes()
    }

    private fun saveNodeHashes() {
        val nodeHashFile = getHashFile(NODES_HASH_FILE_NAME)
        val nodes = loadStringXmlNodes()
        val content = nodes.joinToString("\n") { "${it.name}:${it.value.hashCode()}" }
        try {
            nodeHashFile.writeText(content)
        } catch (e: Exception) {
            println("Error writing to file: ${e.message}")
        }
    }


    /**
     * Returns the file used to store the hash of the strings.xml file.
     *
     * @return The hash file.
     */
    private fun getHashFile(hashFileName:String): File {
        val hashDir = getHashDirectory()
        return File(hashDir, hashFileName)
    }

    /**
     * Returns the directory used for storing hash files.
     *
     * @return The hash directory.
     */
    private fun getHashDirectory(): File {
        val buildDir = File(mProjDir, "build")
        buildDir.mkdirs()

        val hashDir = File(buildDir, "hashes")
        hashDir.mkdirs()
        return hashDir
    }

    /**
     * Saves the localized version of the XML file with translated string values.
     *
     * @param lang The language code for the localization (e.g., "en", "fr").
     * @param translatedNodes A list of translated [LNode] objects.
     */
    fun saveLocalized(lang: String, translatedNodes: List<LNode>) {
        val localizedValuesDir = File(
            mProjDir,
            "src${File.separator}main${File.separator}res${File.separator}values-$lang"
        )
        localizedValuesDir.mkdirs()

        val translatedXmlFile = File(localizedValuesDir, STRINGS_XML_FILE_NAME)
        saveXmlFile(translatedNodes, translatedXmlFile)
    }

    /**
     * Saves the XML file with the specified list of nodes to the provided output file.
     *
     * @param nodes The list of [LNode] objects to save.
     * @param outputFile The output file to save the XML content to.
     */
    private fun saveXmlFile(nodes: List<LNode>, outputFile: File) {
        val docFactory = DocumentBuilderFactory.newInstance()
        val docBuilder = docFactory.newDocumentBuilder()
        val doc: Document
        val rootElement: Element

        if (outputFile.exists() && outputFile.length() > 0) {
            doc = docBuilder.parse(outputFile)
            rootElement = doc.documentElement
            doc.normalizeDocument()
        } else {
            doc = docBuilder.newDocument()
            rootElement = doc.createElement("resources")
            doc.appendChild(rootElement)
        }

        // Remove empty text nodes (to clean existing file)
        removeWhitespaceNodes(rootElement)

        val existingNodes = mutableSetOf<String>()
        val nodeList = rootElement.getElementsByTagName("string")
        for (i in 0 until nodeList.length) {
            val element = nodeList.item(i) as Element
            existingNodes.add(element.getAttribute("name"))
        }

        for (node in nodes) {
            if (!existingNodes.contains(node.name)) {
                val element = doc.createElement("string")
                element.setAttribute("name", node.name)
                element.textContent = node.value
                rootElement.appendChild(element)
            }
        }

        // Transformer settings to completely remove extra spaces & blank lines
        val transformer = TransformerFactory.newInstance().newTransformer()
        transformer.setOutputProperty(OutputKeys.INDENT, "yes") // No indentation
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2") // Set indent level
        val source = DOMSource(doc)
        val result = StreamResult(outputFile)
        transformer.transform(source, result)
    }

    // Function to remove all blank text nodes (whitespace-only)
    private fun removeWhitespaceNodes(node: Node) {
        val children = node.childNodes
        for (i in children.length - 1 downTo 0) { // Iterate in reverse to avoid skipping nodes
            val child = children.item(i)
            if (child.nodeType == Node.TEXT_NODE && child.textContent.trim().isEmpty()) {
                node.removeChild(child)
            } else if (child.nodeType == Node.ELEMENT_NODE) {
                removeWhitespaceNodes(child) // Recursively clean child elements
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
