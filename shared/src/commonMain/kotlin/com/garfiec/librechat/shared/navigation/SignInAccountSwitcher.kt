package com.garfiec.librechat.shared.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import com.garfiec.librechat.feature.conversations.drawer.AccountSwitcherSheet
import com.garfiec.librechat.feature.conversations.drawer.AccountUiModel

/**
 * The drawer's account switcher, opened from a sign-in screen's "Use another account": only the
 * other accounts still signed in ([NavHostViewModel.signInEscapeAccounts]), switch only (no remove,
 * no add: the screen under it is the sign-in). Closes itself once there is nothing left to offer,
 * which a successful switch causes.
 */
@Composable
internal fun SignInAccountSwitcher(
    accounts: List<AccountUiModel>,
    onSwitchAccount: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    LaunchedEffect(accounts.isEmpty()) {
        if (accounts.isEmpty()) currentOnDismiss()
    }
    if (accounts.isEmpty()) return
    AccountSwitcherSheet(
        accounts = accounts,
        onSwitchAccount = { accountId ->
            onDismiss()
            onSwitchAccount(accountId)
        },
        onRemoveAccountRequest = null,
        onAddAccount = null,
        onDismiss = onDismiss,
    )
}
