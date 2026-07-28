import com.android.build.gradle.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension

class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.library")
            pluginManager.apply("org.jetbrains.kotlin.android")
            pluginManager.apply("librechat.detekt")
            pluginManager.apply("org.jetbrains.kotlinx.kover")

            extensions.configure<KotlinAndroidProjectExtension> {
                jvmToolchain(BuildConstants.JVM_TOOLCHAIN_VERSION)
                compilerOptions {
                    freeCompilerArgs.addAll("-opt-in=kotlin.time.ExperimentalTime")
                }
            }

            extensions.configure<LibraryExtension> {
                compileSdk = BuildConstants.COMPILE_SDK
                defaultConfig {
                    minSdk = BuildConstants.MIN_SDK
                    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                }
                compileOptions {
                    isCoreLibraryDesugaringEnabled = true
                }
                lint {
                    // Workaround: NonNullableMutableLiveDataDetector crashes with
                    // IncompatibleClassChangeError on AGP 8.7.x + Kotlin 2.1.x
                    disable += "NullSafeMutableLiveData"
                }
                testOptions {
                    unitTests.isReturnDefaultValues = true
                }
            }

            dependencies.add("coreLibraryDesugaring", libs.findLibrary("desugar-jdk").get())
            dependencies.add("testImplementation", libs.findBundle("testing").get())
        }
    }
}

private val Project.libs
    get() = extensions.getByType(org.gradle.api.artifacts.VersionCatalogsExtension::class.java)
        .named("libs")
