package com.example.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File

data class BrainFileItem(
  val name: String,
  val sizeBytes: Long,
  val lastModifiedMs: Long,
)

class BrainFileRepository(private val context: Context) {

  private val scriptsDir: File by lazy {
    File(context.filesDir, "brain_scripts").apply {
      if (!exists()) mkdirs()
    }
  }

  init {
    seedExamplesIfEmpty()
  }

  private fun seedExamplesIfEmpty() {
    val existing = scriptsDir.listFiles { f -> f.isFile && f.name.endsWith(".brain") }
    if (existing != null && existing.isNotEmpty()) return

    saveFile(
      "ola_brain.brain",
      """
      # Bem-vindo ao Brain Language!
      Brain.data_types
      int idade = 20
      float pi = 3.14
      char letra = "B"
      bool ativo = true
      
      IA.digitalwrite.line "=== Brain IDE ==="
      print letra
      print "Idade + 5:"
      soma idade 5
      
      if idade >= 18
          print "Maior de idade"
      else
          print "Menor de idade"
      endif
      show.vars
      """.trimIndent(),
    )

    saveFile(
      "contador_while.brain",
      """
      // Exemplo de ciclo while e condições aninhadas
      int n = 1
      while n <= 5
          if n % 2 == 0
              print "Par: " + n
          else
              print "Ímpar: " + n
          endif
          n = n + 1
      endwhile
      print "Fim da contagem!"
      """.trimIndent(),
    )

    saveFile(
      "entrada_dados.brain",
      """
      # Leitura interativa na consola
      IA.programa
      IA.digitalwrite.line "Calculadora Brain"
      .scanln.Br a
      .scanln.Br b
      print "Soma:"
      soma a b
      print "Divisão:"
      div a b
      .run
      """.trimIndent(),
    )
  }

  fun sanitizeFileName(rawName: String): String {
    val cleaned =
      rawName
        .trim()
        .replace(Regex("""[/\\:*?"<>|]"""), "_")
        .ifEmpty { "sem_nome" }
    return if (cleaned.endsWith(".brain", ignoreCase = true)) {
      cleaned.dropLast(6) + ".brain"
    } else {
      "$cleaned.brain"
    }
  }

  fun listFiles(): List<BrainFileItem> {
    val files = scriptsDir.listFiles { f -> f.isFile && f.name.endsWith(".brain") } ?: return emptyList()
    return files
      .sortedByDescending { it.lastModified() }
      .map { f ->
        BrainFileItem(
          name = f.name,
          sizeBytes = f.length(),
          lastModifiedMs = f.lastModified(),
        )
      }
  }

  fun readFile(fileName: String): String {
    val safeName = sanitizeFileName(fileName)
    val file = File(scriptsDir, safeName)
    return if (file.exists()) file.readText(Charsets.UTF_8) else ""
  }

  fun saveFile(fileName: String, content: String): String {
    val safeName = sanitizeFileName(fileName)
    val file = File(scriptsDir, safeName)
    file.writeText(content, Charsets.UTF_8)
    return safeName
  }

  fun deleteFile(fileName: String): Boolean {
    val safeName = sanitizeFileName(fileName)
    val file = File(scriptsDir, safeName)
    return if (file.exists()) file.delete() else false
  }

  fun exportToUri(uri: Uri, content: String): Result<Unit> {
    return runCatching {
      context.contentResolver.openOutputStream(uri)?.use { stream ->
        stream.write(content.toByteArray(Charsets.UTF_8))
        stream.flush()
      } ?: error("Não foi possível abrir o destino de exportação.")
    }
  }

  fun importFromUri(uri: Uri): Result<Pair<String, String>> {
    return runCatching {
      val content =
        context.contentResolver.openInputStream(uri)?.use { stream ->
          stream.bufferedReader(Charsets.UTF_8).readText()
        } ?: error("Não foi possível ler o ficheiro selecionado.")
      val rawName = resolveDisplayName(uri) ?: "importado.brain"
      val savedName = saveFile(rawName, content)
      savedName to content
    }
  }

  private fun resolveDisplayName(uri: Uri): String? {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
      ?.use { cursor ->
        if (cursor.moveToFirst()) {
          val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
          if (idx >= 0) return cursor.getString(idx)
        }
      }
    return uri.lastPathSegment?.substringAfterLast('/')
  }
}
