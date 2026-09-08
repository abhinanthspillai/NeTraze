package com.netraze.app.ui.canvas

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.netraze.app.data.local.dao.ScanCycleDao
import com.netraze.app.data.local.dao.SpatialPositionDao
import com.netraze.app.data.local.dao.SurveyDao
import com.netraze.app.data.local.dao.WifiObservationDao
import com.netraze.app.data.local.entity.ScanCycleEntity
import com.netraze.app.data.local.entity.SpatialPositionEntity
import com.netraze.app.data.local.entity.SurveyEntity
import com.netraze.app.data.local.entity.WifiObservationEntity
import com.netraze.app.data.location.LocationProvider
import com.netraze.app.data.wifi.WifiScanRunner
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class PositionWithObservations(
    val position: SpatialPositionEntity,
    val cycles: List<ScanCycleEntity>,
    val observations: List<WifiObservationEntity>
)

data class SurveyCanvasUiState(
    val survey: SurveyEntity? = null,
    val positions: List<PositionWithObservations> = emptyList(),
    val totalObservationsCount: Int = 0,
    val uniqueBssidCount: Int = 0,
    val maxRssi: Int? = null,
    val avgRssi: Double? = null,
    val isLoading: Boolean = false,
    val isScanning: Boolean = false,
    val error: String? = null
)

private const val MODE_FLOOR_PLAN = "floor_plan"
private const val MODE_SIMPLE_MAP = "simple_map"
private const val MODE_LOCATION_SURVEY = "location_survey"

@HiltViewModel
class SurveyCanvasViewModel @Inject constructor(
    private val surveyDao: SurveyDao,
    private val spatialPositionDao: SpatialPositionDao,
    private val scanCycleDao: ScanCycleDao,
    private val wifiObservationDao: WifiObservationDao,
    private val wifiScanRunner: WifiScanRunner,
    private val locationProvider: LocationProvider
) : ViewModel() {

    private val _uiState = MutableStateFlow(SurveyCanvasUiState())
    val uiState: StateFlow<SurveyCanvasUiState> = _uiState.asStateFlow()

    fun loadSurveyCanvasData(surveyId: UUID) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val survey = surveyDao.getSurveyById(surveyId)
                val rawPositions = spatialPositionDao.getSpatialPositionsForSurvey(surveyId)

                val posWithObs = rawPositions.map { pos ->
                    val cycles = scanCycleDao.getScanCyclesForPosition(pos.id)
                    val obsList = cycles.flatMap { cycle ->
                        wifiObservationDao.getObservationsForCycle(cycle.id)
                    }
                    PositionWithObservations(pos, cycles, obsList)
                }

                val allObs = posWithObs.flatMap { it.observations }
                val totalObs = allObs.size
                val uniqueBssids = allObs.map { it.bssid }.distinct().size
                val maxRssiVal = allObs.maxOfOrNull { it.rssiDbm }
                val avgRssiVal = if (allObs.isNotEmpty()) allObs.map { it.rssiDbm }.average() else null

                _uiState.value = SurveyCanvasUiState(
                    survey = survey,
                    positions = posWithObs,
                    totalObservationsCount = totalObs,
                    uniqueBssidCount = uniqueBssids,
                    maxRssi = maxRssiVal,
                    avgRssi = avgRssiVal,
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to load canvas data"
                )
            }
        }
    }

    fun addPositionAndScan(
        surveyId: UUID,
        mode: String,
        x: Double? = null,
        y: Double? = null,
        label: String? = null
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isScanning = true, error = null)
            try {
                val now = System.currentTimeMillis()
                val posId = UUID.randomUUID()
                val count = _uiState.value.positions.size + 1
                val posLabel = label ?: "Point $count"

                val posEntity = when (mode) {
                    MODE_FLOOR_PLAN -> {
                        require(x != null && y != null) {
                            "Tap the normalized floor-plan workspace to choose a sampling position."
                        }
                        SpatialPositionEntity(
                            id = posId,
                            surveyId = surveyId,
                            label = posLabel,
                            floorPlanX = x.coerceIn(0.0, 1.0),
                            floorPlanY = y.coerceIn(0.0, 1.0),
                            simpleMapX = null,
                            simpleMapY = null,
                            latitude = null,
                            longitude = null,
                            accuracyMeters = null,
                            capturedAt = null,
                            createdAt = now,
                            syncState = "pending"
                        )
                    }
                    MODE_SIMPLE_MAP -> {
                        require(x != null && y != null) {
                            "Tap the simple map workspace to choose a sampling position."
                        }
                        SpatialPositionEntity(
                            id = posId,
                            surveyId = surveyId,
                            label = posLabel,
                            floorPlanX = null,
                            floorPlanY = null,
                            simpleMapX = x.coerceIn(0.0, 1.0),
                            simpleMapY = y.coerceIn(0.0, 1.0),
                            latitude = null,
                            longitude = null,
                            accuracyMeters = null,
                            capturedAt = null,
                            createdAt = now,
                            syncState = "pending"
                        )
                    }
                    else -> {
                        val locationFix = locationProvider.getCurrentLocation()
                        SpatialPositionEntity(
                            id = posId,
                            surveyId = surveyId,
                            label = posLabel,
                            floorPlanX = null,
                            floorPlanY = null,
                            simpleMapX = null,
                            simpleMapY = null,
                            latitude = locationFix.latitude,
                            longitude = locationFix.longitude,
                            accuracyMeters = locationFix.accuracyMeters,
                            capturedAt = locationFix.capturedAt,
                            createdAt = now,
                            syncState = "pending"
                        )
                    }
                }

                spatialPositionDao.insertSpatialPosition(posEntity)

                // Execute real Wi-Fi hardware scan cycle
                wifiScanRunner.performScanCycle(surveyId, posId)

                // Reload UI state
                loadSurveyCanvasData(surveyId)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isScanning = false,
                    error = e.message ?: "Failed to add position and scan"
                )
            }
        }
    }

    fun scanExistingPosition(surveyId: UUID, spatialPositionId: UUID) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isScanning = true, error = null)
            try {
                wifiScanRunner.performScanCycle(surveyId, spatialPositionId)
                loadSurveyCanvasData(surveyId)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isScanning = false,
                    error = e.message ?: "Failed to add scan cycle"
                )
            }
        }
    }

    fun showPermissionDenied() {
        _uiState.value = _uiState.value.copy(
            isScanning = false,
            error = "Location and Wi-Fi permissions are required to add a Location Survey point."
        )
    }
}
