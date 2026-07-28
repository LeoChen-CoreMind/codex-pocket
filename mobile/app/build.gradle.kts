plugins {
    id("librechat.mobile.application")
    id("librechat.mobile.compose")
    id("librechat.mobile.koin")
    id("librechat.kotlin.serialization")
}

android {
    namespace = "com.garfiec.librechat"

    defaultConfig {
        applicationId = "com.leochen.codexpocket"
    }

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(project(":core:ui"))
    implementation(project(":core:data"))
    implementation(project(":core:network"))
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    implementation(project(":core:logging"))
    implementation(project(":feature:auth"))
    implementation(project(":feature:chat"))
    implementation(project(":feature:conversations"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:agents"))
    implementation(project(":feature:files"))
    implementation(project(":feature:skills"))

    implementation(libs.activity.compose)
    implementation(libs.navigation3.ui.kmp)
    implementation(libs.lifecycle.viewmodel.navigation3.kmp)
    implementation(libs.koin.compose)
    implementation(libs.koin.compose.viewmodel)
    implementation(libs.koin.compose.viewmodel.navigation)
    implementation(libs.compose.material3.wsc)
    implementation(libs.bundles.lifecycle)
    implementation(libs.coil3.compose)
    implementation(libs.coil3.network.ktor)
    implementation(libs.coil3.svg)
    implementation(libs.kermit)

    androidTestImplementation(libs.compose.ui.test)
    debugImplementation(libs.compose.ui.test.manifest)
}
