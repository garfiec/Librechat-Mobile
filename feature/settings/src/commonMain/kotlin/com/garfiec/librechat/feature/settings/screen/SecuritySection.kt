package com.garfiec.librechat.feature.settings.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.ui.components.AdaptiveDivider
import com.garfiec.librechat.core.ui.components.AdaptiveOutlinedButton
import com.garfiec.librechat.feature.settings.resources.*
import com.garfiec.librechat.feature.settings.resources.Res
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun SecuritySection(
    isTwoFactorEnabled: Boolean,
    isLoading: Boolean,
    onToggleTwoFactor: () -> Unit,
    onViewBackupCodes: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 2FA status indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = if (isTwoFactorEnabled) {
                        Icons.Default.CheckCircle
                    } else {
                        Icons.Default.Warning
                    },
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = if (isTwoFactorEnabled) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = stringResource(Res.string.two_factor_auth),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = stringResource(if (isTwoFactorEnabled) Res.string.status_enabled else Res.string.status_disabled),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isTwoFactorEnabled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }

            // Enable/Disable 2FA button
            AdaptiveOutlinedButton(
                onClick = onToggleTwoFactor,
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    stringResource(if (isTwoFactorEnabled) Res.string.disable_two_factor else Res.string.enable_two_factor),
                )
            }

            // View backup codes (only when 2FA is enabled)
            if (isTwoFactorEnabled) {
                AdaptiveOutlinedButton(
                    onClick = onViewBackupCodes,
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(Res.string.view_backup_codes))
                }
            }

            Spacer(modifier = Modifier.height(0.dp))
        }
        AdaptiveDivider(modifier = Modifier.padding(top = 8.dp))
    }
}
