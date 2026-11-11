plugins {
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.android.application) apply false
    `maven-publish`
}

group = "com.github.adamyork"
version = "0.0.4"

kotlin {
    explicitApi()

    android {
        namespace = "com.github.adamyork.particles"
        compileSdk = 37
        minSdk = 24
        androidResources {
            enable = true
        }
        withHostTest {
            isReturnDefaultValues = true
        }
    }

    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }

    sourceSets {
        getByName("commonMain").dependencies {
            implementation(libs.kotlin.logging)
            implementation(libs.compose.runtime)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
        }

        getByName("androidMain").dependencies {
            implementation(libs.androidx.core.ktx)
            api(libs.kotlinx.coroutines.core)
        }

        getByName("androidHostTest").dependencies {
            implementation(kotlin("test"))
            implementation(libs.junit)
        }

        getByName("wasmJsMain").dependencies {
            implementation(libs.kotlinx.browser)
            implementation(libs.kotlinx.coroutines.core)
        }
    }
}

compose.resources {
    publicResClass = true
    packageOfResClass = "com.github.adamyork.particles.resources"
}

tasks.matching { it.name == "checkComposeUiTestConfigurationForWasmJs" }.configureEach {
    enabled = false
}
