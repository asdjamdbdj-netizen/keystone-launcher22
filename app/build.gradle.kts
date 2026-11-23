plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.keystone.launcher"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.keystone.launcher"
        // Android 8.0+ : eski ve yeni Qin F22 yazilimlarinin hepsini kapsar.
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}
// Bilerek hicbir androidx / Compose bagimliligi yok: kucuk APK, dusuk RAM.
