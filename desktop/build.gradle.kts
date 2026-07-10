import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

// Secondary target (docs/Roadmap.md Phase "desktop"): the same :core engine
// (RaceEngine, OpenF1LiveDataSource, the intelligence adapter) that :app
// drives on Android, wrapped in a small Compose Desktop window instead of
// the mobile UI. Deliberately its own simple screens rather than a port of
// feature/race/feature/settings - no Hilt (manual composition root in
// AppContainer.kt instead), no Android resources, no navigation shell.

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":intelligence"))

    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.foundation)
    implementation(compose.runtime)

    implementation(libs.kotlinx.coroutines.swing)

    testImplementation(libs.junit)
}

compose.desktop {
    application {
        mainClass = "com.projectapex.desktop.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Dmg, TargetFormat.Deb)
            packageName = "ProjectApexDesktop"
            packageVersion = "0.1.0"
        }
    }
}
