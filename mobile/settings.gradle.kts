pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.aliyun.com/repository/gradle-plugin")
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/public")
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "Codex-Pocket-Mobile"

include(":app")
include(":core:common")
include(":core:logging")
include(":core:model")
include(":core:network")
include(":core:data")
include(":core:ui")
include(":feature:auth")
include(":feature:chat")
include(":feature:conversations")
include(":feature:settings")
include(":feature:agents")
include(":feature:files")
include(":feature:skills")
include(":shared")
include(":detekt-rules")
