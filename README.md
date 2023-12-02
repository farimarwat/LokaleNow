# LokaleNow
An android gradle plugin for automating localization. It will automaticall generate all strings.xml files for your desired languages.

### Implementation
Inlclude the plugin in app level gradle
```
plugins {
  ...
  ...
  id("io.github.farimarwat.lokalenow") version "1.2"
}
```

Also make sure that **gradlePluginPortal()** in included in settings.gradle.kts
```
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()

    }
}
```

## Now apply languages
Outside of the android{} block use this:
```
android{
  ...
  ...
}
lokalenow{
    listLang = listOf("fr","ru","ar")
}
```
## Done.
When you will build, It will automatically generate strings.xml file according to requested languages.

## Note:
This gradle plugin is developed based upon google translate api. It may some time not work due to frequent requests.

## In case of any errors:
Kindly perform these steps:
1. Build>Clean Project
2. Build>Rebuild Project

## What Next:
There is need to handle symbols while translation. If you want to improve the conversion process then feel free to contribute.

## Version History
**1.2**

Minor bugs fixed

**1.1**

strings.xml path issue fixed for mac

**1.0**

Initial commit

## Buy me a cup of Tea
If you want to support me then buy me a cup of tea:
<a href="https://www.patreon.com/farimarwat">Buy</a>
