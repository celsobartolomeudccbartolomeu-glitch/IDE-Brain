package com.example.ui

import android.app.Application
import android.net.Uri
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.brain.BrainIO
import com.example.brain.BrainInterpreter
import com.example.brain.OutputType
import com.example.data.BrainFileItem
import com.example.data.BrainFileRepository
import com.example.data.IdePreferences
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class IdeTab {
  EDITOR,
  CONSOLE,
  REPL,
  PWA,
}

data class ConsoleEntry(
  val id: Long,
  val text: String,
  val type: OutputType,
)

data class BrainIdeUiState(
  val activeTab: IdeTab = IdeTab.EDITOR,
  val activeFileName: String = "ola_brain.brain",
  val codeValue: TextFieldValue = TextFieldValue(""),
  val savedCodeSnapshot: String = "",
  val replInput: TextFieldValue = TextFieldValue(""),
  val replHistory: List<String> = emptyList(),
  val consoleEntries: List<ConsoleEntry> = emptyList(),
  val isRunning: Boolean = false,
  val isReplProgramBufferActive: Boolean = false,
  val replProgramLineCount: Int = 0,
  val pendingInputVar: String? = null,
  val pendingInputText: String = "",
  val darkTheme: Boolean = true,
  val fontSizeSp: Float = 14f,
  val savedFiles: List<BrainFileItem> = emptyList(),
  val showFileManager: Boolean = false,
  val showNewFileDialog: Boolean = false,
  val showHelpDialog: Boolean = false,
  val editorFocusNonce: Int = 0,
  val editorSuggestions: List<BrainSuggestion> = emptyList(),
  val replSuggestions: List<BrainSuggestion> = emptyList(),
  val statusMessage: String? = null,
) {
  val hasUnsavedChanges: Boolean
    get() = codeValue.text != savedCodeSnapshot
}

class BrainIdeViewModel(application: Application) : AndroidViewModel(application) {

  private val repository = BrainFileRepository(application)
  private val prefs = IdePreferences(application)
  private val nextEntryId = AtomicLong(1L)

  private val _uiState = MutableStateFlow(BrainIdeUiState())
  val uiState: StateFlow<BrainIdeUiState> = _uiState.asStateFlow()

  private var executionJob: Job? = null
  private var inputDeferred: CompletableDeferred<String>? = null

  private val brainIO =
    object : BrainIO {
      override fun onOutput(text: String, type: OutputType) {
        appendConsole(text, type)
      }

      override fun onClearScreen() {
        _uiState.update { it.copy(consoleEntries = emptyList()) }
      }

      override suspend fun onRequestInput(varName: String): String {
        val deferred = CompletableDeferred<String>()
        inputDeferred = deferred
        _uiState.update {
          it.copy(pendingInputVar = varName, pendingInputText = "")
        }
        return try {
          deferred.await()
        } finally {
          inputDeferred = null
          _uiState.update { it.copy(pendingInputVar = null, pendingInputText = "") }
        }
      }

      override fun onProgramModeChanged(active: Boolean, lineCount: Int) {
        _uiState.update {
          it.copy(isReplProgramBufferActive = active, replProgramLineCount = lineCount)
        }
      }

      override fun onExitRequested() {
        appendConsole("Sessão REPL reiniciada.", OutputType.SYSTEM)
      }
    }

  private val programInterpreter = BrainInterpreter(brainIO)
  private val replInterpreter = BrainInterpreter(brainIO)

  init {
    val files = repository.listFiles()
    val preferredName = prefs.lastFileName
    val targetFile =
      files.find { it.name == preferredName }?.name ?: files.firstOrNull()?.name ?: "ola_brain.brain"
    val content = repository.readFile(targetFile)

    _uiState.update {
      it.copy(
        activeFileName = targetFile,
        codeValue = TextFieldValue(content),
        savedCodeSnapshot = content,
        darkTheme = prefs.darkTheme,
        fontSizeSp = prefs.fontSizeSp,
        savedFiles = files,
        consoleEntries =
          listOf(
            ConsoleEntry(
              id = nextEntryId.getAndIncrement(),
              text = "Brain Language v1.10_test-fix3 — Offline IDE pronto.",
              type = OutputType.SYSTEM,
            ),
            ConsoleEntry(
              id = nextEntryId.getAndIncrement(),
              text = "Escreva no Editor e toque em ▶ para executar.",
              type = OutputType.SYSTEM,
            ),
          ),
      )
    }
  }

