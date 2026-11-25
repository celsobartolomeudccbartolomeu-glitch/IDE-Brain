package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.TextDecrease
import androidx.compose.material.icons.filled.TextIncrease
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.BrainEditorPanel
import com.example.ui.components.BrainHelpDialog
import com.example.ui.components.ConsolePanel
import com.example.ui.components.FileManagerSheet
import com.example.ui.components.PwaWebView
import com.example.ui.theme.BrainIdeTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrainIdeApp(viewModel: BrainIdeViewModel) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val snackbarHostState = remember { SnackbarHostState() }

  val importLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
      if (uri != null) viewModel.importFileFromUri(uri)
    }

  val exportLauncher =
    rememberLauncherForActivityResult(
      ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
      if (uri != null) viewModel.exportActiveFileToUri(uri)
    }

  LaunchedEffect(uiState.statusMessage) {
    val msg = uiState.statusMessage
    if (msg != null) {
      snackbarHostState.showSnackbar(msg)
      viewModel.clearStatusMessage()
    }
  }

  // Handle back press cleanly when sheets/dialogs or secondary modes are open
  BackHandler(enabled = uiState.showFileManager || uiState.showHelpDialog) {
    viewModel.toggleFileManager(false)
    viewModel.toggleHelpDialog(false)
  }

  BackHandler(
    enabled = !uiState.showFileManager && !uiState.showHelpDialog && uiState.mode != IdeMode.PROGRAM
  ) {
    viewModel.setMode(IdeMode.PROGRAM)
  }

  BrainIdeTheme(darkTheme = uiState.darkTheme) {
    Scaffold(
      contentWindowInsets = WindowInsets.safeDrawing,
      snackbarHost = { SnackbarHost(snackbarHostState) },
      topBar = {
        Column(modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
          TopAppBar(
            title = {
              Column {
                Text(
                  text = "Brain IDE",
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.Bold,
                )
                Text(
                  text = "${uiState.fontSizeSp.toInt()}sp · Offline",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            },
            colors =
              TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface
              ),
            actions = {
              IconButton(
                onClick = { viewModel.toggleFileManager(true) },
                modifier = Modifier.minimumInteractiveComponentSize().testTag("files_button"),
              ) {
                Icon(
                  imageVector = Icons.Filled.FolderOpen,
                  contentDescription = "Gerir ficheiros",
                )
              }
              IconButton(
                onClick = { viewModel.adjustFontSize(-1f) },
                modifier = Modifier.minimumInteractiveComponentSize().testTag("font_decrease_button"),
              ) {
                Icon(
                  imageVector = Icons.Filled.TextDecrease,
                  contentDescription = "Diminuir letra",
                )
              }
              IconButton(
                onClick = { viewModel.adjustFontSize(1f) },
                modifier = Modifier.minimumInteractiveComponentSize().testTag("font_increase_button"),
              ) {
                Icon(
                  imageVector = Icons.Filled.TextIncrease,
                  contentDescription = "Aumentar letra",
                )
              }
              IconButton(
                onClick = viewModel::toggleTheme,
                modifier = Modifier.minimumInteractiveComponentSize().testTag("theme_toggle_button"),
              ) {
                Icon(
                  imageVector =
                    if (uiState.darkTheme) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                  contentDescription =
                    if (uiState.darkTheme) "Mudar para tema claro" else "Mudar para tema escuro",
                )
              }
              IconButton(
                onClick = { viewModel.toggleHelpDialog(true) },
                modifier = Modifier.minimumInteractiveComponentSize().testTag("help_button"),
              ) {
                Icon(
                  imageVector = Icons.Filled.HelpOutline,
                  contentDescription = "Ajuda Brain Language",
                )
              }
            },
          )

          // Mode Switcher Bar: "Modo programa" vs "Modo REPL (>>>)" vs "PWA (IndexedDB)"
          Row(
            modifier =
              Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp)
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            FilterChip(
              selected = uiState.mode == IdeMode.PROGRAM,
              onClick = { viewModel.setMode(IdeMode.PROGRAM) },
              label = { Text("Modo programa") },
              leadingIcon = {
                Icon(
                  imageVector = Icons.Filled.Code,
                  contentDescription = null,
                  modifier = Modifier.size(16.dp),
                )
              },
              shape = RoundedCornerShape(10.dp),
              modifier = Modifier.height(48.dp).testTag("mode_program_button"),
            )

            FilterChip(
              selected = uiState.mode == IdeMode.REPL,
              onClick = { viewModel.setMode(IdeMode.REPL) },
              label = { Text("Modo REPL (>>>)") },
              leadingIcon = {
                Icon(
                  imageVector = Icons.Filled.Terminal,
                  contentDescription = null,
                  modifier = Modifier.size(16.dp),
                )
              },
              shape = RoundedCornerShape(10.dp),
              modifier = Modifier.height(48.dp).testTag("mode_repl_button"),
            )

            FilterChip(
              selected = uiState.mode == IdeMode.PWA,
              onClick = { viewModel.setMode(IdeMode.PWA) },
              label = { Text("PWA (IndexedDB)") },
              leadingIcon = {
                Icon(
                  imageVector = Icons.Filled.Language,
                  contentDescription = null,
                  modifier = Modifier.size(16.dp),
                )
              },
              shape = RoundedCornerShape(10.dp),
              modifier = Modifier.height(48.dp).testTag("mode_pwa_button"),
            )
          }
        }
      },
      modifier = Modifier.fillMaxSize().imePadding(),
    ) { innerPadding ->
      BoxWithConstraints(
        modifier =
          Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .padding(horizontal = 10.dp, vertical = 8.dp)
      ) {
        val isLandscapeOrWide = maxWidth >= 600.dp || maxWidth > maxHeight

        if (uiState.mode == IdeMode.PWA) {
          PwaWebView(
            onStatusMessage = viewModel::showStatusMessage,
            modifier = Modifier.fillMaxSize(),
          )
        } else if (uiState.mode == IdeMode.REPL) {
          // Full-focus Console + >>> prompt in REPL mode
          ConsolePanel(
            mode = uiState.mode,
            consoleEntries = uiState.consoleEntries,
            isRunning = uiState.isRunning,
            isReplProgramBufferActive = uiState.isReplProgramBufferActive,
            replProgramLineCount = uiState.replProgramLineCount,
            pendingInputVar = uiState.pendingInputVar,
            pendingInputText = uiState.pendingInputText,
            replInput = uiState.replInput,
            fontSizeSp = uiState.fontSizeSp,
            onRunClick = viewModel::runProgram,
            onStopClick = viewModel::stopExecution,
            onClearClick = viewModel::clearConsole,
            onPendingInputChanged = viewModel::onPendingInputChanged,
            onSubmitPendingInput = viewModel::submitPendingInput,
            onReplInputChanged = viewModel::onReplInputChanged,
            onSubmitReplCommand = viewModel::submitReplCommand,
            onInsertSymbol = viewModel::insertSymbol,
            modifier = Modifier.fillMaxSize(),
          )
        } else if (isLandscapeOrWide) {
          // Horizontal / Wide split-pane layout
          Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            BrainEditorPanel(
              fileName = uiState.activeFileName,
              hasUnsavedChanges = uiState.hasUnsavedChanges,
              codeValue = uiState.codeValue,
              fontSizeSp = uiState.fontSizeSp,
              onCodeChange = viewModel::onCodeChanged,
              onSaveClick = { viewModel.saveCurrentFile() },
              onInsertSymbol = viewModel::insertSymbol,
              modifier = Modifier.weight(1.15f).fillMaxHeight(),
            )
            ConsolePanel(
              mode = uiState.mode,
              consoleEntries = uiState.consoleEntries,
              isRunning = uiState.isRunning,
              isReplProgramBufferActive = uiState.isReplProgramBufferActive,
              replProgramLineCount = uiState.replProgramLineCount,
              pendingInputVar = uiState.pendingInputVar,
              pendingInputText = uiState.pendingInputText,
              replInput = uiState.replInput,
              fontSizeSp = uiState.fontSizeSp,
              onRunClick = viewModel::runProgram,
              onStopClick = viewModel::stopExecution,
              onClearClick = viewModel::clearConsole,
              onPendingInputChanged = viewModel::onPendingInputChanged,
              onSubmitPendingInput = viewModel::submitPendingInput,
              onReplInputChanged = viewModel::onReplInputChanged,
              onSubmitReplCommand = viewModel::submitReplCommand,
              onInsertSymbol = viewModel::insertSymbol,
              modifier = Modifier.weight(0.85f).fillMaxHeight(),
            )
          }
        } else {
          // Vertical / Portrait stacked layout (editor top, console bottom)
          Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            BrainEditorPanel(
              fileName = uiState.activeFileName,
              hasUnsavedChanges = uiState.hasUnsavedChanges,
              codeValue = uiState.codeValue,
              fontSizeSp = uiState.fontSizeSp,
              onCodeChange = viewModel::onCodeChanged,
              onSaveClick = { viewModel.saveCurrentFile() },
              onInsertSymbol = viewModel::insertSymbol,
              modifier = Modifier.weight(0.56f).fillMaxWidth(),
            )
            ConsolePanel(
              mode = uiState.mode,
              consoleEntries = uiState.consoleEntries,
              isRunning = uiState.isRunning,
              isReplProgramBufferActive = uiState.isReplProgramBufferActive,
              replProgramLineCount = uiState.replProgramLineCount,
              pendingInputVar = uiState.pendingInputVar,
              pendingInputText = uiState.pendingInputText,
              replInput = uiState.replInput,
              fontSizeSp = uiState.fontSizeSp,
              onRunClick = viewModel::runProgram,
              onStopClick = viewModel::stopExecution,
              onClearClick = viewModel::clearConsole,
              onPendingInputChanged = viewModel::onPendingInputChanged,
              onSubmitPendingInput = viewModel::submitPendingInput,
              onReplInputChanged = viewModel::onReplInputChanged,
              onSubmitReplCommand = viewModel::submitReplCommand,
              onInsertSymbol = viewModel::insertSymbol,
              modifier = Modifier.weight(0.44f).fillMaxWidth(),
            )
          }
        }
      }
    }

    if (uiState.showFileManager) {
      FileManagerSheet(
        activeFileName = uiState.activeFileName,
        files = uiState.savedFiles,
        onDismiss = { viewModel.toggleFileManager(false) },
        onSaveCurrent = { viewModel.saveCurrentFile() },
        onSaveAs = viewModel::saveFileAs,
        onCreateNew = viewModel::createNewFile,
        onOpenFile = viewModel::openFile,
        onDeleteFile = viewModel::deleteFile,
        onImportClick = { importLauncher.launch(arrayOf("*/*")) },
        onExportClick = { exportLauncher.launch(uiState.activeFileName) },
      )
    }

    if (uiState.showHelpDialog) {
      BrainHelpDialog(onDismiss = { viewModel.toggleHelpDialog(false) })
    }
  }
}
