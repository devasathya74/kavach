package com.kavach.app.di

import android.content.Context
import androidx.room.Room
import com.kavach.app.BuildConfig
import com.kavach.app.data.local.SessionDataStore
import com.kavach.app.data.local.dao.*
import com.kavach.app.data.local.db.KavachDatabase
import com.kavach.app.data.remote.api.AuthRefreshApiService
import com.kavach.app.data.remote.api.KavachApiService
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

    @Provides
    @Singleton
    @Named("authed")
    fun provideOkHttpClient(
        sessionDataStore   : SessionDataStore,
        tokenAuthenticator : TokenAuthenticator
    ): OkHttpClient {
        val certificatePinner = okhttp3.CertificatePinner.Builder()
            .add(com.kavach.app.KavachConfig.PINNED_DOMAIN, com.kavach.app.KavachConfig.SSL_PIN_PRIMARY)
            .build()

        val authInterceptor = Interceptor { chain ->
            val token = runBlocking { sessionDataStore.token.first() }
            val requestBuilder = chain.request().newBuilder()
                .addHeader("X-Timestamp", (System.currentTimeMillis() / 1000).toString())
                .addHeader("X-Nonce", java.util.UUID.randomUUID().toString())
                .addHeader("X-Device-Id", android.os.Build.ID)

            if (!token.isNullOrBlank()) {
                requestBuilder.addHeader("Authorization", "Bearer $token")
            }
            chain.proceed(requestBuilder.build())
        }

        return OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .authenticator(tokenAuthenticator)
            .certificatePinner(certificatePinner)
            .apply {
                if (BuildConfig.DEBUG) {
                    addInterceptor(
                        HttpLoggingInterceptor().apply {
                            level = HttpLoggingInterceptor.Level.BODY
                        }
                    )
                }
            }
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    // ── OkHttp #2: Plain client (for token refresh — no auth interceptor) ──

    @Provides
    @Singleton
    @Named("plain")
    fun providePlainOkHttpClient(): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

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
        ).fallbackToDestructiveMigration().build()

    @Provides fun provideTrainingDao(db: KavachDatabase): TrainingDao             = db.trainingDao()
    @Provides fun provideQuizDao(db: KavachDatabase): QuizDao                     = db.quizDao()
    @Provides fun provideOrderDao(db: KavachDatabase): OrderDao                   = db.orderDao()
    @Provides fun providePendingAckDao(db: KavachDatabase): PendingAckDao         = db.pendingAckDao()
    @Provides fun provideBehaviorEventDao(db: KavachDatabase): BehaviorEventDao   = db.behaviorEventDao()
}
