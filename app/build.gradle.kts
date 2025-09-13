plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.example.cekpicklist"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.cekpicklist"
        minSdk = 30
        targetSdk = 35
        versionCode = 3
        versionName = "1.0.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        // Optimasi untuk mengurangi duplicate classes dan meningkatkan performa
        multiDexEnabled = true
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            isDebuggable = true
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
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
    
    // Networking
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    
    // UI Components
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    
    // RFID SDK
    implementation(files("libs/DeviceAPI_ver20250209_release.aar"))
    
    // Supabase-KT Dependencies
    implementation("io.github.jan-tennert.supabase:supabase-kt:2.4.0")
    implementation("io.github.jan-tennert.supabase:realtime-kt:2.4.0")
    implementation("io.github.jan-tennert.supabase:postgrest-kt:2.4.0")
    implementation("io.github.jan-tennert.supabase:gotrue-kt:2.4.0")
    
    // Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")
    
    // HTTP Client Engine untuk Supabase-KT (Android)
    implementation("io.ktor:ktor-client-android:2.3.7")
    implementation("io.ktor:ktor-client-core:2.3.7")
    implementation("io.ktor:ktor-client-cio:2.3.7")
    implementation("io.ktor:ktor-client-logging:2.3.7")
    implementation("io.ktor:ktor-client-content-negotiation:2.3.7")
    implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.7")
    
    // Networking (untuk implementasi Supabase manual)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.10.1")
}