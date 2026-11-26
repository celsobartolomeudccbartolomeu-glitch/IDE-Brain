package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.BrainSuggestion
import com.example.ui.BrainSyntaxVisualTransformation
import com.example.ui.SuggestionKind
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalSyntaxColors

private val AUX_SYMBOLS = listOf("=", "\"", "+", "-", "*", "/", "(", ")", "tab")

@Composable
fun BrainEditorPanel(
  fileName: String,
  hasUnsavedChanges: Boolean,
  codeValue: TextFieldValue,
  fontSizeSp: Float,
  focusNonce: Int,
  isRunning: Boolean,
  suggestions: List<BrainSuggestion>,
  onCodeChange: (TextFieldValue) -> Unit,
  onRunClick: () -> Unit,
  onInsertSymbol: (String) -> Unit,
  onApplySuggestion: (BrainSuggestion) -> Unit,
  modifier: Modifier = Modifier,
) {
  val syntaxColors = LocalSyntaxColors.current
  val verticalScroll = rememberScrollState()
  val horizontalScroll = rememberScrollState()
  val focusRequester = remember { FocusRequester() }
  val keyboardController = LocalSoftwareKeyboardController.current
  val visualTransformation =
    remember(syntaxColors) { BrainSyntaxVisualTransformation(syntaxColors) }

  LaunchedEffect(focusNonce) {
    if (focusNonce > 0) {
      focusRequester.requestFocus()
      keyboardController?.show()
    }
  }

  val lineCount = remember(codeValue.text) {
    maxOf(1, codeValue.text.count { it == '\n' } + 1)
  }
  val lineNumbersText = remember(lineCount) {
    (1..lineCount).joinToString("\n")
  }

  val lineHeightSp = (fontSizeSp * 1.48f)
  val editorTextStyle =
    remember(fontSizeSp, syntaxColors.consoleOutput) {
      TextStyle(
        fontFamily = JetBrainsMonoFontFamily,
        fontSize = fontSizeSp.sp,
        lineHeight = lineHeightSp.sp,
        color = syntaxColors.consoleOutput,
      )
    }

  Column(
    modifier =
      modifier
        .clip(RoundedCornerShape(12.dp))
        .background(syntaxColors.editorBg)
        .border(1.dp, syntaxColors.border, RoundedCornerShape(12.dp))
  ) {
    // Slim single-line file bar (never wraps text)
    Row(
      modifier =
        Modifier
          .fillMaxWidth()
          .height(36.dp)
          .background(syntaxColors.gutterBg)
          .padding(horizontal = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.weight(1f),
      ) {
        Icon(
          imageVector = Icons.Filled.Code,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
          text = if (hasUnsavedChanges) "$fileName *" else fileName,
          fontSize = 12.sp,
          fontWeight = FontWeight.SemiBold,
          color = MaterialTheme.colorScheme.onSurface,
          maxLines = 1,
          softWrap = false,
          overflow = TextOverflow.Ellipsis,
        )
      }

      Text(
        text = "$lineCount ln",
        fontSize = 11.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        softWrap = false,
      )
    }

    HorizontalDivider(color = syntaxColors.border, thickness = 1.dp)

    // Full-screen editor body with floating Run button
    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
      Row(
        modifier =
          Modifier
            .fillMaxSize()
            .verticalScroll(verticalScroll)
      ) {
        Text(
          text = lineNumbersText,
          style =
            editorTextStyle.copy(
              color = syntaxColors.gutterText,
              textAlign = TextAlign.End,
            ),
          modifier =
            Modifier
              .widthIn(min = 36.dp)
              .fillMaxHeight()
              .background(syntaxColors.gutterBg)
              .padding(start = 6.dp, end = 6.dp, top = 10.dp, bottom = 72.dp),
        )

        Box(
          modifier =
            Modifier
              .width(1.dp)
              .fillMaxHeight()
              .background(syntaxColors.border)
        )

        Box(
          modifier =
            Modifier
              .weight(1f)
              .horizontalScroll(horizontalScroll)
              .padding(start = 10.dp, end = 16.dp, top = 10.dp, bottom = 72.dp)
        ) {
          if (codeValue.text.isEmpty()) {
            Text(
              text = "# Escreva código Brain aqui...",
              style = editorTextStyle.copy(color = syntaxColors.gutterText),
            )
          }
          BasicTextField(
            value = codeValue,
            onValueChange = onCodeChange,
            textStyle = editorTextStyle,
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            visualTransformation = visualTransformation,
            modifier =
              Modifier
                .fillMaxSize()
                .defaultMinSize(minWidth = 260.dp, minHeight = 220.dp)
                .focusRequester(focusRequester)
                .testTag("code_editor_input"),
          )
        }
      }

      FloatingActionButton(
        onClick = { if (!isRunning) onRunClick() },
        shape = CircleShape,
        containerColor = MaterialTheme.colorScheme.tertiary,
        contentColor = MaterialTheme.colorScheme.onTertiary,
        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp),
        modifier =
          Modifier
            .align(Alignment.BottomEnd)
            .padding(14.dp)
            .size(50.dp)
            .testTag("run_fab_button"),
      ) {
        Icon(
          imageVector = Icons.Filled.PlayArrow,
          contentDescription = "Executar programa",
          modifier = Modifier.size(26.dp),
        )
      }
    }

    // Smart auto-complete suggestion bar directly above the auxiliary symbol bar
    AutoCompleteSuggestionBar(
      suggestions = suggestions,
      onApplySuggestion = onApplySuggestion,
    )

    HorizontalDivider(color = syntaxColors.border, thickness = 1.dp)

    // Auxiliary symbol keyboard bar pinned above the soft keyboard
    AuxiliarySymbolBar(onInsertSymbol = onInsertSymbol)
  }
}

