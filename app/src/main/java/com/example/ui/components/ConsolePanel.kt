package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.brain.OutputType
import com.example.ui.ConsoleEntry
import com.example.ui.IdeMode
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalSyntaxColors

private val REPL_QUICK_COMMANDS =
  listOf(".help", "show.vars", "IA.programa", ".run", ".version", ".screnclear")

@Composable
fun ConsolePanel(
  mode: IdeMode,
  consoleEntries: List<ConsoleEntry>,
  isRunning: Boolean,
  isReplProgramBufferActive: Boolean,
  replProgramLineCount: Int,
  pendingInputVar: String?,
  pendingInputText: String,
  replInput: TextFieldValue,
  fontSizeSp: Float,
  onRunClick: () -> Unit,
  onStopClick: () -> Unit,
  onClearClick: () -> Unit,
  onPendingInputChanged: (String) -> Unit,
  onSubmitPendingInput: () -> Unit,
  onReplInputChanged: (TextFieldValue) -> Unit,
  onSubmitReplCommand: (String?) -> Unit,
  onInsertSymbol: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val syntaxColors = LocalSyntaxColors.current
  val listState = rememberLazyListState()

  LaunchedEffect(consoleEntries.size) {
    if (consoleEntries.isNotEmpty()) {
      listState.animateScrollToItem(consoleEntries.lastIndex)
    }
  }

  Column(
    modifier =
      modifier
        .clip(RoundedCornerShape(14.dp))
        .background(syntaxColors.consoleBg)
        .border(1.dp, syntaxColors.border, RoundedCornerShape(14.dp))
  ) {
    // Console Header + Executar / Parar / Limpar controls
    Row(
      modifier =
        Modifier
          .fillMaxWidth()
          .background(syntaxColors.gutterBg)
          .padding(horizontal = 10.dp, vertical = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
          imageVector = Icons.Filled.Terminal,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
          text = if (mode == IdeMode.REPL) "REPL (>>>)" else "Consola",
          style = MaterialTheme.typography.titleSmall,
          color = MaterialTheme.colorScheme.onSurface,
        )
        if (isReplProgramBufferActive && mode == IdeMode.REPL) {
          Spacer(Modifier.width(6.dp))
          Surface(
            color = MaterialTheme.colorScheme.secondaryContainer,
            shape = RoundedCornerShape(6.dp),
          ) {
            Text(
              text = "PROG:$replProgramLineCount",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSecondaryContainer,
              modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            )
          }
        }
      }

      Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Button(
          onClick = onRunClick,
          enabled = !isRunning,
          contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
          colors =
            ButtonDefaults.buttonColors(
              containerColor = MaterialTheme.colorScheme.tertiary,
              contentColor = MaterialTheme.colorScheme.onTertiary,
            ),
          modifier = Modifier.height(48.dp).testTag("run_button"),
        ) {
          Icon(
            imageVector = Icons.Filled.PlayArrow,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
          )
          Spacer(Modifier.width(4.dp))
          Text("Executar", style = MaterialTheme.typography.labelLarge)
        }

        OutlinedButton(
          onClick = onStopClick,
          enabled = isRunning,
          contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
          modifier = Modifier.height(48.dp).testTag("stop_button"),
        ) {
          Icon(
            imageVector = Icons.Filled.Stop,
            contentDescription = null,
            tint =
              if (isRunning) MaterialTheme.colorScheme.error
              else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
          )
          Spacer(Modifier.width(4.dp))
          Text("Parar", style = MaterialTheme.typography.labelLarge)
        }

        IconButton(
          onClick = onClearClick,
          modifier = Modifier.minimumInteractiveComponentSize().testTag("clear_button"),
        ) {
          Icon(
            imageVector = Icons.Filled.DeleteSweep,
            contentDescription = "Limpar consola",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }

    HorizontalDivider(color = syntaxColors.border, thickness = 1.dp)

    // Output log
    SelectionContainer(modifier = Modifier.weight(1f).fillMaxWidth()) {
      LazyColumn(
        state = listState,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        if (consoleEntries.isEmpty()) {
          item {
            Text(
              text = "Consola vazia.",
              fontFamily = JetBrainsMonoFontFamily,
              fontSize = fontSizeSp.sp,
              color = syntaxColors.gutterText,
            )
          }
        } else {
          items(consoleEntries, key = { it.id }) { entry ->
            val color =
              when (entry.type) {
                OutputType.OUTPUT -> syntaxColors.consoleOutput
                OutputType.ERROR -> syntaxColors.consoleError
                OutputType.WARNING -> syntaxColors.consoleWarning
                OutputType.SYSTEM -> syntaxColors.consoleSystem
                OutputType.PROMPT -> syntaxColors.consolePrompt
              }
            Text(
              text = entry.text,
              fontFamily = JetBrainsMonoFontFamily,
              fontSize = fontSizeSp.sp,
              lineHeight = (fontSizeSp * 1.4f).sp,
              color = color,
            )
          }
        }
      }
    }

    // Inline .scanln.Br input prompt (replaces blocking prompt())
    if (pendingInputVar != null) {
      HorizontalDivider(color = syntaxColors.border, thickness = 1.dp)
      Row(
        modifier =
          Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        OutlinedTextField(
          value = pendingInputText,
          onValueChange = onPendingInputChanged,
          singleLine = true,
          label = { Text("Digite $pendingInputVar") },
          textStyle =
            TextStyle(
              fontFamily = JetBrainsMonoFontFamily,
              fontSize = fontSizeSp.sp,
            ),
          keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
          keyboardActions = KeyboardActions(onSend = { onSubmitPendingInput() }),
          modifier = Modifier.weight(1f).testTag("scanln_input"),
        )
        FilledTonalIconButton(
          onClick = onSubmitPendingInput,
          modifier = Modifier.size(48.dp).testTag("scanln_submit"),
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.Send,
            contentDescription = "Enviar entrada",
          )
        }
      }
    }

    // REPL Interactive Prompt & Quick Commands
    if (mode == IdeMode.REPL) {
      HorizontalDivider(color = syntaxColors.border, thickness = 1.dp)

      Row(
        modifier =
          Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        REPL_QUICK_COMMANDS.forEach { cmd ->
          AssistChip(
            onClick = { onSubmitReplCommand(cmd) },
            enabled = !isRunning && pendingInputVar == null,
            label = {
              Text(
                text = cmd,
                fontFamily = JetBrainsMonoFontFamily,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
              )
            },
            modifier = Modifier.defaultMinSize(minHeight = 40.dp),
          )
        }
      }

      AuxiliarySymbolBar(onInsertSymbol = onInsertSymbol)

      Row(
        modifier =
          Modifier
            .fillMaxWidth()
            .background(syntaxColors.gutterBg)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Text(
          text = if (isReplProgramBufferActive) "..." else ">>>",
          fontFamily = JetBrainsMonoFontFamily,
          fontWeight = FontWeight.Bold,
          fontSize = fontSizeSp.sp,
          color = syntaxColors.consolePrompt,
        )
        OutlinedTextField(
          value = replInput,
          onValueChange = onReplInputChanged,
          singleLine = true,
          enabled = !isRunning && pendingInputVar == null,
          placeholder = {
            Text(
              text =
                if (isReplProgramBufferActive) "Linha do programa ou .run"
                else "Comando Brain...",
              fontFamily = JetBrainsMonoFontFamily,
            )
          },
          textStyle =
            TextStyle(
              fontFamily = JetBrainsMonoFontFamily,
              fontSize = fontSizeSp.sp,
            ),
          keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
          keyboardActions = KeyboardActions(onSend = { onSubmitReplCommand(null) }),
          modifier = Modifier.weight(1f).testTag("repl_command_input"),
        )
        FilledTonalIconButton(
          onClick = { onSubmitReplCommand(null) },
          enabled = !isRunning && pendingInputVar == null,
          modifier = Modifier.size(48.dp).testTag("repl_send_button"),
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.Send,
            contentDescription = "Executar comando REPL",
          )
        }
      }
    }
  }
}
