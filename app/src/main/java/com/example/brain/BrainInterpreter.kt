package com.example.brain

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.yield

enum class OutputType {
  OUTPUT,
  ERROR,
  WARNING,
  SYSTEM,
  PROMPT,
}

interface BrainIO {
  fun onOutput(text: String, type: OutputType = OutputType.OUTPUT)
  fun onClearScreen()
  suspend fun onRequestInput(varName: String): String
  fun onProgramModeChanged(active: Boolean, lineCount: Int) {}
  fun onExitRequested() {}
}

/**
 * Interpretador da Brain Language v1.10_test-fix3 portado fielmente do Python,
 * executando em Coroutines (fora da thread de UI) com parser de expressões seguro.
 */
class BrainInterpreter(private val io: BrainIO) {

  companion object {
    const val VERSION = "Brain Language v1.10_test-fix3"
    const val MAX_PROGRAM_WHILE_ITERATIONS = 1000
    const val MAX_REPL_WHILE_ITERATIONS = 50

    private val ASSIGN_REGEX = Regex("""^(\w+)\s*=(?!=)\s*(.+)$""")
    private val INLINE_BIN_OP_REGEX =
      Regex("""^(\w+|\d+(?:\.\d+)?)\s*([+\-*/])\s*(\w+|\d+(?:\.\d+)?)$""")
    private val WHITESPACE_REGEX = Regex("""\s+""")
  }

  private val variaveis = linkedMapOf<String, Any>()
  private var modoPrograma = false
  private val programa = mutableListOf<String>()

  fun resetSession() {
    variaveis.clear()
    modoPrograma = false
    programa.clear()
    io.onProgramModeChanged(false, 0)
  }

  fun resetVariables() {
    variaveis.clear()
  }

  fun isProgramModeActive(): Boolean = modoPrograma

  fun getVariablesSnapshot(): Map<String, Any> = variaveis.toMap()

  private fun evalSeguro(expr: String): Any {
    return BrainExpressionParser.evaluate(expr, variaveis)
  }

  private fun avaliarExpressao(expr: String): Any {
    return try {
      evalSeguro(expr)
    } catch (_: Exception) {
      expr
    }
  }

  private fun tipoDado(tipo: String, nome: String, valor: String) {
    when (tipo) {
      "int" -> {
        val parsed = valor.trim().toLongOrNull()
        if (parsed != null) {
          variaveis[nome] = parsed
        } else {
          io.onOutput("Erro: Valor inválido para int", OutputType.ERROR)
        }
      }
      "float" -> {
        val parsed = valor.trim().toDoubleOrNull()
        if (parsed != null) {
          variaveis[nome] = parsed
        } else {
          io.onOutput("Erro: Valor inválido para float", OutputType.ERROR)
        }
      }
      "char" -> {
        // FIX 2: char aceita apenas 1 caractere
        if (valor.length == 1) {
          variaveis[nome] = valor
        } else {
          io.onOutput("Erro: char aceita apenas 1 caractere", OutputType.ERROR)
        }
      }
      "bool" -> {
        when (valor) {
          "true", "True", "1" -> variaveis[nome] = true
          "false", "False", "0" -> variaveis[nome] = false
          else -> io.onOutput("Erro: Valor inválido para bool", OutputType.ERROR)
        }
      }
      else -> io.onOutput("Erro: Tipo de dado desconhecido", OutputType.ERROR)
    }
  }

  private fun div(a: Number, b: Number): Any? {
    val db = b.toDouble()
    return if (db != 0.0) {
      BrainExpressionParser.normalizaResultado(a.toDouble() / db)
    } else {
      io.onOutput("Erro: Divisão por zero", OutputType.ERROR)
      null
    }
  }

  private fun addNums(a: Number, b: Number): Any {
    return if (a is Long && b is Long) {
      a + b
    } else {
      BrainExpressionParser.normalizaResultado(a.toDouble() + b.toDouble())!!
    }
  }

  private fun subNums(a: Number, b: Number): Any {
    return if (a is Long && b is Long) {
      a - b
    } else {
      BrainExpressionParser.normalizaResultado(a.toDouble() - b.toDouble())!!
    }
  }

