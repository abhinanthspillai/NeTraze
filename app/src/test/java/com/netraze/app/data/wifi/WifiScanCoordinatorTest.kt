package com.netraze.app.data.wifi

import android.content.Context
import android.net.wifi.WifiManager
import com.netraze.app.data.local.dao.ScanCycleDao
import com.netraze.app.data.local.entity.ScanCycleEntity
import com.netraze.app.data.local.entity.WifiObservationEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class WifiScanCoordinatorTest {

    private lateinit var mockContext: Context
    private lateinit var mockWifiManager: WifiManager
    private lateinit var fakeScanCycleDao: FakeScanCycleDao
    private lateinit var coordinator: WifiScanCoordinator

    @Before
    fun setUp() {
        mockContext = Mockito.mock(Context::class.java)
        mockWifiManager = Mockito.mock(WifiManager::class.java)
        Mockito.`when`(mockContext.applicationContext).thenReturn(mockContext)
        Mockito.`when`(mockContext.getSystemService(Context.WIFI_SERVICE)).thenReturn(mockWifiManager)

        fakeScanCycleDao = FakeScanCycleDao()
        coordinator = WifiScanCoordinator(mockContext, fakeScanCycleDao)
    }

    @Test
    fun testFrequencyToChannelConversion() {
        assertEquals(1, WifiUtils.frequencyToChannel(2412))
        assertEquals(6, WifiUtils.frequencyToChannel(2437))
        assertEquals(11, WifiUtils.frequencyToChannel(2462))
        assertEquals(14, WifiUtils.frequencyToChannel(2484))
        assertEquals(36, WifiUtils.frequencyToChannel(5180))
        assertEquals(149, WifiUtils.frequencyToChannel(5745))
        assertEquals(1, WifiUtils.frequencyToChannel(5955))
    }

    @Test
    fun testBssidNormalization() {
        assertEquals("AA:BB:CC:DD:EE:FF", WifiUtils.normalizeBssid("aa:bb:cc:dd:ee:ff"))
        assertEquals("12:34:56:78:9A:BC", WifiUtils.normalizeBssid(" 12:34:56:78:9a:bc "))
    }

    @Test
    fun testPerformScanCycleCreatesCanonicalScanCycleEntity() = runTest {
        val surveyId = UUID.randomUUID()
        val positionId = UUID.randomUUID()

        val result = coordinator.performScanCycle(surveyId, positionId)

        assertTrue(result.isSuccess)
        val cycle = result.getOrThrow()

        assertNotNull(cycle.id)
        assertEquals(surveyId, cycle.surveyId)
        assertEquals(positionId, cycle.spatialPositionId)
        assertEquals(false, cycle.freshResults)
        assertEquals("pending", cycle.syncState)
        assertEquals(1, fakeScanCycleDao.insertedCycles.size)
    }

    private class FakeScanCycleDao : ScanCycleDao {
        val insertedCycles = mutableListOf<ScanCycleEntity>()
        val insertedObservations = mutableListOf<WifiObservationEntity>()

        override suspend fun insertScanCycle(scanCycle: ScanCycleEntity) {
            insertedCycles.add(scanCycle)
        }

        override suspend fun insertWifiObservations(observations: List<WifiObservationEntity>) {
            insertedObservations.addAll(observations)
        }

        override suspend fun getScanCycleById(id: UUID): ScanCycleEntity? {
            return insertedCycles.find { it.id == id }
        }

        override suspend fun getScanCyclesForSurvey(surveyId: UUID): List<ScanCycleEntity> {
            return insertedCycles.filter { it.surveyId == surveyId }
        }

        override suspend fun getScanCyclesForPosition(spatialPositionId: UUID): List<ScanCycleEntity> {
            return insertedCycles.filter { it.spatialPositionId == spatialPositionId }
        }

        override suspend fun updateSyncState(ids: List<UUID>, syncState: String) {
            insertedCycles.replaceAll { if (it.id in ids) it.copy(syncState = syncState) else it }
        }
    }
}
