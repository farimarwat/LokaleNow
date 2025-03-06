package com.farimarwat.lokalenow.utils

object PlaceholderManager {
    private val placeholderMap = listOf(
        "%s" to "__PLH1__",
        "\\n" to "__PLH2__",
        "\\t" to "__PLH3__"
    )

    fun applyPlaceholders(text: String): String {
        var modifiedText = text
        placeholderMap.forEach { (key, value) ->
            modifiedText = modifiedText.replace(Regex.fromLiteral(key), value)
        }
        return modifiedText
    }

    fun restorePlaceholders(text: String): String {
        var modifiedText = text
        placeholderMap.forEach { (key, value) ->
            val googleMessedUpValue = value.lowercase().replace(" ", "")
            modifiedText = modifiedText.replace(googleMessedUpValue, key)
        }
        return modifiedText
    }
}


