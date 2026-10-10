package com.garfiec.librechat.feature.settings.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.model.speech.TtsVoice
import com.garfiec.librechat.core.ui.components.AdaptiveButton
import com.garfiec.librechat.core.ui.components.AdaptiveDivider
import com.garfiec.librechat.core.ui.components.AdaptiveOutlinedTextField
import com.garfiec.librechat.core.ui.components.AdaptiveSwitch
import com.garfiec.librechat.feature.settings.resources.*
import com.garfiec.librechat.feature.settings.resources.Res
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SpeechSettingsSection(
    autoSendAfterSttEnabled: Boolean,
    autoReadEnabled: Boolean,
    selectedVoice: TtsVoice?,
    availableVoices: List<TtsVoice>,
    ttsSource: String,
    onAutoSendAfterSttChange: (Boolean) -> Unit,
    onAutoReadChange: (Boolean) -> Unit,
    onVoiceSelect: (TtsVoice) -> Unit,
    onTestVoice: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            // Auto-send after speech toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(Res.string.auto_send_after_speech),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = stringResource(Res.string.auto_send_after_speech_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                val toggleAutoSendCd = stringResource(Res.string.cd_toggle_auto_send_stt)
                AdaptiveSwitch(
                    checked = autoSendAfterSttEnabled,
                    onCheckedChange = onAutoSendAfterSttChange,
                    modifier = Modifier.semantics {
                        contentDescription = toggleAutoSendCd
                    },
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Auto-read toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(Res.string.auto_read_responses),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = stringResource(Res.string.auto_read_responses_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                val toggleAutoReadCd = stringResource(Res.string.cd_toggle_auto_read)
                AdaptiveSwitch(
                    checked = autoReadEnabled,
                    onCheckedChange = onAutoReadChange,
                    modifier = Modifier.semantics {
                        contentDescription = toggleAutoReadCd
                    },
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // TTS Voice selector (only relevant for server TTS)
            if (ttsSource == "server") {
                if (availableVoices.isNotEmpty()) {
                    Text(
                        text = stringResource(Res.string.tts_voice),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    var expanded by remember { mutableStateOf(false) }

                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = it },
                    ) {
                        val voiceName = selectedVoice?.name ?: stringResource(Res.string.stt_default)
                        val voiceCd = stringResource(Res.string.cd_tts_voice_selector, voiceName)
                        AdaptiveOutlinedTextField(
                            value = voiceName,
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                .semantics {
                                    contentDescription = voiceCd
                                },
                        )
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false },
                        ) {
                            availableVoices.forEach { voice ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(
                                                text = voice.name,
                                                style = MaterialTheme.typography.bodyMedium,
                                            )
                                            voice.provider?.let { provider ->
                                                Text(
                                                    text = provider,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                        }
                                    },
                                    onClick = {
                                        onVoiceSelect(voice)
                                        expanded = false
                                    },
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Test voice button
                    val testVoiceCd = stringResource(Res.string.cd_test_tts_voice)
                    AdaptiveButton(
                        onClick = onTestVoice,
                        modifier = Modifier.semantics {
                            contentDescription = testVoiceCd
                        },
                    ) {
                        Text(stringResource(Res.string.test_voice))
                    }
                } else {
                    Text(
                        text = stringResource(Res.string.no_server_tts_voices),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Text(
                    text = stringResource(Res.string.using_device_tts),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        AdaptiveDivider(modifier = Modifier.padding(top = 8.dp))
    }
}
