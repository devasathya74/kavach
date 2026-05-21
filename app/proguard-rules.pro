# Retrofit / Moshi
-keep class retrofit2.** { *; }
-keep class com.squareup.moshi.** { *; }
-keepattributes Signature, *Annotation*, InnerClasses, EnclosingMethod

# Room
-keep class androidx.room.** { *; }

# ExoPlayer / Media3
-keep class androidx.media3.** { *; }

# Kavach Models & DTOs (CRITICAL: Prevents runtime JSON parsing crashes)
-keep class com.kavach.app.data.remote.dto.** { *; }
-keepclassmembers class com.kavach.app.data.remote.dto.** { *; }
-keepattributes Signature, *Annotation*, InnerClasses, EnclosingMethod
-keep class com.kavach.app.KavachConfig { *; }
-keep class com.kavach.app.util.SecurityUtils { *; }

# Hilt
-keep class dagger.hilt.** { *; }
-dontwarn dagger.hilt.**

# Kotlin Serialization (Just in case)
-keep class kotlinx.serialization.** { *; }
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

# Preserve Moshi's generated adapters
-keep class *JsonAdapter { *; }
-keep class com.squareup.moshi.Json { *; }
