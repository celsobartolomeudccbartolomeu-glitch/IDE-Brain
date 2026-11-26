package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.TextDecrease
import androidx.compose.material.icons.filled.TextIncrease
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.BrainEditorPanel
import com.example.ui.components.BrainHelpDialog
import com.example.ui.components.ConsolePanel
import com.example.ui.components.FileManagerSheet
import com.example.ui.components.NewFileDialog
import com.example.ui.components.PwaWebView
import com.example.ui.theme.BrainIdeTheme

@Composable
fun BrainIdeApp(viewModel: BrainIdeViewModel) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val snackbarHostState = remember { SnackbarHostState() }
  var showOverflowMenu by remember { mutableStateOf(false) }

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

  BackHandler(enabled = uiState.showFileManager || uiState.showHelpDialog || uiState.showNewFileDialog) {
    viewModel.toggleFileManager(false)
    viewModel.toggleHelpDialog(false)
    viewModel.dismissNewFileDialog()
  }

  BackHandler(
    enabled =
      !uiState.showFileManager &&
        !uiState.showHelpDialog &&
        !uiState.showNewFileDialog &&
        uiState.activeTab != IdeTab.EDITOR
  ) {
    viewModel.setTab(IdeTab.EDITOR)
  }

  BrainIdeTheme(darkTheme = uiState.darkTheme) {
    Scaffold(
      contentWindowInsets = WindowInsets.safeDrawing,
      snackbarHost = { SnackbarHost(snackbarHostState) },
      topBar = {
        Surface(color = MaterialTheme.colorScheme.surface) {
          Column {
            // Single-line compact header with 18dp-20dp icons (fits 360dp cleanly without wrapping)
            Row(
              modifier =
                Modifier
                  .fillMaxWidth()
                  .height(48.dp)
                  .padding(start = 12.dp, end = 4.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Text(
                text = "Brain IDE",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                softWrap = false,
              )

              Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                  onClick = viewModel::openNewFileDialog,
                  modifier =
                    Modifier.minimumInteractiveComponentSize().testTag("new_file_top_button"),
                ) {
                  Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "Novo ficheiro",
                    modifier = Modifier.size(20.dp),
                  )
                }

                IconButton(
                  onClick = { viewModel.toggleFileManager(true) },
                  modifier = Modifier.minimumInteractiveComponentSize().testTag("files_button"),
                ) {
                  Icon(
                    imageVector = Icons.Filled.FolderOpen,
                    contentDescription = "Gerir ficheiros",
                    modifier = Modifier.size(19.dp),
                  )
                }

                IconButton(
                  onClick = { viewModel.saveCurrentFile() },
                  modifier =
                    Modifier.minimumInteractiveComponentSize().testTag("save_file_button"),
                ) {
                  Icon(
                    imageVector = Icons.Filled.Save,
                    contentDescription = "Guardar ficheiro",
                    tint =
                      if (uiState.hasUnsavedChanges) MaterialTheme.colorScheme.primary
                      else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(19.dp),
                  )
                }

                IconButton(
                  onClick = viewModel::toggleTheme,
                  modifier =
                    Modifier.minimumInteractiveComponentSize().testTag("theme_toggle_button"),
                ) {
                  Icon(
                    imageVector =
                      if (uiState.darkTheme) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                    contentDescription =
                      if (uiState.darkTheme) "Tema claro" else "Tema escuro",
                    modifier = Modifier.size(19.dp),
                  )
                }

                Box {
                  IconButton(
                    onClick = { showOverflowMenu = true },
                    modifier =
                      Modifier.minimumInteractiveComponentSize().testTag("more_menu_button"),
                  ) {
                    Icon(
                      imageVector = Icons.Filled.MoreVert,
                      contentDescription = "Mais opções",
                      modifier = Modifier.size(19.dp),
                    )
                  }

                  DropdownMenu(
                    expanded = showOverflowMenu,
                    onDismissRequest = { showOverflowMenu = false },
                  ) {
                    DropdownMenuItem(
                      text = { Text("Aumentar letra (${uiState.fontSizeSp.toInt()}sp)") },
                      leadingIcon = {
                        Icon(
                          Icons.Filled.TextIncrease,
                          contentDescription = null,
                          modifier = Modifier.size(18.dp),
                        )
                      },
                      onClick = { viewModel.adjustFontSize(1f) },
                    )
                    DropdownMenuItem(
                      text = { Text("Diminuir letra (${uiState.fontSizeSp.toInt()}sp)") },
                      leadingIcon = {
                        Icon(
                          Icons.Filled.TextDecrease,
                          contentDescription = null,
                          modifier = Modifier.size(18.dp),
                        )
                      },
                      onClick = { viewModel.adjustFontSize(-1f) },
                    )
                    DropdownMenuItem(
                      text = { Text("Ajuda Brain Language") },
                      leadingIcon = {
                        Icon(
                          Icons.AutoMirrored.Filled.HelpOutline,
                          contentDescription = null,
                          modifier = Modifier.size(18.dp),
                        )
                      },
                      onClick = {
                        showOverflowMenu = false
                        viewModel.toggleHelpDialog(true)
                      },
                    )
                    DropdownMenuItem(
                      text = { Text("Modo PWA (IndexedDB)") },
                      leadingIcon = {
                        Icon(
                          Icons.Filled.Language,
                          contentDescription = null,
                          modifier = Modifier.size(18.dp),
                        )
                      },
                      onClick = {
                        showOverflowMenu = false
                        viewModel.setTab(IdeTab.PWA)
                      },
                    )
                  }
                }
              }
            }

            if (uiState.activeTab != IdeTab.PWA) {
              val selectedIndex =
                when (uiState.activeTab) {
                  IdeTab.EDITOR -> 0
                  IdeTab.CONSOLE -> 1
                  IdeTab.REPL -> 2
                  IdeTab.PWA -> 0
                }
              @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
              PrimaryTabRow(
                selectedTabIndex = selectedIndex,
                containerColor = MaterialTheme.colorScheme.surface,
              ) {
                Tab(
                  selected = selectedIndex == 0,
                  onClick = { viewModel.setTab(IdeTab.EDITOR) },
                  modifier = Modifier.height(42.dp).testTag("tab_editor"),
                  text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                      Icon(
                        imageVector = Icons.Filled.Code,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                      )
                      Spacer(Modifier.width(5.dp))
                      Text("Editor", fontSize = 12.sp, maxLines = 1, softWrap = false)
                    }
                  },
                )
                Tab(
                  selected = selectedIndex == 1,
                  onClick = { viewModel.setTab(IdeTab.CONSOLE) },
                  modifier = Modifier.height(42.dp).testTag("tab_console"),
                  text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                      Icon(
                        imageVector = Icons.Filled.Terminal,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                      )
                      Spacer(Modifier.width(5.dp))
                      Text(
                        text = if (uiState.isRunning) "Consola •" else "Consola",
                        fontSize = 12.sp,
                        maxLines = 1,
                        softWrap = false,
                      )
                    }
                  },
                )
                Tab(
                  selected = selectedIndex == 2,
                  onClick = { viewModel.setTab(IdeTab.REPL) },
                  modifier = Modifier.height(42.dp).testTag("tab_repl"),
                  text = {
                    Text("REPL (>>>)", fontSize = 12.sp, maxLines = 1, softWrap = false)
                  },
                )
              }
            } else {
              Row(
                modifier =
                  Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                TextButton(onClick = { viewModel.setTab(IdeTab.EDITOR) }) {
                  Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                  )
                  Spacer(Modifier.width(4.dp))
                  Text("Voltar ao Editor", fontSize = 12.sp)
                }
              }
              HorizontalDivider()
            }
          }
        }
      },
      modifier = Modifier.fillMaxSize().imePadding(),
    ) { innerPadding ->
      Box(
        modifier =
          Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .padding(horizontal = 6.dp, vertical = 6.dp)
      ) {
        when (uiState.activeTab) {
          IdeTab.EDITOR -> {
            BrainEditorPanel(
              fileName = uiState.activeFileName,
              hasUnsavedChanges = uiState.hasUnsavedChanges,
              codeValue = uiState.codeValue,
              fontSizeSp = uiState.fontSizeSp,
              focusNonce = uiState.editorFocusNonce,
              isRunning = uiState.isRunning,
              suggestions = uiState.editorSuggestions,
              onCodeChange = viewModel::onCodeChanged,
              onRunClick = viewModel::runProgram,
              onInsertSymbol = viewModel::insertSymbol,
              onApplySuggestion = viewModel::applyEditorSuggestion,
              modifier = Modifier.fillMaxSize(),
            )
          }

          IdeTab.CONSOLE,
          IdeTab.REPL -> {
            ConsolePanel(
              activeTab = uiState.activeTab,
              consoleEntries = uiState.consoleEntries,
              isRunning = uiState.isRunning,
              isReplProgramBufferActive = uiState.isReplProgramBufferActive,
              replProgramLineCount = uiState.replProgramLineCount,
              pendingInputVar = uiState.pendingInputVar,
              pendingInputText = uiState.pendingInputText,
              replInput = uiState.replInput,
              replSuggestions = uiState.replSuggestions,
              fontSizeSp = uiState.fontSizeSp,
              onBackToEditor = { viewModel.setTab(IdeTab.EDITOR) },
              onRunClick = viewModel::runProgram,
              onStopClick = viewModel::stopExecution,
              onClearClick = viewModel::clearConsole,
              onPendingInputChanged = viewModel::onPendingInputChanged,
              onSubmitPendingInput = viewModel::submitPendingInput,
              onReplInputChanged = viewModel::onReplInputChanged,
              onSubmitReplCommand = viewModel::submitReplCommand,
              onInsertSymbol = viewModel::insertSymbol,
              onApplyReplSuggestion = viewModel::applyReplSuggestion,
              modifier = Modifier.fillMaxSize(),
            )
          }

          IdeTab.PWA -> {
            PwaWebView(
              onStatusMessage = viewModel::showStatusMessage,
              modifier = Modifier.fillMaxSize(),
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
        onOpenNewFileDialog = viewModel::openNewFileDialog,
        onOpenFile = viewModel::openFile,
        onDeleteFile = viewModel::deleteFile,
        onImportClick = { importLauncher.launch(arrayOf("*/*")) },
        onExportClick = { exportLauncher.launch(uiState.activeFileName) },
      )
    }

    if (uiState.showNewFileDialog) {
      NewFileDialog(
        suggestedName = "programa_${uiState.savedFiles.size + 1}.brain",
        onDismiss = viewModel::dismissNewFileDialog,
        onCreate = viewModel::createNewFile,
      )
    }

    if (uiState.showHelpDialog) {
      BrainHelpDialog(onDismiss = { viewModel.toggleHelpDialog(false) })
    }
  }
}
