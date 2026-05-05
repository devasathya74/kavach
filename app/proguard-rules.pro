# Retrofit / Moshi
-keep class retrofit2.** { *; }
-keep class com.squareup.moshi.** { *; }

# Room
-keep class androidx.room.** { *; }

# ExoPlayer / Media3
-keep class androidx.media3.** { *; }

# Kavach Models & Config
-keep class com.kavach.app.data.remote.models.** { *; }
-keep class com.kavach.app.KavachConfig { *; }
-keep class com.kavach.app.util.SecurityUtils { *; }

# Hilt
-keep class dagger.hilt.** { *; }
-dontwarn dagger.hilt.**

# Kotlin
-dontwarn kotlin.Unit
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}