  private fun appendConsole(text: String, type: OutputType) {
    val entry = ConsoleEntry(id = nextEntryId.getAndIncrement(), text = text, type = type)
    _uiState.update { state ->
      val updated = (state.consoleEntries + entry)
      val trimmed = if (updated.size > 1000) updated.takeLast(800) else updated
      state.copy(consoleEntries = trimmed)
    }
  }

  fun setTab(tab: IdeTab) {
    if (_uiState.value.activeTab == tab) return
    _uiState.update { it.copy(activeTab = tab) }
    if (tab == IdeTab.REPL && _uiState.value.consoleEntries.none { it.text.contains("Modo REPL") }) {
      appendConsole("Modo REPL (>>>) ativo. Digite '.help' para ver os comandos.", OutputType.SYSTEM)
    }
  }

  fun onCodeChanged(newValue: TextFieldValue) {
    _uiState.update { state ->
      val indentedValue = BrainEditorIndent.applyAutoIndentOnEnter(state.codeValue, newValue)
      val suggestions =
        BrainAutoComplete.suggest(
          value = indentedValue,
          extraVariables = programInterpreter.getVariablesSnapshot().keys,
        )
      state.copy(codeValue = indentedValue, editorSuggestions = suggestions)
    }
  }

  fun onReplInputChanged(newValue: TextFieldValue) {
    _uiState.update { state ->
      val editorVars = BrainAutoComplete.extractVariables(state.codeValue.text)
      val suggestions =
        BrainAutoComplete.suggest(
          value = newValue,
          extraVariables = replInterpreter.getVariablesSnapshot().keys + editorVars,
          isRepl = true,
        )
      state.copy(replInput = newValue, replSuggestions = suggestions)
    }
  }

  fun applyEditorSuggestion(suggestion: BrainSuggestion) {
    _uiState.update { state ->
      val applied = BrainAutoComplete.applySuggestion(state.codeValue, suggestion)
      val nextSuggestions =
        BrainAutoComplete.suggest(
          value = applied,
          extraVariables = programInterpreter.getVariablesSnapshot().keys,
        )
      state.copy(codeValue = applied, editorSuggestions = nextSuggestions)
    }
  }

  fun applyReplSuggestion(suggestion: BrainSuggestion) {
    _uiState.update { state ->
      val applied = BrainAutoComplete.applySuggestion(state.replInput, suggestion)
      val editorVars = BrainAutoComplete.extractVariables(state.codeValue.text)
      val nextSuggestions =
        BrainAutoComplete.suggest(
          value = applied,
          extraVariables = replInterpreter.getVariablesSnapshot().keys + editorVars,
          isRepl = true,
        )
      state.copy(replInput = applied, replSuggestions = nextSuggestions)
    }
  }

  fun onPendingInputChanged(text: String) {
    _uiState.update { it.copy(pendingInputText = text) }
  }

  fun submitPendingInput() {
    val current = _uiState.value
    val varName = current.pendingInputVar ?: return
    val value = current.pendingInputText
    appendConsole("Digite $varName: $value", OutputType.PROMPT)
    inputDeferred?.complete(value)
  }

  fun insertSymbol(symbol: String) {
    val insertText = if (symbol == "tab") "    " else symbol
    val state = _uiState.value
    if (state.activeTab == IdeTab.EDITOR) {
      val current = state.codeValue
      val sel = current.selection
      val start = sel.min.coerceIn(0, current.text.length)
      val end = sel.max.coerceIn(0, current.text.length)
      val updatedText =
        current.text.substring(0, start) + insertText + current.text.substring(end)
      val newCursor = start + insertText.length
      val updatedValue =
        current.copy(
          text = updatedText,
          selection = TextRange(newCursor),
        )
      _uiState.update {
        it.copy(
          codeValue = updatedValue,
          editorSuggestions = BrainAutoComplete.suggest(updatedValue),
        )
      }
    } else if (state.activeTab == IdeTab.REPL) {
      val current = state.replInput
      val sel = current.selection
      val start = sel.min.coerceIn(0, current.text.length)
      val end = sel.max.coerceIn(0, current.text.length)
      val updatedText =
        current.text.substring(0, start) + insertText + current.text.substring(end)
      val newCursor = start + insertText.length
      val updatedValue =
        current.copy(
          text = updatedText,
          selection = TextRange(newCursor),
        )
      _uiState.update {
        it.copy(
          replInput = updatedValue,
          replSuggestions =
            BrainAutoComplete.suggest(
              value = updatedValue,
              extraVariables = replInterpreter.getVariablesSnapshot().keys,
              isRepl = true,
            ),
        )
      }
    }
  }

