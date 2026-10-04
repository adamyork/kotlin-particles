plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.ksp)
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.github.adamyork.kparticles"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.github.adamyork.kparticles"
        minSdk = 24
        //noinspection OldTargetApi
        targetSdk = 36
        versionCode = 1
        versionName = "0.0.1"
    }
}

dependencies {
    implementation(project(":"))
    implementation(libs.activity.compose)
    implementation(libs.kotlin.inject.runtime)
    implementation(libs.kotlin.logging)
    implementation(libs.compose.runtime)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    ksp(libs.kotlin.inject.compiler)
}
