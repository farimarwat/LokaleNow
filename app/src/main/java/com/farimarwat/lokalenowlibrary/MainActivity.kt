package com.farimarwat.lokalenowlibrary

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
fun Main(){
    Column(
        modifier = Modifier
            .fillMaxSize()
    ) {
        Text(
            text = stringResource(id = R.string.title),
            textAlign = TextAlign.Center,
            fontSize = 24.sp
        )
        Text(
            text = stringResource(id = R.string.description),
            textAlign = TextAlign.Center,
            fontSize = 18.sp
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
        ) {
            Button(
                modifier = Modifier
                    .weight(1f),
                onClick = { /*TODO*/ }) {
                Text(text = stringResource(id = R.string.previous))
            }
            Button(
                modifier = Modifier
                    .weight(1f),
                onClick = { /*TODO*/ }) {
                Text(text = stringResource(id = R.string.next))
            }
        }
        Button(
            modifier = Modifier
                .fillMaxWidth(),
            onClick = { /*TODO*/ }) {
            Text(text = stringResource(id = R.string.login))
        }
    }
}