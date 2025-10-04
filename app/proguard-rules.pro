# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# ===========================================
# OPTIMASI UNTUK MENGATASI MASALAH LOGCAT
# ===========================================

# Keep line number information untuk debugging
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ===========================================
# SUPPRESS HIDDEN API WARNINGS
# ===========================================

# Suppress hidden API access warnings
-dontwarn android.app.LoadedApk
-dontwarn android.app.ApplicationLoaders
-dontwarn dalvik.system.DexPathList
-dontwarn dalvik.system.DexFile
-dontwarn java.lang.Thread
-dontwarn java.lang.ThreadGroup
-dontwarn dalvik.system.VMRuntime
-dontwarn libcore.io.Libcore
-dontwarn libcore.io.Os
-dontwarn android.system.StructStat
-dontwarn android.content.pm.SharedLibraryInfo
-dontwarn android.app.AppComponentFactory
-dontwarn android.app.ActivityThread
-dontwarn android.content.pm.IPackageManager
-dontwarn android.app.IActivityManager
-dontwarn android.app.IServiceConnection
-dontwarn android.content.IIntentReceiver
-dontwarn android.app.LoadedApk$ReceiverDispatcher
-dontwarn android.app.LoadedApk$ServiceDispatcher
-dontwarn android.app.LoadedApk$SplitDependencyLoaderImpl
-dontwarn android.view.DisplayAdjustments
-dontwarn android.content.res.CompatibilityInfo
-dontwarn android.content.res.AssetManager
-dontwarn android.util.Slog
-dontwarn android.os.StrictMode
-dontwarn android.app.DexLoadReporter
-dontwarn android.app.Instrumentation
-dontwarn android.content.pm.PackageManager
-dontwarn android.content.pm.ApplicationInfo
-dontwarn android.content.pm.PackageInfo
-dontwarn android.content.pm.SharedLibraryInfo
-dontwarn android.content.pm.IPackageManager
-dontwarn android.app.IActivityManager
-dontwarn android.app.IServiceConnection
-dontwarn android.content.IIntentReceiver
-dontwarn android.app.LoadedApk$ReceiverDispatcher
-dontwarn android.app.LoadedApk$ServiceDispatcher
-dontwarn android.app.LoadedApk$SplitDependencyLoaderImpl
-dontwarn android.view.DisplayAdjustments
-dontwarn android.content.res.CompatibilityInfo
-dontwarn android.content.res.AssetManager
-dontwarn android.util.Slog
-dontwarn android.os.StrictMode
-dontwarn android.app.DexLoadReporter
-dontwarn android.app.Instrumentation
-dontwarn android.content.pm.PackageManager
-dontwarn android.content.pm.ApplicationInfo
-dontwarn android.content.pm.PackageInfo

# Keep semua class yang digunakan oleh aplikasi
-keep class com.example.cekpicklist.** { *; }

# Keep RFID SDK classes
-keep class com.rfid.** { *; }
-keep class com.rscja.** { *; }

# Keep SystemProperties untuk RFID SDK
-keep class android.os.SystemProperties { *; }
-dontwarn android.os.SystemProperties

# Keep Supabase classes
-keep class io.github.jan.tennert.supabase.** { *; }

# Keep Retrofit dan OkHttp
-keep class retrofit2.** { *; }
-keep class okhttp3.** { *; }

# Keep Gson
-keep class com.google.gson.** { *; }

# Keep Kotlin serialization
-keep class kotlinx.serialization.** { *; }

# Keep ViewModel dan LiveData
-keep class androidx.lifecycle.** { *; }

# Keep RecyclerView dan adapter
-keep class androidx.recyclerview.** { *; }

# Optimasi untuk mengurangi duplicate classes
-dontwarn com.rfid.**
-dontwarn io.github.jan.tennert.supabase.**
-dontwarn retrofit2.**
-dontwarn okhttp3.**

# Remove logging untuk release build
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int i(...);
    public static int w(...);
    public static int d(...);
    public static int e(...);
}

# Optimasi untuk mengurangi ukuran APK
-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*
-optimizationpasses 5
-allowaccessmodification
-dontpreverify

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep enums
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Keep Parcelable
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# Keep Serializable
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}