package com.example.ui

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import com.example.ui.theme.BrainSyntaxColors

class BrainSyntaxVisualTransformation(private val colors: BrainSyntaxColors) :
  VisualTransformation {

  override fun filter(text: AnnotatedString): TransformedText {
    return TransformedText(highlightBrainCode(text.text, colors), OffsetMapping.Identity)
  }

  companion object {
    private val KEYWORDS =
      setOf(
        "int",
        "float",
        "char",
        "bool",
        "soma",
        "sub",
        "multiplica",
        "div",
        "print",
        "if",
        "else",
        "endif",
        "while",
        "endwhile",
        "and",
        "or",
        "not",
      )

    private val BOOLEANS = setOf("true", "True", "false", "False")

    private val DOT_COMMANDS =
      setOf(
        "IA.programa",
        "IA.digitalwrite.line",
        "Brain.data_types",
        "see.lines",
        "show.vars",
        ".run",
        ".sair_prog",
        ".del",
        ".version",
        ".help",
        ".screnclear",
        ".scanln.Br",
        ".clear_var",
      )

    fun highlightBrainCode(source: String, colors: BrainSyntaxColors): AnnotatedString {
      val builder = AnnotatedString.Builder(source)
      val n = source.length
      var i = 0

      while (i < n) {
        val c = source[i]

        // Line comments: # ... or // ...
        if (c == '#' || (c == '/' && i + 1 < n && source[i + 1] == '/')) {
          val start = i
          while (i < n && source[i] != '\n') i++
          builder.addStyle(
            SpanStyle(color = colors.comment, fontStyle = FontStyle.Italic),
            start,
            i,
          )
          continue
        }

        // String literals
        if (c == '"' || c == '\'') {
          val quote = c
          val start = i
          i++
          while (i < n && source[i] != '\n') {
            if (source[i] == '\\' && i + 1 < n) {
              i += 2
              continue
            }
            if (source[i] == quote) {
              i++
              break
            }
            i++
          }
          builder.addStyle(SpanStyle(color = colors.stringLiteral), start, i)
          continue
        }

        // Numbers
        if (c.isDigit() && (i == 0 || !source[i - 1].isLetterOrDigit())) {
          val start = i
          var hasDot = false
          while (i < n) {
            val ch = source[i]
            if (ch.isDigit()) {
              i++
            } else if (ch == '.' && !hasDot && i + 1 < n && source[i + 1].isDigit()) {
              hasDot = true
              i++
            } else {
              break
            }
          }
          builder.addStyle(SpanStyle(color = colors.numberLiteral), start, i)
          continue
        }

        // Dot commands starting with '.' (e.g. .run, .scanln.Br)
        if (c == '.' && i + 1 < n && source[i + 1].isLetter()) {
          val start = i
          i++
          while (i < n && (source[i].isLetterOrDigit() || source[i] == '_' || source[i] == '.')) {
            i++
          }
          val word = source.substring(start, i)
          if (word in DOT_COMMANDS) {
            builder.addStyle(
              SpanStyle(color = colors.dotCommand, fontWeight = FontWeight.SemiBold),
              start,
              i,
            )
          }
          continue
        }

        // Identifiers / Keywords / Dotted Brain commands (IA.programa, Brain.data_types, etc.)
        if (c.isLetter() || c == '_') {
          val start = i
          while (i < n && (source[i].isLetterOrDigit() || source[i] == '_' || source[i] == '.')) {
            i++
          }
          val token = source.substring(start, i)
          when {
            token in DOT_COMMANDS -> {
              builder.addStyle(
                SpanStyle(color = colors.dotCommand, fontWeight = FontWeight.SemiBold),
                start,
                i,
              )
            }
            token in KEYWORDS -> {
              builder.addStyle(
                SpanStyle(color = colors.keyword, fontWeight = FontWeight.SemiBold),
                start,
                i,
              )
            }
            token in BOOLEANS -> {
              builder.addStyle(
                SpanStyle(color = colors.booleanLiteral, fontWeight = FontWeight.Medium),
                start,
                i,
              )
            }
          }
          continue
        }

        // Operators
        if (c in charArrayOf('=', '+', '-', '*', '/', '%', '<', '>', '!', '&', '|', '^')) {
          builder.addStyle(SpanStyle(color = colors.operator), i, i + 1)
          i++
          continue
        }

        i++
      }

      return builder.toAnnotatedString()
    }
  }
}
