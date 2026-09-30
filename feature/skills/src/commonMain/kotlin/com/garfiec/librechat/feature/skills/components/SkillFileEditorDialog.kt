package com.garfiec.librechat.feature.skills.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.feature.skills.resources.*
import com.garfiec.librechat.feature.skills.resources.Res
import com.garfiec.librechat.feature.skills.viewmodel.SkillFileEditorState
import org.jetbrains.compose.resources.stringResource

/**
 * One skill file, viewed or edited in place (v0.8.8, upstream `SkillTextEditor`). Editable only
 * when the server sent the file's revision; a conflicting save blocks further saves until the user
 * reloads the server's copy, so a second editor's change is never silently overwritten.
 */
@Composable
fun SkillFileEditorDialog(
    editor: SkillFileEditorState,
    editable: Boolean,
    onSave: (String) -> Unit,
    onReload: () -> Unit,
    onDismiss: () -> Unit,
) {
    // Keyed on the revision so a reload replaces the draft with the server's copy.
    var draft by rememberSaveable(editor.relativePath, editor.fileId, editor.content) {
        mutableStateOf(editor.content.orEmpty())
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(editor.relativePath) },
        text = {
            Column {
                when {
                    editor.isLoading -> CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    editor.isBinary || editor.content == null -> Text(
                        stringResource(Res.string.skill_file_binary),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    else -> OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        readOnly = !editable,
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp, max = 420.dp),
                    )
                }
                if (editor.conflict) {
                    Text(
                        stringResource(Res.string.skill_file_conflict),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                editor.error?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            when {
                editor.conflict -> TextButton(onClick = onReload) { Text(stringResource(Res.string.skill_file_reload)) }
                editable -> TextButton(
                    onClick = { onSave(draft) },
                    enabled = !editor.isSaving && draft != editor.content,
                ) { Text(stringResource(Res.string.skill_file_save)) }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.skill_file_close)) }
        },
    )
}
