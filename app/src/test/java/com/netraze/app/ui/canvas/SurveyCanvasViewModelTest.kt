package com.netraze.app.ui.canvas

import com.netraze.app.data.local.dao.ScanCycleDao
import com.netraze.app.data.local.dao.SpatialPositionDao
import com.netraze.app.data.local.dao.SurveyDao
import com.netraze.app.data.local.dao.WifiObservationDao
import com.netraze.app.data.local.entity.ScanCycleEntity
import com.netraze.app.data.local.entity.SpatialPositionEntity
import com.netraze.app.data.local.entity.SurveyEntity
import com.netraze.app.data.local.entity.WifiObservationEntity
import com.netraze.app.data.location.DeviceLocationFix
import com.netraze.app.data.location.LocationFixUnavailableException
import com.netraze.app.data.location.LocationProvider
import com.netraze.app.data.wifi.WifiScanRunner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class SurveyCanvasViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeSurveyDao: FakeSurveyDao
    private lateinit var fakeSpatialDao: FakeSpatialPositionDao
    private lateinit var fakeScanCycleDao: FakeScanCycleDao
    private lateinit var fakeWifiObsDao: FakeWifiObservationDao
    private lateinit var fakeWifiScanRunner: FakeWifiScanRunner
    private lateinit var fakeLocationProvider: FakeLocationProvider
    private lateinit var viewModel: SurveyCanvasViewModel

    class FakeLocationProvider : LocationProvider {
        var shouldThrowError = false
        var deviceLocationFix = DeviceLocationFix(10.0, 20.0, 5.0, 1000L)
        
        override suspend fun getCurrentLocation(): DeviceLocationFix {
            if (shouldThrowError) {
                throw LocationFixUnavailableException("Unable to obtain a current location fix. Try again.")
            }
            return deviceLocationFix
        }
    }

    class FakeWifiScanRunner : WifiScanRunner {
        val calls = mutableListOf<Pair<UUID, UUID?>>()

        override suspend fun performScanCycle(
            surveyId: UUID,
            spatialPositionId: UUID?
        ): Result<ScanCycleEntity> {
            calls.add(surveyId to spatialPositionId)
            return Result.success(
                ScanCycleEntity(
                    id = UUID.randomUUID(),
                    surveyId = surveyId,
                    spatialPositionId = spatialPositionId,
                    capturedAtWallclock = 2000L,
                    androidScanTimestampRaw = 2000L,
                    freshResults = true,
                    createdAt = 2000L,
                    syncState = "pending"
                )
            )
        }
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeSurveyDao = FakeSurveyDao()
        fakeSpatialDao = FakeSpatialPositionDao()
        fakeScanCycleDao = FakeScanCycleDao()
        fakeWifiObsDao = FakeWifiObservationDao()
        fakeWifiScanRunner = FakeWifiScanRunner()
        fakeLocationProvider = FakeLocationProvider()

        viewModel = SurveyCanvasViewModel(
            fakeSurveyDao,
            fakeSpatialDao,
            fakeScanCycleDao,
            fakeWifiObsDao,
            fakeWifiScanRunner,
            fakeLocationProvider
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testLoadSurveyCanvasDataComputesSpatialAnalyticsCorrectly() = runTest {
        val surveyId = UUID.randomUUID()
        val areaId = UUID.randomUUID()
        val userId = UUID.randomUUID()

        fakeSurveyDao.surveys[surveyId] = SurveyEntity(
            id = surveyId,
            surveyAreaId = areaId,
            title = "Canvas Test Survey",
            mode = "location_survey",
            status = "in_progress",
            floorPlanId = null,
            simpleMapId = null,
            createdBy = userId,
            startedAt = System.currentTimeMillis(),
            completedAt = null,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            syncState = "synced"
        )

        val posId = UUID.randomUUID()
        fakeSpatialDao.positions.add(
            SpatialPositionEntity(
                id = posId,
                surveyId = surveyId,
                label = "Point A",
                floorPlanX = null,
                floorPlanY = null,
                simpleMapX = null,
                simpleMapY = null,
                latitude = 12.9716,
                longitude = 77.5946,
                accuracyMeters = 2.5,
                capturedAt = System.currentTimeMillis(),
                createdAt = System.currentTimeMillis(),
                syncState = "synced"
            )
        )

        val cycleId = UUID.randomUUID()
        fakeScanCycleDao.cycles.add(
            ScanCycleEntity(
                id = cycleId,
                surveyId = surveyId,
                spatialPositionId = posId,
                capturedAtWallclock = System.currentTimeMillis(),
                androidScanTimestampRaw = 123456789L,
                freshResults = true,
                createdAt = System.currentTimeMillis(),
                syncState = "synced"
            )
        )

        fakeWifiObsDao.observations.addAll(
            listOf(
                WifiObservationEntity(
                    id = UUID.randomUUID(),
                    scanCycleId = cycleId,
                    ssid = "AP_5G",
                    bssid = "11:22:33:44:55:66",
                    rssiDbm = -40,
                    frequencyMhz = 5180,
                    channel = 36,
                    channelSource = "frequency_conversion",
                    capabilities = "WPA2"
                ),
                WifiObservationEntity(
                    id = UUID.randomUUID(),
                    scanCycleId = cycleId,
                    ssid = "AP_2G",
                    bssid = "AA:BB:CC:DD:EE:FF",
                    rssiDbm = -60,
                    frequencyMhz = 2412,
                    channel = 1,
                    channelSource = "frequency_conversion",
                    capabilities = "WPA2"
                )
            )
        )

        viewModel.loadSurveyCanvasData(surveyId)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNotNull(state.survey)
        assertEquals("Canvas Test Survey", state.survey?.title)
        assertEquals(1, state.positions.size)
        assertEquals(2, state.totalObservationsCount)
        assertEquals(2, state.uniqueBssidCount)
        assertEquals(-40, state.maxRssi)
        assertEquals(-50.0, state.avgRssi!!, 0.001)
    }

    @Test
    fun testLocationSurveyUsesRealLocationFixBeforeWifiScan() = runTest {
        val surveyId = UUID.randomUUID()
        fakeLocationProvider.deviceLocationFix = DeviceLocationFix(
            latitude = 12.9716,
            longitude = 77.5946,
            accuracyMeters = 4.25,
            capturedAt = 123456L
        )

        viewModel.addPositionAndScan(surveyId = surveyId, mode = "location_survey")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, fakeSpatialDao.positions.size)
        val position = fakeSpatialDao.positions.single()
        assertEquals(12.9716, position.latitude!!, 0.0)
        assertEquals(77.5946, position.longitude!!, 0.0)
        assertEquals(4.25, position.accuracyMeters!!, 0.0)
        assertEquals(123456L, position.capturedAt)
        assertTrue(position.hasValidLocationFix())

        assertEquals(1, fakeWifiScanRunner.calls.size)
        assertEquals(surveyId, fakeWifiScanRunner.calls.single().first)
        assertEquals(position.id, fakeWifiScanRunner.calls.single().second)
    }

    @Test
    fun testLocationSurveyFailureDoesNotPersistOrScan() = runTest {
        val surveyId = UUID.randomUUID()
        fakeLocationProvider.shouldThrowError = true

        viewModel.addPositionAndScan(surveyId = surveyId, mode = "location_survey")
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(fakeSpatialDao.positions.isEmpty())
        assertTrue(fakeWifiScanRunner.calls.isEmpty())
        assertFalse(viewModel.uiState.value.isScanning)
        assertEquals("Unable to obtain a current location fix. Try again.", viewModel.uiState.value.error)
    }

    @Test
    fun testFloorPlanPositionLeavesLocationFixNull() = runTest {
        val surveyId = UUID.randomUUID()

        viewModel.addPositionAndScan(surveyId = surveyId, mode = "floor_plan", x = 0.2, y = 0.8)
        testDispatcher.scheduler.advanceUntilIdle()

        val position = fakeSpatialDao.positions.single()
        assertEquals(0.2, position.floorPlanX!!, 0.0)
        assertEquals(0.8, position.floorPlanY!!, 0.0)
        assertNull(position.latitude)
        assertNull(position.longitude)
        assertNull(position.accuracyMeters)
        assertNull(position.capturedAt)
    }

    @Test
    fun testSimpleMapPositionLeavesLocationFixNull() = runTest {
        val surveyId = UUID.randomUUID()

        viewModel.addPositionAndScan(surveyId = surveyId, mode = "simple_map", x = 0.35, y = 0.65)
        testDispatcher.scheduler.advanceUntilIdle()

        val position = fakeSpatialDao.positions.single()
        assertEquals(0.35, position.simpleMapX!!, 0.0)
        assertEquals(0.65, position.simpleMapY!!, 0.0)
        assertNull(position.latitude)
        assertNull(position.longitude)
        assertNull(position.accuracyMeters)
        assertNull(position.capturedAt)
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
        override suspend fun updateSyncState(id: UUID, syncState: String) {}
        override suspend fun getPendingSurveys(): List<SurveyEntity> = emptyList()
    }

    private class FakeSpatialPositionDao : SpatialPositionDao {
        val positions = mutableListOf<SpatialPositionEntity>()

        override suspend fun insertSpatialPosition(spatialPosition: SpatialPositionEntity) { positions.add(spatialPosition) }
        override suspend fun getSpatialPositionById(id: UUID): SpatialPositionEntity? = positions.find { it.id == id }
        override suspend fun getSpatialPositionsForSurvey(surveyId: UUID): List<SpatialPositionEntity> = positions.filter { it.surveyId == surveyId }
    }

    private class FakeScanCycleDao : ScanCycleDao {
        val cycles = mutableListOf<ScanCycleEntity>()

        override suspend fun insertScanCycle(scanCycle: ScanCycleEntity) { cycles.add(scanCycle) }
        override suspend fun insertWifiObservations(observations: List<WifiObservationEntity>) {}
        override suspend fun getScanCycleById(id: UUID): ScanCycleEntity? = cycles.find { it.id == id }
        override suspend fun getScanCyclesForSurvey(surveyId: UUID): List<ScanCycleEntity> = cycles.filter { it.surveyId == surveyId }
        override suspend fun getScanCyclesForPosition(spatialPositionId: UUID): List<ScanCycleEntity> = cycles.filter { it.spatialPositionId == spatialPositionId }
    }

    private class FakeWifiObservationDao : WifiObservationDao {
        val observations = mutableListOf<WifiObservationEntity>()

        override suspend fun insertObservations(observations: List<WifiObservationEntity>) { this.observations.addAll(observations) }
        override suspend fun getObservationsForCycle(scanCycleId: UUID): List<WifiObservationEntity> = observations.filter { it.scanCycleId == scanCycleId }
        override suspend fun getObservationsForBssid(bssid: String): List<WifiObservationEntity> = observations.filter { it.bssid == bssid }
    }
}
