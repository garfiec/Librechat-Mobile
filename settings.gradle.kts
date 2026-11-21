pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
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
