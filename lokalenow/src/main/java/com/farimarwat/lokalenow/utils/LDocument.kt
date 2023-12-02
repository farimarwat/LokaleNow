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

class LDocument private constructor(builder: Builder) {
    private val mProjDir: File
    private val mStringsFile: File
    private val mOriginalXmlFile: File
    private val mDocument: Document?
    private val mInputStream: InputStream?

    class Builder(val projDir: File) {
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
                        LNode(name, value)
                    }
                )
            }
        } catch (ex: Exception) {
            println("Error listing elements: $ex")
        }
        return list
    }

    // Modification detection
    fun isModified(): Boolean {
        val hashFile = getHashFile()
        val currentHash = calculateFileHash(mStringsFile)

        if (hashFile.exists()) {
            val storedHash = hashFile.readText()
            return storedHash != currentHash
        }
        return true
    }

    fun saveCurrentHash() {
        val hashFile = getHashFile()
        val currentHash = calculateFileHash(mStringsFile)
        hashFile.writeText(currentHash)
        saveOriginalXml()
    }

    private fun getHashFile(): File {
        val hashDir = getHashDirectory()
        val hashFileName = "${mStringsFile.nameWithoutExtension}.hash"
        return File(hashDir, hashFileName)
    }

    private fun getHashDirectory(): File {
        val buildDir = File(mProjDir, "build")
        buildDir.mkdirs()

        val hashDir = File(buildDir, "hashes")
        hashDir.mkdirs()
        return hashDir
    }

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

    fun saveOriginalXml() {
        val hashDir = getHashDirectory()
        val originalXmlFile = File(hashDir, STRINGS_XML)
        mStringsFile.copyTo(originalXmlFile, true)
    }

    private fun ByteArray.encodeHex(): String {
        return joinToString("") { "%02x".format(it) }
    }

    //End Modification

    //Save localized file
    fun saveLocalized(lang: String, translatedNodes: List<LNode>) {
        val localizedValuesDir = File(mProjDir, "src${File.separator}main${File.separator}res${File.separator}values-$lang")
        localizedValuesDir.mkdirs()

        val translatedXmlFile = File(localizedValuesDir, STRINGS_XML)
        saveXmlFile(translatedNodes, translatedXmlFile)
    }
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
    //End save localized file

    fun shouldUpdate(languageCodes: List<String>, projDir: File): Boolean {
        for (languageCode in languageCodes) {
            val languageFolder = File(projDir,"${PATH_RES}values-$languageCode")
            val stringsXml = File(languageFolder, LDocument.STRINGS_XML)
            if (!stringsXml.exists()) {
                return true
            }
        }

        return false
    }
    companion object {
        val PATH_VALUES = "${File.separator}src${File.separator}main${File.separator}res${File.separator}values${File.separator}"
        val PATH_RES = "${File.separator}src${File.separator}main${File.separator}res${File.separator}"
        const val STRINGS_XML = "strings.xml"
        const val NAME = "strings"
    }
}
