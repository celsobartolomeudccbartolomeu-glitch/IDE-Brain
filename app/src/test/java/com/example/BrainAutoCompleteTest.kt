package com.example

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.example.ui.BrainAutoComplete
import com.example.ui.SuggestionKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrainAutoCompleteTest {

  @Test
  fun suggestsCommandsMatchingPrefix() {
    val input = TextFieldValue("pr", TextRange(2))
    val suggestions = BrainAutoComplete.suggest(input)
    assertTrue(suggestions.any { it.label == "print" })
  }

  @Test
  fun suggestsDotCommandsWhenTypingDotOrIa() {
    val dotInput = TextFieldValue(".", TextRange(1))
    val dotSuggestions = BrainAutoComplete.suggest(dotInput)
    assertTrue(dotSuggestions.any { it.label == ".run" })
    assertTrue(dotSuggestions.any { it.label == ".scanln.Br" })

    val iaInput = TextFieldValue("ia", TextRange(2))
    val iaSuggestions = BrainAutoComplete.suggest(iaInput)
    assertTrue(iaSuggestions.any { it.label == "IA.programa" })
    assertTrue(iaSuggestions.any { it.label == "IA.digitalwrite.line" })
  }

  @Test
  fun suggestsDeclaredVariablesAfterPrintSpace() {
    val code = "int contador = 5\nnome = \"Brain\"\nprint "
    val input = TextFieldValue(code, TextRange(code.length))
    val suggestions = BrainAutoComplete.suggest(input)
    assertTrue(suggestions.any { it.kind == SuggestionKind.VARIABLE && it.label == "contador" })
    assertTrue(suggestions.any { it.kind == SuggestionKind.VARIABLE && it.label == "nome" })
  }

  @Test
  fun boostsClosingKeywordInsideOpenBlock() {
    val code = "if x > 0\n    en"
    val input = TextFieldValue(code, TextRange(code.length))
    val suggestions = BrainAutoComplete.suggest(input)
    assertEquals("endif", suggestions.first().label)

    val whileCode = "while n < 5\n    en"
    val whileSuggestions = BrainAutoComplete.suggest(TextFieldValue(whileCode, TextRange(whileCode.length)))
    assertEquals("endwhile", whileSuggestions.first().label)
  }

  @Test
  fun suppressesSuggestionsInsideCommentsAndStrings() {
    val commentInput = TextFieldValue("# pr", TextRange(4))
    assertTrue(BrainAutoComplete.suggest(commentInput).isEmpty())

    val inlineComment = TextFieldValue("x = 1 // pr", TextRange(11))
    assertTrue(BrainAutoComplete.suggest(inlineComment).isEmpty())

    val stringInput = TextFieldValue("print \"pr", TextRange(9))
    assertTrue(BrainAutoComplete.suggest(stringInput).isEmpty())
  }

  @Test
  fun applySuggestionReplacesOnlyTokenPrefixAndSetsCursor() {
    val code = "    ia.dig"
    val input = TextFieldValue(code, TextRange(code.length))
    val suggestion = BrainAutoComplete.suggest(input).first { it.label == "IA.digitalwrite.line" }
    val applied = BrainAutoComplete.applySuggestion(input, suggestion)
    assertEquals("    IA.digitalwrite.line \"\"", applied.text)
    assertEquals(applied.text.length - 1, applied.selection.start)
    assertFalse(applied.text.contains("ia.dig"))
  }

  @Test
  fun applySnippetPreservesLineIndentation() {
    val code = "    if"
    val input = TextFieldValue(code, TextRange(code.length))
    val snippet = BrainAutoComplete.suggest(input).first { it.label == "if ... endif" }
    val applied = BrainAutoComplete.applySuggestion(input, snippet)
    assertEquals("    if \n        \n    endif", applied.text)
    assertEquals(7, applied.selection.start)
  }
}
