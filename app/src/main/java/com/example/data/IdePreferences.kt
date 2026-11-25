package com.example.data

import android.content.Context

class IdePreferences(context: Context) {
  private val prefs = context.getSharedPreferences("brain_ide_prefs", Context.MODE_PRIVATE)

  var darkTheme: Boolean
    get() = prefs.getBoolean(KEY_DARK_THEME, true)
    set(value) = prefs.edit().putBoolean(KEY_DARK_THEME, value).apply()

  var fontSizeSp: Float
    get() = prefs.getFloat(KEY_FONT_SIZE, 14f).coerceIn(11f, 22f)
    set(value) = prefs.edit().putFloat(KEY_FONT_SIZE, value.coerceIn(11f, 22f)).apply()

  var lastFileName: String
    get() = prefs.getString(KEY_LAST_FILE, "ola_brain.brain") ?: "ola_brain.brain"
    set(value) = prefs.edit().putString(KEY_LAST_FILE, value).apply()

  companion object {
    private const val KEY_DARK_THEME = "dark_theme"
    private const val KEY_FONT_SIZE = "font_size_sp"
    private const val KEY_LAST_FILE = "last_file_name"
  }
}
