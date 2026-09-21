package io.github.painter99.wswolomouc.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

/**
 * Room DAO for measurements (PRD F4.1). Retention cleanup (F4.2) uses
 * [deleteFetchedBefore]; the 24 h graph (F3.3) uses [since].
 */
@Dao
interface MeasurementDao {

    @Insert
    suspend fun insert(m: MeasurementEntity): Long

    @Query("SELECT * FROM measurement WHERE station = :station ORDER BY measuredAt DESC LIMIT 1")
    suspend fun latestForStation(station: String): MeasurementEntity?

    @Query("SELECT * FROM measurement WHERE measuredAt >= :fromEpochMs ORDER BY measuredAt ASC")
    suspend fun since(fromEpochMs: Long): List<MeasurementEntity>

    @Query("DELETE FROM measurement WHERE fetchedAt < :beforeEpochMs")
    suspend fun deleteFetchedBefore(beforeEpochMs: Long): Int
}
