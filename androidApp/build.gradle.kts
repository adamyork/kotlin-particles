plugins {
    alias(libs.plugins.android.application)
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
}
