package com.garfiec.librechat.feature.conversations.search

/**
 * Whether the Search screen draws its own back arrow. iOS has no system Back, so the screen needs
 * one; Android's Back gesture and button already leave the screen.
 */
internal expect val searchShowsBackButton: Boolean
