package com.netraze.app.data.wifi

import android.annotation.SuppressLint
import android.content.Context
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import com.netraze.app.data.local.dao.ScanCycleDao
import com.netraze.app.data.local.entity.ScanCycleEntity
import com.netraze.app.data.local.entity.WifiObservationEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

interface WifiScanRunner {
    suspend fun performScanCycle(
        surveyId: UUID,
        spatialPositionId: UUID? = null
    ): Result<ScanCycleEntity>
}

@Singleton
class WifiScanCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val scanCycleDao: ScanCycleDao
) : WifiScanRunner {
    private val wifiManager: WifiManager? by lazy {
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    }

    /**
     * Executes a Wi-Fi scan cycle, maps hardware scan results to WifiObservationEntity list,
     * generates canonical Android UUIDs, and atomically persists ScanCycleEntity + WifiObservationEntity records in Room DB.
     */
    @SuppressLint("MissingPermission")
    override suspend fun performScanCycle(
        surveyId: UUID,
        spatialPositionId: UUID?
    ): Result<ScanCycleEntity> {
        val manager = wifiManager ?: return Result.failure(IllegalStateException("WifiManager service unavailable"))

        // startScan is asynchronous; the immediate scanResults read below is treated as freshness-unknown.
        val scanRequested = try {
            manager.startScan()
        } catch (e: Exception) {
            false
        }

        if (!scanRequested) {
            // Android may throttle active scans. Preserve whatever results are available, but do not call them fresh.
        }

        val rawResults: List<ScanResult> = try {
            manager.scanResults ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }

        val now = System.currentTimeMillis()
        val scanCycleId = UUID.randomUUID()

        val observations = rawResults.map { scanResult ->
            val bssidNormalized = WifiUtils.normalizeBssid(scanResult.BSSID)
            val calculatedChannel = WifiUtils.frequencyToChannel(scanResult.frequency)

            @Suppress("DEPRECATION")
            val ssidStr = if (scanResult.SSID.isNullOrBlank()) null else scanResult.SSID

            WifiObservationEntity(
                id = UUID.randomUUID(), // Canonical Android UUID
                scanCycleId = scanCycleId,
                ssid = ssidStr,
                bssid = bssidNormalized,
                rssiDbm = scanResult.level,
                frequencyMhz = scanResult.frequency,
                channel = calculatedChannel,
                channelSource = "frequency_conversion",
                capabilities = scanResult.capabilities,
                syncState = "pending"
            )
        }

        val scanCycle = ScanCycleEntity(
            id = scanCycleId, // Canonical Android UUID
            surveyId = surveyId,
            spatialPositionId = spatialPositionId,
            capturedAtWallclock = now,
            androidScanTimestampRaw = now,
            freshResults = false,
            createdAt = now,
            syncState = "pending"
        )

        return try {
            scanCycleDao.insertScanCycleWithObservations(scanCycle, observations)
            Result.success(scanCycle)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
