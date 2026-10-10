package com.farimarwat.lokalenowlibrary

import android.content.Context
import android.os.Bundle
import android.text.Html
import android.text.Spanned
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import com.farimarwat.lokalenowlibrary.ui.theme.LokaleNowLibraryTheme
import com.farimarwat.lokalenowlibrary.R
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.unit.dp
import java.io.File
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LokaleNowLibraryTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                   Main()
                }
            }
        }
    }
}


@Composable
fun Main() {
    val context = LocalContext.current
    val resources = context.resources
    var showLangDialog by remember { mutableStateOf(false) }
    var currentLocale by remember { mutableStateOf(Locale.getDefault().language) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Language switcher
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "Language: $currentLocale", fontSize = 18.sp)
            Button(onClick = { showLangDialog = true }) {
                Text(text = "Change")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Basic strings
        Text(text = "--- Basic Strings ---", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(text = stringResource(id = R.string.title))
        Text(text = stringResource(id = R.string.description))
        Text(text = stringResource(id = R.string.login))

        Spacer(modifier = Modifier.height(16.dp))

        // Special characters
        Text(text = "--- Special Characters ---", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(text = stringResource(id = R.string.quote_example))
        Text(text = stringResource(id = R.string.apostrophe_example))
        Text(text = stringResource(id = R.string.ampersand_example))

        Spacer(modifier = Modifier.height(16.dp))

        // CDATA
        Text(text = "--- CDATA ---", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(text = stringResource(id = R.string.cdata_example))

        Spacer(modifier = Modifier.height(16.dp))

        // Plurals
        Text(text = "--- Plurals ---", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(text = resources.getQuantityString(R.plurals.songs_found, 1, 1))
        Text(text = resources.getQuantityString(R.plurals.songs_found, 5, 5))
        Text(text = resources.getQuantityString(R.plurals.items_in_cart, 0, 0))
        Text(text = resources.getQuantityString(R.plurals.items_in_cart, 1, 1))
        Text(text = resources.getQuantityString(R.plurals.items_in_cart, 3, 3))

        Spacer(modifier = Modifier.height(16.dp))

        // String array
        Text(text = "--- String Arrays ---", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        val planets = resources.getStringArray(R.array.planets)
        Text(text = "Planets: ${planets.joinToString(", ")}")
        val colors = resources.getStringArray(R.array.colors)
        Text(text = "Colors: ${colors.joinToString(", ")}")

        Spacer(modifier = Modifier.height(16.dp))

        Spacer(modifier = Modifier.height(16.dp))

        // Placeholders
        Text(text = "--- Placeholders ---", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(text = stringResource(id = R.string.welcome_user, "Farman", 5))
        Text(text = stringResource(id = R.string.discount, 12.5f))
        Text(text = stringResource(id = R.string.two_lines))
        Text(text = stringResource(id = R.string.named_arg))
        Text(text = stringResource(id = R.string.ref_test))
        Text(text = resources.getQuantityString(R.plurals.files_deleted, 1, 1, "Downloads"))
        Text(text = resources.getQuantityString(R.plurals.files_deleted, 4, 4, "Downloads"))

        Spacer(modifier = Modifier.height(16.dp))

        // HTML rendered
        Text(text = "--- HTML Rendered ---", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(text = htmlText(context, R.string.bold_word))
        Text(text = AnnotatedString.fromHtml(stringResource(id = R.string.cdata_example)))

        // Buttons
        Row(modifier = Modifier.fillMaxWidth()) {
            Button(modifier = Modifier.weight(1f), onClick = {}) {
                Text(text = stringResource(id = R.string.previous))
            }
            Button(modifier = Modifier.weight(1f), onClick = {}) {
                Text(text = stringResource(id = R.string.next))
            }
        }
    }

    if (showLangDialog) {
        val availableLocales = getAvailableLocales(context)
        AlertDialog(
            onDismissRequest = { showLangDialog = false },
            title = { Text(text = "Select Language") },
            text = {
                LazyColumn {
                    items(availableLocales) { locale ->
                        TextButton(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                showLangDialog = false
                                currentLocale = locale.language
                                setAppLocale(context, locale)
                            }
                        ) {
                            Text(
                                text = "${locale.displayLanguage} (${locale.language})",
                                modifier = Modifier.fillMaxWidth(),
                                fontSize = 16.sp
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLangDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

fun getAvailableLocales(context: Context): List<Locale> {
    val locales = mutableListOf<Locale>()
    locales.add(Locale("en")) // default

    val resDir = File(context.applicationInfo.sourceDir).parentFile
    // Use AssetManager trick to detect available locales
    val config = context.resources.configuration
    val allLocales = Locale.getAvailableLocales()

    for (locale in allLocales) {
        val testConfig = android.content.res.Configuration(config)
        testConfig.setLocale(locale)
        val testResources = context.createConfigurationContext(testConfig).resources
        // Check if this locale has its own strings.xml by comparing app_name
        val localizedAppName = testResources.getString(R.string.login)
        val defaultConfig = android.content.res.Configuration(config)
        defaultConfig.setLocale(Locale("en"))
        val defaultResources = context.createConfigurationContext(defaultConfig).resources
        val defaultAppName = defaultResources.getString(R.string.login)

        if (localizedAppName != defaultAppName && !locales.any { it.language == locale.language }) {
            locales.add(locale)
        }
    }
    return locales.sortedBy { it.displayLanguage }
}

fun setAppLocale(context: Context, locale: Locale) {
    Locale.setDefault(locale)
    val config = android.content.res.Configuration(context.resources.configuration)
    config.setLocale(locale)
    context.resources.updateConfiguration(config, context.resources.displayMetrics)

    // Restart activity to apply
    if (context is ComponentActivity) {
        context.recreate()
    }
}

fun htmlText(context: Context, @StringRes id: Int): AnnotatedString {
    val text = context.getText(id)
    val html = if (text is Spanned) {
        Html.toHtml(text, Html.TO_HTML_PARAGRAPH_LINES_CONSECUTIVE)
    } else {
        text.toString()
    }
    return AnnotatedString.fromHtml(html)
}