  private fun mulNums(a: Number, b: Number): Any {
    return if (a is Long && b is Long) {
      a * b
    } else {
      BrainExpressionParser.normalizaResultado(a.toDouble() * b.toDouble())!!
    }
  }

  /**
   * Processa uma entrada no Modo REPL (respeitando estado IA.programa / .run / FIX 6).
   */
  suspend fun processarEntradaRepl(entradaBruta: String) {
    currentCoroutineContext().ensureActive()
    val entrada = entradaBruta.trim()
    if (entrada.isEmpty()) return

    if (entrada.equals("sair", ignoreCase = true)) {
      io.onOutput("Saindo do Brain Language. Até logo!", OutputType.SYSTEM)
      modoPrograma = false
      programa.clear()
      io.onProgramModeChanged(false, 0)
      io.onExitRequested()
      return
    }

    if (modoPrograma) {
      // FIX 6: comandos de sistema também funcionam dentro do modo programa
      if (entrada in setOf(".run", "see.lines", ".sair_prog", ".help", ".version", ".screnclear") ||
        entrada.startsWith(".del ")
      ) {
        executarLinha(entrada)
      } else {
        programa.add(entrada)
        io.onProgramModeChanged(true, programa.size)
      }
    } else {
      executarLinha(entrada)
    }
  }

  /**
   * Executa um script completo no Modo Programa (editor).
   * Aceita código com ou sem wrapper IA.programa / .run.
   */
  suspend fun executarScriptEditor(codigo: String) {
    currentCoroutineContext().ensureActive()
    resetVariables()
    val rawLines = codigo.lines()
    val codeOnlyLines =
      rawLines.map { BrainExpressionParser.stripInlineComment(it) }.filter { it.isNotEmpty() }

    val linesToRun =
      if (codeOnlyLines.firstOrNull() == "IA.programa" && codeOnlyLines.lastOrNull() == ".run") {
        val firstIdx =
          rawLines.indexOfFirst { BrainExpressionParser.stripInlineComment(it) == "IA.programa" }
        val lastIdx =
          rawLines.indexOfLast { BrainExpressionParser.stripInlineComment(it) == ".run" }
        if (firstIdx in 0 until lastIdx) {
          rawLines.subList(firstIdx + 1, lastIdx)
        } else {
          rawLines
        }
      } else {
        rawLines
      }

    executarPrograma(linesToRun)
  }

