import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.report.ReportMergeTask

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.room) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.skie) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.kover)
}

dependencies {
    subprojects.forEach { subproject ->
        if (subproject.buildFile.exists()) {
            kover(subproject)
        }
    }
}

val detektReportMerge by tasks.registering(ReportMergeTask::class) {
    output.set(rootProject.layout.buildDirectory.file("reports/detekt/merged.sarif"))
}

subprojects {
    plugins.withId("io.gitlab.arturbosch.detekt") {
        tasks.withType<Detekt>().configureEach {
            reports.sarif.required.set(true)
            finalizedBy(detektReportMerge)
        }
        detektReportMerge.configure {
            input.from(tasks.withType<Detekt>().map { it.sarifReportFile })
        }
    }
}
