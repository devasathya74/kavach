plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.kavach.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.kavach.app"
        minSdk = 29          // Android 10+
        targetSdk = 35

        // ✏️ EDIT: Match with KavachConfig.APP_VERSION_CODE
        versionCode = 1

        // ✏️ EDIT: Match with KavachConfig.APP_VERSION
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // ✏️ EDIT: Production BASE_URL — must match KavachConfig.BASE_URL
        // Format: "https://YOUR_SERVER_DOMAIN.com/api/"
        buildConfigField("String", "BASE_URL", "\"https://YOUR_SERVER_DOMAIN.com/api/\"")
    }

    signingConfigs {
        create("release") {
            storeFile = file("kavach-release.jks")
            storePassword = "kavach123"
            keyAlias = "kavach-alias"
            keyPassword = "kavach123"
        }
    }

    buildTypes {
        debug {
            isDebuggable = true
            buildConfigField("String", "BASE_URL", "\"http://192.168.1.6:8000/api/v1/\"")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
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

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.material)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.lifecycle.viewmodel.compose)

    // Navigation
    implementation(libs.navigation.compose)

    // Hilt DI
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    // Retrofit + Moshi
    implementation(libs.retrofit)
    implementation(libs.retrofit.moshi)
    implementation(libs.okhttp.logging)
    implementation(libs.moshi.kotlin)
    ksp(libs.moshi.codegen)

    // Room DB
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // ExoPlayer
    implementation(libs.exoplayer)
    implementation(libs.exoplayer.ui)
    implementation(libs.exoplayer.dash)

    // Coil (image loading)
    implementation(libs.coil.compose)

    // DataStore
    implementation(libs.datastore.preferences)

    // WorkManager
    implementation(libs.work.runtime.ktx)

    // Splash Screen
    implementation(libs.splashscreen)

    // Firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)

    // Play Integrity API — Device Attestation
    implementation("com.google.android.play:integrity:1.4.0")

    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
