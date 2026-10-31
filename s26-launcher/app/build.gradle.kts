// Build para Android Studio. Sin dependencias AndroidX: solo framework + kotlin-stdlib.
// (Los APK publicados se generaron con tools/build.sh; ver docs/COMPILAR.md.)
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.s26home.launcher"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.s26home.launcher"
        minSdk = 31
        targetSdk = 36
        versionCode = 2
        versionName = "1.0.1"
    }
    signingConfigs {
        create("release") {
            storeFile = System.getenv("RELEASE_KS")?.let { file(it) }
            storePassword = System.getenv("RELEASE_KS_PASS")
            keyAlias = System.getenv("RELEASE_ALIAS") ?: "inicios26"
            keyPassword = System.getenv("RELEASE_KS_PASS")
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "../tools/proguard.pro")
            if (System.getenv("RELEASE_KS") != null) signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