  fun runProgram() {
    if (_uiState.value.isRunning) return
    val state = _uiState.value
    if (state.activeTab == IdeTab.REPL) {
      submitReplCommand()
      return
    }

    val code = state.codeValue.text
    saveCurrentFile(silent = true)
    _uiState.update { it.copy(activeTab = IdeTab.CONSOLE, isRunning = true) }

    executionJob =
      viewModelScope.launch(Dispatchers.Default) {
        appendConsole("▶ A executar ${state.activeFileName}...", OutputType.SYSTEM)
        try {
          programInterpreter.executarScriptEditor(code)
          appendConsole("✓ Execução concluída.", OutputType.SYSTEM)
        } catch (_: CancellationException) {
          appendConsole("■ Execução interrompida.", OutputType.WARNING)
        } catch (e: Exception) {
          appendConsole("Erro interno: ${e.message ?: "falha desconhecida"}", OutputType.ERROR)
        } finally {
          inputDeferred?.cancel()
          inputDeferred = null
          _uiState.update {
            it.copy(isRunning = false, pendingInputVar = null, pendingInputText = "")
          }
        }
      }
  }

  fun submitReplCommand(overrideCommand: String? = null) {
    if (_uiState.value.isRunning) return
    val raw = overrideCommand ?: _uiState.value.replInput.text
    val cmd = raw.trim()
    if (cmd.isEmpty()) return

    val prefix = if (_uiState.value.isReplProgramBufferActive) "... " else ">>> "
    appendConsole("$prefix$cmd", OutputType.PROMPT)

    _uiState.update { state ->
      val updatedHistory =
        if (state.replHistory.lastOrNull() == cmd) state.replHistory
        else (state.replHistory + cmd).takeLast(30)
      state.copy(
        replInput = if (overrideCommand == null) TextFieldValue("") else state.replInput,
        replSuggestions = if (overrideCommand == null) emptyList() else state.replSuggestions,
        replHistory = updatedHistory,
      )
    }

    executionJob =
      viewModelScope.launch(Dispatchers.Default) {
        _uiState.update { it.copy(isRunning = true) }
        try {
          replInterpreter.processarEntradaRepl(cmd)
        } catch (_: CancellationException) {
          appendConsole("■ Execução interrompida.", OutputType.WARNING)
        } catch (e: Exception) {
          appendConsole("Erro: ${e.message ?: "falha na execução"}", OutputType.ERROR)
        } finally {
          inputDeferred?.cancel()
          inputDeferred = null
          _uiState.update {
            it.copy(isRunning = false, pendingInputVar = null, pendingInputText = "")
          }
        }
      }
  }

  fun pickHistoryCommand(command: String) {
    _uiState.update {
      it.copy(
        replInput = TextFieldValue(command, selection = TextRange(command.length))
      )
    }
  }

  fun stopExecution() {
    inputDeferred?.cancel()
    inputDeferred = null
    executionJob?.cancel()
    executionJob = null
    _uiState.update {
      it.copy(isRunning = false, pendingInputVar = null, pendingInputText = "")
    }
  }

  fun clearConsole() {
    _uiState.update { it.copy(consoleEntries = emptyList()) }
  }

  fun toggleTheme() {
    val next = !_uiState.value.darkTheme
    prefs.darkTheme = next
    _uiState.update { it.copy(darkTheme = next) }
  }

  fun adjustFontSize(delta: Float) {
    val next = (_uiState.value.fontSizeSp + delta).coerceIn(11f, 22f)
    prefs.fontSizeSp = next
    _uiState.update { it.copy(fontSizeSp = next) }
  }

  fun toggleFileManager(show: Boolean) {
    if (show) {
      _uiState.update { it.copy(showFileManager = true, savedFiles = repository.listFiles()) }
    } else {
      _uiState.update { it.copy(showFileManager = false) }
    }
  }

  fun openNewFileDialog() {
    _uiState.update { it.copy(showFileManager = false, showNewFileDialog = true) }
  }

  fun dismissNewFileDialog() {
    _uiState.update { it.copy(showNewFileDialog = false) }
  }

