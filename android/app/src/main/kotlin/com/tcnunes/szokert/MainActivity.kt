package com.tcnunes.szokert

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tcnunes.szokert.settings.ThemeChoice
import com.tcnunes.szokert.ui.App
import com.tcnunes.szokert.ui.theme.SzokertTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as SzokertApp
        setContent {
            val choice by app.settings.theme.collectAsStateWithLifecycle()
            val dark = when (choice) {
                ThemeChoice.SYSTEM -> isSystemInDarkTheme()
                ThemeChoice.DARK -> true
                ThemeChoice.LIGHT -> false
            }
            // System bar icons follow the app's theme, not the phone's.
            DisposableEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(style, style)
                onDispose {}
            }
            SzokertTheme(dark = dark) { App(app) }
        }
    }
}
