plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.organizador.scanner"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.boramichael.hihi"
        minSdk = 26
        targetSdk = 36
        versionCode = 26
        versionName = "2.5.0"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.06.00")
    implementation(composeBom)
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.ui:ui")
}
