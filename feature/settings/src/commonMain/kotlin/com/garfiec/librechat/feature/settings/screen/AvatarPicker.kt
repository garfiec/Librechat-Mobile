package com.garfiec.librechat.feature.settings.screen

import androidx.compose.runtime.Composable

/** Opens the platform image picker; [onPick] gets the chosen image. */
@Composable
internal expect fun rememberAvatarPicker(onPick: (PickedAvatar) -> Unit): () -> Unit
