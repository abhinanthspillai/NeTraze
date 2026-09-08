package com.netraze.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.netraze.app.data.local.entity.WifiObservationEntity
import java.util.UUID

@Dao
interface WifiObservationDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertObservations(observations: List<WifiObservationEntity>)

    @Query("SELECT * FROM wifi_observations WHERE scanCycleId = :scanCycleId")
    suspend fun getObservationsForCycle(scanCycleId: UUID): List<WifiObservationEntity>

    @Query("SELECT * FROM wifi_observations WHERE bssid = :bssid")
    suspend fun getObservationsForBssid(bssid: String): List<WifiObservationEntity>

    @Query("UPDATE wifi_observations SET syncState = :syncState WHERE id IN (:ids)")
    suspend fun updateSyncState(ids: List<UUID>, syncState: String)
}
