package com.farimarwat.lokalenow.utils

import com.farimarwat.lokalenow.models.LNode
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.security.MessageDigest
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
class LDocument private constructor(builder: Builder) {
    private val mProjDir: File
    private val mStringsFile: File
    private val mOriginalXmlFile: File
    private val mDocument: Document?
    private val mInputStream: InputStream?

    /**
     * Builder pattern for constructing an [LDocument] instance.
     *
     * @property projDir The project directory containing the XML files.
     */
    class Builder(val projDir: File) {
        /**
         * Builds and returns an instance of [LDocument].
         *
         * @return The created [LDocument] instance.
         */
        fun build(): LDocument = LDocument(this)
    }

    init {
        mProjDir = builder.projDir
        mStringsFile = File("${mProjDir}${PATH_VALUES}${STRINGS_XML}")
        mOriginalXmlFile = File("${mProjDir}${PATH_VALUES}${STRINGS_XML}")

        // Copy the original XML file to the "hashes" directory
        val hashDir = getHashDirectory()
        mStringsFile.copyTo(File(hashDir, STRINGS_XML), true)

        mDocument = try {
            val factory = DocumentBuilderFactory.newInstance()
            val dbuilder = factory.newDocumentBuilder()
            FileInputStream(mStringsFile).use { stream ->
                dbuilder.parse(stream)
            }
        } catch (ex: Exception) {
            println("Error creating LDocument: $ex")
            null
        }
        mInputStream = null
    }

    /**
     * Lists all elements in the XML document, converting them into [LNode] objects.
     * The elements include attributes like `name`, `value`, and `translatable`.
     *
     * @return A list of [LNode] objects representing the XML elements.
     */
    fun listElements(): List<LNode> {
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

    /**
     * Determines whether the strings.xml file has been modified by comparing the current
     * file hash with the stored hash.
     *
     * @return `true` if the file has been modified, otherwise `false`.
     */
    fun isModified(): Boolean {
        val hashFile = getHashFile()
        val currentHash = calculateFileHash(mStringsFile)

        // If the hash file does not exist (e.g., it was deleted during clean project)
        if (!hashFile.exists()) {
            return true // Consider the file modified if the hash file is missing
        }

        // If the hash file exists, compare its stored hash with the current hash
        val storedHash = hashFile.readText().trim() // Ensure any extra whitespace is removed
        return storedHash != currentHash
    }


    /**
     * Saves the current hash of the strings.xml file to detect modifications later.
     */
    fun saveCurrentHash() {
        val hashFile = getHashFile()
        val currentHash = calculateFileHash(mStringsFile)
        hashFile.writeText(currentHash)
        saveOriginalXml()
    }

    /**
     * Returns the file used to store the hash of the strings.xml file.
     *
     * @return The hash file.
     */
    private fun getHashFile(): File {
        val hashDir = getHashDirectory()
        val hashFileName = "${mStringsFile.nameWithoutExtension}.hash"
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
     * Calculates the SHA-256 hash of the specified file.
     *
     * @param file The file to calculate the hash for.
     * @return The calculated hash as a hexadecimal string.
     */
    private fun calculateFileHash(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(8192)
        FileInputStream(file).use { inputStream ->
            var bytesRead: Int
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().encodeHex()
    }

    /**
     * Saves the original strings.xml file to the hash directory.
     */
    fun saveOriginalXml() {
        val hashDir = getHashDirectory()
        val originalXmlFile = File(hashDir, STRINGS_XML)
        mStringsFile.copyTo(originalXmlFile, true)
    }

    /**
     * Encodes the byte array as a hexadecimal string.
     *
     * @return The hexadecimal representation of the byte array.
     */
    private fun ByteArray.encodeHex(): String {
        return joinToString("") { "%02x".format(it) }
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

        val translatedXmlFile = File(localizedValuesDir, STRINGS_XML)
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

        val doc = docBuilder.newDocument()
        val rootElement = doc.createElement("resources")
        doc.appendChild(rootElement)

        for (node in nodes) {
            val element = doc.createElement("string")
            element.setAttribute("name", node.name)
            element.textContent = node.value
            rootElement.appendChild(element)
        }

        val transformer = TransformerFactory.newInstance().newTransformer()
        transformer.setOutputProperty(OutputKeys.INDENT, "yes")

        val source = DOMSource(doc)
        val result = StreamResult(outputFile)

        transformer.transform(source, result)
    }

    /**
     * Determines whether a localization update is needed based on the presence of the
     * strings.xml file for the specified language codes.
     *
     * @param languageCodes A list of language codes to check.
     * @param projDir The project directory to check for the existence of the localized files.
     * @return `true` if an update is needed, otherwise `false`.
     */
    fun shouldUpdate(languageCodes: List<String>, projDir: File): Boolean {
        for (languageCode in languageCodes) {
            val languageFolder = File(projDir, "${PATH_RES}values-$languageCode")
            val stringsXml = File(languageFolder, LDocument.STRINGS_XML)
            if (!stringsXml.exists()) {
                return true
            }
        }

        return false
    }

    companion object {
        const val STRINGS_XML = "strings.xml"
        const val NAME = "strings"
        val PATH_VALUES =
            "${File.separator}src${File.separator}main${File.separator}res${File.separator}values${File.separator}"
        val PATH_RES =
            "${File.separator}src${File.separator}main${File.separator}res${File.separator}"
    }
}
