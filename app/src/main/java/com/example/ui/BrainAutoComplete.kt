package com.example.ui

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.example.brain.BrainExpressionParser

enum class SuggestionKind(val badge: String) {
  COMMAND("CMD"),
  KEYWORD("KW"),
  TYPE("TIPO"),
  OPERATOR("OP"),
  BOOLEAN("BOOL"),
  VARIABLE("VAR"),
  SNIPPET("BLOCO"),
}

data class BrainSuggestion(
  val id: String,
  val label: String,
  val insertText: String,
  val kind: SuggestionKind,
  val detail: String = "",
  val cursorBackOffset: Int = 0,
)

object BrainAutoComplete {

  private val VAR_DECL_ASSIGN_REGEX = Regex("""^([A-Za-z_]\w*)\s*=(?!=)""")
  private val VAR_DECL_TYPED_REGEX = Regex("""^(?:int|float|char|bool)\s+([A-Za-z_]\w*)\s*=""")
  private val VAR_SCANLN_REGEX = Regex("""^\.scanln\.Br\s+([A-Za-z_]\w*)""")

  private val CONTEXT_ARG_TRIGGERS =
    setOf(
      "print",
      "if",
      "while",
      "soma",
      "sub",
      "multiplica",
      "div",
      ".scanln.Br",
      ".clear_var",
      "IA.digitalwrite.line",
    )

