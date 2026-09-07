package com.netraze.app.di

import android.os.Build
import com.netraze.app.BuildConfig
import com.netraze.app.data.local.dao.HierarchyDao
import com.netraze.app.data.local.dao.ScanCycleDao
import com.netraze.app.data.local.dao.SpatialPositionDao
import com.netraze.app.data.local.dao.SurveyDao
import com.netraze.app.data.local.dao.WifiObservationDao
import com.netraze.app.data.remote.api.AuthApi
import com.netraze.app.data.remote.api.HierarchyApi
import com.netraze.app.data.remote.api.SyncApi
import com.netraze.app.data.remote.api.SurveyApi
import com.netraze.app.data.repository.AuthRepository
import com.netraze.app.data.repository.AuthRepositoryImpl
import com.netraze.app.data.repository.HierarchyRepository
import com.netraze.app.data.repository.HierarchyRepositoryImpl
import com.netraze.app.data.repository.SurveyRepository
import com.netraze.app.data.repository.SurveyRepositoryImpl
import com.netraze.app.data.security.SecureSessionStore
import com.netraze.app.data.sync.SyncManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private fun getBaseUrl(): String {
        val isEmulator = (Build.FINGERPRINT.startsWith("generic") ||
                Build.FINGERPRINT.startsWith("unknown") ||
                Build.MODEL.contains("google_sdk") ||
                Build.MODEL.contains("Emulator") ||
                Build.MODEL.contains("Android SDK built for x86") ||
                Build.MANUFACTURER.contains("Genymotion") ||
                (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic")) ||
                "google_sdk" == Build.PRODUCT)
        return if (isEmulator) {
            "http://10.0.2.2:8000/"
        } else {
            "https://netraze.onrender.com/"
        }
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(sessionStore: SecureSessionStore): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val requestBuilder = chain.request().newBuilder()
                val session = runBlocking { sessionStore.getSession() }
                session?.accessToken?.let { token ->
                    requestBuilder.header("Authorization", "Bearer $token")
                }
                chain.proceed(requestBuilder.build())
            }
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)

        // Never log request/response bodies: auth bodies can contain passwords.
        if (BuildConfig.DEBUG) {
            builder.addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            })
        }

        return builder.build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl(getBaseUrl())
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun provideAuthApi(retrofit: Retrofit): AuthApi = retrofit.create(AuthApi::class.java)

    @Provides
    @Singleton
    fun provideAuthRepository(authApi: AuthApi, sessionStore: SecureSessionStore): AuthRepository =
        AuthRepositoryImpl(authApi, sessionStore)

    @Provides
    @Singleton
    fun provideHierarchyApi(retrofit: Retrofit): HierarchyApi = retrofit.create(HierarchyApi::class.java)

    @Provides
    @Singleton
    fun provideHierarchyRepository(hierarchyApi: HierarchyApi, hierarchyDao: HierarchyDao): HierarchyRepository =
        HierarchyRepositoryImpl(hierarchyApi, hierarchyDao)

    @Provides
    @Singleton
    fun provideSurveyApi(retrofit: Retrofit): SurveyApi = retrofit.create(SurveyApi::class.java)

    @Provides
    @Singleton
    fun provideSyncApi(retrofit: Retrofit): SyncApi = retrofit.create(SyncApi::class.java)

    @Provides
    @Singleton
    fun provideSyncManager(
        syncApi: SyncApi,
        surveyDao: SurveyDao,
        spatialPositionDao: SpatialPositionDao,
        scanCycleDao: ScanCycleDao,
        wifiObservationDao: WifiObservationDao
    ): SyncManager = SyncManager(syncApi, surveyDao, spatialPositionDao, scanCycleDao, wifiObservationDao)

    @Provides
    @Singleton
    fun provideSurveyRepository(
        surveyApi: SurveyApi,
        surveyDao: SurveyDao,
        sessionStore: SecureSessionStore,
        syncManager: SyncManager
    ): SurveyRepository = SurveyRepositoryImpl(surveyApi, surveyDao, sessionStore, syncManager)
}
