plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.devbyeai.macuisine"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.devbyeai.macuisine"
        minSdk = 23
        targetSdk = 27
        versionCode = 1
        versionName = "1.0"
        ndk { abiFilters += listOf("armeabi-v7a") }
    }
    buildTypes {
        getByName("release") { isMinifyEnabled = false }
        getByName("debug") { isMinifyEnabled = false }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    packaging { jniLibs { useLegacyPackaging = true } }
}
dependencies { }