  fun toggleHelpDialog(show: Boolean) {
    _uiState.update { it.copy(showHelpDialog = show) }
  }

  fun saveCurrentFile(silent: Boolean = false) {
    val state = _uiState.value
    val savedName = repository.saveFile(state.activeFileName, state.codeValue.text)
    prefs.lastFileName = savedName
    _uiState.update {
      it.copy(
        activeFileName = savedName,
        savedCodeSnapshot = state.codeValue.text,
        savedFiles = repository.listFiles(),
        statusMessage = if (silent) null else "Ficheiro '$savedName' guardado.",
      )
    }
  }

  fun saveFileAs(newName: String) {
    val savedName = repository.saveFile(newName, _uiState.value.codeValue.text)
    prefs.lastFileName = savedName
    _uiState.update {
      it.copy(
        activeFileName = savedName,
        savedCodeSnapshot = it.codeValue.text,
        savedFiles = repository.listFiles(),
        showFileManager = false,
        statusMessage = "Guardado como '$savedName'.",
      )
    }
  }

  fun createNewFile(fileName: String) {
    val savedName = repository.saveFile(fileName, "")
    prefs.lastFileName = savedName
    _uiState.update {
      it.copy(
        activeTab = IdeTab.EDITOR,
        activeFileName = savedName,
        codeValue = TextFieldValue(""),
        savedCodeSnapshot = "",
        savedFiles = repository.listFiles(),
        showFileManager = false,
        showNewFileDialog = false,
        editorFocusNonce = it.editorFocusNonce + 1,
        editorSuggestions = emptyList(),
        statusMessage = "Criado '$savedName'.",
      )
    }
  }

  fun openFile(fileName: String) {
    val content = repository.readFile(fileName)
    prefs.lastFileName = fileName
    _uiState.update {
      it.copy(
        activeTab = IdeTab.EDITOR,
        activeFileName = fileName,
        codeValue = TextFieldValue(content),
        savedCodeSnapshot = content,
        showFileManager = false,
        editorSuggestions = emptyList(),
        statusMessage = "Aberto '$fileName'.",
      )
    }
  }

  fun deleteFile(fileName: String) {
    repository.deleteFile(fileName)
    val files = repository.listFiles()
    _uiState.update { state ->
      if (state.activeFileName == fileName) {
        val fallback = files.firstOrNull()?.name
        if (fallback != null) {
          val content = repository.readFile(fallback)
          prefs.lastFileName = fallback
          state.copy(
            activeFileName = fallback,
            codeValue = TextFieldValue(content),
            savedCodeSnapshot = content,
            savedFiles = files,
            statusMessage = "Apagado '$fileName'.",
          )
        } else {
          val emptyName = "sem_nome.brain"
          prefs.lastFileName = emptyName
          state.copy(
            activeFileName = emptyName,
            codeValue = TextFieldValue(""),
            savedCodeSnapshot = "",
            savedFiles = files,
            statusMessage = "Apagado '$fileName'.",
          )
        }
      } else {
        state.copy(savedFiles = files, statusMessage = "Apagado '$fileName'.")
      }
    }
  }

  fun exportActiveFileToUri(uri: Uri) {
    val content = _uiState.value.codeValue.text
    repository
      .exportToUri(uri, content)
      .onSuccess {
        _uiState.update {
          it.copy(
            showFileManager = false,
            statusMessage = "Exportado '${it.activeFileName}' com sucesso.",
          )
        }
      }
      .onFailure { err ->
        _uiState.update {
          it.copy(statusMessage = "Erro ao exportar: ${err.message}")
        }
      }
  }

  fun importFileFromUri(uri: Uri) {
    repository
      .importFromUri(uri)
      .onSuccess { (name, content) ->
        prefs.lastFileName = name
        _uiState.update {
          it.copy(
            activeTab = IdeTab.EDITOR,
            activeFileName = name,
            codeValue = TextFieldValue(content),
            savedCodeSnapshot = content,
            savedFiles = repository.listFiles(),
            showFileManager = false,
            statusMessage = "Importado '$name'.",
          )
        }
      }
      .onFailure { err ->
        _uiState.update {
          it.copy(statusMessage = "Erro ao importar: ${err.message}")
        }
      }
  }

  fun showStatusMessage(message: String) {
    _uiState.update { it.copy(statusMessage = message) }
  }

  fun clearStatusMessage() {
    _uiState.update { it.copy(statusMessage = null) }
  }
}
