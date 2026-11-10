package com.garfiec.librechat.shared.navigation

import kotlinx.serialization.modules.SerializersModule
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.qualifier.named
import org.koin.dsl.module

val sharedAppModule = module {
    viewModelOf(::NavHostViewModel)
    single { DeepLinkInbox() }
    single<SerializersModule>(named("navigation")) { navigationSerializersModule }
}