  private val CATALOG: List<BrainSuggestion> =
    listOf(
      // Comandos principais
      BrainSuggestion(
        id = "cmd_print",
        label = "print",
        insertText = "print ",
        kind = SuggestionKind.COMMAND,
        detail = "<expr>",
      ),
      BrainSuggestion(
        id = "cmd_digitalwrite",
        label = "IA.digitalwrite.line",
        insertText = "IA.digitalwrite.line \"\"",
        kind = SuggestionKind.COMMAND,
        detail = "\"texto\"",
        cursorBackOffset = 1,
      ),
      BrainSuggestion(
        id = "cmd_scanln",
        label = ".scanln.Br",
        insertText = ".scanln.Br ",
        kind = SuggestionKind.COMMAND,
        detail = "<var>",
      ),
      BrainSuggestion(
        id = "cmd_programa",
        label = "IA.programa",
        insertText = "IA.programa",
        kind = SuggestionKind.COMMAND,
        detail = "início",
      ),
      BrainSuggestion(
        id = "cmd_run",
        label = ".run",
        insertText = ".run",
        kind = SuggestionKind.COMMAND,
        detail = "executar",
      ),
      BrainSuggestion(
        id = "cmd_show_vars",
        label = "show.vars",
        insertText = "show.vars",
        kind = SuggestionKind.COMMAND,
        detail = "variáveis",
      ),
      BrainSuggestion(
        id = "cmd_clear_var",
        label = ".clear_var",
        insertText = ".clear_var ",
        kind = SuggestionKind.COMMAND,
        detail = "<var>",
      ),
      BrainSuggestion(
        id = "cmd_screnclear",
        label = ".screnclear",
        insertText = ".screnclear",
        kind = SuggestionKind.COMMAND,
        detail = "limpar",
      ),
      BrainSuggestion(
        id = "cmd_data_types",
        label = "Brain.data_types",
        insertText = "Brain.data_types",
        kind = SuggestionKind.COMMAND,
        detail = "tipos",
      ),
      BrainSuggestion(
        id = "cmd_see_lines",
        label = "see.lines",
        insertText = "see.lines",
        kind = SuggestionKind.COMMAND,
        detail = "linhas",
      ),
      BrainSuggestion(
        id = "cmd_del",
        label = ".del",
        insertText = ".del ",
        kind = SuggestionKind.COMMAND,
        detail = "<num>",
      ),
      BrainSuggestion(
        id = "cmd_sair_prog",
        label = ".sair_prog",
        insertText = ".sair_prog",
        kind = SuggestionKind.COMMAND,
        detail = "cancelar",
      ),
      BrainSuggestion(
        id = "cmd_help",
        label = ".help",
        insertText = ".help",
        kind = SuggestionKind.COMMAND,
        detail = "ajuda",
      ),
      BrainSuggestion(
        id = "cmd_version",
        label = ".version",
        insertText = ".version",
        kind = SuggestionKind.COMMAND,
        detail = "versão",
      ),
      // Estruturas de controlo
      BrainSuggestion(
        id = "kw_if",
        label = "if",
        insertText = "if ",
        kind = SuggestionKind.KEYWORD,
        detail = "<condição>",
      ),
      BrainSuggestion(
        id = "snip_if_block",
        label = "if ... endif",
        insertText = "if \n    \nendif",
        kind = SuggestionKind.SNIPPET,
        detail = "bloco",
        cursorBackOffset = 11,
      ),
      BrainSuggestion(
        id = "kw_else",
        label = "else",
        insertText = "else",
        kind = SuggestionKind.KEYWORD,
        detail = "senão",
      ),
      BrainSuggestion(
        id = "kw_endif",
        label = "endif",
        insertText = "endif",
        kind = SuggestionKind.KEYWORD,
        detail = "fim if",
      ),
      BrainSuggestion(
        id = "kw_while",
        label = "while",
        insertText = "while ",
        kind = SuggestionKind.KEYWORD,
        detail = "<condição>",
      ),
      BrainSuggestion(
        id = "snip_while_block",
        label = "while ... endwhile",
        insertText = "while \n    \nendwhile",
        kind = SuggestionKind.SNIPPET,
        detail = "ciclo",
        cursorBackOffset = 14,
      ),
      BrainSuggestion(
        id = "kw_endwhile",
        label = "endwhile",
        insertText = "endwhile",
        kind = SuggestionKind.KEYWORD,
        detail = "fim while",
      ),
      // Tipos de dados
      BrainSuggestion(
        id = "type_int",
        label = "int",
        insertText = "int ",
        kind = SuggestionKind.TYPE,
        detail = "inteiro",
      ),
      BrainSuggestion(
        id = "type_float",
        label = "float",
        insertText = "float ",
        kind = SuggestionKind.TYPE,
        detail = "decimal",
      ),
      BrainSuggestion(
        id = "type_char",
        label = "char",
        insertText = "char ",
        kind = SuggestionKind.TYPE,
        detail = "1 car.",
      ),
      BrainSuggestion(
        id = "type_bool",
        label = "bool",
        insertText = "bool ",
        kind = SuggestionKind.TYPE,
        detail = "lógico",
      ),
      // Operações e operadores
      BrainSuggestion(
        id = "op_soma",
        label = "soma",
        insertText = "soma ",
        kind = SuggestionKind.OPERATOR,
        detail = "a b",
      ),
      BrainSuggestion(
        id = "op_sub",
        label = "sub",
        insertText = "sub ",
        kind = SuggestionKind.OPERATOR,
        detail = "a b",
      ),
      BrainSuggestion(
        id = "op_multiplica",
        label = "multiplica",
        insertText = "multiplica ",
        kind = SuggestionKind.OPERATOR,
        detail = "a b",
      ),
      BrainSuggestion(
        id = "op_div",
        label = "div",
        insertText = "div ",
        kind = SuggestionKind.OPERATOR,
        detail = "a b",
      ),
      BrainSuggestion(
        id = "op_and",
        label = "and",
        insertText = "and ",
        kind = SuggestionKind.OPERATOR,
        detail = "e",
      ),
      BrainSuggestion(
        id = "op_or",
        label = "or",
        insertText = "or ",
        kind = SuggestionKind.OPERATOR,
        detail = "ou",
      ),
      BrainSuggestion(
        id = "op_not",
        label = "not",
        insertText = "not ",
        kind = SuggestionKind.OPERATOR,
        detail = "não",
      ),
      // Booleanos
      BrainSuggestion(
        id = "bool_true",
        label = "true",
        insertText = "true",
        kind = SuggestionKind.BOOLEAN,
        detail = "verdadeiro",
      ),
      BrainSuggestion(
        id = "bool_false",
        label = "false",
        insertText = "false",
        kind = SuggestionKind.BOOLEAN,
        detail = "falso",
      ),
    )

