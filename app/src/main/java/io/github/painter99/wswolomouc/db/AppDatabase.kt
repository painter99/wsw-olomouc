package io.github.painter99.wswolomouc.db

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Application database (M1.4). Schema export is disabled until the first
 * schema migration becomes relevant (single-user personal app, Fáze 1).
 */
@Database(entities = [MeasurementEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun measurementDao(): MeasurementDao

    companion object {
        const val NAME = "wsw-olomouc.db"
    }
}
