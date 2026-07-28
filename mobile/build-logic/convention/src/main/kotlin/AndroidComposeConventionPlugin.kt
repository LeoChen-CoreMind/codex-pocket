import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")

            // Stability configuration — marks core model classes as @Stable so Compose
            // can skip recomposition when parameters haven't changed.
            val stabilityConfig = rootProject.file("compose-stability.conf")
            if (stabilityConfig.exists()) {
                tasks.withType<KotlinCompile>().configureEach {
                    compilerOptions {
                        freeCompilerArgs.addAll(
                            "-P", "plugin:androidx.compose.compiler.plugins.kotlin:stabilityConfigurationPath=${stabilityConfig.absolutePath}",
                        )
                    }
                }
            }

            // Compose compiler metrics/reports (opt-in via -PenableComposeCompilerReports=true)
            if (providers.gradleProperty("enableComposeCompilerReports").orNull == "true") {
                val reportsDir = layout.buildDirectory.dir("compose_metrics").get().asFile.absolutePath
                tasks.withType<KotlinCompile>().configureEach {
                    compilerOptions {
                        freeCompilerArgs.addAll(
                            "-P", "plugin:androidx.compose.compiler.plugins.kotlin:metricsDestination=$reportsDir",
                            "-P", "plugin:androidx.compose.compiler.plugins.kotlin:reportsDestination=$reportsDir",
                        )
                    }
                }
            }

            extensions.findByType(com.android.build.gradle.LibraryExtension::class.java)?.apply {
                buildFeatures.compose = true
            }
            extensions.findByType(com.android.build.api.dsl.ApplicationExtension::class.java)?.apply {
                buildFeatures.compose = true
            }

            dependencies {
                val bom = libs.findLibrary("compose-bom").get()
                add("implementation", platform(bom))
                add("implementation", libs.findBundle("compose").get())
                add("debugImplementation", libs.findLibrary("compose-ui-tooling").get())
                add("androidTestImplementation", platform(bom))
                add("androidTestImplementation", libs.findLibrary("compose-ui-test").get())
                add("lintChecks", libs.findLibrary("compose-lint-checks").get())
            }
        }
    }
}

private val Project.libs
    get() = extensions.getByType(org.gradle.api.artifacts.VersionCatalogsExtension::class.java)
        .named("libs")
