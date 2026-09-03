package com.garfiec.librechat.core.brand.di

import com.garfiec.librechat.core.brand.ServerPinning
import com.garfiec.librechat.core.brand.TchatBrand
import org.koin.dsl.module

// TCHAT: whole file. The single Koin-visible seam between the brand constants and the rest of the
// app — :core:data and :feature:auth ask for a ServerPinning, never for TchatBrand directly.
//
// allowOverride is unconditionally false — in every build, debug included. There is no way, in
// any installed Tchat build, to see or change the server URL: ServerUrlScreen never renders its
// form (see ServerUrlViewModel's force-pin init block) and auto-connects straight to
// TchatBrand.SERVER_URL. An earlier revision tied this to BrandBuild.isDebugBuild so a debug
// build could point at a staging server — deliberately dropped: every config must be set for
// TensorGrid, full stop, with no build-variant carve-out a tester could stumble into. A developer
// who genuinely needs to point a personal build elsewhere edits TchatBrand.SERVER_URL directly
// before building, which is a source change, not something an installed APK ever exposes.
val brandModule = module {
    single { ServerPinning(url = TchatBrand.SERVER_URL, allowOverride = false) }
}
