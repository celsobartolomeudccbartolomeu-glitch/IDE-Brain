package com.example.ui

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.example.brain.BrainExpressionParser

object BrainEditorIndent {
  private const val INDENT_STEP = 4

  fun computeNextLineIndent(textBeforeNewline: String): String {
    val lines = textBeforeNewline.split('\n')
    val currentLine = lines.lastOrNull().orEmpty()
    val currentLeadingWhitespace = currentLine.takeWhile { it == ' ' || it == '\t' }
    val currentIndentColumns =
      currentLeadingWhitespace.sumOf { if (it == '\t') INDENT_STEP else 1 }

    var depth = 0
    for (rawLine in lines) {
      val stripped = BrainExpressionParser.stripInlineComment(rawLine)
      if (isBlockOpener(stripped)) {
        depth++
      } else if (isBlockCloser(stripped)) {
        depth = maxOf(0, depth - 1)
      }
    }

    val lastStripped = BrainExpressionParser.stripInlineComment(currentLine)
    return when {
      isBlockOpener(lastStripped) || isElseLine(lastStripped) -> {
        val targetCols = maxOf(depth * INDENT_STEP, currentIndentColumns + INDENT_STEP)
        " ".repeat(targetCols)
      }
      isBlockCloser(lastStripped) -> {
        " ".repeat(depth * INDENT_STEP)
      }
      else -> currentLeadingWhitespace
    }
  }

  fun applyAutoIndentOnEnter(
    previous: TextFieldValue,
    incoming: TextFieldValue,
  ): TextFieldValue {
    if (!incoming.selection.collapsed) return incoming
    val cursor = incoming.selection.start
    if (cursor <= 0 || cursor > incoming.text.length) return incoming
    if (incoming.text[cursor - 1] != '\n') return incoming

    val prevSelStart = previous.selection.min.coerceIn(0, previous.text.length)
    val prevSelEnd = previous.selection.max.coerceIn(0, previous.text.length)
    val prefixMatches =
      cursor - 1 == prevSelStart &&
        incoming.text.substring(0, prevSelStart) == previous.text.substring(0, prevSelStart)
    val suffixMatches =
      incoming.text.substring(cursor) == previous.text.substring(prevSelEnd)

    if (!prefixMatches || !suffixMatches) return incoming

    val textBeforeNewline = incoming.text.substring(0, cursor - 1)
    val indent = computeNextLineIndent(textBeforeNewline)
    if (indent.isEmpty()) return incoming

    val updatedText =
      incoming.text.substring(0, cursor) + indent + incoming.text.substring(cursor)
    return incoming.copy(
      text = updatedText,
      selection = TextRange(cursor + indent.length),
    )
  }

  private fun isBlockOpener(stripped: String): Boolean {
    return stripped == "if" ||
      stripped.startsWith("if ") ||
      stripped == "while" ||
      stripped.startsWith("while ")
  }

  private fun isElseLine(stripped: String): Boolean {
    return stripped == "else" || stripped == "else:"
  }

  private fun isBlockCloser(stripped: String): Boolean {
    return stripped == "endif" || stripped == "endwhile"
  }
}
