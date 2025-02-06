package com.farimarwat.lokalenow.plugin

import com.farimarwat.lokalenow.utils.LDocument
import com.farimarwat.lokalenow.utils.Translator
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import java.io.File


abstract class LokaleNowTask: DefaultTask() {
    @get:Input
    var languages = listOf<String>()

    @TaskAction
    fun doTranslate() {
        val path = project.layout.projectDirectory.toString()
        val filePath = File(path)
        val ldoc = LDocument
            .Builder(filePath)
            .build()

        // Clean up old languages (removes unused ones)
        cleanUpOldLanguages(path)

        // Check if the document is modified or if we need to update the languages
        if (ldoc.isModified()) {
            ldoc.saveCurrentHash()
            val listString = ldoc.listElements()
            val translator = Translator.Builder()
                .addNodes(listString)
                .build()

            // Process each language only if it's a new language or has not been processed yet
            languages.forEach { lang ->
                print("Translating for: $lang")
                val translated = translator.translate(lang)
                ldoc.saveLocalized(lang, translated)
            }
        } else {
            val existingFilesCount = countExistingLangDirs(path)
            if(existingFilesCount != languages.count()){
                ldoc.saveCurrentHash()
                val listString = ldoc.listElements()
                val translator = Translator.Builder()
                    .addNodes(listString)
                    .build()

                // Process each language only if it's a new language or has not been processed yet
                languages.forEach { lang ->
                    // Check if the translation already exists for this language
                    val langFolder = File(path, "src${File.separator}main${File.separator}res${File.separator}values-$lang")
                    val translatedXmlFile = File(langFolder, LDocument.STRINGS_XML)
                    if(!translatedXmlFile.exists()){
                        print("Translating for: $lang")
                        val translated = translator.translate(lang)
                        ldoc.saveLocalized(lang, translated)
                    }
                }
            }
        }
    }


    /**
     * Cleans up old language folders that are no longer required.
     */
    private fun cleanUpOldLanguages(projectPath: String) {
        // Define the base directory where language folders are stored
        val resDir = File(projectPath, "src${File.separator}main${File.separator}res")

        // Get the current list of language folders (e.g., values-ar, values-fr)
        val existingLangDirs = resDir.listFiles { file ->
            file.isDirectory && file.name.startsWith("values-")
        }?.map { it.name.substringAfter("values-") } ?: emptyList()

        // Find out which languages need to be removed (those that are not in the new list)
        val languagesToRemove = existingLangDirs.filterNot { it in languages }
        print("Languages To Remove: "+languagesToRemove+"\n")

        // Remove unwanted language folders
        languagesToRemove.forEach { lang ->
            val langDir = File(resDir, "values-$lang")
            if (langDir.exists()) {
                println("Removing directory for language: $lang")
                deleteDirectory(langDir)
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

    /**
     * Recursively deletes a directory and its contents.
     */
    private fun deleteDirectory(directory: File) {
        if (directory.isDirectory) {
            directory.listFiles()?.forEach {
                deleteDirectory(it) // Recursively delete files
            }
        }
        directory.delete()
    }
}