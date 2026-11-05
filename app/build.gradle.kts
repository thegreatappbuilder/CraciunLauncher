plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "ro.craciun.launcher"
    compileSdk = 35

    defaultConfig {
        applicationId = "ro.craciun.launcher"
        minSdk = 24          // Android 7.0+ (practic toate telefoanele active)
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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

// Fără biblioteci externe: totul (grafică, efecte, interfață) e desenat cu API-ul Android.
dependencies {
}
