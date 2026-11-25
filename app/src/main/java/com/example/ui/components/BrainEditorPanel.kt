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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.BrainSyntaxVisualTransformation
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalSyntaxColors

private val AUX_SYMBOLS = listOf("=", "\"", "+", "-", "*", "/", "(", ")", "tab")

@Composable
fun BrainEditorPanel(
  fileName: String,
  hasUnsavedChanges: Boolean,
  codeValue: TextFieldValue,
  fontSizeSp: Float,
  onCodeChange: (TextFieldValue) -> Unit,
  onSaveClick: () -> Unit,
  onInsertSymbol: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val syntaxColors = LocalSyntaxColors.current
  val verticalScroll = rememberScrollState()
  val horizontalScroll = rememberScrollState()
  val visualTransformation =
    remember(syntaxColors) { BrainSyntaxVisualTransformation(syntaxColors) }

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
        .clip(RoundedCornerShape(14.dp))
        .background(syntaxColors.editorBg)
        .border(1.dp, syntaxColors.border, RoundedCornerShape(14.dp))
  ) {
    // Compact file header bar
    Row(
      modifier =
        Modifier
          .fillMaxWidth()
          .background(syntaxColors.gutterBg)
          .padding(horizontal = 12.dp, vertical = 4.dp),
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
          modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
          text = if (hasUnsavedChanges) "$fileName *" else fileName,
          style = MaterialTheme.typography.titleSmall,
          color = MaterialTheme.colorScheme.onSurface,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(8.dp))
        Text(
          text = "$lineCount linhas",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      IconButton(
        onClick = onSaveClick,
        modifier = Modifier.minimumInteractiveComponentSize().testTag("save_file_button"),
      ) {
        Icon(
          imageVector = Icons.Filled.Save,
          contentDescription = "Guardar ficheiro",
          tint =
            if (hasUnsavedChanges) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.size(20.dp),
        )
      }
    }

    HorizontalDivider(color = syntaxColors.border, thickness = 1.dp)

    // Scrollable editor with synchronized line number gutter
    Row(
      modifier =
        Modifier
          .weight(1f)
          .fillMaxWidth()
          .verticalScroll(verticalScroll)
    ) {
      // Line numbers gutter
      Text(
        text = lineNumbersText,
        style =
          editorTextStyle.copy(
            color = syntaxColors.gutterText,
            textAlign = TextAlign.End,
          ),
        modifier =
          Modifier
            .widthIn(min = 38.dp)
            .fillMaxHeight()
            .background(syntaxColors.gutterBg)
            .padding(start = 8.dp, end = 8.dp, top = 10.dp, bottom = 16.dp),
      )

      Box(
        modifier =
          Modifier
            .width(1.dp)
            .fillMaxHeight()
            .background(syntaxColors.border)
      )

      // Code input area
      Box(
        modifier =
          Modifier
            .weight(1f)
            .horizontalScroll(horizontalScroll)
            .padding(horizontal = 10.dp, vertical = 10.dp)
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
              .defaultMinSize(minWidth = 260.dp, minHeight = 140.dp)
              .testTag("code_editor_input"),
        )
      }
    }

    HorizontalDivider(color = syntaxColors.border, thickness = 1.dp)

    // Auxiliary symbol keyboard bar
    AuxiliarySymbolBar(onInsertSymbol = onInsertSymbol)
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
    horizontalArrangement = Arrangement.spacedBy(6.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    AUX_SYMBOLS.forEach { symbol ->
      FilledTonalButton(
        onClick = { onInsertSymbol(symbol) },
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
        modifier =
          Modifier
            .defaultMinSize(minWidth = 44.dp, minHeight = 48.dp)
            .height(48.dp)
            .testTag("aux_key_$symbol"),
      ) {
        Text(
          text = symbol,
          fontFamily = JetBrainsMonoFontFamily,
          fontWeight = FontWeight.SemiBold,
          fontSize = 14.sp,
        )
      }
    }
  }
}
