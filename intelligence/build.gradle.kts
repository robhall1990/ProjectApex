plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Pure Kotlin/JVM by design (docs/RaceIntelligencePlatform.md §7): no Android
// plugin, so an accidental android.* import cannot compile. The same artifact
// runs on-device, in JVM tests, or server-side.

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
    testImplementation(libs.junit)
}
