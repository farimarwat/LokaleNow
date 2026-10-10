package com.farimarwat.lokalenow.plugin

import com.farimarwat.lokalenow.models.NodeType
import com.farimarwat.lokalenow.models.PrimaryStringDocument
import com.farimarwat.lokalenow.utils.Translator
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import java.io.File


abstract class LokaleNowTask : DefaultTask() {
    @get:Input
    var languages = listOf<String>()

    @get:Input
    var activate = true

    @TaskAction
    fun doTranslate() {
        if (!activate) return

        val rootProject = project.rootProject
        val allProjects = rootProject.allprojects

        for (proj in allProjects) {
            val projPath = proj.layout.projectDirectory.toString()
            val stringsFile = File(
                projPath,
                "src${File.separator}main${File.separator}res${File.separator}values${File.separator}strings.xml"
            )
            if (!stringsFile.exists()) continue

            println("LokaleNow: Processing module -> ${proj.name}")
            processModule(projPath)
        }
    }

    private fun processModule(path: String) {
        val filePath = File(path)
        val ldoc = PrimaryStringDocument
            .Builder(filePath)
            .build()

        cleanUpOldLanguages(path)

        val allNodes = ldoc.getAllNodes() ?: return

        languages.forEach { lang ->
            val langFolder = File(
                path,
                "src${File.separator}main${File.separator}res${File.separator}values-$lang"
            )
            val translatedXmlFile = File(langFolder, PrimaryStringDocument.STRINGS_XML_FILE_NAME)

            if (!translatedXmlFile.exists() || ldoc.isModified()) {
                val nodesToTranslate = if (translatedXmlFile.exists() && ldoc.getModifiedNodes() != null) {
                    ldoc.getModifiedNodes()!!
                } else {
                    allNodes
                }

                val translator = Translator.Builder()
                    .addNodes(nodesToTranslate)
                    .build()
                val translated = translator.translate(lang)
                ldoc.saveLocalized(lang, translated)
            }
        }
        ldoc.saveHashes()
    }

    private fun cleanUpOldLanguages(projectPath: String) {
        val resDir = File(projectPath, "src${File.separator}main${File.separator}res")
        val existingLangDirs = resDir.listFiles { file ->
            file.isDirectory && file.name.startsWith("values-")
        }?.map { it.name.substringAfter("values-") } ?: emptyList()

        val languagesToRemove = existingLangDirs.filterNot { it in languages }

        languagesToRemove.forEach { lang ->
            val langDir = File(resDir, "values-$lang")
            if (langDir.exists()) {
                println("  Removing directory for language: $lang")
                deleteDirectory(langDir)
            }
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