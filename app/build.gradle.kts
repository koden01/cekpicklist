plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    // id("kotlin-kapt") // Disabled sementara untuk Room Database
}

android {
    namespace = "com.example.cekpicklist"
    compileSdk = 35

    signingConfigs {
        create("release") {
            // Aktifkan signing release
            storeFile = file("../cekpicklist-release-key.keystore")
            storePassword = "CekPicklist#2025"
            keyAlias = "cekpicklist"
            keyPassword = "CekPicklist#2025"
        }
    }

    defaultConfig {
        applicationId = "com.example.cekpicklist"
        minSdk = 30
        targetSdk = 35
        versionCode = 4
        versionName = "1.0.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        // Optimasi untuk mengurangi duplicate classes dan meningkatkan performa
        multiDexEnabled = true
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            isShrinkResources = false
            isDebuggable = true
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            // Gunakan signing config release
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Optimasi untuk mengurangi duplicate classes
            packaging {
                resources {
                    excludes += "/META-INF/{AL2.0,LGPL2.1}"
                    excludes += "/META-INF/DEPENDENCIES"
                    excludes += "/META-INF/LICENSE"
                    excludes += "/META-INF/LICENSE.txt"
                    excludes += "/META-INF/license.txt"
                    excludes += "/META-INF/NOTICE"
                    excludes += "/META-INF/NOTICE.txt"
                    excludes += "/META-INF/notice.txt"
                    excludes += "/META-INF/ASL2.0"
                    excludes += "/META-INF/*.kotlin_module"
                }
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    // Core Android
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    
    // Multidex untuk mengatasi masalah classloader
    implementation("androidx.multidex:multidex:2.0.1")
    
    // ViewModel & LiveData
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.7.0")
    
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    
    // UI Components
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    
    // JSON Parsing
    implementation("com.google.code.gson:gson:2.10.1")
    
    // Media3 ExoPlayer untuk video splash screen
    implementation("androidx.media3:media3-exoplayer:1.2.0")
    implementation("androidx.media3:media3-ui:1.2.0")
    implementation("androidx.media3:media3-common:1.2.0")
    
    // Room Database untuk cache persistence - DISABLED sementara
    // implementation("androidx.room:room-runtime:2.6.1")
    // implementation("androidx.room:room-ktx:2.6.1")
    
    // RFID SDK
    implementation(files("libs/DeviceAPI_ver20250209_release.aar"))
}
