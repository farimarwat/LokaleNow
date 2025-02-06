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
    fun doTranslate(){
        val path = project.layout.projectDirectory.toString()
        val filePath= File(path)
        val ldoc = LDocument
            .Builder(filePath)
            .build()
        cleanUpOldLanguages(path)
        if(ldoc.isModified() || ldoc.shouldUpdate(languages,File(path))){
            ldoc.saveCurrentHash()
            val list_string = ldoc.listElements()
            val translator = Translator.Builder()
                .addNodes(list_string)
                .build()
           languages.forEach{ lang->
               println("Translating for: ${lang.uppercase()}")
               val translated = translator.translate(lang)
               ldoc.saveLocalized(lang,translated)
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