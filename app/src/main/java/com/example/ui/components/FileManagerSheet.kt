package com.example.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SaveAs
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.BrainFileItem
import com.example.ui.theme.JetBrainsMonoFontFamily

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileManagerSheet(
  activeFileName: String,
  files: List<BrainFileItem>,
  onDismiss: () -> Unit,
  onSaveCurrent: () -> Unit,
  onSaveAs: (String) -> Unit,
  onCreateNew: (String) -> Unit,
  onOpenFile: (String) -> Unit,
  onDeleteFile: (String) -> Unit,
  onImportClick: () -> Unit,
  onExportClick: () -> Unit,
) {
  var showNameDialog by remember { mutableStateOf<String?>(null) } // "new" or "save_as"
  var draftName by remember { mutableStateOf("") }

  ModalBottomSheet(onDismissRequest = onDismiss) {
    Column(
      modifier =
        Modifier
          .fillMaxWidth()
          .padding(horizontal = 16.dp)
          .padding(bottom = 24.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Text(
        text = "Gerir Ficheiros (.brain)",
        style = MaterialTheme.typography.titleLarge,
      )

      // Primary file actions
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        FilledTonalButton(
          onClick = {
            draftName = "programa_${files.size + 1}.brain"
            showNameDialog = "new"
          },
          modifier = Modifier.weight(1f).height(48.dp).testTag("new_file_button"),
        ) {
          Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(4.dp))
          Text("Novo")
        }

        FilledTonalButton(
          onClick = {
            onSaveCurrent()
            onDismiss()
          },
          modifier = Modifier.weight(1f).height(48.dp).testTag("sheet_save_button"),
        ) {
          Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(4.dp))
          Text("Guardar")
        }

        FilledTonalButton(
          onClick = {
            draftName = activeFileName
            showNameDialog = "save_as"
          },
          modifier = Modifier.weight(1f).height(48.dp).testTag("save_as_button"),
        ) {
          Icon(Icons.Filled.SaveAs, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(4.dp))
          Text("Como")
        }
      }

      // Import / Export .brain
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        OutlinedButton(
          onClick = onImportClick,
          modifier = Modifier.weight(1f).height(48.dp).testTag("import_brain_button"),
        ) {
          Icon(Icons.Filled.Upload, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(6.dp))
          Text("Importar .brain")
        }
        OutlinedButton(
          onClick = onExportClick,
          modifier = Modifier.weight(1f).height(48.dp).testTag("export_brain_button"),
        ) {
          Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(6.dp))
          Text("Exportar .brain")
        }
      }

      HorizontalDivider()

      Text(
        text = "Ficheiros Guardados (${files.size})",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      if (files.isEmpty()) {
        Text(
          text = "Nenhum ficheiro guardado offline.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(vertical = 12.dp),
        )
      } else {
        LazyColumn(
          modifier = Modifier.fillMaxWidth().height(240.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          items(files, key = { it.name }) { item ->
            val isActive = item.name == activeFileName
            Surface(
              shape = RoundedCornerShape(10.dp),
              color =
                if (isActive) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
              modifier =
                Modifier
                  .fillMaxWidth()
                  .clickable { onOpenFile(item.name) },
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Icon(
                  imageVector = Icons.Filled.Description,
                  contentDescription = null,
                  tint =
                    if (isActive) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                  Text(
                    text = "${item.sizeBytes} bytes",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
                IconButton(
                  onClick = { onDeleteFile(item.name) },
                  modifier = Modifier.minimumInteractiveComponentSize(),
                ) {
                  Icon(
                    imageVector = Icons.Filled.DeleteOutline,
                    contentDescription = "Apagar ${item.name}",
                    tint = MaterialTheme.colorScheme.error,
                  )
                }
              }
            }
          }
        }
      }
    }
  }

  if (showNameDialog != null) {
    val isNew = showNameDialog == "new"
    AlertDialog(
      onDismissRequest = { showNameDialog = null },
      title = { Text(if (isNew) "Novo Ficheiro" else "Guardar Como") },
      text = {
        OutlinedTextField(
          value = draftName,
          onValueChange = { draftName = it },
          singleLine = true,
          label = { Text("Nome do ficheiro (.brain)") },
          modifier = Modifier.fillMaxWidth(),
        )
      },
      confirmButton = {
        TextButton(
          onClick = {
            if (isNew) onCreateNew(draftName) else onSaveAs(draftName)
            showNameDialog = null
          }
        ) {
          Text(if (isNew) "Criar" else "Guardar")
        }
      },
      dismissButton = {
        TextButton(onClick = { showNameDialog = null }) {
          Text("Cancelar")
        }
      },
    )
  }
}

@Composable
fun BrainHelpDialog(onDismiss: () -> Unit) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Referência Brain Language") },
    text = {
      Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        val items =
          listOf(
            "Variáveis" to "x = 10 | int a = 5 | float f = 2.5 | char c = \"A\" | bool b = true",
            "Operações" to "soma a b | sub a b | multiplica a b | div a b | a + b",
            "Saída" to "print \"Olá\" | print x | IA.digitalwrite.line \"Texto\"",
            "Entrada" to ".scanln.Br nome",
            "Condição" to "if x > 5 ... else ... endif",
            "Ciclo" to "while n <= 10 ... endwhile (máx. 1000 iterações)",
            "Sistema" to "show.vars | .clear_var x | .screnclear | .version | .help",
            "Modo programa (REPL)" to "IA.programa ... see.lines | .del N | .sair_prog | .run",
            "Comentários" to "# comentário ou // comentário",
          )
        items.forEach { (title, desc) ->
          Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
          )
          Text(
            text = desc,
            fontFamily = JetBrainsMonoFontFamily,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    },
    confirmButton = {
      TextButton(onClick = onDismiss) {
        Text("Fechar")
      }
    },
  )
}
