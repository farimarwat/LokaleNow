package com.farimarwat.lokalenow.plugin

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Action

class LokaleNow: Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create("lokalenow",LokaleNowExtension::class.java)
        val task = project.tasks.register("translatenow",LokaleNowTask::class.java)
        task.configure(object : Action<LokaleNowTask> {
            override fun execute(t: LokaleNowTask) {
                t.languages = extension.languages
                t.activate = extension.activate
            }
        })
        project.tasks.named("preBuild").get().dependsOn(task)
    }
}
