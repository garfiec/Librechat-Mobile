package com.garfiec.librechat.core.common.di

import org.koin.core.module.Module
import org.koin.dsl.module

// The browser build runs without Koin; nothing here is ever resolved.
@Suppress("EmptyModule")
actual val commonPlatformModule: Module = module { }
