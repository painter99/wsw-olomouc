package io.github.painter99.wswolomouc.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.painter99.wswolomouc.data.ChmuDataSource
import io.github.painter99.wswolomouc.data.InfopocasiDataSource
import io.github.painter99.wswolomouc.data.StationDataSource
import io.github.painter99.wswolomouc.data.WeatherRepository
import io.github.painter99.wswolomouc.db.AppDatabase
import io.github.painter99.wswolomouc.db.MeasurementDao
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

    @Provides
    @Singleton
    fun appDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME).build()

    @Provides
    fun measurementDao(db: AppDatabase): MeasurementDao = db.measurementDao()

    /**
     * Source order matters: the first entry is the primary station (F1.4).
     * Primary = INFOPOCASI, hardcoded per the M1.4 decision (Q5
     * recommendation; user choice arrives with F5.1).
     */
    @Provides
    @Singleton
    fun stationSources(client: OkHttpClient): List<StationDataSource> = listOf(
        InfopocasiDataSource(client),
        ChmuDataSource(client)
    )

    @Provides
    @Singleton
    fun weatherRepository(
        sources: List<StationDataSource>,
        dao: MeasurementDao
    ): WeatherRepository = WeatherRepository(sources, dao)
}
