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
        val file_original = File(path)
        val ldoc = LDocument
            .Builder(file_original)
            .build()
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
}