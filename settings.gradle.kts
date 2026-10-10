pluginManagement {
    repositories {
        maven {
            url = uri(file("${System.getProperty("user.home")}/repo"))
        }
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven {
            url = uri(file("${System.getProperty("user.home")}/repo"))
        }
        google()
        mavenCentral()

    }
}

rootProject.name = "LokaleNowLibrary"
include(":app")
include(":lokalenow")
