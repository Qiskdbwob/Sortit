# ---- Sortit ProGuard Rules ----

# Room: keep DAO methods + entities
-keep class com.sortit.data.** { *; }
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao class *
-keep @androidx.room.Database class *

# Coil: keep video frame decoder
-keep class coil.** { *; }
-dontwarn coil.**

# Compose: keep Compose stability
-dontwarn androidx.compose.**
-keep class androidx.compose.** { *; }

# Kotlin coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# Keep FileOps interface (used via reflection in tests)
-keep class com.sortit.repo.FileOps { *; }
-keep class com.sortit.repo.RealFileOps { *; }

# DocumentFile
-keep class androidx.documentfile.** { *; }
