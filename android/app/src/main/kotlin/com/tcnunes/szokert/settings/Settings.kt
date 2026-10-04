package com.tcnunes.szokert.settings

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class ThemeChoice(val label: String) { SYSTEM("System"), DARK("Dark"), LIGHT("Light") }

/** Per-device settings. The theme starts dark, like the website. */
class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _theme = MutableStateFlow(
        runCatching { ThemeChoice.valueOf(prefs.getString(THEME, null) ?: "") }.getOrDefault(ThemeChoice.DARK),
    )
    val theme: StateFlow<ThemeChoice> = _theme

    fun setTheme(choice: ThemeChoice) {
        _theme.value = choice
        prefs.edit { putString(THEME, choice.name) }
    }

    private companion object {
        const val THEME = "theme"
    }
}
