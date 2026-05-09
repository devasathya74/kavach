package com.kavach.app.di

import android.content.Context
import androidx.room.Room
import com.kavach.app.BuildConfig
import com.kavach.app.data.local.SessionDataStore
import com.kavach.app.data.local.dao.*
import com.kavach.app.data.local.db.KavachDatabase
import com.kavach.app.data.remote.api.AuthRefreshApiService
import com.kavach.app.data.remote.api.KavachApiService
import com.kavach.app.data.remote.api.KavachApiV2
import com.kavach.app.data.remote.api.TokenAuthenticator
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    // ── Moshi ─────────────────────────────────────────────

    @Provides
    @Singleton
    fun provideMoshi(): Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    // ── OkHttp #1: Auth-intercepted client (for all normal API calls) ──
    //
    // Risk 7 FIX: Certificate Pinning hardened.
    //
    // Why HMAC alone is not enough (per Risk 7):
    //   HMAC signs requests. But if an attacker installs a user CA and MITM the connection,
    //   they can:
    //     1. See the HMAC algorithm and key rotation timing
    //     2. Study replay window timing (when to capture valid requests)
    //     3. Learn request metadata and endpoint structure
    //   Certificate pinning prevents the MITM itself. HMAC is useless without it.
    //
    // Risk 6 NOTE — SessionDataStore = High-Value Target:
    //   The refresh token, device secret, and integrity level stored in DataStore are NOT
    //   secret against a root-privileged attacker. They can dump DataStore, hook crypto APIs,
    //   and extract these values.
    //
    //   Correct design assumption: CLIENT SECRECY IS TEMPORARY.
    //   Real mitigations:
    //     1. Short trust windows (STRONG=30m, DEVICE=10m) force frequent re-attestation
    //     2. Server-side verification of Play Integrity verdict (unbypassable by client)
    //     3. Behavior analysis on backend detects anomalous session usage
    //     4. Certificate pinning makes MITM impractical even with rooted device
    //
    //   A rooted device that can dump DataStore will also FAIL Play Integrity checks
    //   (MEETS_STRONG_INTEGRITY requires hardware-backed keystore = no root).
    //   So the threat model is: dump DataStore + pass integrity check = contradictory.

    @Provides
    @Singleton
    @Named("authed")
    fun provideOkHttpClient(
        @ApplicationContext context: Context,
        sessionDataStore   : SessionDataStore,
        tokenAuthenticator : TokenAuthenticator
    ): OkHttpClient {
        // Risk 7 FIX: Hardened certificate pinning (with Pilot Bypass for placeholder)
        val certificatePinner = okhttp3.CertificatePinner.Builder().apply {
            if (!com.kavach.app.KavachConfig.PILOT_MODE) {
                val primary = com.kavach.app.KavachConfig.SSL_PIN_PRIMARY
                val backup  = com.kavach.app.KavachConfig.SSL_PIN_BACKUP
                
                if (!primary.contains("REPLACE_WITH_YOUR_PRIMARY_CERT_HASH")) {
                    add(com.kavach.app.KavachConfig.PINNED_DOMAIN, primary)
                }
                if (!backup.contains("REPLACE_WITH_YOUR_BACKUP_CERT_HASH")) {
                    add(com.kavach.app.KavachConfig.PINNED_DOMAIN, backup)
                }
            }
        }.build()

        // Risk 7 FIX: Restrict to TLS 1.2+ and strong cipher suites only
        // Prevents downgrade attacks where attacker forces TLS 1.0/1.1 connection
        val tlsSpec = okhttp3.ConnectionSpec.Builder(okhttp3.ConnectionSpec.MODERN_TLS)
            .tlsVersions(okhttp3.TlsVersion.TLS_1_3, okhttp3.TlsVersion.TLS_1_2)
            .cipherSuites(
                okhttp3.CipherSuite.TLS_AES_128_GCM_SHA256,
                okhttp3.CipherSuite.TLS_AES_256_GCM_SHA384,
                okhttp3.CipherSuite.TLS_CHACHA20_POLY1305_SHA256,
                okhttp3.CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256,
                okhttp3.CipherSuite.TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256,
                okhttp3.CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384,
                okhttp3.CipherSuite.TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384
            )
            .build()

        val authInterceptor = Interceptor { chain ->

            val request = chain.request()
            val urlPath = request.url.encodedPath
            
            // Fail-Secure Logic:
            // 1. Define exact auth bypass paths (ONLY absolute minimum)
            val isAuthRoute = urlPath == "/api/v1/login/" ||
                             urlPath == "/api/v1/verify-otp/" ||
                             urlPath == "/api/v1/otp/"

            // OTA / startup endpoints — AllowAny on backend, must also bypass here
            val isStartupRoute = urlPath == "/api/v1/app-version/" ||
                                 urlPath == "/api/v1/app-update/log/" ||
                                 urlPath.startsWith("/api/v1/health")

            val isProfileSync = urlPath == "/api/v1/profile/" || urlPath == "/api/v2/profile/"
            val isV2Path      = urlPath.startsWith("/api/v2/")

            // 3. Early exit — no session reads needed for these routes
            if (isAuthRoute || isStartupRoute) {
                return@Interceptor chain.proceed(request)
            }

            // 2. Fetch session states (only for routes that need auth)
            val deviceSecret = runBlocking { sessionDataStore.deviceSecret.first() }
            val isVerified   = runBlocking { sessionDataStore.isVerifiedInThisSession.first() }

            // CRITICAL: Block all non-essential traffic if not verified in THIS session
            // This prevents "Yesterday's Truth" from making API calls.
            // isStartupRoute already returned early above — this guard is for remaining routes.
            if (!isVerified && !isProfileSync) {
                return@Interceptor okhttp3.Response.Builder()
                    .request(request)
                    .protocol(okhttp3.Protocol.HTTP_1_1)
                    .code(403)
                    .message("KAVACH_SESSION_UNVERIFIED")
                    .body(okhttp3.ResponseBody.create(null, "Session not verified. Access restricted to profile sync only."))
                    .build()
            }

            // If not an auth route, we MUST have a secret. No excuses.
            if (deviceSecret.isNullOrEmpty()) {
                return@Interceptor okhttp3.Response.Builder()
                    .request(request)
                    .protocol(okhttp3.Protocol.HTTP_1_1)
                    .code(401)
                    .message("KAVACH_INTEGRITY_MISSING_SECRET")
                    .body(okhttp3.ResponseBody.create(null, "Missing security context. Re-authentication required."))
                    .build()
            }

            val token = runBlocking { sessionDataStore.token.first() }
            val timestamp = (System.currentTimeMillis() / 1000).toString()
            val nonce = java.util.UUID.randomUUID().toString()
            val method = request.method
            
            // Hardened: HMAC Signing (Using Dynamic Device Secret)
            val dataToSign = "$nonce$timestamp$method$urlPath"
            val hmac = Mac.getInstance("HmacSHA256")
            val secretKey = SecretKeySpec(deviceSecret.toByteArray(), "HmacSHA256")
            hmac.init(secretKey)
            val signature = hmac.doFinal(dataToSign.toByteArray()).joinToString("") { "%02x".format(it) }

            val requestBuilder = request.newBuilder()
                .addHeader("X-Timestamp", timestamp)
                .addHeader("X-Nonce", nonce)
                .addHeader("X-Device-Id", com.kavach.app.utils.DeviceIdUtil.getDeviceId(context))
                .addHeader("X-Kavach-Signature", signature)
                // FIX 7: Build type header — production backend rejects debug builds
                .addHeader("X-Build-Type", com.kavach.app.BuildConfig.BUILD_TYPE)
                .addHeader("X-App-Version", com.kavach.app.BuildConfig.VERSION_NAME)
                .addHeader("X-Device-Manufacturer", android.os.Build.MANUFACTURER)
                .addHeader("X-Device-Model", android.os.Build.MODEL)
                .addHeader("X-Android-Version", android.os.Build.VERSION.SDK_INT.toString())
                
            // ADDED: Explicit Secret Header for diagnostic tracking
            deviceSecret?.takeIf { it.isNotBlank() }?.let {
                requestBuilder.addHeader("X-Device-Secret", it)
            }

            // Integrity headers — backend uses to enforce trust window
            val integrityLevel = runBlocking { sessionDataStore.integrityLevel.first() }
            val lastAttestedAt = runBlocking { sessionDataStore.lastAttestedAt.first() }
            if (integrityLevel.isNotBlank()) {
                requestBuilder.addHeader("X-Integrity-Level", integrityLevel)
                requestBuilder.addHeader("X-Attested-At", lastAttestedAt.toString())
            }

            if (!token.isNullOrBlank()) {
                requestBuilder.addHeader("Authorization", "Bearer $token")
            }
            chain.proceed(requestBuilder.build())
        }

        return OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .authenticator(tokenAuthenticator)
            .certificatePinner(certificatePinner)
            .connectionSpecs(listOf(tlsSpec))  // Risk 7: TLS 1.2+ only, strong ciphers
            .apply {
                if (BuildConfig.DEBUG) {
                    addInterceptor(
                        HttpLoggingInterceptor().apply {
                            level = HttpLoggingInterceptor.Level.BODY
                        }
                    )
                }
            }
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    // ── OkHttp #2: Plain client (for token refresh — no auth interceptor) ──
    //
    // Risk 7 FIX: Plain client ALSO gets certificate pinning.
    // Token refresh without TLS pinning is a MITM vector:
    //   Attacker intercepts refresh response → injects fake tokens → session taken.
    // The refresh endpoint is as sensitive as any authenticated endpoint.

    @Provides
    @Singleton
    @Named("plain")
    fun providePlainOkHttpClient(
        @Named("signature") sigInterceptor: Interceptor
    ): OkHttpClient {
        // Reuse the same pinning config as the authed client (with Pilot Bypass)
        val refreshPinner = okhttp3.CertificatePinner.Builder().apply {
            if (!com.kavach.app.KavachConfig.PILOT_MODE) {
                val primary = com.kavach.app.KavachConfig.SSL_PIN_PRIMARY
                val backup  = com.kavach.app.KavachConfig.SSL_PIN_BACKUP
                
                if (!primary.contains("REPLACE_WITH_YOUR_PRIMARY_CERT_HASH")) {
                    add(com.kavach.app.KavachConfig.PINNED_DOMAIN, primary)
                }
                if (!backup.contains("REPLACE_WITH_YOUR_BACKUP_CERT_HASH")) {
                    add(com.kavach.app.KavachConfig.PINNED_DOMAIN, backup)
                }
            }
        }.build()

        val refreshTlsSpec = okhttp3.ConnectionSpec.Builder(okhttp3.ConnectionSpec.MODERN_TLS)
            .tlsVersions(okhttp3.TlsVersion.TLS_1_3, okhttp3.TlsVersion.TLS_1_2)
            .build()

        return OkHttpClient.Builder()
            .addInterceptor(sigInterceptor)
            .certificatePinner(refreshPinner)
            .connectionSpecs(listOf(refreshTlsSpec))
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    @Named("signature")
    fun provideSignatureInterceptor(@ApplicationContext context: Context): Interceptor = Interceptor { chain ->
        val request = chain.request()
        val timestamp = (System.currentTimeMillis() / 1000).toString()
        val nonce = java.util.UUID.randomUUID().toString()
        val method = request.method
        val path = request.url.encodedPath
        
        val secret = "CHANGE_ME_BEFORE_DEPLOY" 
        if (secret.isEmpty()) return@Interceptor chain.proceed(request)
        
        val dataToSign = "$nonce$timestamp$method$path"
        val hmac = Mac.getInstance("HmacSHA256")
        val secretKey = SecretKeySpec(secret.toByteArray(), "HmacSHA256")
        hmac.init(secretKey)
        val signature = hmac.doFinal(dataToSign.toByteArray()).joinToString("") { "%02x".format(it) }

        val requestBuilder = request.newBuilder()
            .addHeader("X-Timestamp", timestamp)
            .addHeader("X-Nonce", nonce)
            .addHeader("X-Device-Id", com.kavach.app.utils.DeviceIdUtil.getDeviceId(context))
            .addHeader("X-Kavach-Signature", signature)
            .addHeader("X-Build-Type", com.kavach.app.BuildConfig.BUILD_TYPE)
            .addHeader("X-App-Version", com.kavach.app.BuildConfig.VERSION_NAME)
            .addHeader("X-Device-Manufacturer", android.os.Build.MANUFACTURER)
            .addHeader("X-Device-Model", android.os.Build.MODEL)
            .addHeader("X-Android-Version", android.os.Build.VERSION.SDK_INT.toString())

        chain.proceed(requestBuilder.build())
    }

    // ── Retrofit (authed) ─────────────────────────────────

    @Provides
    @Singleton
    fun provideRetrofit(@Named("authed") okHttpClient: OkHttpClient, moshi: Moshi): Retrofit =
        Retrofit.Builder()
            .baseUrl(BuildConfig.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()

    @Provides
    @Singleton
    fun provideApiService(retrofit: Retrofit): KavachApiService =
        retrofit.create(KavachApiService::class.java)

    @Provides
    @Singleton
    fun provideApiV2Service(retrofit: Retrofit): KavachApiV2 =
        retrofit.create(KavachApiV2::class.java)

    // ── Retrofit (plain — for token refresh) ─────────────

    @Provides
    @Singleton
    fun provideAuthRefreshService(
        @Named("plain") okHttpClient: OkHttpClient,
        moshi: Moshi
    ): AuthRefreshApiService =
        Retrofit.Builder()
            .baseUrl(BuildConfig.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(AuthRefreshApiService::class.java)

    // ── Room DB ───────────────────────────────────────────

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): KavachDatabase =
        Room.databaseBuilder(
            context,
            KavachDatabase::class.java,
            "kavach.db"
        ).build()

    @Provides fun provideTrainingDao(db: KavachDatabase): TrainingDao             = db.trainingDao()
    @Provides fun provideQuizDao(db: KavachDatabase): QuizDao                     = db.quizDao()
    @Provides fun provideOrderDao(db: KavachDatabase): OrderDao                   = db.orderDao()
    @Provides fun providePendingAckDao(db: KavachDatabase): PendingAckDao         = db.pendingAckDao()
    @Provides fun provideBehaviorEventDao(db: KavachDatabase): BehaviorEventDao   = db.behaviorEventDao()
    @Provides fun provideNavigationDao(db: KavachDatabase): NavigationDao         = db.navigationDao()
    @Provides fun provideOfficerDao(db: KavachDatabase): OfficerDao               = db.officerDao()
}
