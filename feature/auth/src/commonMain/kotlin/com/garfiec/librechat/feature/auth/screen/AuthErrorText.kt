package com.garfiec.librechat.feature.auth.screen

import androidx.compose.runtime.Composable
import com.garfiec.librechat.feature.auth.resources.Res
import com.garfiec.librechat.feature.auth.resources.auth_error_tenant_required
import com.garfiec.librechat.feature.auth.resources.login_error_federated_blocked
import com.garfiec.librechat.feature.auth.viewmodel.AuthRefusal
import org.jetbrains.compose.resources.stringResource

/**
 * The error line a sign-in screen shows: the app's own wording for a coded [refusal], else [error].
 * Shown only while [error] is set, so the edits that clear a screen's error clear the refusal's
 * wording with it.
 */
@Composable
internal fun authErrorText(refusal: AuthRefusal?, error: String?): String? {
    if (error == null) return null
    return when (refusal) {
        AuthRefusal.FederatedBlocked -> stringResource(Res.string.login_error_federated_blocked)
        AuthRefusal.TenantRequired -> stringResource(Res.string.auth_error_tenant_required)
        null -> error
    }
}
