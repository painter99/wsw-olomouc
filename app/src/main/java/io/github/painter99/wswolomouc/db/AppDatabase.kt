package io.github.painter99.wswolomouc.db

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Application database (M1.4). Schema export is disabled until the first
 * schema migration becomes relevant (single-user personal app, Fáze 1).
 *
 * v2 (M1.6b-2, Pavel 22. 9.): + rainDailyMm (unified daily precipitation
 * total for both stations). Migration is a simple nullable ADD COLUMN.
 */
@Database(entities = [MeasurementEntity::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun measurementDao(): MeasurementDao

    companion object {
        const val NAME = "wsw-olomouc.db"

        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE measurement ADD COLUMN rainDailyMm REAL")
            }
        }
    }
}
