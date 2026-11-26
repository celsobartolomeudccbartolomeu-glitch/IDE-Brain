package com.example

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.example.ui.BrainEditorIndent
import org.junit.Assert.assertEquals
import org.junit.Test

class BrainEditorIndentTest {

  @Test
  fun `auto indents nested while and if blocks and dedents after endif and endwhile`() {
    val step1 =
      BrainEditorIndent.applyAutoIndentOnEnter(
        previous = TextFieldValue("while i <= 5", TextRange(12)),
        incoming = TextFieldValue("while i <= 5\n", TextRange(13)),
      )
    assertEquals("while i <= 5\n    ", step1.text)
    assertEquals(TextRange(17), step1.selection)

    val step2 =
      BrainEditorIndent.applyAutoIndentOnEnter(
        previous = TextFieldValue("while i <= 5\n    if i == 2 # check", TextRange(34)),
        incoming = TextFieldValue("while i <= 5\n    if i == 2 # check\n", TextRange(35)),
      )
    assertEquals("while i <= 5\n    if i == 2 # check\n        ", step2.text)

    val stepElse =
      BrainEditorIndent.applyAutoIndentOnEnter(
        previous =
          TextFieldValue("while i <= 5\n    if i == 2\n        print i\n        else", TextRange(56)),
        incoming =
          TextFieldValue("while i <= 5\n    if i == 2\n        print i\n        else\n", TextRange(57)),
      )
    assertEquals(
      "while i <= 5\n    if i == 2\n        print i\n        else\n            ",
      stepElse.text,
    )

    val afterEndif =
      BrainEditorIndent.computeNextLineIndent(
        "while i <= 5\n    if i == 2\n        print i\n    endif"
      )
    assertEquals("    ", afterEndif)

    val afterEndwhile =
      BrainEditorIndent.computeNextLineIndent(
        "while i <= 5\n    if i == 2\n        print i\n    endif\nendwhile"
      )
    assertEquals("", afterEndwhile)
  }

  @Test
  fun `preserves current line indent for regular statements`() {
    val indent = BrainEditorIndent.computeNextLineIndent("while x > 0\n    int a = 1")
    assertEquals("    ", indent)
  }
}
