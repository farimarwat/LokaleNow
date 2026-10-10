package com.farimarwat.lokalenow.plugin

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Action
import org.gradle.api.Task
import java.io.File

class LokaleNow: Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create("lokalenow",LokaleNowExtension::class.java)

        // Remember every module that applies the plugin itself
        val owners = ownerPaths(project.rootProject)
        owners.add(project.path)

        val task = project.tasks.register("translatenow",LokaleNowTask::class.java)
        task.configure(object : Action<LokaleNowTask> {
            override fun execute(t: LokaleNowTask) {
                t.languages = extension.languages
                t.activate = extension.activate
                t.modules = findModules(project, owners)
            }
        })

        // Works whether the plugin is applied before or after the Android plugin
        project.tasks.matching { it.name == "preBuild" }.configureEach(object : Action<Task> {
            override fun execute(t: Task) {
                t.dependsOn(task)
            }
        })
    }

    /**
     * Each module that applies the plugin translates itself.
     * The first one (by path) also translates modules that don't apply the plugin,
     * so two tasks never write the same files.
     */
    private fun findModules(project: Project, owners: Set<String>): Map<String, File> {
        val result = linkedMapOf(project.path to project.projectDir)
        if (project.path == owners.minOrNull()) {
            for (other in project.rootProject.allprojects) {
                if (other.path !in owners) result[other.path] = other.projectDir
            }
        }
        return result
    }

    @Suppress("UNCHECKED_CAST")
    private fun ownerPaths(root: Project): MutableSet<String> {
        val extra = root.extensions.extraProperties
        if (!extra.has(OWNERS_KEY)) extra.set(OWNERS_KEY, sortedSetOf<String>())
        return extra.get(OWNERS_KEY) as MutableSet<String>
    }

    companion object {
        private const val OWNERS_KEY = "lokalenow.ownerModules"
    }
}
