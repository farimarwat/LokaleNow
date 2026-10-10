# LokaleNow
An android gradle plugin for automating localization. It will automatically generate all strings.xml files for your desired languages.

### Implementation
Include the plugin in app level gradle
```
plugins {
  ...
  ...
  // for kotlin
  id("io.github.farimarwat.lokalenow") version "1.16"
  //for groovy
  id "io.github.farimarwat.lokalenow" version "1.16"
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
Outside of the `android{}` block, add this:

```kotlin
android {
    ...
}

// Kotlin DSL (build.gradle.kts)
lokalenow {
    languages = listOf("fr", "ru", "ar") // target language codes
    activate = true                      // enable automatic translation
    useProxy = false                     // optional: send translation requests through proxies
    proxyCount = 10                      // optional: number of proxies to keep (max 20)
}
```

```groovy
// Groovy DSL (build.gradle)
lokalenow {
    languages = ["fr", "ru", "ar"] // target language codes
    activate = true                // enable automatic translation
    useProxy = false               // optional: send translation requests through proxies
    proxyCount = 10                // optional: number of proxies to keep (max 20)
}
```

## Proxy support (optional)

### Why use a proxy?
LokaleNow uses Google Translate's free endpoint. Google limits how many requests one IP address can send. With many strings or many languages, you may see `HTTP 429` errors, and some strings will fail to translate. Turning on `useProxy` spreads the requests across different IP addresses so the translation can finish.

### How it works
- Free public proxies are fetched and tested against the Google Translate endpoint.
- The fastest working proxies are kept (`proxyCount`, max 20), plus a backup pool.
- Requests use the fastest proxy until it fails. Then the next fastest one takes over and retries the same string.
- Failed proxies are replaced from the backup pool automatically, with no wait.
- If all proxy attempts fail, LokaleNow tries a direct connection.
- Proxies are loaded only when there are new or changed strings. If nothing changed, the build doesn't slow down.

### Good to know
- `useProxy` is **off by default**.
- The first proxy scan can take up to about 60 seconds.
- All requests use HTTPS with normal certificate checks, so proxies can't read or change your text.
- Free proxies are run by unknown third parties and can be slow or unstable. Use this only if you hit rate limits.
- Using proxies to get around Google's rate limits may go against Google's terms of service. Use it at your own risk.
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
**1.15**
1. Minor bugs fixed
2. Multi-Module translation added
3. Plurals, CDATA and string arrays translation added

## Version History
**1.12**
1. Minor bugs fixed

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
