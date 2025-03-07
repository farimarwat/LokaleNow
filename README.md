# LokaleNow
An android gradle plugin for automating localization. It will automatically generate all strings.xml files for your desired languages.

### Implementation
Include the plugin in app level gradle
```
plugins {
  ...
  ...
  // for kotlin
  id("io.github.farimarwat.lokalenow") version "1.10"
  //for groovy
  id "io.github.farimarwat.lokalenow" version "1.10"
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
// for kotlin
lokalenow{
     languages = listOf("fr","ru","ar") //target languages codes
     activate = true /// Enable automatic translation
}
// for groovy
lokalenow{
     languages = ["fr","ru","ar"] //target languages codes
     acivate = true // Enable automatic translation
}
```
## Done.
When you will build, It will automatically generate strings.xml file according to requested languages.

## Exclude strings
Use "translatable=false" for excluding any translation
```
<string name="details" translatable="false">This will not be translated</string>
```

## Note:
This gradle plugin is developed based upon google translate api. It may some time not work due to frequent requests.

## In case of any errors:
Kindly perform these steps:
1. Build>Clean Project
2. Build>Rebuild Project
3. Progress added

## What Next:
There is need to handle symbols while translation. If you want to improve the conversion process then feel free to contribute.


## Version History
**1.10**
1. Moved hash dir to root project to maintain hashes even after clean or rebuild project
2. Fixed delete whole language values dir.
3. Fixed <a href='https://github.com/farimarwat/LokaleNow/issues/12'>#12</a>

**1.9**
1. Fixed %s format, newline and tab tag issue
2. Bugs, after applying the plugin, red lines under each android{} configuration, fixed
3. Other minor changes

**1.4**
1. Fixed encoding issues
2. Old file delete issue


**1.3** Modified by RufenKhokhar

**1.2** Minor bugs fixed

**1.1** strings.xml path issue fixed for mac

**1.0** Initial commit

## Buy me a cup of Tea
If you want to support me then buy me a cup of tea:
<a href="https://www.patreon.com/farimarwat">Buy</a>
