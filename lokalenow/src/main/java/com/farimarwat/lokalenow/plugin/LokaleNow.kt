package com.farimarwat.lokalenow.plugin

import org.gradle.api.Plugin
import org.gradle.api.Project

class LokaleNow: Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create("lokalenow",LokaleNowExtension::class.java)
        val task = project.tasks.register("translatenow",LokaleNowTask::class.java){
            listLang = extension.listLang
        }
        project.tasks.named("preBuild").get().dependsOn(task)
    }
}