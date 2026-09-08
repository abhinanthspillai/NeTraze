package com.netraze.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.netraze.app.data.local.entity.SpatialPositionEntity
import java.util.UUID

@Dao
interface SpatialPositionDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSpatialPosition(spatialPosition: SpatialPositionEntity)

    @Query("SELECT * FROM spatial_positions WHERE id = :id")
    suspend fun getSpatialPositionById(id: UUID): SpatialPositionEntity?

    @Query("SELECT * FROM spatial_positions WHERE surveyId = :surveyId ORDER BY createdAt ASC")
    suspend fun getSpatialPositionsForSurvey(surveyId: UUID): List<SpatialPositionEntity>

    @Query("UPDATE spatial_positions SET syncState = :syncState WHERE id IN (:ids)")
    suspend fun updateSyncState(ids: List<UUID>, syncState: String)

    @Transaction
    suspend fun insertAtomicSpatialPosition(spatialPosition: SpatialPositionEntity) {
        require(spatialPosition.hasValidLocationFix()) {
            "LocationFix atomicity violation: latitude, longitude, accuracyMeters, and capturedAt must all be present or all null"
        }
        insertSpatialPosition(spatialPosition)
    }
}
