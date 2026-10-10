package com.farimarwat.lokalenow.plugin

import com.farimarwat.lokalenow.models.LNode
import com.farimarwat.lokalenow.models.NodeType
import com.farimarwat.lokalenow.models.PrimaryStringDocument
import com.farimarwat.lokalenow.utils.NetworkHelper
import com.farimarwat.lokalenow.utils.ProxyManager
import com.farimarwat.lokalenow.utils.Translator
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import java.io.File
import org.gradle.api.tasks.Internal
abstract class LokaleNowTask : DefaultTask() {
    @get:Input
    var languages = listOf<String>()

    @get:Input
    var activate = true

    @get:Input
    var useProxy = false

    @get:Input
    var proxyCount = 10

    private var proxiesLoaded = false

    // Module path -> module folder, collected at configuration time by the plugin
    @get:Internal
    var modules: Map<String, File> = emptyMap()

    @TaskAction
    fun doTranslate() {
        if (!activate) return

        try {
            for ((name, dir) in modules) {
                val stringsFile = File(
                    dir,
                    "src${File.separator}main${File.separator}res${File.separator}values${File.separator}strings.xml"
                )
                if (!stringsFile.exists()) continue

                println("LokaleNow: Processing module -> $name")
                processModule(dir.path)
            }
        } finally {
            // Gradle daemon keeps objects alive between builds, so always reset
            NetworkHelper.proxyManager?.let { manager ->
                manager.close()
                println(manager.summary())
            }
            NetworkHelper.proxyManager = null
            proxiesLoaded = false
        }
    }

    /** Loads proxies only once, and only when something actually needs translating. */
    private fun ensureProxies() {
        if (!useProxy || proxiesLoaded) return
        proxiesLoaded = true

        if (proxyCount > ProxyManager.MAX_COUNT) {
            println("LokaleNow: proxyCount $proxyCount is above max ${ProxyManager.MAX_COUNT}, using ${ProxyManager.MAX_COUNT}")
        }
        val manager = ProxyManager(NetworkHelper.client, proxyCount)
        if (manager.load() > 0) {
            NetworkHelper.proxyManager = manager
        } else {
            println("LokaleNow: No working proxies found. Translating directly.")
        }
    }

    private fun processModule(path: String) {
        val filePath = File(path)
        val ldoc = PrimaryStringDocument
            .Builder(filePath)
            .build()

        cleanUpOldLanguages(path)

        val allNodes = ldoc.getAllNodes() ?: return
        val sourceNodes = allNodes.filter { it.translatable }
        val changedNames = ldoc.getChangedNodeNames()
        val failedNames = mutableSetOf<String>()

        languages.forEach { lang ->
            val existing = ldoc.readLocalized(lang)

            // Only new strings, strings whose English text changed, or strings whose type changed
            val nodesToTranslate = sourceNodes.filter { node ->
                val old = existing[node.name]
                old == null || old.type != node.type || node.name in changedNames
            }

            val translatedByName = if (nodesToTranslate.isEmpty()) {
                emptyMap()
            } else {
                ensureProxies()
                val translator = Translator.Builder()
                    .addNodes(nodesToTranslate)
                    .build()
                val result = translator.translate(lang).associateBy { it.name }
                failedNames.addAll(translator.getFailedNames())
                result
            }

            // Keep existing translations, replace changed ones, drop deleted ones
            val finalNodes = linkedMapOf<String, LNode>()
            for (node in sourceNodes) {
                val result = translatedByName[node.name] ?: existing[node.name] ?: continue
                finalNodes[node.name] = result.copy(attributes = node.attributes)
            }
            ldoc.saveLocalized(lang, finalNodes)
        }
        if (failedNames.isNotEmpty()) {
            println("  LokaleNow: could not translate ${failedNames.joinToString()}. Will retry on next build.")
        }
        ldoc.saveHashes(failedNames)
    }

    /**
     * Removes translations for languages no longer in the list.
     * Only deletes strings.xml files this plugin generated; other files and folders
     * (values-night, values-v21, dimens.xml, etc.) are never touched.
     */
    private fun cleanUpOldLanguages(projectPath: String) {
        val resDir = File(projectPath, "src${File.separator}main${File.separator}res")
        val langDirs = resDir.listFiles { file ->
            file.isDirectory && file.name.startsWith("values-")
        } ?: return

        for (dir in langDirs) {
            val lang = dir.name.substringAfter("values-")
            if (lang in languages) continue
            val stringsFile = File(dir, PrimaryStringDocument.STRINGS_XML_FILE_NAME)
            if (!PrimaryStringDocument.isGeneratedFile(stringsFile)) continue

            println("  Removing translations for: $lang")
            stringsFile.delete()
            if (dir.listFiles()?.isEmpty() == true) dir.delete()
        }
    }

    private fun countExistingLangDirs(projectPath: String): Int {
        val resDir = File(projectPath, "src${File.separator}main${File.separator}res")
        val existingLangDirs = resDir.listFiles { file ->
            file.isDirectory && file.name.startsWith("values-")
        }?.map { it.name.substringAfter("values-") } ?: emptyList()
        return existingLangDirs.size
    }

    private fun deleteDirectory(directory: File) {
        if (directory.isDirectory) {
            directory.listFiles()?.forEach {
                deleteDirectory(it)
            }
        }
        directory.delete()
    }
}