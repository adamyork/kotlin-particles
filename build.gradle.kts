@file:Suppress("UnstableApiUsage")

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.compose)
    alias(libs.plugins.compose.compiler)
}


group = "com.github.adamyork"
version = "0.0.1"

repositories {
    google()
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
}

kotlin {
    android {
        namespace = "com.github.adamyork.kparticles.core"
        compileSdk = 36
        minSdk = 24
    }

    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs {
        binaries.executable()
        browser {
            commonWebpackConfig {
                devServer = (devServer
                    ?: org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig.DevServer()).apply {
                    static(
                        project.layout.buildDirectory.dir("dist/wasmJs/productionExecutable").get().asFile.absolutePath
                    )
                }
            }
        }
    }

    sourceSets {
        getByName("commonMain").dependencies {
            implementation(libs.kotlin.inject.runtime)
            implementation(libs.cache4k)
            implementation(libs.kotlin.logging)
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.ui)
            implementation(libs.compose.material3)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.material.icons.core)
            implementation(libs.compose.material.icons.extended)
            implementation(libs.ktor.client.core)
            implementation(libs.kaml)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.lifecycle.viewmodel)
            implementation(libs.lifecycle.viewmodel.compose)
        }

        getByName("androidMain").dependencies {
            implementation(libs.ktor.client.okhttp)
            implementation(libs.kotlinx.coroutines.android)
        }

        getByName("wasmJsMain").dependencies {
            implementation(libs.ktor.client.js)
            implementation(libs.kotlinx.browser)
        }
    }
}

compose.resources {
    publicResClass = true
}

val prepareDevServer = tasks.register<Copy>("prepareDevServer") {
    description = "Prepares static assets and Compose resources for the dev server."
    from(project.file("src/wasmJsMain/web"))
    from(project.file("src/commonMain/composeResources/files"))
    from(tasks.named("wasmJsProcessResources"))
    into(layout.buildDirectory.dir("dist/wasmJs/productionExecutable"))
}

tasks.named("prepareDevServer").configure {
    dependsOn("wasmJsProcessResources")
}

tasks.named("wasmJsBrowserDevelopmentRun").configure {
    dependsOn(prepareDevServer)
}

dependencies {
    add("kspWasmJs", libs.kotlin.inject.compiler)
}