  fun suggest(
    value: TextFieldValue,
    extraVariables: Collection<String> = emptyList(),
    isRepl: Boolean = false,
    maxResults: Int = 10,
  ): List<BrainSuggestion> {
    if (!value.selection.collapsed) return emptyList()
    val text = value.text
    val cursor = value.selection.start.coerceIn(0, text.length)
    val lineStart = text.lastIndexOf('\n', cursor - 1).let { if (it < 0) 0 else it + 1 }
    val lineBeforeCursor = text.substring(lineStart, cursor)

    // Não sugerir dentro de comentários (# ou //) nem dentro de strings abertas
    if (isInsideCommentOrString(lineBeforeCursor)) return emptyList()

    val tokenStart = findTokenStart(lineBeforeCursor)
    val prefix = lineBeforeCursor.substring(tokenStart)
    val prefixBeforeToken = lineBeforeCursor.substring(0, tokenStart).trim()

    val declaredVars = extractVariables(text, extraVariables)
    val openBlocks = analyzeOpenBlocks(text.substring(0, lineStart))

    if (prefix.isEmpty()) {
      // Sugestões contextuais após comandos que esperam variável/expressão (ex.: "print ", "if ", "x = ")
      val lastWord = prefixBeforeToken.split(Regex("""\s+""")).lastOrNull().orEmpty()
      if (lastWord in CONTEXT_ARG_TRIGGERS || prefixBeforeToken.endsWith("=")) {
        val contextual = mutableListOf<BrainSuggestion>()
        declaredVars.forEach { varName ->
          contextual.add(
            BrainSuggestion(
              id = "var_$varName",
              label = varName,
              insertText = varName,
              kind = SuggestionKind.VARIABLE,
              detail = "var",
            )
          )
        }
        if (lastWord == "if" || lastWord == "while" || prefixBeforeToken.endsWith("=")) {
          contextual.add(CATALOG.first { it.id == "bool_true" })
          contextual.add(CATALOG.first { it.id == "bool_false" })
          if (lastWord == "if" || lastWord == "while") {
            contextual.add(CATALOG.first { it.id == "op_not" })
          }
        }
        return contextual.take(maxResults)
      }
      return emptyList()
    }

    val candidates = mutableListOf<BrainSuggestion>()
    if (!isRepl) {
      candidates.addAll(CATALOG)
    } else {
      // No REPL prioriza comandos REPL e comandos de linha única
      candidates.addAll(CATALOG.filterNot { it.kind == SuggestionKind.SNIPPET })
    }

    declaredVars.forEach { varName ->
      candidates.add(
        BrainSuggestion(
          id = "var_$varName",
          label = varName,
          insertText = varName,
          kind = SuggestionKind.VARIABLE,
          detail = "var",
        )
      )
    }

    val lowerPrefix = prefix.lowercase()
    val isAtStatementStart = prefixBeforeToken.isEmpty()

    return candidates
      .mapNotNull { item ->
        val score =
          scoreMatch(
            item = item,
            prefix = prefix,
            lowerPrefix = lowerPrefix,
            isAtStatementStart = isAtStatementStart,
            openBlocks = openBlocks,
          )
        if (score <= 0) null else item to score
      }
      .sortedWith(compareByDescending<Pair<BrainSuggestion, Int>> { it.second }.thenBy { it.first.label.length })
      .map { it.first }
      .distinctBy { it.label }
      .take(maxResults)
  }

  fun applySuggestion(value: TextFieldValue, suggestion: BrainSuggestion): TextFieldValue {
    val text = value.text
    val cursor = value.selection.start.coerceIn(0, text.length)
    val lineStart = text.lastIndexOf('\n', cursor - 1).let { if (it < 0) 0 else it + 1 }
    val lineBeforeCursor = text.substring(lineStart, cursor)
    val tokenStartInLine = findTokenStart(lineBeforeCursor)
    val replaceStart = lineStart + tokenStartInLine

    // Preserva indentação da linha atual caso o snippet tenha múltiplas linhas
    val currentIndent = lineBeforeCursor.takeWhile { it == ' ' || it == '\t' }
    val formattedInsert =
      if (suggestion.insertText.contains('\n') && currentIndent.isNotEmpty()) {
        suggestion.insertText.replace("\n", "\n$currentIndent")
      } else {
        suggestion.insertText
      }

    // Se o snippet tiver quebra de linha, ajusta cursorBackOffset considerando indentação adicionada
    val extraIndentInSuffix =
      if (suggestion.insertText.contains('\n') && suggestion.cursorBackOffset > 0) {
        val tail = suggestion.insertText.takeLast(suggestion.cursorBackOffset)
        tail.count { it == '\n' } * currentIndent.length
      } else {
        0
      }

    val updatedText = text.substring(0, replaceStart) + formattedInsert + text.substring(cursor)
    val rawNewCursor = replaceStart + formattedInsert.length - suggestion.cursorBackOffset - extraIndentInSuffix
    val clampedCursor = rawNewCursor.coerceIn(replaceStart, updatedText.length)

    return value.copy(
      text = updatedText,
      selection = TextRange(clampedCursor),
    )
  }

  fun extractVariables(source: String, extraVariables: Collection<String> = emptyList()): List<String> {
    val found = linkedSetOf<String>()
    for (rawLine in source.lines()) {
      val clean = BrainExpressionParser.stripInlineComment(rawLine)
      if (clean.isEmpty()) continue
      VAR_DECL_TYPED_REGEX.find(clean)?.groupValues?.getOrNull(1)?.let { found.add(it) }
      VAR_SCANLN_REGEX.find(clean)?.groupValues?.getOrNull(1)?.let { found.add(it) }
      val assignName = VAR_DECL_ASSIGN_REGEX.find(clean)?.groupValues?.getOrNull(1)
      if (assignName != null && assignName !in setOf("int", "float", "char", "bool")) {
        found.add(assignName)
      }
    }
    found.addAll(extraVariables)
    return found.toList()
  }

