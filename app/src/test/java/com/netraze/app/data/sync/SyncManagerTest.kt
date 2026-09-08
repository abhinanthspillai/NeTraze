package com.netraze.app.data.sync

import com.netraze.app.data.local.dao.ScanCycleDao
import com.netraze.app.data.local.dao.SpatialPositionDao
import com.netraze.app.data.local.dao.SurveyDao
import com.netraze.app.data.local.dao.WifiObservationDao
import com.netraze.app.data.local.entity.ScanCycleEntity
import com.netraze.app.data.local.entity.SpatialPositionEntity
import com.netraze.app.data.local.entity.SurveyEntity
import com.netraze.app.data.local.entity.WifiObservationEntity
import com.netraze.app.data.remote.api.SyncApi
import com.netraze.app.data.remote.dto.SurveySyncPayloadDto
import com.netraze.app.data.remote.dto.SurveySyncResultDto
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class SyncManagerTest {

    private lateinit var fakeApi: FakeSyncApi
    private lateinit var fakeSurveyDao: FakeSurveyDao
    private lateinit var fakeSpatialDao: FakeSpatialPositionDao
    private lateinit var fakeScanCycleDao: FakeScanCycleDao
    private lateinit var fakeWifiObsDao: FakeWifiObservationDao
    private lateinit var syncManager: SyncManager

    @Before
    fun setUp() {
        fakeApi = FakeSyncApi()
        fakeSurveyDao = FakeSurveyDao()
        fakeSpatialDao = FakeSpatialPositionDao()
        fakeScanCycleDao = FakeScanCycleDao()
        fakeWifiObsDao = FakeWifiObservationDao()

        syncManager = SyncManager(
            fakeApi,
            fakeSurveyDao,
            fakeSpatialDao,
            fakeScanCycleDao,
            fakeWifiObsDao
        )
    }

    @Test
    fun testDrainPendingSyncGathersPendingItemsAndCallsApi() = runTest {
        val surveyId = UUID.randomUUID()
        val surveyAreaId = UUID.randomUUID()
        val userId = UUID.randomUUID()

        fakeSurveyDao.surveys[surveyId] = SurveyEntity(
            id = surveyId,
            surveyAreaId = surveyAreaId,
            title = "Pending Offline Survey",
            mode = "location_survey",
            status = "in_progress",
            floorPlanId = null,
            simpleMapId = null,
            createdBy = userId,
            startedAt = System.currentTimeMillis(),
            completedAt = null,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            syncState = "pending"
        )
        val positionId = UUID.randomUUID()
        val cycleId = UUID.randomUUID()
        val obsId = UUID.randomUUID()
        fakeSpatialDao.positions.add(
            SpatialPositionEntity(
                id = positionId,
                surveyId = surveyId,
                label = "Point 1",
                latitude = 12.9716,
                longitude = 77.5946,
                accuracyMeters = 4.0,
                capturedAt = 1000L,
                createdAt = 1000L,
                syncState = "pending"
            )
        )
        fakeScanCycleDao.cycles.add(
            ScanCycleEntity(
                id = cycleId,
                surveyId = surveyId,
                spatialPositionId = positionId,
                capturedAtWallclock = 1100L,
                androidScanTimestampRaw = 1100L,
                freshResults = false,
                createdAt = 1100L,
                syncState = "pending"
            )
        )
        fakeWifiObsDao.observations.add(
            WifiObservationEntity(
                id = obsId,
                scanCycleId = cycleId,
                ssid = "AP",
                bssid = "AA:BB:CC:DD:EE:FF",
                rssiDbm = -55,
                frequencyMhz = 2412,
                channel = 1,
                channelSource = "frequency_conversion",
                capabilities = null,
                syncState = "pending"
            )
        )

        val result = syncManager.drainPendingSync(surveyId)

        assertTrue(result.isSuccess)
        val resDto = result.getOrThrow()
        assertEquals(surveyId, resDto.surveyId)

        // Verify survey root syncState updated to "synced"
        assertEquals("synced", fakeSurveyDao.surveys[surveyId]?.syncState)
        assertEquals("synced", fakeSpatialDao.positions.single().syncState)
        assertEquals("synced", fakeScanCycleDao.cycles.single().syncState)
        assertEquals("synced", fakeWifiObsDao.observations.single().syncState)
        assertEquals(surveyAreaId, fakeApi.lastPayload?.survey?.surveyAreaId)
        assertEquals(false, fakeApi.lastPayload?.scanCycles?.single()?.freshResults)
    }

    @Test
    fun testFailedSyncLeavesLocalRecordsPendingAndRetryIsSafe() = runTest {
        val surveyId = UUID.randomUUID()
        val surveyAreaId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val positionId = UUID.randomUUID()
        val cycleId = UUID.randomUUID()
        val obsId = UUID.randomUUID()

        fakeSurveyDao.surveys[surveyId] = SurveyEntity(
            id = surveyId,
            surveyAreaId = surveyAreaId,
            title = "Retry Survey",
            mode = "simple_map",
            status = "in_progress",
            floorPlanId = null,
            simpleMapId = UUID.randomUUID(),
            createdBy = userId,
            startedAt = 1000L,
            completedAt = null,
            createdAt = 1000L,
            updatedAt = 1000L,
            syncState = "pending"
        )
        fakeSpatialDao.positions.add(
            SpatialPositionEntity(
                id = positionId,
                surveyId = surveyId,
                label = "Point 1",
                simpleMapX = 0.25,
                simpleMapY = 0.75,
                createdAt = 1000L,
                syncState = "pending"
            )
        )
        fakeScanCycleDao.cycles.add(
            ScanCycleEntity(
                id = cycleId,
                surveyId = surveyId,
                spatialPositionId = positionId,
                capturedAtWallclock = 1100L,
                androidScanTimestampRaw = 1100L,
                freshResults = false,
                createdAt = 1100L,
                syncState = "pending"
            )
        )
        fakeWifiObsDao.observations.add(
            WifiObservationEntity(
                id = obsId,
                scanCycleId = cycleId,
                ssid = "AP",
                bssid = "AA:BB:CC:DD:EE:FF",
                rssiDbm = -50,
                frequencyMhz = 2412,
                channel = 1,
                channelSource = "frequency_conversion",
                capabilities = null,
                syncState = "pending"
            )
        )

        fakeApi.shouldFail = true
        val failedResult = syncManager.drainPendingSync(surveyId)

        assertFalse(failedResult.isSuccess)
        assertEquals("pending", fakeSurveyDao.surveys[surveyId]?.syncState)
        assertEquals("pending", fakeSpatialDao.positions.single().syncState)
        assertEquals("pending", fakeScanCycleDao.cycles.single().syncState)
        assertEquals("pending", fakeWifiObsDao.observations.single().syncState)

        fakeApi.shouldFail = false
        val retryResult = syncManager.drainPendingSync(surveyId)

        assertTrue(retryResult.isSuccess)
        assertEquals("synced", fakeSurveyDao.surveys[surveyId]?.syncState)
        assertEquals("synced", fakeSpatialDao.positions.single().syncState)
        assertEquals("synced", fakeScanCycleDao.cycles.single().syncState)
        assertEquals("synced", fakeWifiObsDao.observations.single().syncState)
    }

    @Test
    fun testPendingObservationUnderSyncedCycleStillSynchronizes() = runTest {
        val surveyId = UUID.randomUUID()
        val surveyAreaId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val cycleId = UUID.randomUUID()
        val obsId = UUID.randomUUID()

        fakeSurveyDao.surveys[surveyId] = SurveyEntity(
            id = surveyId,
            surveyAreaId = surveyAreaId,
            title = "Observation Retry",
            mode = "location_survey",
            status = "in_progress",
            floorPlanId = null,
            simpleMapId = null,
            createdBy = userId,
            startedAt = 1000L,
            completedAt = null,
            createdAt = 1000L,
            updatedAt = 1000L,
            syncState = "synced"
        )
        fakeScanCycleDao.cycles.add(
            ScanCycleEntity(
                id = cycleId,
                surveyId = surveyId,
                spatialPositionId = null,
                capturedAtWallclock = 1100L,
                androidScanTimestampRaw = 1100L,
                freshResults = false,
                createdAt = 1100L,
                syncState = "synced"
            )
        )
        fakeWifiObsDao.observations.add(
            WifiObservationEntity(
                id = obsId,
                scanCycleId = cycleId,
                ssid = "AP",
                bssid = "AA:BB:CC:DD:EE:FF",
                rssiDbm = -60,
                frequencyMhz = 2412,
                channel = 1,
                channelSource = "frequency_conversion",
                capabilities = null,
                syncState = "pending"
            )
        )

        val result = syncManager.drainPendingSync(surveyId)

        assertTrue(result.isSuccess)
        assertEquals(null, fakeApi.lastPayload?.survey)
        assertEquals(1, fakeApi.lastPayload?.scanCycles?.size)
        assertEquals(1, fakeApi.lastPayload?.scanCycles?.single()?.observations?.size)
        assertEquals("synced", fakeScanCycleDao.cycles.single().syncState)
        assertEquals("synced", fakeWifiObsDao.observations.single().syncState)
    }

    private class FakeSyncApi : SyncApi {
        var shouldFail = false
        var lastPayload: SurveySyncPayloadDto? = null

        override suspend fun syncSurvey(
            surveyId: UUID,
            payload: SurveySyncPayloadDto
        ): SurveySyncResultDto {
            if (shouldFail) throw IllegalStateException("Network unavailable")
            lastPayload = payload
            return SurveySyncResultDto(
                surveyId = surveyId,
                ingestedSpatialPositions = payload.spatialPositions.size,
                ingestedScanCycles = payload.scanCycles.size,
                ingestedWifiObservations = payload.scanCycles.sumOf { it.observations.size }
            )
        }
    }

    private class FakeSurveyDao : SurveyDao {
        val surveys = mutableMapOf<UUID, SurveyEntity>()

        override suspend fun insertSurvey(survey: SurveyEntity) { surveys[survey.id] = survey }
        override suspend fun upsertSurvey(survey: SurveyEntity) { surveys[survey.id] = survey }
        override suspend fun upsertSurveys(surveys: List<SurveyEntity>) { surveys.forEach { upsertSurvey(it) } }
        override suspend fun getSurveyById(id: UUID): SurveyEntity? = surveys[id]
        override suspend fun getSurveysForArea(surveyAreaId: UUID): List<SurveyEntity> = surveys.values.filter { it.surveyAreaId == surveyAreaId }
        override suspend fun getAllSurveys(): List<SurveyEntity> = surveys.values.toList()
        override suspend fun updateSurveyCompletion(id: UUID, status: String, completedAt: Long, updatedAt: Long) {}
        override suspend fun updateSyncState(id: UUID, syncState: String) {
            surveys[id]?.let { surveys[id] = it.copy(syncState = syncState) }
        }
        override suspend fun getPendingSurveys(): List<SurveyEntity> = surveys.values.filter { it.syncState == "pending" }
    }

    private class FakeSpatialPositionDao : SpatialPositionDao {
        val positions = mutableListOf<SpatialPositionEntity>()

        override suspend fun insertSpatialPosition(spatialPosition: SpatialPositionEntity) { positions.add(spatialPosition) }
        override suspend fun getSpatialPositionById(id: UUID): SpatialPositionEntity? = null
        override suspend fun getSpatialPositionsForSurvey(surveyId: UUID): List<SpatialPositionEntity> = positions.filter { it.surveyId == surveyId }
        override suspend fun updateSyncState(ids: List<UUID>, syncState: String) {
            positions.replaceAll { if (it.id in ids) it.copy(syncState = syncState) else it }
        }
    }

    private class FakeScanCycleDao : ScanCycleDao {
        val cycles = mutableListOf<ScanCycleEntity>()

        override suspend fun insertScanCycle(scanCycle: ScanCycleEntity) {}
        override suspend fun insertWifiObservations(observations: List<WifiObservationEntity>) {}
        override suspend fun getScanCycleById(id: UUID): ScanCycleEntity? = null
        override suspend fun getScanCyclesForSurvey(surveyId: UUID): List<ScanCycleEntity> = cycles.filter { it.surveyId == surveyId }
        override suspend fun getScanCyclesForPosition(spatialPositionId: UUID): List<ScanCycleEntity> = emptyList()
        override suspend fun updateSyncState(ids: List<UUID>, syncState: String) {
            cycles.replaceAll { if (it.id in ids) it.copy(syncState = syncState) else it }
        }
    }

    private class FakeWifiObservationDao : WifiObservationDao {
        val observations = mutableListOf<WifiObservationEntity>()

        override suspend fun insertObservations(observations: List<WifiObservationEntity>) {}
        override suspend fun getObservationsForCycle(scanCycleId: UUID): List<WifiObservationEntity> = observations.filter { it.scanCycleId == scanCycleId }
        override suspend fun getObservationsForBssid(bssid: String): List<WifiObservationEntity> = emptyList()
        override suspend fun updateSyncState(ids: List<UUID>, syncState: String) {
            observations.replaceAll { if (it.id in ids) it.copy(syncState = syncState) else it }
        }
    }
}
