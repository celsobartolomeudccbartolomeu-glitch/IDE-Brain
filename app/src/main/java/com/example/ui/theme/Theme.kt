package com.example.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val BrainDarkColorScheme =
  darkColorScheme(
    primary = DarkAccentCyan,
    onPrimary = Color(0xFF042F49),
    primaryContainer = Color(0xFF0E3A5C),
    onPrimaryContainer = Color(0xFFBAE6FD),
    secondary = DarkAccentViolet,
    onSecondary = Color(0xFF1E1046),
    secondaryContainer = Color(0xFF2E1F5E),
    onSecondaryContainer = Color(0xFFDDD6FE),
    tertiary = DarkAccentEmerald,
    onTertiary = Color(0xFF022C22),
    background = DarkBg,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkTextSecondary,
    outline = DarkBorder,
    error = DarkErrorRed,
    onError = Color(0xFF450A0A),
  )

private val BrainLightColorScheme =
  lightColorScheme(
    primary = LightAccentCyan,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0F2FE),
    onPrimaryContainer = Color(0xFF0C4A6E),
    secondary = LightAccentViolet,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEDE9FE),
    onSecondaryContainer = Color(0xFF4C1D95),
    tertiary = LightAccentEmerald,
    onTertiary = Color.White,
    background = LightBg,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightTextSecondary,
    outline = LightBorder,
    error = LightErrorRed,
    onError = Color.White,
  )

@Composable
fun BrainIdeTheme(
  darkTheme: Boolean = true,
  content: @Composable () -> Unit,
) {
  val colorScheme = if (darkTheme) BrainDarkColorScheme else BrainLightColorScheme
  val syntaxColors = if (darkTheme) DarkSyntaxColors else LightSyntaxColors

  val view = LocalView.current
  if (!view.isInEditMode) {
    SideEffect {
      val window = (view.context as? Activity)?.window ?: return@SideEffect
      WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
      WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
    }
  }

  CompositionLocalProvider(LocalSyntaxColors provides syntaxColors) {
    MaterialTheme(
      colorScheme = colorScheme,
      typography = BrainTypography,
      content = content,
    )
  }
}