  suspend fun executarLinha(linhaBruta: String) {
    currentCoroutineContext().ensureActive()
    val linha = BrainExpressionParser.stripInlineComment(linhaBruta)

    if (linha.isEmpty()) {
      return
    }

    // FIX 1: '=' seguido de outro '=' não é atribuição (ex.: x == 5)
    val assignMatch = ASSIGN_REGEX.matchEntire(linha)
    if (assignMatch != null) {
      val nome = assignMatch.groupValues[1]
      val valor = assignMatch.groupValues[2].trim()
      if (BrainExpressionParser.isSingleQuotedLiteral(valor) && valor.startsWith("\"")) {
        variaveis[nome] = BrainExpressionParser.unquote(valor)
      } else {
        variaveis[nome] = avaliarExpressao(valor)
      }
      return
    }

    if (linha.startsWith("Brain.data_types")) {
      io.onOutput("Biblioteca Brain.data_types carregada", OutputType.SYSTEM)
      return
    }

    if (linha.startsWith("int ") || linha.startsWith("float ") ||
      linha.startsWith("char ") || linha.startsWith("bool ")
    ) {
      val partes = linha.split(WHITESPACE_REGEX)
      if (partes.size >= 4 && partes[2] == "=") {
        val tipo = partes[0]
        val nome = partes[1]
        val valorRaw = partes.drop(3).joinToString(" ")
        val valor =
          if (valorRaw.length >= 2 && valorRaw.startsWith("\"") && valorRaw.endsWith("\"")) {
            valorRaw.substring(1, valorRaw.length - 1)
          } else {
            valorRaw.trim('"')
          }
        tipoDado(tipo, nome, valor)
      } else {
        io.onOutput(
          "Erro: Formato incorreto para declaração de variável. Use: int nome = valor",
          OutputType.ERROR,
        )
      }
      return
    }

    if (linha.startsWith("soma ") || linha.startsWith("sub ") ||
      linha.startsWith("multiplica ") || linha.startsWith("div ")
    ) {
      val partes = linha.split(WHITESPACE_REGEX)
      if (partes.size == 3) {
        val op = partes[0]
        val a = BrainExpressionParser.paraNumero(avaliarExpressao(partes[1]))
        val b = BrainExpressionParser.paraNumero(avaliarExpressao(partes[2]))
        if (a == null || b == null) {
          io.onOutput("Erro: Valores inválidos para operação", OutputType.ERROR)
          return
        }
        when (op) {
          "soma" -> io.onOutput(BrainExpressionParser.formatValue(addNums(a, b)))
          "sub" -> io.onOutput(BrainExpressionParser.formatValue(subNums(a, b)))
          "multiplica" -> io.onOutput(BrainExpressionParser.formatValue(mulNums(a, b)))
          "div" -> {
            val resultado = div(a, b)
            if (resultado != null) {
              io.onOutput(BrainExpressionParser.formatValue(resultado))
            }
          }
        }
      } else {
        io.onOutput("Erro: Formato incorreto para operação. Use: soma a b", OutputType.ERROR)
      }
      return
    }

    val binOpMatch = INLINE_BIN_OP_REGEX.matchEntire(linha)
    if (binOpMatch != null) {
      val leftRaw = binOpMatch.groupValues[1].trim()
      val op = binOpMatch.groupValues[2]
      val rightRaw = binOpMatch.groupValues[3].trim()
      val a = BrainExpressionParser.paraNumero(avaliarExpressao(leftRaw))
      val b = BrainExpressionParser.paraNumero(avaliarExpressao(rightRaw))
      if (a == null || b == null) {
        io.onOutput("Erro: Valores inválidos para operação", OutputType.ERROR)
        return
      }
      when (op) {
        "+" -> io.onOutput(BrainExpressionParser.formatValue(addNums(a, b)))
        "-" -> io.onOutput(BrainExpressionParser.formatValue(subNums(a, b)))
        "*" -> io.onOutput(BrainExpressionParser.formatValue(mulNums(a, b)))
        "/" -> {
          val resultado = div(a, b)
          if (resultado != null) {
            io.onOutput(BrainExpressionParser.formatValue(resultado))
          }
        }
      }
      return
    }

    if (linha == "if" || linha.startsWith("if ")) {
      val condicao = linha.drop(2).trim()
      try {
        val resultado = evalSeguro(condicao)
        if (BrainExpressionParser.isTruthy(resultado)) {
          io.onOutput("Condição verdadeira")
        } else {
          io.onOutput("Condição falsa")
        }
      } catch (_: Exception) {
        io.onOutput("Erro: Condição inválida", OutputType.ERROR)
      }
      return
    }

    if (linha == "else" || linha == "else:") {
      io.onOutput(
        "Bloco else executado (use dentro de IA.programa com endif)",
        OutputType.WARNING,
      )
      return
    }

    if (linha == "while" || linha.startsWith("while ")) {
      val condicao = linha.drop(5).trim()
      var contador = 0
      while (true) {
        currentCoroutineContext().ensureActive()
        val resultado =
          try {
            evalSeguro(condicao)
          } catch (_: Exception) {
            io.onOutput("Erro: Condição inválida no while", OutputType.ERROR)
            break
          }
        if (BrainExpressionParser.isTruthy(resultado)) {
          io.onOutput("Loop ativo")
        } else {
          break
        }
        contador++
        if (contador > MAX_REPL_WHILE_ITERATIONS) {
          io.onOutput("Loop interrompido: limite de 50 iterações", OutputType.WARNING)
          break
        }
        if (contador % 25 == 0) yield()
      }
      return
    }

    if (linha == "IA.programa") {
      modoPrograma = true
      programa.clear()
      io.onProgramModeChanged(true, 0)
      io.onOutput("Modo programa iniciado. Digite '.run'", OutputType.SYSTEM)
      return
    }

    if (linha == ".sair_prog") {
      modoPrograma = false
      programa.clear()
      io.onProgramModeChanged(false, 0)
      io.onOutput("Modo programa cancelado.", OutputType.SYSTEM)
      return
    }

    if (linha == ".run") {
      io.onOutput("Executando programa...", OutputType.SYSTEM)
      val linhasParaExecutar = programa.toList()
      modoPrograma = false
      programa.clear()
      io.onProgramModeChanged(false, 0)
      executarPrograma(linhasParaExecutar)
      return
    }

    if (linha == "see.lines") {
      if (programa.isEmpty()) {
        io.onOutput("(programa vazio)", OutputType.SYSTEM)
      } else {
        programa.forEachIndexed { idx, l -> io.onOutput("${idx + 1}: $l") }
      }
      return
    }

    if (linha.startsWith(".del ")) {
      val parts = linha.split(WHITESPACE_REGEX)
      val num = parts.getOrNull(1)?.toIntOrNull()
      if (num == null) {
        io.onOutput("Erro: Use .del <numero>", OutputType.ERROR)
      } else if (num in 1..programa.size) {
        val removida = programa.removeAt(num - 1)
        io.onProgramModeChanged(modoPrograma, programa.size)
        io.onOutput("Linha $num removida: $removida", OutputType.SYSTEM)
      } else {
        io.onOutput("Erro: Número da linha inválido", OutputType.ERROR)
      }
      return
    }

    if (linha == ".version") {
      io.onOutput(VERSION, OutputType.SYSTEM)
      return
    }

    if (linha == ".help") {
      val ajuda =
        listOf(
          "Comandos disponíveis:",
          "IA.programa - Inicia o modo programa",
          ".run - Executa o programa",
          "see.lines - Mostra as linhas do programa",
          ".del <numero> - Remove uma linha do programa",
          ".sair_prog - Cancela o modo programa",
          ".version - Mostra a versão",
          ".help - Mostra ajuda",
          "IA.digitalwrite.line <texto> - Mostra um texto",
          ".screnclear - Limpa a tela",
          ".scanln.Br <variavel> - Lê entrada do usuário",
          "show.vars - Mostra variáveis",
          ".clear_var <variavel> - Apaga uma variável",
          "Brain.data_types - Carrega tipos de dados",
          "int/float/char/bool nome = valor - Declara variável",
          "soma/sub/multiplica/div a b - Operações",
          "if condicao / else / endif - Condição com bloco",
          "while condicao / endwhile - Loop com bloco",
          "print <texto> - Mostra um texto",
        )
      ajuda.forEach { io.onOutput(it, OutputType.SYSTEM) }
      return
    }

    if (linha.startsWith("IA.digitalwrite.line ")) {
      val texto = linha.substring("IA.digitalwrite.line ".length).trim()
      imprimirArgumento(texto)
      return
    }

    if (linha == ".screnclear") {
      io.onClearScreen()
      return
    }

    if (linha.startsWith(".scanln.Br ")) {
      val nome = linha.substring(".scanln.Br ".length).trim()
      if (nome.isEmpty()) {
        io.onOutput("Erro: Indique o nome da variável", OutputType.ERROR)
        return
      }
      val valor = io.onRequestInput(nome)
      if (valor.contains(".")) {
        val d = valor.trim().toDoubleOrNull()
        variaveis[nome] = d ?: valor
      } else {
        val l = valor.trim().toLongOrNull()
        variaveis[nome] = l ?: valor
      }
      return
    }

    if (linha == "show.vars") {
      if (variaveis.isNotEmpty()) {
        variaveis.forEach { (nome, valor) ->
          io.onOutput("$nome = ${BrainExpressionParser.formatValue(valor)}")
        }
      } else {
        io.onOutput("Nenhuma variável definida", OutputType.SYSTEM)
      }
      return
    }

    if (linha.startsWith(".clear_var ")) {
      val nome = linha.substring(".clear_var ".length).trim()
      if (variaveis.containsKey(nome)) {
        variaveis.remove(nome)
        io.onOutput("Variável $nome removida", OutputType.SYSTEM)
      } else {
        io.onOutput("Variável $nome não encontrada", OutputType.WARNING)
      }
      return
    }

    if (linha.startsWith("print ")) {
      val texto = linha.substring(6).trim()
      imprimirArgumento(texto)
      return
    }

    if (variaveis.containsKey(linha)) {
      io.onOutput(BrainExpressionParser.formatValue(variaveis[linha]))
      return
    }

    io.onOutput("Comando desconhecido: $linha", OutputType.ERROR)
  }

