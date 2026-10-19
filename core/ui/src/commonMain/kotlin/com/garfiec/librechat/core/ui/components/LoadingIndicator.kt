package com.garfiec.librechat.core.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.garfiec.librechat.core.ui.resources.Res
import com.garfiec.librechat.core.ui.resources.cd_loading
import org.jetbrains.compose.resources.stringResource

@Composable
fun LoadingIndicator(
    modifier: Modifier = Modifier,
) {
    val loadingCd = stringResource(Res.string.cd_loading)
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        AdaptiveCircularProgressIndicator(
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.semantics { contentDescription = loadingCd },
        )
    }
}
