package com.garfiec.librechat.feature.agents.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.ui.components.AdaptiveOutlinedTextField
import com.garfiec.librechat.feature.agents.components.model.SupportContactState
import com.garfiec.librechat.feature.agents.resources.*
import com.garfiec.librechat.feature.agents.resources.Res
import org.jetbrains.compose.resources.stringResource

@Composable
fun AgentSupportContactSection(
    supportContact: SupportContactState,
    onSupportContactChange: (SupportContactState) -> Unit,
    modifier: Modifier = Modifier,
    nameError: String? = null,
    emailError: String? = null,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(Res.string.label_support_contact),
            style = MaterialTheme.typography.titleSmall,
        )
        Spacer(modifier = Modifier.height(8.dp))

        // Name field
        Text(
            text = stringResource(Res.string.label_name),
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(modifier = Modifier.height(4.dp))
        AdaptiveOutlinedTextField(
            value = supportContact.name,
            onValueChange = {
                onSupportContactChange(supportContact.copy(name = it))
            },
            placeholder = { Text(stringResource(Res.string.support_contact_placeholder)) },
            isError = nameError != null,
            supportingText = nameError?.let { error -> { Text(error) } },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Email field
        Text(
            text = stringResource(Res.string.label_email),
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(modifier = Modifier.height(4.dp))
        AdaptiveOutlinedTextField(
            value = supportContact.email,
            onValueChange = {
                onSupportContactChange(supportContact.copy(email = it))
            },
            placeholder = { Text("support@example.com") },
            isError = emailError != null,
            supportingText = emailError?.let { error -> { Text(error) } },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