  private fun imprimirArgumento(texto: String) {
    if (BrainExpressionParser.isSingleQuotedLiteral(texto) && texto.startsWith("\"")) {
      io.onOutput(BrainExpressionParser.unquote(texto))
    } else if (variaveis.containsKey(texto)) {
      io.onOutput(BrainExpressionParser.formatValue(variaveis[texto]))
    } else {
      try {
        val res = evalSeguro(texto)
        io.onOutput(BrainExpressionParser.formatValue(res))
      } catch (_: BrainDivisionByZeroException) {
        io.onOutput("Erro: Divisão por zero", OutputType.ERROR)
      } catch (_: Exception) {
        io.onOutput(texto)
      }
    }
  }

  suspend fun executarPrograma(linhas: List<String>) {
    var i = 0
    while (i < linhas.size) {
      currentCoroutineContext().ensureActive()
      val linha = BrainExpressionParser.stripInlineComment(linhas[i])

      if (linha.isEmpty()) {
        i++
        continue
      }

      if (linha == "if" || linha.startsWith("if ")) {
        val condicao = linha.drop(2).trim()
        var nivel = 0
        var posElse = -1
        var posEndif = -1
        var j = i + 1
        while (j < linhas.size) {
          val l = BrainExpressionParser.stripInlineComment(linhas[j])
          if (l == "if" || l.startsWith("if ")) {
            nivel++
          } else if (l == "endif") {
            if (nivel == 0) {
              posEndif = j
              break
            }
            nivel--
          } else if (l == "else" && nivel == 0) {
            if (posElse == -1) {
              posElse = j
            } else {
              io.onOutput("Aviso: 'else' duplicado ignorado", OutputType.WARNING)
            }
          }
          j++
        }

        if (posEndif == -1) {
          io.onOutput("Erro: if sem endif", OutputType.ERROR)
          i++
          continue
        }

        val resultado =
          try {
            evalSeguro(condicao)
          } catch (_: Exception) {
            io.onOutput("Erro na condição do if", OutputType.ERROR)
            i = posEndif + 1
            continue
          }

        if (BrainExpressionParser.isTruthy(resultado)) {
          val fim = if (posElse != -1) posElse else posEndif
          executarPrograma(linhas.subList(i + 1, fim))
        } else if (posElse != -1) {
          executarPrograma(linhas.subList(posElse + 1, posEndif))
        }

        i = posEndif + 1
        continue
      }

      if (linha == "while" || linha.startsWith("while ")) {
        val condicao = linha.drop(5).trim()
        var nivel = 0
        var posEnd = -1
        var j = i + 1
        while (j < linhas.size) {
          val l = BrainExpressionParser.stripInlineComment(linhas[j])
          if (l == "while" || l.startsWith("while ")) {
            nivel++
          } else if (l == "endwhile") {
            if (nivel == 0) {
              posEnd = j
              break
            }
            nivel--
          }
          j++
        }

        if (posEnd == -1) {
          io.onOutput("Erro: while sem endwhile", OutputType.ERROR)
          i++
          continue
        }

        var contador = 0
        val corpo = linhas.subList(i + 1, posEnd)
        while (true) {
          currentCoroutineContext().ensureActive()
          val resultado =
            try {
              evalSeguro(condicao)
            } catch (_: Exception) {
              io.onOutput("Erro no while", OutputType.ERROR)
              break
            }
          if (!BrainExpressionParser.isTruthy(resultado)) {
            break
          }
          executarPrograma(corpo)
          contador++
          if (contador > MAX_PROGRAM_WHILE_ITERATIONS) {
            io.onOutput("Loop interrompido: limite de 1000 iterações", OutputType.WARNING)
            break
          }
          if (contador % 25 == 0) yield()
        }

        i = posEnd + 1
        continue
      }

      if (linha in setOf("else", "endif", "endwhile")) {
        i++
        continue
      }

      executarLinha(linha)
      i++
    }
  }
}
