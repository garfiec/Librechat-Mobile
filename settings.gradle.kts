pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

// A browser build (`-Plibrechat.web=true`): see KmpWebConventionPlugin.
val webBuild = providers.gradleProperty("librechat.web").orNull == "true"

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
        // The Node.js, Yarn and Binaryen toolchains a `-Plibrechat.web=true` build downloads.
        // KmpWebConventionPlugin stops the Kotlin plugin adding these as project repositories.
        if (webBuild) {
            exclusiveContent {
                forRepository {
                    ivy("https://nodejs.org/dist") {
                        name = "Node.js"
                        patternLayout { artifact("v[revision]/[artifact](-v[revision]-[classifier]).[ext]") }
                        metadataSources { artifact() }
                    }
                }
                filter { includeModule("org.nodejs", "node") }
            }
            exclusiveContent {
                forRepository {
                    ivy("https://github.com/yarnpkg/yarn/releases/download") {
                        name = "Yarn"
                        patternLayout { artifact("v[revision]/[artifact](-v[revision]).[ext]") }
                        metadataSources { artifact() }
                    }
                }
                filter { includeModule("com.yarnpkg", "yarn") }
            }
            exclusiveContent {
                forRepository {
                    ivy("https://github.com/WebAssembly/binaryen/releases/download") {
                        name = "Binaryen"
                        patternLayout { artifact("version_[revision]/[module]-version_[revision]-[classifier].[ext]") }
                        metadataSources { artifact() }
                    }
                }
                filter { includeModule("com.github.webassembly", "binaryen") }
            }
        }
    }
}

rootProject.name = "Switchboard"

include(":app")
include(":core:common")
include(":core:logging")
include(":core:model")
include(":core:network")
include(":core:data")
include(":core:ui")
include(":core:notifications")
include(":feature:auth")
include(":feature:auth:ui")
include(":feature:chat")
include(":feature:chat:ui")
include(":feature:conversations")
include(":feature:conversations:ui")
include(":feature:settings")
include(":feature:settings:ui")
include(":feature:agents")
include(":feature:agents:ui")
include(":feature:files")
include(":feature:files:ui")
include(":feature:schedules")
include(":feature:schedules:ui")
include(":feature:skills")
include(":feature:skills:ui")
include(":shared")
include(":detekt-rules")
include(":testing:speech-fixture")

if (webBuild) {
    include(":web:demo")
}
