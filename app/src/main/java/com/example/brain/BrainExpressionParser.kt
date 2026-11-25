package com.example.brain

import kotlin.math.floor
import kotlin.math.pow

class BrainEvalException(message: String) : Exception(message)

class BrainDivisionByZeroException : Exception("Erro: Divisão por zero")

/**
 * Parser próprio e seguro para expressões da Brain Language.
 * Nunca utiliza eval() nem reflexão.
 */
object BrainExpressionParser {

  private sealed interface Token {
    data class NumberTok(val value: Number) : Token
    data class StringTok(val value: String) : Token
    data class BoolTok(val value: Boolean) : Token
    data class IdentTok(val name: String) : Token
    data class OpTok(val op: String) : Token
    data object LParen : Token
    data object RParen : Token
  }

  fun normalizaResultado(value: Any?): Any? {
    return when (value) {
      is Double -> {
        if (value.isFinite() && value == floor(value) &&
          value >= Long.MIN_VALUE.toDouble() && value <= Long.MAX_VALUE.toDouble()
        ) {
          value.toLong()
        } else {
          value
        }
      }
      is Float -> normalizaResultado(value.toDouble())
      is Int -> value.toLong()
      else -> value
    }
  }

  fun paraNumero(value: Any?): Number? {
    if (value == null || value is Boolean) return null
    return when (value) {
      is Long -> value
      is Int -> value.toLong()
      is Double -> value
      is Float -> value.toDouble()
      is String -> {
        val trimmed = value.trim()
        trimmed.toLongOrNull() ?: trimmed.toDoubleOrNull()
      }
      else -> null
    }
  }

  fun isTruthy(value: Any?): Boolean {
    return when (value) {
      null -> false
      is Boolean -> value
      is Long -> value != 0L
      is Int -> value != 0
      is Double -> value != 0.0
      is String -> value.isNotEmpty()
      else -> true
    }
  }

  fun formatValue(value: Any?): String {
    return when (value) {
      null -> ""
      is Boolean -> if (value) "true" else "false"
      is Double -> {
        if (value.isFinite() && value == floor(value) &&
          value >= Long.MIN_VALUE.toDouble() && value <= Long.MAX_VALUE.toDouble()
        ) {
          value.toLong().toString()
        } else {
          value.toString()
        }
      }
      else -> value.toString()
    }
  }

  fun isSingleQuotedLiteral(text: String): Boolean {
    if (text.length < 2) return false
    val quote = text.first()
    if ((quote != '"' && quote != '\'') || text.last() != quote) return false
    var i = 1
    while (i < text.length - 1) {
      if (text[i] == '\\') {
        i += 2
        continue
      }
      if (text[i] == quote) {
        return false
      }
      i++
    }
    return true
  }

  fun unquote(text: String): String {
    if (text.length < 2) return text
    val sb = StringBuilder(text.length - 2)
    var i = 1
    while (i < text.length - 1) {
      val c = text[i]
      if (c == '\\' && i + 1 < text.length - 1) {
        when (text[i + 1]) {
          'n' -> sb.append('\n')
          't' -> sb.append('\t')
          'r' -> sb.append('\r')
          '\\' -> sb.append('\\')
          '"' -> sb.append('"')
          '\'' -> sb.append('\'')
          else -> sb.append(text[i + 1])
        }
        i += 2
      } else {
        sb.append(c)
        i++
      }
    }
    return sb.toString()
  }

  fun stripInlineComment(line: String): String {
    var inQuote: Char? = null
    var i = 0
    val n = line.length
    while (i < n) {
      val c = line[i]
      if (inQuote != null) {
        if (c == '\\' && i + 1 < n) {
          i += 2
          continue
        }
        if (c == inQuote) {
          inQuote = null
        }
        i++
        continue
      }
      if (c == '"' || c == '\'') {
        inQuote = c
        i++
        continue
      }
      if (c == '#' || (c == '/' && i + 1 < n && line[i + 1] == '/')) {
        return line.substring(0, i).trim()
      }
      i++
    }
    return line.trim()
  }

