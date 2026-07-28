import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("librechat.mobile.library")
            pluginManager.apply("librechat.mobile.compose")
            pluginManager.apply("librechat.mobile.koin")

            dependencies {
                add("implementation", project(":core:ui"))
                add("implementation", project(":core:data"))
                add("implementation", project(":core:model"))
                add("implementation", project(":core:common"))
                add("implementation", libs.findLibrary("koin-compose").get())
                add("implementation", libs.findLibrary("koin-compose-viewmodel").get())
                add("implementation", libs.findLibrary("koin-compose-viewmodel-navigation").get())
                add("implementation", libs.findLibrary("navigation3-ui-kmp").get())
                add("implementation", libs.findLibrary("lifecycle-viewmodel-navigation3-kmp").get())
                add("implementation", libs.findBundle("lifecycle").get())
            }
        }
    }
}

private val Project.libs
    get() = extensions.getByType(org.gradle.api.artifacts.VersionCatalogsExtension::class.java)
        .named("libs")
