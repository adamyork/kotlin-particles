import java.time.Duration

plugins {
    id("com.android.kotlin.multiplatform.library")
    id("org.jetbrains.kotlin.multiplatform")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}


group = "com.github.adamyork"
version = "0.0.1"

repositories {
    mavenLocal()
    google()
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
}

val useLocalLibrary = providers.gradleProperty("useLocalLibrary").map { it == "true" }.getOrElse(false)

val kotlinParticlesDependency: Any = if (useLocalLibrary) {
    project(":")
} else {
    "com.github.adamyork:kotlin-particles:${providers.gradleProperty("kotlinParticlesVersion").getOrElse("0.0.2")}"
}

kotlin {
    android {
        namespace = "com.github.adamyork.kparticles.harness"
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
            api(kotlinParticlesDependency)
            implementation(libs.kotlin.inject.runtime)
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
        }

        getByName("androidMain").dependencies {
            implementation(libs.ktor.client.okhttp)
            implementation(libs.kotlinx.coroutines.android)
        }

        getByName("androidHostTest").dependencies {
            implementation(kotlin("test"))
            implementation(libs.junit)
        }

        getByName("wasmJsMain").dependencies {
            implementation(libs.ktor.client.js)
            implementation(libs.kotlinx.browser)
        }
    }
}

compose.resources {
    publicResClass = true
    packageOfResClass = "com.github.adamyork.kotlin_particles.generated.resources"
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

val benchmarkFile = layout.projectDirectory.file("src/androidHostTest/benchmarks/physics.benchmarks")

val updateBenchmarksRequested = gradle.startParameter.taskNames.any { it.substringAfterLast(':') == "updateBenchmarks" }

val skipSimulationTests = providers.gradleProperty("skipSimulationTests").map { it == "true" }.getOrElse(false)

tasks.withType<Test>().configureEach {
    timeout.set(Duration.ofMinutes(3))
    systemProperty("kparticles.benchmarkFile", benchmarkFile.asFile.absolutePath)
    systemProperty("kparticles.updateBenchmarks", updateBenchmarksRequested.toString())
    testLogging {
        showStandardStreams = true
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
    inputs.files(benchmarkFile)
    if (updateBenchmarksRequested) {
        outputs.upToDateWhen { false }
    }
}

tasks.matching { task ->
    task.name.startsWith("wasmJsBrowser") &&
        (task.name.endsWith("Run") || task.name.endsWith("Webpack") || task.name.endsWith("Distribution"))
}.configureEach {
    if (!skipSimulationTests) {
        dependsOn("testAndroidHostTest")
    }
}

tasks.register("updateBenchmarks") {
    group = "verification"
    description = "Runs the simulation tests, reports drift from the stored benchmarks, then rewrites them."
    dependsOn("testAndroidHostTest")
}

dependencies {
    add("kspWasmJs", libs.kotlin.inject.compiler)
}
