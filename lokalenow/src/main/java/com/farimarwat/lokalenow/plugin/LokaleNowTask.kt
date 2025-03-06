package com.farimarwat.lokalenow.plugin

import com.farimarwat.lokalenow.models.PrimaryStringDocument
import com.farimarwat.lokalenow.utils.Translator
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.io.IOException


abstract class LokaleNowTask: DefaultTask() {
    @get:Input
    var languages = listOf<String>()

    @get:Input
    var activate = true

    @TaskAction
    fun doTranslate() {
        if(!activate) return
        println("Starting translating languages")
        val path = project.layout.projectDirectory.toString()
        val filePath = File(path)
        val ldoc = PrimaryStringDocument
            .Builder(filePath)
            .build()

        // Clean up old languages (removes unused ones)
        cleanUpOldLanguages(path)

        val existingFilesCount = countExistingLangDirs(path)
        if(existingFilesCount != languages.count()){
            val listString = ldoc.getAllNodes()
            if(listString == null) return
            val translator = Translator.Builder()
                .addNodes(listString)
                .build()

            languages.forEach { lang ->
                // Check if the translation already exists for this language
                val langFolder = File(path, "src${File.separator}main${File.separator}res${File.separator}values-$lang")
                val translatedXmlFile = File(langFolder, PrimaryStringDocument.STRINGS_XML_FILE_NAME)
                if(!translatedXmlFile.exists()){
                    val translated = translator.translate(lang)
                    ldoc.saveLocalized(lang, translated)
                }
            }
        }

        // Check if the document is modified or if we need to update the languages
        if (ldoc.isModified()) {
            val listString = if(ldoc.getModifiedNodes() != null) ldoc.getModifiedNodes() else ldoc.getAllNodes()
            if(listString == null) return
            val translator = Translator.Builder()
                .addNodes(listString)
                .build()

            languages.forEach { lang ->
                val translated = translator.translate(lang)
                ldoc.saveLocalized(lang, translated)
            }
        }
        ldoc.saveHashes()
    }


    /**
     * Cleans up old language folders that are no longer required.
     */
    /**
     * Cleans up old language folders by removing only the strings.xml files that are no longer required.
     */
    private fun cleanUpOldLanguages(projectPath: String) {
        // Define the base directory where language folders are stored
        val resDir = File(projectPath, "src${File.separator}main${File.separator}res")

        // Check if the directory exists and is accessible
        if (!resDir.exists() || !resDir.isDirectory) {
            println("Error: Resource directory does not exist or is not accessible: ${resDir.absolutePath}")
            return
        }

        // Get the current list of language folders (e.g., values-ar, values-fr)
        val existingLangDirs = resDir.listFiles { file ->
            file.isDirectory && file.name.startsWith("values-")
        }?.mapNotNull { file ->
            val langCode = file.name.substringAfter("values-")
            val stringsFile = File(file, "strings.xml")
            if (stringsFile.exists()) {
                langCode
            } else {
                null
            }
        } ?: emptyList()

        val languagesToRemove = existingLangDirs.filterNot { it in languages }
        println("Languages To Remove: $languagesToRemove")
        languagesToRemove.forEach { lang ->
            val langDir = File(resDir, "values-$lang")
            if (langDir.exists()) {
                val stringsFile = File(langDir, "strings.xml")
                if (stringsFile.exists()) {
                    try {
                        stringsFile.delete()
                    } catch (e: SecurityException) {
                        println("SecurityException: Unable to delete strings.xml for language: $lang. ${e.message}")
                    } catch (e: IOException) {
                        println("IOException: Unable to delete strings.xml for language: $lang. ${e.message}")
                    }
                }
            }
        }
    }
    fun countExistingLangDirs(projectPath: String): Int {
        val resDir = File(projectPath, "src${File.separator}main${File.separator}res")

        val existingLangDirs = resDir.listFiles { file ->
            file.isDirectory && file.name.startsWith("values-")
        }?.map { it.name.substringAfter("values-") } ?: emptyList()

        return existingLangDirs.size
    }
}