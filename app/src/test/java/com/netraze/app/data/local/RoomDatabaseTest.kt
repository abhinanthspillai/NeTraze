package com.netraze.app.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.netraze.app.data.local.entity.BuildingEntity
import com.netraze.app.data.local.entity.FloorEntity
import com.netraze.app.data.local.entity.ProjectEntity
import com.netraze.app.data.local.entity.ScanAttemptEntity
import com.netraze.app.data.local.entity.ScanCycleEntity
import com.netraze.app.data.local.entity.SpatialPositionEntity
import com.netraze.app.data.local.entity.SurveyAreaEntity
import com.netraze.app.data.local.entity.SurveyEntity
import com.netraze.app.data.local.entity.WifiObservationEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class RoomDatabaseTest {

    private lateinit var db: NetrazeDatabase

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, NetrazeDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun closeDb() {
        if (::db.isInitialized) {
            db.close()
        }
    }

    @Test
    fun testInsertHierarchyAndSurveySession() = runBlocking {
        val projectId = UUID.randomUUID()
        val buildingId = UUID.randomUUID()
        val floorId = UUID.randomUUID()
        val areaId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val surveyId = UUID.randomUUID()

        val project = ProjectEntity(projectId, userId, "HQ Project", true, 1000L, 1000L)
        val building = BuildingEntity(buildingId, projectId, "Building A", 1000L, 1000L)
        val floor = FloorEntity(floorId, buildingId, "Floor 2", 1000L, 1000L)
        val area = SurveyAreaEntity(areaId, floorId, "East Wing", 1000L, 1000L)

        db.hierarchyDao().insertProjects(listOf(project))
        db.hierarchyDao().insertBuildings(listOf(building))
        db.hierarchyDao().insertFloors(listOf(floor))
        db.hierarchyDao().insertSurveyAreas(listOf(area))

        val survey = SurveyEntity(
            id = surveyId,
            surveyAreaId = areaId,
            title = "Survey 1",
            mode = "location_survey",
            status = "in_progress",
            floorPlanId = null,
            simpleMapId = null,
            createdBy = userId,
            startedAt = 2000L,
            completedAt = null,
            createdAt = 2000L,
            updatedAt = 2000L,
            syncState = "pending"
        )
        db.surveyDao().insertSurvey(survey)

        val retrieved = db.surveyDao().getSurveyById(surveyId)
        assertNotNull(retrieved)
        assertEquals("Survey 1", retrieved?.title)
        assertEquals(surveyId, retrieved?.id)
    }

    @Test
    fun testFloorPlanSpatialPositionPersistence() = runBlocking {
        val (surveyId, posId) = setupBaseSurvey()
        val fpPos = SpatialPositionEntity(
            id = posId,
            surveyId = surveyId,
            label = "Desk 4",
            floorPlanX = 0.45,
            floorPlanY = 0.72,
            createdAt = 1000L
        )
        db.spatialPositionDao().insertSpatialPosition(fpPos)

        val saved = db.spatialPositionDao().getSpatialPositionById(posId)
        assertNotNull(saved)
        assertEquals(0.45, saved?.floorPlanX!!, 0.0001)
        assertEquals(0.72, saved?.floorPlanY!!, 0.0001)
        assertNull(saved?.simpleMapX)
        assertNull(saved?.latitude)
    }

    @Test
    fun testSimpleMapSpatialPositionPersistence() = runBlocking {
        val (surveyId, posId) = setupBaseSurvey()
        val smPos = SpatialPositionEntity(
            id = posId,
            surveyId = surveyId,
            label = "Canvas Pin 1",
            simpleMapX = 0.15,
            simpleMapY = 0.85,
            createdAt = 1000L
        )
        db.spatialPositionDao().insertSpatialPosition(smPos)

        val saved = db.spatialPositionDao().getSpatialPositionById(posId)
        assertNotNull(saved)
        assertEquals(0.15, saved?.simpleMapX!!, 0.0001)
        assertEquals(0.85, saved?.simpleMapY!!, 0.0001)
        assertNull(saved?.floorPlanX)
    }

    @Test
    fun testLocationSurveySpatialPositionWithAndWithoutFix() = runBlocking {
        val (surveyId, posId1) = setupBaseSurvey()
        val posId2 = UUID.randomUUID()

        // Position without LocationFix
        val posNoFix = SpatialPositionEntity(
            id = posId1, surveyId = surveyId, label = "Outdoor P1", createdAt = 1000L
        )
        assertTrue(posNoFix.hasValidLocationFix())
        db.spatialPositionDao().insertSpatialPosition(posNoFix)

        // Complete LocationFix
        val posWithFix = SpatialPositionEntity(
            id = posId2, surveyId = surveyId, label = "Outdoor P2",
            latitude = 12.9716, longitude = 77.5946, accuracyMeters = 3.2, capturedAt = 1005L,
            createdAt = 1005L
        )
        assertTrue(posWithFix.hasValidLocationFix())
        db.spatialPositionDao().insertSpatialPosition(posWithFix)

        // Partial invalid fix
        val posPartialFix = SpatialPositionEntity(
            id = UUID.randomUUID(), surveyId = surveyId, label = "Invalid",
            latitude = 12.9716, longitude = null, accuracyMeters = 3.2, capturedAt = 1005L,
            createdAt = 1005L
        )
        assertFalse(posPartialFix.hasValidLocationFix())
    }

    @Test
    fun testScanAttemptCorrelationAndMultipleAttemptsToOneCycle() = runBlocking {
        val (surveyId, posId) = setupBaseSurvey()

        val pos = SpatialPositionEntity(id = posId, surveyId = surveyId, label = "Point 1", createdAt = 10L)
        db.spatialPositionDao().insertSpatialPosition(pos)

        val attempt1Id = UUID.randomUUID()
        val attempt1 = ScanAttemptEntity(
            id = attempt1Id, surveyId = surveyId, spatialPositionId = posId,
            correlatedScanCycleId = null, dispatchedAtWallclock = 100L, status = "dispatched"
        )
        db.scanAttemptDao().insertScanAttempt(attempt1)
        assertNull(db.scanAttemptDao().getScanAttemptById(attempt1Id)?.correlatedScanCycleId)

        val cycleId = UUID.randomUUID()
        val cycle = ScanCycleEntity(
            id = cycleId, surveyId = surveyId, spatialPositionId = posId,
            capturedAtWallclock = 105L, androidScanTimestampRaw = 1000L, freshResults = true, createdAt = 105L
        )
        db.scanCycleDao().insertScanCycle(cycle)

        db.scanAttemptDao().updateScanAttemptCorrelation(attempt1Id, "completed", cycleId)

        val attempt2Id = UUID.randomUUID()
        val attempt2 = ScanAttemptEntity(
            id = attempt2Id, surveyId = surveyId, spatialPositionId = posId,
            correlatedScanCycleId = cycleId, dispatchedAtWallclock = 102L, status = "completed"
        )
        db.scanAttemptDao().insertScanAttempt(attempt2)

        val attemptsForCycle = db.scanAttemptDao().getScanAttemptsForCycle(cycleId)
        assertEquals(2, attemptsForCycle.size)
    }

    @Test
    fun testTransactionalScanCycleWithMultipleSameBssidObservations() = runBlocking {
        val (surveyId, posId) = setupBaseSurvey()
        val pos = SpatialPositionEntity(id = posId, surveyId = surveyId, label = "Point 1", createdAt = 10L)
        db.spatialPositionDao().insertSpatialPosition(pos)

        val cycleId = UUID.randomUUID()
        val cycle = ScanCycleEntity(
            id = cycleId, surveyId = surveyId, spatialPositionId = posId,
            capturedAtWallclock = 15L, androidScanTimestampRaw = 1000L, freshResults = true, createdAt = 15L
        )

        val bssid = "00:11:22:33:44:55"
        val obs1 = WifiObservationEntity(
            id = UUID.randomUUID(), scanCycleId = cycleId, ssid = "Netraze_WiFi",
            bssid = bssid, rssiDbm = -60, frequencyMhz = 5180, channel = 36, channelSource = "derived", capabilities = "WPA2"
        )
        val obs2 = WifiObservationEntity(
            id = UUID.randomUUID(), scanCycleId = cycleId, ssid = "Netraze_WiFi",
            bssid = bssid, rssiDbm = -58, frequencyMhz = 5180, channel = 36, channelSource = "derived", capabilities = "WPA2"
        )

        db.scanCycleDao().insertScanCycleWithObservations(cycle, listOf(obs1, obs2))

        val obsList = db.wifiObservationDao().getObservationsForCycle(cycleId)
        assertEquals(2, obsList.size)
        assertEquals(bssid, obsList[0].bssid)
        assertEquals(bssid, obsList[1].bssid)
    }

    @Test
    fun testPersistedEvidenceReloadsOriginalValuesWithoutFabricatedDefaults() = runBlocking {
        val (surveyId, posId) = setupBaseSurvey()
        val cycleId = UUID.randomUUID()
        val obsId = UUID.randomUUID()
        val pos = SpatialPositionEntity(
            id = posId,
            surveyId = surveyId,
            label = "Reload Point",
            simpleMapX = 0.33,
            simpleMapY = 0.67,
            latitude = null,
            longitude = null,
            accuracyMeters = null,
            capturedAt = null,
            createdAt = 2000L
        )
        val cycle = ScanCycleEntity(
            id = cycleId,
            surveyId = surveyId,
            spatialPositionId = posId,
            capturedAtWallclock = 2100L,
            androidScanTimestampRaw = 987654321L,
            freshResults = false,
            createdAt = 2100L
        )
        val obs = WifiObservationEntity(
            id = obsId,
            scanCycleId = cycleId,
            ssid = null,
            bssid = "AA:BB:CC:DD:EE:FF",
            rssiDbm = -61,
            frequencyMhz = 2462,
            channel = null,
            channelSource = "unavailable",
            capabilities = null
        )

        db.spatialPositionDao().insertSpatialPosition(pos)
        db.scanCycleDao().insertScanCycleWithObservations(cycle, listOf(obs))

        val reloadedPosition = db.spatialPositionDao().getSpatialPositionById(posId)
        val reloadedCycle = db.scanCycleDao().getScanCycleById(cycleId)
        val reloadedObservation = db.wifiObservationDao().getObservationsForCycle(cycleId).single()

        assertEquals(posId, reloadedPosition?.id)
        assertEquals(0.33, reloadedPosition?.simpleMapX!!, 0.0001)
        assertEquals(0.67, reloadedPosition.simpleMapY!!, 0.0001)
        assertNull(reloadedPosition.latitude)
        assertNull(reloadedPosition.longitude)
        assertNull(reloadedPosition.accuracyMeters)
        assertNull(reloadedPosition.capturedAt)
        assertEquals(cycleId, reloadedCycle?.id)
        assertEquals(posId, reloadedCycle?.spatialPositionId)
        assertEquals(2100L, reloadedCycle?.capturedAtWallclock)
        assertEquals(false, reloadedCycle?.freshResults)
        assertEquals(obsId, reloadedObservation.id)
        assertNull(reloadedObservation.ssid)
        assertEquals("AA:BB:CC:DD:EE:FF", reloadedObservation.bssid)
        assertEquals(-61, reloadedObservation.rssiDbm)
        assertEquals(2462, reloadedObservation.frequencyMhz)
        assertNull(reloadedObservation.channel)
        assertNull(reloadedObservation.capabilities)
    }

    private suspend fun setupBaseSurvey(): Pair<UUID, UUID> {
        val projectId = UUID.randomUUID()
        val buildingId = UUID.randomUUID()
        val floorId = UUID.randomUUID()
        val areaId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val surveyId = UUID.randomUUID()
        val posId = UUID.randomUUID()

        db.hierarchyDao().insertProjects(listOf(ProjectEntity(projectId, userId, "P", true, 1L, 1L)))
        db.hierarchyDao().insertBuildings(listOf(BuildingEntity(buildingId, projectId, "B", 1L, 1L)))
        db.hierarchyDao().insertFloors(listOf(FloorEntity(floorId, buildingId, "F", 1L, 1L)))
        db.hierarchyDao().insertSurveyAreas(listOf(SurveyAreaEntity(areaId, floorId, "A", 1L, 1L)))

        val survey = SurveyEntity(
            id = surveyId, surveyAreaId = areaId, title = "S", mode = "location_survey",
            status = "in_progress", floorPlanId = null, simpleMapId = null, createdBy = userId,
            startedAt = 10L, completedAt = null, createdAt = 10L, updatedAt = 10L
        )
        db.surveyDao().insertSurvey(survey)
        return Pair(surveyId, posId)
    }
}