  fun evaluate(expr: String, variables: Map<String, Any>): Any {
    val tokens = tokenize(expr)
    if (tokens.isEmpty()) throw BrainEvalException("Expressão vazia")
    val parser = Parser(tokens, variables)
    val result = parser.parseOr()
    if (parser.hasMore()) {
      throw BrainEvalException("Token inesperado na expressão")
    }
    return normalizaResultado(result) ?: result
  }

  private fun tokenize(input: String): List<Token> {
    val tokens = mutableListOf<Token>()
    var i = 0
    val n = input.length

    while (i < n) {
      val c = input[i]
      if (c.isWhitespace()) {
        i++
        continue
      }
      if (c == '#' || (c == '/' && i + 1 < n && input[i + 1] == '/')) {
        break
      }

      if (c == '(') {
        tokens.add(Token.LParen)
        i++
        continue
      }
      if (c == ')') {
        tokens.add(Token.RParen)
        i++
        continue
      }

      // String literal
      if (c == '"' || c == '\'') {
        val quote = c
        i++
        val sb = StringBuilder()
        var closed = false
        while (i < n) {
          val ch = input[i]
          if (ch == '\\' && i + 1 < n) {
            when (input[i + 1]) {
              'n' -> sb.append('\n')
              't' -> sb.append('\t')
              'r' -> sb.append('\r')
              '\\' -> sb.append('\\')
              '"' -> sb.append('"')
              '\'' -> sb.append('\'')
              else -> sb.append(input[i + 1])
            }
            i += 2
          } else if (ch == quote) {
            closed = true
            i++
            break
          } else {
            sb.append(ch)
            i++
          }
        }
        if (!closed) throw BrainEvalException("String não fechada")
        tokens.add(Token.StringTok(sb.toString()))
        continue
      }

      // Numbers (including leading dot like .5)
      if (c.isDigit() || (c == '.' && i + 1 < n && input[i + 1].isDigit())) {
        val start = i
        var hasDot = false
        while (i < n) {
          val ch = input[i]
          if (ch.isDigit()) {
            i++
          } else if (ch == '.' && !hasDot) {
            hasDot = true
            i++
          } else {
            break
          }
        }
        val numStr = input.substring(start, i)
        val num: Number =
          if (hasDot) {
            numStr.toDoubleOrNull() ?: throw BrainEvalException("Número inválido: $numStr")
          } else {
            numStr.toLongOrNull()
              ?: numStr.toDoubleOrNull()
              ?: throw BrainEvalException("Número inválido: $numStr")
          }
        tokens.add(Token.NumberTok(num))
        continue
      }

      // Two-char operators
      if (i + 1 < n) {
        val two = input.substring(i, i + 2)
        if (two in setOf("==", "!=", "<=", ">=", "&&", "||", "**")) {
          tokens.add(Token.OpTok(two))
          i += 2
          continue
        }
      }

      // Single-char operators
      if (c in setOf('+', '-', '*', '/', '%', '^', '<', '>', '!')) {
        tokens.add(Token.OpTok(c.toString()))
        i++
        continue
      }

      // Identifiers & keywords
      if (c.isLetter() || c == '_') {
        val start = i
        while (i < n && (input[i].isLetterOrDigit() || input[i] == '_')) {
          i++
        }
        val ident = input.substring(start, i)
        when (ident) {
          "true", "True" -> tokens.add(Token.BoolTok(true))
          "false", "False" -> tokens.add(Token.BoolTok(false))
          "and", "or", "not" -> tokens.add(Token.OpTok(ident))
          else -> tokens.add(Token.IdentTok(ident))
        }
        continue
      }

      throw BrainEvalException("Caractere inválido na expressão: $c")
    }

    return tokens
  }

