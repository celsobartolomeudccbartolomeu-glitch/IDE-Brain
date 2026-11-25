package com.example

import com.example.brain.BrainIO
import com.example.brain.BrainInterpreter
import com.example.brain.OutputType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrainInterpreterTest {

  private class RecordingIO(private val inputs: ArrayDeque<String> = ArrayDeque()) : BrainIO {
    val outputs = mutableListOf<Pair<String, OutputType>>()
    var clearedCount = 0

    override fun onOutput(text: String, type: OutputType) {
      outputs.add(text to type)
    }

    override fun onClearScreen() {
      clearedCount++
      outputs.clear()
    }

    override suspend fun onRequestInput(varName: String): String {
      return inputs.removeFirstOrNull() ?: ""
    }
  }

  @Test
  fun `variables and data types enforce rules and preserve integers`() = runTest {
    val io = RecordingIO()
    val interp = BrainInterpreter(io)

    interp.executarScriptEditor(
      """
      Brain.data_types
      int a = 3
      int b = 4
      float f = 2.5
      char c = "Z"
      char invalido = "AB"
      bool ok = 1
      bool nao = false
      x = 10
      x == 5
      print x
      soma a b
      div 8 2
      div 5 0
      3 + 4
      """.trimIndent()
    )

    val texts = io.outputs.map { it.first }
    assertTrue(texts.contains("Biblioteca Brain.data_types carregada"))
    assertTrue(texts.contains("Erro: char aceita apenas 1 caractere"))
    assertTrue(texts.contains("10")) // x == 5 did not overwrite x
    assertTrue(texts.contains("7")) // soma 3 4 -> 7 (not 7.0)
    assertTrue(texts.contains("4")) // div 8 2 -> 4 (not 4.0)
    assertTrue(texts.contains("Erro: Divisão por zero"))
    assertFalse(texts.contains("7.0"))
  }

  @Test
  fun `nested if else and while loops execute and cap at 1000 iterations`() = runTest {
    val io = RecordingIO()
    val interp = BrainInterpreter(io)

    interp.executarScriptEditor(
      """
      # Comentário hash
      // Comentário barra
      int i = 1
      while i <= 3
          if i == 2
              print "dois"
          else
              print i
          endif
          i = i + 1
      endwhile
      """.trimIndent()
    )

    assertEquals(listOf("1", "dois", "3"), io.outputs.map { it.first })

    val ioCap = RecordingIO()
    val interpCap = BrainInterpreter(ioCap)
    interpCap.executarScriptEditor(
      """
      int k = 0
      while k >= 0
          k = k + 1
      endwhile
      """.trimIndent()
    )
    assertTrue(ioCap.outputs.any { it.first == "Loop interrompido: limite de 1000 iterações" })
  }

  @Test
  fun `scanln show vars clear var and repl program mode work`() = runTest {
    val io = RecordingIO(ArrayDeque(listOf("42")))
    val interp = BrainInterpreter(io)

    interp.processarEntradaRepl("IA.programa")
    interp.processarEntradaRepl(".version")
    interp.processarEntradaRepl(".scanln.Br idade")
    interp.processarEntradaRepl("IA.digitalwrite.line idade")
    interp.processarEntradaRepl("see.lines")
    interp.processarEntradaRepl(".run")
    interp.processarEntradaRepl("show.vars")
    interp.processarEntradaRepl(".clear_var idade")
    interp.processarEntradaRepl("show.vars")

    val texts = io.outputs.map { it.first }
    assertTrue(texts.contains("Brain Language v1.10_test-fix3"))
    assertTrue(texts.contains("42"))
    assertTrue(texts.contains("idade = 42"))
    assertTrue(texts.contains("Variável idade removida"))
    assertTrue(texts.contains("Nenhuma variável definida"))
  }

  @Test
  fun `inline and full line comments with hash or double slash are ignored safely`() = runTest {
    val io = RecordingIO()
    val interp = BrainInterpreter(io)

    interp.executarScriptEditor(
      """
      # Comentário de linha inteira com #
      // Comentário de linha inteira com //
      int x = 10 # comentário inline com hash
      if x == 10 // comentário inline na condição
          print "URL: https://brain.dev #ok" // comentário após string
      else # comentário no else
          print "nao"
      endif // comentário no endif
      """.trimIndent()
    )

    assertEquals(listOf("URL: https://brain.dev #ok"), io.outputs.map { it.first })
  }
}
