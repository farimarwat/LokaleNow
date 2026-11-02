package com.farimarwat.lokalenow.plugin

import com.farimarwat.lokalenow.models.LNode
import com.farimarwat.lokalenow.models.NodeType
import com.farimarwat.lokalenow.models.PrimaryStringDocument
import com.farimarwat.lokalenow.utils.NetworkHelper
import com.farimarwat.lokalenow.utils.ProxyManager
import com.farimarwat.lokalenow.utils.Translator
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
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
        NetworkHelper.proxyEnabled = useProxy

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
        } catch (e: NetworkHelper.RateLimitedException) {
            throw GradleException(
                "\nLokaleNow: Google Translate is rate-limiting this network (HTTP 429).\n" +
                    "Too many translation requests were sent from this IP address in a short time, so Google\n" +
                    "temporarily refuses more (usually for minutes to a few hours). Nothing was lost: strings\n" +
                    "translated so far are saved, the rest are translated on the next build.\n" +
                    "Kindly enable the proxy in build.gradle.kts and build again:\n" +
                    "    lokalenow { useProxy = true }\n" +
                    "Or set activate = false to build without translating for now."
            )
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
        val skipped = SkipList(File(path, PrimaryStringDocument.HASH_DIR_NAME))

        languages.forEach { lang ->
            val existing = ldoc.readLocalized(lang)

            // Only new strings, strings whose English text changed, or strings whose type changed,
            // in this language only; never ones Google can't translate without breaking placeholders
            val nodesToTranslate = sourceNodes.filter { node ->
                val old = existing[node.name]
                val wanted = old == null || old.type != node.type || node.name in changedNames
                wanted && !skipped.contains(lang, node)
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
                translator.getBrokenNames().forEach { name ->
                    sourceNodes.firstOrNull { it.name == name }?.let { skipped.add(lang, it) }
                }
                result
            }

            // Keep existing translations, replace changed ones, drop deleted ones. A changed string
            // that failed loses its stale translation (English shows), so it's retried next build
            val finalNodes = linkedMapOf<String, LNode>()
            for (node in sourceNodes) {
                val result = translatedByName[node.name]
                    ?: existing[node.name]?.takeIf { node.name !in changedNames }
                    ?: continue
                finalNodes[node.name] = result.copy(attributes = node.attributes)
            }
            ldoc.saveLocalized(lang, finalNodes)
        }
        if (failedNames.isNotEmpty()) {
            println("  LokaleNow: could not translate ${failedNames.joinToString()}. Will retry on next build.")
        }
        skipped.save(sourceNodes)
        // Every string is recorded as seen: a failure is retried through its missing translation
        // in that one language, not by marking it changed (which re-translated it in all languages
        // on every build)
        ldoc.saveHashes()
    }

    /**
     * Strings Google can't translate into a language without losing a placeholder, per language,
     * remembered until their English text changes: retrying them gives the same broken result.
     */
    private class SkipList(dir: File) {
        private val file = File(dir, "skipped.hash")
        private val entries: MutableSet<String> =
            if (file.exists()) file.readLines().filter { it.isNotBlank() }.toMutableSet() else mutableSetOf()

        private fun key(lang: String, node: LNode): String {
            val hash = when (node.type) {
                NodeType.STRING -> node.value.hashCode()
                NodeType.PLURALS -> node.pluralItems.hashCode()
                NodeType.STRING_ARRAY -> node.arrayItems.hashCode()
            }
            return "$lang:${node.name}:$hash"
        }

        fun contains(lang: String, node: LNode) = key(lang, node) in entries
        fun add(lang: String, node: LNode) { entries.add(key(lang, node)) }

        /** Keeps only entries still matching a current string. */
        fun save(nodes: List<LNode>) {
            val names = nodes.map { it.name }.toSet()
            val kept = entries.filter { it.split(":").getOrNull(1) in names }
            if (kept.isEmpty()) { file.delete(); return }
            file.parentFile.mkdirs()
            file.writeText(kept.joinToString("\n"))
        }
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