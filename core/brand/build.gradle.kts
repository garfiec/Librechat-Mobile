// TCHAT: whole module. Holds every Tchat-specific constant (server URL, name, OIDC label) in one
// place so the fork's identity never leaks into upstream files as an inline literal — see
// TCHAT.md. New module, so `git merge upstream/main` never touches it.
plugins {
    id("librechat.kmp.library")
    id("librechat.kmp.koin")
}

android {
    namespace = "com.garfiec.librechat.core.brand"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // No dependencies on other :core modules on purpose — every module in the graph is
            // free to depend on :core:brand without a cycle.
        }
    }
}