  private fun scoreMatch(
    item: BrainSuggestion,
    prefix: String,
    lowerPrefix: String,
    isAtStatementStart: Boolean,
    openBlocks: OpenBlockState,
  ): Int {
    val label = item.label
    val lowerLabel = label.lowercase()

    // Se o utilizador já digitou exatamente a palavra completa sem espaço, ainda pode escolher snippets (ex: "if" -> "if ... endif")
    val baseScore =
      when {
        label.startsWith(prefix) && label != prefix -> 120
        lowerLabel.startsWith(lowerPrefix) && lowerLabel != lowerPrefix -> 105
        label == prefix && item.kind == SuggestionKind.SNIPPET -> 95
        lowerLabel.contains(".$lowerPrefix") -> 80
        lowerLabel.contains(lowerPrefix) && prefix.length >= 2 -> 55
        else -> 0
      }
    if (baseScore == 0) return 0

    var bonus = 0
    if (isAtStatementStart) {
      if (item.kind in setOf(SuggestionKind.COMMAND, SuggestionKind.KEYWORD, SuggestionKind.TYPE, SuggestionKind.SNIPPET)) {
        bonus += 15
      }
    } else {
      // No meio da linha, prioriza variáveis e booleanos/operadores
      if (item.kind == SuggestionKind.VARIABLE) bonus += 25
      if (item.kind == SuggestionKind.BOOLEAN || item.kind == SuggestionKind.OPERATOR) bonus += 10
    }

    // Prioridade inteligente para fechar blocos abertos (if -> else/endif; while -> endwhile)
    if (openBlocks.lastOpened == "if" && (label == "endif" || label == "else")) {
      bonus += 35
    } else if (openBlocks.lastOpened == "while" && label == "endwhile") {
      bonus += 35
    } else if (openBlocks.openIfCount > 0 && (label == "endif" || label == "else")) {
      bonus += 20
    } else if (openBlocks.openWhileCount > 0 && label == "endwhile") {
      bonus += 20
    }

    return baseScore + bonus
  }

  private data class OpenBlockState(
    val openIfCount: Int,
    val openWhileCount: Int,
    val lastOpened: String?,
  )

  private fun analyzeOpenBlocks(textBeforeLine: String): OpenBlockState {
    val stack = ArrayDeque<String>()
    for (rawLine in textBeforeLine.lines()) {
      val stripped = BrainExpressionParser.stripInlineComment(rawLine)
      when {
        stripped == "if" || stripped.startsWith("if ") -> stack.addLast("if")
        stripped == "while" || stripped.startsWith("while ") -> stack.addLast("while")
        stripped == "endif" -> {
          val idx = stack.lastIndexOf("if")
          if (idx >= 0) stack.removeAt(idx)
        }
        stripped == "endwhile" -> {
          val idx = stack.lastIndexOf("while")
          if (idx >= 0) stack.removeAt(idx)
        }
      }
    }
    return OpenBlockState(
      openIfCount = stack.count { it == "if" },
      openWhileCount = stack.count { it == "while" },
      lastOpened = stack.lastOrNull(),
    )
  }

  private fun findTokenStart(lineBeforeCursor: String): Int {
    var i = lineBeforeCursor.length
    while (i > 0) {
      val ch = lineBeforeCursor[i - 1]
      if (ch.isLetterOrDigit() || ch == '_' || ch == '.') {
        i--
      } else {
        break
      }
    }
    return i
  }

  private fun isInsideCommentOrString(lineBeforeCursor: String): Boolean {
    var inQuote: Char? = null
    var i = 0
    while (i < lineBeforeCursor.length) {
      val ch = lineBeforeCursor[i]
      if (inQuote != null) {
        if (ch == '\\' && i + 1 < lineBeforeCursor.length) {
          i += 2
          continue
        }
        if (ch == inQuote) {
          inQuote = null
        }
        i++
        continue
      }
      if (ch == '"' || ch == '\'') {
        inQuote = ch
        i++
        continue
      }
      if (ch == '#' || (ch == '/' && i + 1 < lineBeforeCursor.length && lineBeforeCursor[i + 1] == '/')) {
        return true
      }
      i++
    }
    return inQuote != null
  }
}
