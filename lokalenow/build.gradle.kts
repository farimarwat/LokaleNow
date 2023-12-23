import org.gradle.kotlin.dsl.`kotlin-dsl`
import org.jetbrains.kotlin.load.kotlin.signatures
plugins {
    id("maven-publish")
    `kotlin-dsl`
    id("com.gradle.plugin-publish") version "1.1.0"
}

extra["PUBLISH_GROUP_ID"] = "io.github.farimarwat"
extra["PUBLISH_VERSION"] = "1.3"
extra["PUBLISH_ARTIFACT_ID"] = "lokalenow"
extra["PUBLISH_DESCRIPTION"] = "An android gradle plugin for localization"
extra["PUBLISH_URL"] = "https://github.com/farimarwat/LokaleNow"
extra["PUBLISH_LICENSE_NAME"] = "Apache 2.0 License"
extra["PUBLISH_LICENSE_URL"] =
    "https://www.apache.org/licenses/LICENSE-2.0"
extra["PUBLISH_DEVELOPER_ID"] = "farimarwat"
extra["PUBLISH_DEVELOPER_NAME"] = "Farman Ullah Marwat"
extra["PUBLISH_DEVELOPER_EMAIL"] = "farimarwat@gmail.com"
extra["PUBLISH_SCM_CONNECTION"] =
    "scm:git:github.com/farimarwat/LokaleNow.git"
extra["PUBLISH_SCM_DEVELOPER_CONNECTION"] =
    "scm:git:ssh://github.com/farimarwat/LokaleNow.git"
extra["PUBLISH_SCM_URL"] =
    "https://github.com/farimarwat/LokaleNow/tree/master"


version = extra["PUBLISH_VERSION"] as String
group = extra["PUBLISH_GROUP_ID"] as String
publishing{
    repositories {
        maven {
            url = uri("C:\\repo")
        }
    }
}

gradlePlugin {
    website.set(extra["PUBLISH_URL"] as String)
    vcsUrl.set(extra["PUBLISH_URL"] as String)
    plugins {
        create("LokaleNowPlugin") {
            id = "io.github.farimarwat.lokalenow"
            implementationClass = "com.farimarwat.lokalenow.plugin.LokaleNow"
            displayName = "LokaleNow Android Plugin"
            description = "Android gradle plugin for app's localization"
            tags.set(listOf("android", "locale","string"))
        }
    }
}
dependencies {
    implementation("com.android.tools.build:gradle:7.4.0-rc03")
    //implementation("org.javassist:javassist:3.29.2-GA")
    implementation("com.squareup.okhttp3:okhttp:4.11.0")
    implementation ("com.google.code.gson:gson:2.10.1")
}