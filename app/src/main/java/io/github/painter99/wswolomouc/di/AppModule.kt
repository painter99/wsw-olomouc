package io.github.painter99.wswolomouc.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.painter99.wswolouc.data.ChmuDataSource
import io.github.painter99.wswolomouc.data.DataStoreRateLimitStore
import io.github.painter99.wswolomouc.data.InfopocasiDataSource
import io.github.painter99.wswolomouc.data.RateLimitStore
import io.github.painter99.wswolomouc.data.WeatherRepository
import io.github.painter99.wswolomouc.db.AppDatabase
import io.github.painter99.wswolomouc.db.MeasurementDao
import io.github.painter99.wswolomouc.ui.DataStoreThemeStore
import io.github.painter99.wswolomouc.ui.ThemeStore
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import okhttp3.OkHttpClient

/**
 * Application wiring (M1.5) — the first time the M1.4 data layer is
 * composed for real use in the UI.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun okHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    /** Wall clock in epoch ms — injected so UI code stays testable. */
    @Provides
    @Singleton
    fun systemClock(): () -> Long = System::currentTimeMillis

    @Provides
    @Singleton
    fun appDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME)
            .addMigrations(AppDatabase.MIGRATION_1_2) // v2: + rainDailyMm
            .build()

    @Provides
    fun measurementDao(db: AppDatabase): MeasurementDao = db.measurementDao()

    /** Persistent rate-limit state (M1.7b, Pavel 24. 9. 2026). */
    @Provides
    @Singleton
    fun rateLimitStore(@ApplicationContext context: Context): RateLimitStore =
        DataStoreRateLimitStore(context)

    /**
     * Primary source first (F1.4). Primary = INFOPOCASI, hardcoded per the
     * M1.4 decision (Q5 recommendation; user choice arrives with F5.1).
     * The list is built inline — injecting Kotlin List<T> into Dagger is
     * type-invariant and fails with Dagger/MissingBinding.
     */
    @Provides
    @Singleton
    fun weatherRepository(
        client: OkHttpClient,
        dao: MeasurementDao,
        rateLimitStore: RateLimitStore
    ): WeatherRepository =
        WeatherRepository(
            sources = listOf(
                InfopocasiDataSource(client),
                ChmuDataSource(client)
            ),
            dao = dao,
            rateLimitStore = rateLimitStore
        )

    /** Persisted theme preference (round 2, Pavel 23. 9. 2026). */
    @Provides
    @Singleton
    fun themeStore(@ApplicationContext context: Context): ThemeStore =
        DataStoreThemeStore(context)
}
