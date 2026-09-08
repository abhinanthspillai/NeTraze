package com.netraze.app.data.remote.dto

import com.google.gson.annotations.SerializedName
import java.util.UUID

data class SurveySyncRootDto(
    @SerializedName("id") val id: UUID? = null,
    @SerializedName("survey_area_id") val surveyAreaId: UUID,
    @SerializedName("title") val title: String? = null,
    @SerializedName("mode") val mode: String,
    @SerializedName("floor_plan_id") val floorPlanId: UUID? = null,
    @SerializedName("simple_map_id") val simpleMapId: UUID? = null,
    @SerializedName("started_at") val startedAt: String? = null
)

data class SpatialPositionSyncDto(
    @SerializedName("id") val id: UUID,
    @SerializedName("label") val label: String? = null,
    @SerializedName("floor_plan_x") val floorPlanX: Double? = null,
    @SerializedName("floor_plan_y") val floorPlanY: Double? = null,
    @SerializedName("simple_map_x") val simpleMapX: Double? = null,
    @SerializedName("simple_map_y") val simpleMapY: Double? = null,
    @SerializedName("latitude") val latitude: Double? = null,
    @SerializedName("longitude") val longitude: Double? = null,
    @SerializedName("accuracy_meters") val accuracyMeters: Double? = null,
    @SerializedName("captured_at") val capturedAt: String? = null,
    @SerializedName("created_at") val createdAt: String? = null
)

data class WifiObservationSyncDto(
    @SerializedName("id") val id: UUID,
    @SerializedName("scan_cycle_id") val scanCycleId: UUID,
    @SerializedName("ssid") val ssid: String? = null,
    @SerializedName("bssid") val bssid: String,
    @SerializedName("rssi_dbm") val rssiDbm: Int,
    @SerializedName("frequency_mhz") val frequencyMhz: Int,
    @SerializedName("channel") val channel: Int? = null,
    @SerializedName("channel_source") val channelSource: String = "frequency_conversion",
    @SerializedName("capabilities") val capabilities: String? = null
)

data class ScanCycleSyncDto(
    @SerializedName("id") val id: UUID,
    @SerializedName("spatial_position_id") val spatialPositionId: UUID? = null,
    @SerializedName("captured_at_wallclock") val capturedAtWallclock: String,
    @SerializedName("android_scan_timestamp_raw") val androidScanTimestampRaw: Long? = null,
    @SerializedName("fresh_results") val freshResults: Boolean = true,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("observations") val observations: List<WifiObservationSyncDto> = emptyList()
)

data class SurveySyncPayloadDto(
    @SerializedName("survey") val survey: SurveySyncRootDto? = null,
    @SerializedName("spatial_positions") val spatialPositions: List<SpatialPositionSyncDto> = emptyList(),
    @SerializedName("scan_cycles") val scanCycles: List<ScanCycleSyncDto> = emptyList()
)

data class SurveySyncResultDto(
    @SerializedName("survey_id") val surveyId: UUID,
    @SerializedName("ingested_spatial_positions") val ingestedSpatialPositions: Int,
    @SerializedName("ingested_scan_cycles") val ingestedScanCycles: Int,
    @SerializedName("ingested_wifi_observations") val ingestedWifiObservations: Int
)
