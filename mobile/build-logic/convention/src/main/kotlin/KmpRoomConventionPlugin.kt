import androidx.room.gradle.RoomExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

class KmpRoomConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("androidx.room")
            pluginManager.apply("com.google.devtools.ksp")

            extensions.configure<RoomExtension> {
                schemaDirectory("$projectDir/schemas")
            }

            extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension> {
                sourceSets.commonMain.dependencies {
                    implementation(libs.findLibrary("room-runtime").get())
                    implementation(libs.findLibrary("sqlite-bundled").get())
                }
            }

            dependencies {
                add("kspCommonMainMetadata", libs.findLibrary("room-compiler").get())
                add("kspAndroid", libs.findLibrary("room-compiler").get())
                add("kspIosArm64", libs.findLibrary("room-compiler").get())
                add("kspIosSimulatorArm64", libs.findLibrary("room-compiler").get())
            }
        }
    }
}

private val Project.libs
    get() = extensions.getByType(org.gradle.api.artifacts.VersionCatalogsExtension::class.java)
        .named("libs")
