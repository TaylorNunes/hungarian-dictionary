package com.tcnunes.szokert

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tcnunes.szokert.ui.theme.Szokert
import com.tcnunes.szokert.ui.theme.SzokertTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SzokertTheme {
                Welcome()
            }
        }
    }
}

@Composable
private fun Welcome() {
    val c = Szokert.colors
    var query by rememberSaveable { mutableStateOf("") }
    Column(
        Modifier
            .fillMaxSize()
            .background(c.bg)
            .safeDrawingPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Hungarian or English word…") },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "Szókert",
            color = c.accent,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.SemiBold,
            fontSize = 38.sp,
            modifier = Modifier.padding(top = 28.dp),
        )
        Text("Cultivate your knowledge.", color = c.secondary, fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic, fontSize = 19.sp)
        Text("Hungarian ⇄ English dictionary", color = c.text, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 14.dp))
        Text(
            "Search arrives with the dictionary download in the next build.",
            color = c.muted,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