  private class Parser(
    private val tokens: List<Token>,
    private val variables: Map<String, Any>,
  ) {
    private var pos = 0

    fun hasMore(): Boolean = pos < tokens.size

    private fun peek(): Token? = tokens.getOrNull(pos)

    private fun consume(): Token = tokens[pos++]

    private fun matchOp(vararg ops: String): String? {
      val tok = peek()
      if (tok is Token.OpTok && tok.op in ops) {
        pos++
        return tok.op
      }
      return null
    }

    fun parseOr(): Any {
      var left = parseAnd()
      while (matchOp("or", "||") != null) {
        val right = parseAnd()
        left = isTruthy(left) || isTruthy(right)
      }
      return left
    }

    private fun parseAnd(): Any {
      var left = parseNot()
      while (matchOp("and", "&&") != null) {
        val right = parseNot()
        left = isTruthy(left) && isTruthy(right)
      }
      return left
    }

    private fun parseNot(): Any {
      if (matchOp("not", "!") != null) {
        return !isTruthy(parseNot())
      }
      return parseComparison()
    }

    private fun parseComparison(): Any {
      var left = parseAddSub()
      val ops = mutableListOf<String>()
      val values = mutableListOf(left)

      while (true) {
        val op = matchOp("==", "!=", "<=", ">=", "<", ">") ?: break
        ops.add(op)
        values.add(parseAddSub())
      }

      if (ops.isEmpty()) return left

      var allTrue = true
      for (idx in ops.indices) {
        val a = values[idx]
        val b = values[idx + 1]
        if (!compareValues(a, ops[idx], b)) {
          allTrue = false
        }
      }
      return allTrue
    }

    private fun compareValues(a: Any, op: String, b: Any): Boolean {
      return when (op) {
        "==" -> equalsSmart(a, b)
        "!=" -> !equalsSmart(a, b)
        else -> {
          val cmp = orderCompare(a, b)
          when (op) {
            "<" -> cmp < 0
            "<=" -> cmp <= 0
            ">" -> cmp > 0
            ">=" -> cmp >= 0
            else -> false
          }
        }
      }
    }

    private fun equalsSmart(a: Any, b: Any): Boolean {
      if (a is Boolean || b is Boolean) {
        if (a is Boolean && b is Boolean) return a == b
        if (a is Boolean && b is Number) return (if (a) 1.0 else 0.0) == b.toDouble()
        if (b is Boolean && a is Number) return a.toDouble() == (if (b) 1.0 else 0.0)
        return false
      }
      if (a is Number && b is Number) {
        return a.toDouble() == b.toDouble()
      }
      return a == b
    }

    private fun orderCompare(a: Any, b: Any): Int {
      if (a is Number && b is Number) {
        return a.toDouble().compareTo(b.toDouble())
      }
      if (a is String && b is String) {
        return a.compareTo(b)
      }
      throw BrainEvalException("Comparação inválida")
    }

    private fun parseAddSub(): Any {
      var left = parseMulDiv()
      while (true) {
        val op = matchOp("+", "-") ?: break
        val right = parseMulDiv()
        left =
          if (op == "+") {
            addValues(left, right)
          } else {
            subValues(left, right)
          }
      }
      return left
    }

    private fun addValues(a: Any, b: Any): Any {
      if (a is String || b is String) {
        return formatValue(a) + formatValue(b)
      }
      val na = asNumericOperand(a)
      val nb = asNumericOperand(b)
      return if (na is Long && nb is Long) {
        na + nb
      } else {
        normalizaResultado(na.toDouble() + nb.toDouble())!!
      }
    }

    private fun subValues(a: Any, b: Any): Any {
      val na = asNumericOperand(a)
      val nb = asNumericOperand(b)
      return if (na is Long && nb is Long) {
        na - nb
      } else {
        normalizaResultado(na.toDouble() - nb.toDouble())!!
      }
    }

    private fun parseMulDiv(): Any {
      var left = parseUnary()
      while (true) {
        val op = matchOp("*", "/", "//", "%") ?: break
        val right = parseUnary()
        left =
          when (op) {
            "*" -> mulValues(left, right)
            "/" -> divValues(left, right)
            "//" -> floorDivValues(left, right)
            "%" -> modValues(left, right)
            else -> left
          }
      }
      return left
    }

    private fun mulValues(a: Any, b: Any): Any {
      if (a is String && b is Number) {
        val count = b.toLong().toInt().coerceAtLeast(0).coerceAtMost(1000)
        return a.repeat(count)
      }
      if (b is String && a is Number) {
        val count = a.toLong().toInt().coerceAtLeast(0).coerceAtMost(1000)
        return b.repeat(count)
      }
      val na = asNumericOperand(a)
      val nb = asNumericOperand(b)
      return if (na is Long && nb is Long) {
        na * nb
      } else {
        normalizaResultado(na.toDouble() * nb.toDouble())!!
      }
    }

    private fun divValues(a: Any, b: Any): Any {
      val na = asNumericOperand(a).toDouble()
      val nb = asNumericOperand(b).toDouble()
      if (nb == 0.0) throw BrainDivisionByZeroException()
      return normalizaResultado(na / nb)!!
    }

    private fun floorDivValues(a: Any, b: Any): Any {
      val na = asNumericOperand(a)
      val nb = asNumericOperand(b)
      if (nb.toDouble() == 0.0) throw BrainDivisionByZeroException()
      return if (na is Long && nb is Long) {
        Math.floorDiv(na, nb)
      } else {
        normalizaResultado(floor(na.toDouble() / nb.toDouble()))!!
      }
    }

    private fun modValues(a: Any, b: Any): Any {
      val na = asNumericOperand(a)
      val nb = asNumericOperand(b)
      if (nb.toDouble() == 0.0) throw BrainDivisionByZeroException()
      return if (na is Long && nb is Long) {
        Math.floorMod(na, nb)
      } else {
        normalizaResultado(na.toDouble() % nb.toDouble())!!
      }
    }

    private fun parseUnary(): Any {
      val op = matchOp("+", "-")
      if (op != null) {
        val value = parseUnary()
        val num = asNumericOperand(value)
        return if (op == "-") {
          if (num is Long) -num else normalizaResultado(-num.toDouble())!!
        } else {
          num
        }
      }
      return parsePower()
    }

    private fun parsePower(): Any {
      val base = parsePrimary()
      if (matchOp("**", "^") != null) {
        val exp = parseUnary()
        val nb = asNumericOperand(base).toDouble()
        val ne = asNumericOperand(exp).toDouble()
        return normalizaResultado(nb.pow(ne))!!
      }
      return base
    }

    private fun parsePrimary(): Any {
      val tok = peek() ?: throw BrainEvalException("Fim inesperado da expressão")
      return when (tok) {
        is Token.NumberTok -> {
          consume()
          tok.value
        }
        is Token.StringTok -> {
          consume()
          tok.value
        }
        is Token.BoolTok -> {
          consume()
          tok.value
        }
        is Token.IdentTok -> {
          consume()
          variables[tok.name]
            ?: throw BrainEvalException("Variável não definida: ${tok.name}")
        }
        Token.LParen -> {
          consume()
          val inner = parseOr()
          if (peek() != Token.RParen) {
            throw BrainEvalException("Parêntese ')' esperado")
          }
          consume()
          inner
        }
        else -> throw BrainEvalException("Expressão inválida")
      }
    }

    private fun asNumericOperand(value: Any): Number {
      return when (value) {
        is Long -> value
        is Int -> value.toLong()
        is Double -> value
        is Boolean -> if (value) 1L else 0L
        else -> throw BrainEvalException("Operando numérico esperado")
      }
    }
  }
}
