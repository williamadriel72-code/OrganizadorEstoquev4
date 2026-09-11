plugins {
    id("com.android.application")
}

android {
    namespace = "com.boramichael.ifoodtest"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.boramichael.ifoodtest"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0-test"
    }

    sourceSets["main"].apply {
        manifest.srcFile("src/testapp/AndroidManifest.xml")
        java.setSrcDirs(listOf("src/testapp/java"))
        res.setSrcDirs(listOf("src/testapp/res"))
    }

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
}
