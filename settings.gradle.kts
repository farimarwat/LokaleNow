pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven {
            url = uri(file("${System.getProperty("user.home")}/repo"))
        }

    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven {
            url = uri(file("${System.getProperty("user.home")}/repo"))
        }
    }
}

rootProject.name = "LokaleNowLibrary"
include(":app")
include(":lokalenow")
