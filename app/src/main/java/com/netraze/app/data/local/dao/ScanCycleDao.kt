package com.netraze.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.netraze.app.data.local.entity.ScanCycleEntity
import com.netraze.app.data.local.entity.WifiObservationEntity
import java.util.UUID

@Dao
interface ScanCycleDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertScanCycle(scanCycle: ScanCycleEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertWifiObservations(observations: List<WifiObservationEntity>)

    @Query("SELECT * FROM scan_cycles WHERE id = :id")
    suspend fun getScanCycleById(id: UUID): ScanCycleEntity?

    @Query("SELECT * FROM scan_cycles WHERE surveyId = :surveyId")
    suspend fun getScanCyclesForSurvey(surveyId: UUID): List<ScanCycleEntity>

    @Query("SELECT * FROM scan_cycles WHERE spatialPositionId = :spatialPositionId")
    suspend fun getScanCyclesForPosition(spatialPositionId: UUID): List<ScanCycleEntity>

    @Query("UPDATE scan_cycles SET syncState = :syncState WHERE id IN (:ids)")
    suspend fun updateSyncState(ids: List<UUID>, syncState: String)

    @Transaction
    suspend fun insertScanCycleWithObservations(
        scanCycle: ScanCycleEntity,
        observations: List<WifiObservationEntity>
    ) {
        insertScanCycle(scanCycle)
        if (observations.isNotEmpty()) {
            insertWifiObservations(observations)
        }
    }
}
