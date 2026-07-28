plugins {
    id("librechat.kmp.feature")
}

android {
    namespace = "com.garfiec.librechat.feature.conversations"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:network"))
            implementation(libs.paging.common)
            implementation(libs.kermit)
            implementation(libs.kotlinx.serialization.json)
        }
        androidMain.dependencies {
            implementation(libs.paging.runtime)
            implementation(libs.paging.compose)
        }
        named("androidUnitTest").dependencies {
            implementation(libs.koin.test)
        }
    }
}
