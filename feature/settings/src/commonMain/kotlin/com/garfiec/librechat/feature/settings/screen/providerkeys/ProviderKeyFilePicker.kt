package com.garfiec.librechat.feature.settings.screen.providerkeys

import androidx.compose.runtime.Composable

/**
 * Platform JSON-file picker for the Google service-account key.
 *
 * Returns a launcher closure on platforms where the picker is implemented (Android), or
 * `null` where it is not (iOS). The Google form's always-visible paste-textarea is the
 * fallback whenever this returns `null`, so the picker is purely additive on Android.
 *
 * The [onFileRead] callback receives the raw file contents as a UTF-8 string, or `null` when
 * the file couldn't be read. A cancelled pick doesn't call it. The form-side validator decides
 * whether the contents parse to a valid Google service-account JSON.
 */
@Composable
expect fun rememberProviderKeyFilePicker(
    onFileRead: (jsonContents: String?) -> Unit,
): (() -> Unit)?
