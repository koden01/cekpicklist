plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("kotlin-kapt")
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
        versionCode = 46
        versionName = "5.1.31"

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
        dataBinding = true
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
    
    // Room
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")
    
    // WorkManager untuk background sync
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    
    // RFID SDK
    implementation(files("libs/DeviceAPI_ver20250209_release.aar"))
    
    // Additional JAR files from demo - REMOVED (files not found in libs folder)
    // If you need these libraries, either:
    // 1. Add the JAR files to app/libs/ folder, or
    // 2. Use Maven dependencies instead
    // implementation(files("libs/jxl.jar"))
    // implementation(files("libs/poi-3.12-android-a.jar"))
    // implementation(files("libs/poi-ooxml-schemas-3.12-20150511-a.jar"))
    // implementation(files("libs/xUtils-2.5.5.jar"))
    
    // Supabase Realtime (WebSocket) - minimal client
    // Supabase-kt v3 via BOM + modules (compatible with io.github.jan.supabase.* packages)
    implementation(platform("io.github.jan-tennert.supabase:bom:3.0.1"))
    // Realtime removed
    implementation("io.github.jan-tennert.supabase:postgrest-kt")
    implementation("io.github.jan-tennert.supabase:auth-kt")
    // Ktor HTTP client (gunakan BOM agar versi konsisten dengan engine & plugin)
    implementation(platform("io.ktor:ktor-bom:2.3.8"))
    implementation("io.ktor:ktor-client-core")
    // Gunakan engine Android agar dependensi plugin timeout terikut pasti
    implementation("io.ktor:ktor-client-android")
    implementation("io.ktor:ktor-client-logging")
    implementation("io.ktor:ktor-client-content-negotiation")
    implementation("io.ktor:ktor-serialization-kotlinx-json")
    implementation("io.ktor:ktor-client-encoding")
    // Optional: SLF4J binding to silence warnings on Android
    implementation("org.slf4j:slf4j-android:1.7.36")

    // Built-in Barcode Scanning (Hardware Scanner) - Using local AAR
    // implementation("com.rscja.deviceapi:deviceapi:1.0.0") // Not available in Maven
    
    // Camera Scanning removed - Hardware scanner only
    // implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    // implementation("com.google.zxing:core:3.5.2")
}














































