package com.farimarwat.lokalenow.plugin

import com.farimarwat.lokalenow.models.LNode
import com.farimarwat.lokalenow.models.NodeType
import com.farimarwat.lokalenow.models.PrimaryStringDocument
import com.farimarwat.lokalenow.utils.Translator
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import java.io.File
import org.gradle.api.tasks.Internal
import java.util.Collections
import java.util.concurrent.Callable
import java.util.concurrent.Executors

abstract class LokaleNowTask : DefaultTask() {
    @get:Input
    var languages = listOf<String>()

    @get:Input
    var activate = true

    // Module path -> module folder, collected at configuration time by the plugin
    @get:Internal
    var modules: Map<String, File> = emptyMap()

    @TaskAction
    fun doTranslate() {
        if (!activate) return

        for ((name, dir) in modules) {
            val stringsFile = File(
                dir,
                "src${File.separator}main${File.separator}res${File.separator}values${File.separator}strings.xml"
            )
            if (!stringsFile.exists()) continue

            println("LokaleNow: Processing module -> $name")
            processModule(dir.path)
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

        // Work out what each language needs before starting, so the progress line knows them all
        val existingByLang = languages.associateWith { ldoc.readLocalized(it) }
        val toTranslateByLang = existingByLang.mapValues { (_, existing) ->
            // Only new strings, strings whose English text changed, or strings whose type changed
            sourceNodes.filter { node ->
                val old = existing[node.name]
                old == null || old.type != node.type || node.name in changedNames
            }
        }

        val progress = ProgressLine(toTranslateByLang.filterValues { it.isNotEmpty() }.keys.toList())
        val failedNames = Collections.synchronizedSet(mutableSetOf<String>())
        val threads = languages.size.coerceIn(1, MAX_PARALLEL_LANGUAGES)
        val pool = Executors.newFixedThreadPool(threads)

        try {
            val jobs = languages.map { lang ->
                pool.submit(Callable {
                    translateLanguage(
                        ldoc, lang, sourceNodes,
                        existingByLang.getValue(lang),
                        toTranslateByLang.getValue(lang),
                        progress, failedNames
                    )
                })
            }
            jobs.forEach { it.get() }
        } finally {
            pool.shutdown()
            progress.finish()
        }

        if (failedNames.isNotEmpty()) {
            println("  LokaleNow: could not translate ${failedNames.joinToString()}. Will retry on next build.")
        }
        ldoc.saveHashes(failedNames.toSet())
    }

    private fun translateLanguage(
        ldoc: PrimaryStringDocument,
        lang: String,
        sourceNodes: List<LNode>,
        existing: Map<String, LNode>,
        nodesToTranslate: List<LNode>,
        progress: ProgressLine,
        failedNames: MutableSet<String>
    ) {
        val translatedByName = if (nodesToTranslate.isEmpty()) {
            emptyMap()
        } else {
            val translator = Translator.Builder()
                .addNodes(nodesToTranslate)
                .build()
            val result = translator.translate(lang) { percent -> progress.update(lang, percent) }
                .associateBy { it.name }
            failedNames.addAll(translator.getFailedNames())
            result
        }

        // Keep existing translations, replace changed ones, drop deleted ones
        val finalNodes = linkedMapOf<String, LNode>()
        for (node in sourceNodes) {
            val result = translatedByName[node.name] ?: existing[node.name] ?: continue
            // Attributes like formatted="false" always come from the original strings.xml
            finalNodes[node.name] = result.copy(attributes = node.attributes)
        }
        ldoc.saveLocalized(lang, finalNodes)
    }

    /**
     * One progress line for all languages, updated in place: "  ar 45%  ur 40%  de 50%"
     */
    private class ProgressLine(langs: List<String>) {
        private val percents = linkedMapOf<String, Int>()

        init {
            langs.forEach { percents[it] = 0 }
            print()
        }

        @Synchronized
        fun update(lang: String, percent: Int) {
            if (percents[lang] == percent) return
            percents[lang] = percent
            print()
        }

        @Synchronized
        fun finish() {
            if (percents.isNotEmpty()) System.out.println()
        }

        private fun print() {
            if (percents.isEmpty()) return
            val line = percents.entries.joinToString("  ") { "${it.key} ${it.value}%" }
            System.out.print("\r  $line    ")
            System.out.flush()
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

    companion object {
        // Languages translated at the same time; kept low so Google doesn't rate-limit
        private const val MAX_PARALLEL_LANGUAGES = 4
    }
}