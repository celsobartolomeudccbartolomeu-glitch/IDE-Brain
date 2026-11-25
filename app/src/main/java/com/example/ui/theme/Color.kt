package com.example.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Dark Theme Palette (Tactile Developer Studio)
val DarkBg = Color(0xFF0B0F17)
val DarkSurface = Color(0xFF111827)
val DarkSurfaceVariant = Color(0xFF1E293B)
val DarkGutterBg = Color(0xFF0F1522)
val DarkConsoleBg = Color(0xFF090D14)
val DarkBorder = Color(0xFF283548)
val DarkTextPrimary = Color(0xFFE2E8F0)
val DarkTextSecondary = Color(0xFF94A3B8)
val DarkAccentCyan = Color(0xFF38BDF8)
val DarkAccentViolet = Color(0xFFA78BFA)
val DarkAccentEmerald = Color(0xFF34D399)
val DarkErrorRed = Color(0xFFF87171)
val DarkWarningAmber = Color(0xFFFBBF24)

// Light Theme Palette (Crisp Paper Studio)
val LightBg = Color(0xFFF1F5F9)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceVariant = Color(0xFFE2E8F0)
val LightGutterBg = Color(0xFFF8FAFC)
val LightConsoleBg = Color(0xFFF8FAFC)
val LightBorder = Color(0xFFCBD5E1)
val LightTextPrimary = Color(0xFF0F172A)
val LightTextSecondary = Color(0xFF475569)
val LightAccentCyan = Color(0xFF0284C7)
val LightAccentViolet = Color(0xFF7C3AED)
val LightAccentEmerald = Color(0xFF059669)
val LightErrorRed = Color(0xFFDC2626)
val LightWarningAmber = Color(0xFFD97706)

@Immutable
data class BrainSyntaxColors(
  val editorBg: Color,
  val gutterBg: Color,
  val gutterText: Color,
  val consoleBg: Color,
  val border: Color,
  val keyword: Color,
  val dotCommand: Color,
  val stringLiteral: Color,
  val numberLiteral: Color,
  val comment: Color,
  val operator: Color,
  val booleanLiteral: Color,
  val consoleOutput: Color,
  val consoleError: Color,
  val consoleWarning: Color,
  val consoleSystem: Color,
  val consolePrompt: Color,
)

val DarkSyntaxColors =
  BrainSyntaxColors(
    editorBg = DarkSurface,
    gutterBg = DarkGutterBg,
    gutterText = Color(0xFF64748B),
    consoleBg = DarkConsoleBg,
    border = DarkBorder,
    keyword = Color(0xFFC084FC),
    dotCommand = Color(0xFF38BDF8),
    stringLiteral = Color(0xFF34D399),
    numberLiteral = Color(0xFFFBBF24),
    comment = Color(0xFF64748B),
    operator = Color(0xFF7DD3FC),
    booleanLiteral = Color(0xFFF472B6),
    consoleOutput = DarkTextPrimary,
    consoleError = DarkErrorRed,
    consoleWarning = DarkWarningAmber,
    consoleSystem = DarkAccentCyan,
    consolePrompt = DarkAccentEmerald,
  )

val LightSyntaxColors =
  BrainSyntaxColors(
    editorBg = LightSurface,
    gutterBg = LightGutterBg,
    gutterText = Color(0xFF94A3B8),
    consoleBg = LightConsoleBg,
    border = LightBorder,
    keyword = Color(0xFF7C3AED),
    dotCommand = Color(0xFF0284C7),
    stringLiteral = Color(0xFF059669),
    numberLiteral = Color(0xFFD97706),
    comment = Color(0xFF64748B),
    operator = Color(0xFF0369A1),
    booleanLiteral = Color(0xFFDB2777),
    consoleOutput = LightTextPrimary,
    consoleError = LightErrorRed,
    consoleWarning = LightWarningAmber,
    consoleSystem = LightAccentCyan,
    consolePrompt = LightAccentEmerald,
  )

val LocalSyntaxColors = staticCompositionLocalOf { DarkSyntaxColors }