@Composable
fun AutoCompleteSuggestionBar(
  suggestions: List<BrainSuggestion>,
  onApplySuggestion: (BrainSuggestion) -> Unit,
  modifier: Modifier = Modifier,
) {
  val syntaxColors = LocalSyntaxColors.current
  AnimatedVisibility(
    visible = suggestions.isNotEmpty(),
    enter = expandVertically() + fadeIn(),
    exit = shrinkVertically() + fadeOut(),
  ) {
    Column(modifier = modifier.fillMaxWidth().background(syntaxColors.editorBg)) {
      HorizontalDivider(color = syntaxColors.border, thickness = 1.dp)
      LazyRow(
        modifier =
          Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 4.dp)
            .testTag("autocomplete_bar"),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        items(suggestions, key = { it.id }) { item ->
          val accentColor =
            when (item.kind) {
              SuggestionKind.COMMAND -> syntaxColors.dotCommand
              SuggestionKind.KEYWORD,
              SuggestionKind.SNIPPET -> syntaxColors.keyword
              SuggestionKind.TYPE -> syntaxColors.booleanLiteral
              SuggestionKind.OPERATOR -> syntaxColors.operator
              SuggestionKind.BOOLEAN -> syntaxColors.numberLiteral
              SuggestionKind.VARIABLE -> syntaxColors.stringLiteral
            }
          Surface(
            shape = RoundedCornerShape(8.dp),
            color = syntaxColors.gutterBg,
            tonalElevation = 1.dp,
            modifier =
              Modifier
                .defaultMinSize(minHeight = 36.dp)
                .border(1.dp, accentColor.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                .clip(RoundedCornerShape(8.dp))
                .clickable { onApplySuggestion(item) }
                .minimumInteractiveComponentSize()
                .testTag("autocomplete_chip_${item.id}"),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
              Text(
                text = item.kind.badge,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = accentColor,
                modifier =
                  Modifier
                    .background(accentColor.copy(alpha = 0.14f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp, vertical = 1.dp),
              )
              Text(
                text = item.label,
                fontFamily = JetBrainsMonoFontFamily,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                softWrap = false,
              )
              if (item.detail.isNotEmpty()) {
                Text(
                  text = item.detail,
                  fontSize = 10.sp,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  maxLines = 1,
                  softWrap = false,
                )
              }
            }
          }
        }
      }
    }
  }
}

@Composable
fun AuxiliarySymbolBar(
  onInsertSymbol: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val syntaxColors = LocalSyntaxColors.current
  Row(
    modifier =
      modifier
        .fillMaxWidth()
        .background(syntaxColors.gutterBg)
        .horizontalScroll(rememberScrollState())
        .padding(horizontal = 6.dp, vertical = 4.dp),
    horizontalArrangement = Arrangement.spacedBy(5.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    AUX_SYMBOLS.forEach { symbol ->
      FilledTonalButton(
        onClick = { onInsertSymbol(symbol) },
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
        modifier =
          Modifier
            .defaultMinSize(minWidth = 42.dp, minHeight = 42.dp)
            .height(42.dp)
            .testTag("aux_key_$symbol"),
      ) {
        Text(
          text = symbol,
          fontFamily = JetBrainsMonoFontFamily,
          fontWeight = FontWeight.SemiBold,
          fontSize = 13.sp,
          maxLines = 1,
          softWrap = false,
        )
      }
    }
  }
}
