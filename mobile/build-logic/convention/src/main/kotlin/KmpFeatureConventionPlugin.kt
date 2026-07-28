import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

class KmpFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("librechat.kmp.library")
            pluginManager.apply("librechat.kmp.compose")
            pluginManager.apply("librechat.kmp.koin")
            pluginManager.apply("librechat.kotlin.serialization")

            extensions.configure<KotlinMultiplatformExtension> {
                sourceSets.commonMain.dependencies {
                    implementation(project(":core:ui"))
                    implementation(project(":core:data"))
                    implementation(project(":core:model"))
                    implementation(project(":core:common"))
                    implementation(libs.findLibrary("koin-compose").get())
                    implementation(libs.findLibrary("koin-compose-viewmodel").get())
                    implementation(libs.findLibrary("koin-compose-viewmodel-navigation").get())
                    implementation(libs.findLibrary("navigation3-ui-kmp").get())
                    implementation(libs.findLibrary("lifecycle-runtime-compose-kmp").get())
                    implementation(libs.findLibrary("lifecycle-viewmodel-compose-kmp").get())
                    implementation(libs.findLibrary("lifecycle-viewmodel-navigation3-kmp").get())
                }
            }
        }
    }
}

private val Project.libs
    get() = extensions.getByType(org.gradle.api.artifacts.VersionCatalogsExtension::class.java)
        .named("libs")
