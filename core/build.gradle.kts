plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

// Pure Kotlin/JVM by design, mirroring :intelligence (docs/RaceIntelligencePlatform.md
// §7): domain model, RaceEngine/RaceTimeline, the OpenF1 data source, and the
// intelligence adapter all live here with no Android plugin, so an accidental
// android.* import cannot compile. This is what makes :app (Android) and
// :desktop (Compose Multiplatform) able to share one implementation instead
// of two.

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
    implementation(project(":intelligence"))

    api(libs.kotlinx.coroutines.core)
    api(libs.kotlinx.serialization.json)
    api(libs.retrofit.core)
    api(libs.retrofit.converter.kotlinx.serialization)
    api(libs.okhttp.core)
    api(libs.okhttp.logging.interceptor)
    implementation(libs.javax.inject)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
