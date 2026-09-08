package com.netraze.app.ui.canvas

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.netraze.app.ui.components.InfoCard
import com.netraze.app.ui.components.PrimaryButton
import com.netraze.app.ui.theme.NetrazeTypography
import com.netraze.app.ui.theme.PrimaryDark
import com.netraze.app.ui.theme.SurfaceLight
import com.netraze.app.ui.theme.TextPrimary
import com.netraze.app.ui.theme.TextSecondary
import java.util.UUID

private data class PendingScanRequest(
    val mode: String,
    val x: Double? = null,
    val y: Double? = null,
    val spatialPositionId: UUID? = null
)

private fun checkIsEmulator(): Boolean {
    return (Build.FINGERPRINT.startsWith("generic") ||
            Build.FINGERPRINT.startsWith("unknown") ||
            Build.MODEL.contains("google_sdk") ||
            Build.MODEL.contains("Emulator") ||
            Build.MODEL.contains("Android SDK built for x86") ||
            Build.MANUFACTURER.contains("Genymotion") ||
            (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic")) ||
            "google_sdk" == Build.PRODUCT)
}

private fun hasLocationPermission(context: Context): Boolean {
    val hasFine = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
    val hasCoarse = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_COARSE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
    return hasFine || hasCoarse
}

private fun hasWifiScanPermission(context: Context): Boolean {
    return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.NEARBY_WIFI_DEVICES
        ) == PackageManager.PERMISSION_GRANTED
}

private fun hasScanPermissions(context: Context, mode: String): Boolean {
    val hasModePermissions = mode != "location_survey" || hasLocationPermission(context)
    return hasModePermissions && hasWifiScanPermission(context)
}

private fun permissionsToRequest(mode: String): Array<String> {
    val permissions = mutableListOf<String>()
    if (mode == "location_survey") {
        permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
    }
    return permissions.toTypedArray()
}

private fun modeSupportsWorkspace(mode: String): Boolean = mode == "floor_plan" || mode == "simple_map"

private fun pointCoordinatesForMode(
    position: PositionWithObservations,
    mode: String
): Pair<Double, Double>? {
    return when (mode) {
        "floor_plan" -> {
            val x = position.position.floorPlanX
            val y = position.position.floorPlanY
            if (x != null && y != null) x to y else null
        }
        "simple_map" -> {
            val x = position.position.simpleMapX
            val y = position.position.simpleMapY
            if (x != null && y != null) x to y else null
        }
        else -> null
    }
}

@Composable
private fun NormalizedSpatialWorkspace(
    mode: String,
    positions: List<PositionWithObservations>,
    enabled: Boolean,
    onPointSelected: (Double, Double) -> Unit
) {
    val workspaceTitle = if (mode == "floor_plan") "Normalized Floor Plan Workspace" else "Simple Map Workspace"
    val helperText = if (mode == "floor_plan") {
        "Tap the grid to place a sampling point. This is a normalized placeholder, not an uploaded architectural plan."
    } else {
        "Tap the workspace to place a relative sampling point for this area."
    }

    InfoCard(isHighEmphasis = false) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = workspaceTitle,
                style = NetrazeTypography.titleMedium,
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = helperText,
                style = NetrazeTypography.bodySmall,
                color = TextSecondary
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.35f)
                    .background(Color.White, RoundedCornerShape(12.dp))
                    .pointerInput(mode, enabled) {
                        if (enabled) {
                            detectTapGestures { offset ->
                                val normalizedX = (offset.x / size.width).toDouble().coerceIn(0.0, 1.0)
                                val normalizedY = (offset.y / size.height).toDouble().coerceIn(0.0, 1.0)
                                onPointSelected(normalizedX, normalizedY)
                            }
                        }
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val gridColor = Color(0xFFE2E8E2)
                    val markerColor = Color(0xFF244238)
                    for (i in 1..3) {
                        val x = size.width * i / 4f
                        val y = size.height * i / 4f
                        drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.5f)
                        drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.5f)
                    }
                    positions.forEach { item ->
                        val coords = pointCoordinatesForMode(item, mode) ?: return@forEach
                        drawCircle(
                            color = markerColor,
                            radius = 10.dp.toPx(),
                            center = Offset(
                                (coords.first.toFloat() * size.width).coerceIn(0f, size.width),
                                (coords.second.toFloat() * size.height).coerceIn(0f, size.height)
                            )
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SurveyCanvasScreen(
    viewModel: SurveyCanvasViewModel,
    surveyId: UUID,
    onBackClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedPosition by remember { mutableStateOf<PositionWithObservations?>(null) }
    var showRawEvidenceDialog by remember { mutableStateOf(false) }
    var pendingScanRequest by remember { mutableStateOf<PendingScanRequest?>(null) }
    val context = LocalContext.current
    val isEmulator = remember { checkIsEmulator() }
    val survey = uiState.survey
    val mode = survey?.mode ?: "location_survey"
    val modeText = mode.replace("_", " ").uppercase()
    val syncStateText = (survey?.syncState ?: "pending").uppercase()
    fun executeScanRequest(request: PendingScanRequest) {
        if (request.spatialPositionId != null) {
            viewModel.scanExistingPosition(surveyId = surveyId, spatialPositionId = request.spatialPositionId)
        } else {
            viewModel.addPositionAndScan(
                surveyId = surveyId,
                mode = request.mode,
                x = request.x,
                y = request.y
            )
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        val request = pendingScanRequest
        pendingScanRequest = null
        if (request != null && hasScanPermissions(context, request.mode)) {
            executeScanRequest(request)
        } else {
            viewModel.showPermissionDenied()
        }
    }
    fun startOrRequestScan(request: PendingScanRequest) {
        if (hasScanPermissions(context, request.mode)) {
            executeScanRequest(request)
        } else {
            pendingScanRequest = request
            locationPermissionLauncher.launch(permissionsToRequest(request.mode))
        }
    }

    LaunchedEffect(surveyId) {
        viewModel.loadSurveyCanvasData(surveyId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = survey?.title?.ifBlank { "Survey Workspace" } ?: "Survey Workspace",
                            style = NetrazeTypography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Mode: $modeText | Sync: $syncStateText | Status: ${survey?.status?.uppercase() ?: "IN_PROGRESS"}",
                            style = NetrazeTypography.bodySmall,
                            color = TextSecondary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(imageVector = Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceLight)
            )
        },
        containerColor = SurfaceLight
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Spatial Analytics Summary Card
            InfoCard(isHighEmphasis = true) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(text = "Positions", style = NetrazeTypography.labelSmall, color = TextPrimary)
                        Text(text = "${uiState.positions.size}", style = NetrazeTypography.titleMedium, color = TextPrimary, fontWeight = FontWeight.Bold)
                    }
                    Column {
                        Text(text = "Observations", style = NetrazeTypography.labelSmall, color = TextPrimary)
                        Text(text = "${uiState.totalObservationsCount}", style = NetrazeTypography.titleMedium, color = TextPrimary, fontWeight = FontWeight.Bold)
                    }
                    Column {
                        Text(text = "Unique APs", style = NetrazeTypography.labelSmall, color = TextPrimary)
                        Text(text = "${uiState.uniqueBssidCount}", style = NetrazeTypography.titleMedium, color = TextPrimary, fontWeight = FontWeight.Bold)
                    }
                    Column {
                        Text(text = "Max RSSI", style = NetrazeTypography.labelSmall, color = TextPrimary)
                        Text(
                            text = if (uiState.maxRssi != null) "${uiState.maxRssi} dBm" else "N/A",
                            style = NetrazeTypography.titleMedium,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Emulator Device Notification Banner
            if (isEmulator) {
                InfoCard(isHighEmphasis = false) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Rounded.Info, contentDescription = "Info", tint = TextPrimary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Wi-Fi field scanning is available on a physical Android device.",
                            style = NetrazeTypography.bodySmall,
                            color = TextPrimary
                        )
                    }
                }
            }

            if (modeSupportsWorkspace(mode)) {
                NormalizedSpatialWorkspace(
                    mode = mode,
                    positions = uiState.positions,
                    enabled = !isEmulator && !uiState.isScanning,
                    onPointSelected = { x, y ->
                        startOrRequestScan(PendingScanRequest(mode = mode, x = x, y = y))
                    }
                )
            }

            // Action Buttons Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    PrimaryButton(
                        text = if (isEmulator) {
                            "Field Scan (Physical Only)"
                        } else if (uiState.isScanning) {
                            "Scanning..."
                        } else if (modeSupportsWorkspace(mode)) {
                            "Tap Workspace to Add Point"
                        } else {
                            "+ Scan Wi-Fi"
                        },
                        onClick = {
                            if (!isEmulator && !modeSupportsWorkspace(mode)) {
                                startOrRequestScan(PendingScanRequest(mode = mode))
                            }
                        },
                        enabled = !isEmulator && !uiState.isScanning && !modeSupportsWorkspace(mode),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Box(modifier = Modifier.weight(1f)) {
                    PrimaryButton(
                        text = "Raw Evidence",
                        onClick = { showRawEvidenceDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            if (uiState.error != null) {
                InfoCard(isHighEmphasis = false) {
                    Text(
                        text = uiState.error ?: "",
                        style = NetrazeTypography.bodyMedium,
                        color = TextPrimary
                    )
                }
            }

            if (uiState.isLoading || uiState.isScanning) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = PrimaryDark)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (uiState.isScanning) "Executing Wi-Fi scan cycle..." else "Loading data...",
                            style = NetrazeTypography.bodyMedium,
                            color = PrimaryDark,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            } else if (uiState.positions.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isEmulator)
                            "Survey metadata loaded.\nField Wi-Fi radio measurements require physical Android device hardware."
                        else if (modeSupportsWorkspace(mode))
                            "No spatial positions recorded yet.\nTap the workspace to add a sampling position."
                        else
                            "No spatial positions recorded yet.\nTap '+ Scan Wi-Fi' to add sampling positions.",
                        style = NetrazeTypography.bodyMedium,
                        color = TextSecondary
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.positions) { pos ->
                        val posLabel = pos.position.label ?: "Position"
                        InfoCard(
                            isHighEmphasis = false,
                            modifier = Modifier.fillMaxWidth().clickable { selectedPosition = pos }
                        ) {
                            Column {
                                Text(
                                    text = if (modeSupportsWorkspace(mode)) {
                                        val coords = pointCoordinatesForMode(pos, mode)
                                        if (coords != null) {
                                            "$posLabel (X: %.3f, Y: %.3f)".format(coords.first, coords.second)
                                        } else {
                                            "$posLabel (Position unavailable)"
                                        }
                                    } else {
                                        val lat = pos.position.latitude
                                        val lon = pos.position.longitude
                                        if (lat != null && lon != null) {
                                            "$posLabel (Lat: %.5f, Lon: %.5f)".format(lat, lon)
                                        } else {
                                            posLabel
                                        }
                                    },
                                    style = NetrazeTypography.titleMedium,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Observations recorded: ${pos.observations.size}",
                                    style = NetrazeTypography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showRawEvidenceDialog) {
        AlertDialog(
            onDismissRequest = { showRawEvidenceDialog = false },
            containerColor = SurfaceLight,
            title = { Text("Raw Wi-Fi Evidence", style = NetrazeTypography.titleLarge, fontWeight = FontWeight.Bold, color = TextPrimary) },
            text = {
                if (uiState.positions.isEmpty()) {
                    Text("No raw Wi-Fi evidence recorded for this survey.", style = NetrazeTypography.bodyMedium, color = TextSecondary)
                } else {
                    LazyColumn(modifier = Modifier.height(280.dp)) {
                        items(uiState.positions.flatMap { it.observations }) { obs ->
                            val chVal = obs.channel ?: "N/A"
                            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                Text(text = "SSID: ${obs.ssid ?: "Hidden"} | BSSID: ${obs.bssid}", style = NetrazeTypography.labelMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
                                Text(text = "RSSI: ${obs.rssiDbm} dBm | Freq: ${obs.frequencyMhz} MHz | Ch: $chVal", style = NetrazeTypography.bodySmall, color = TextSecondary)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showRawEvidenceDialog = false }) {
                    Text("Close", color = PrimaryDark, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    if (selectedPosition != null) {
        val position = selectedPosition!!
        val posLabel = position.position.label ?: "Position"
        AlertDialog(
            onDismissRequest = { selectedPosition = null },
            containerColor = SurfaceLight,
            title = {
                Text(
                    text = posLabel,
                    style = NetrazeTypography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Scan cycles recorded: ${position.cycles.size}",
                        style = NetrazeTypography.bodyMedium,
                        color = TextPrimary
                    )
                    Text(
                        text = "Wi-Fi observations recorded: ${position.observations.size}",
                        style = NetrazeTypography.bodyMedium,
                        color = TextPrimary
                    )
                    if (modeSupportsWorkspace(mode)) {
                        val coords = pointCoordinatesForMode(position, mode)
                        if (coords != null) {
                            Text(
                                text = "Normalized position: X %.3f, Y %.3f".format(coords.first, coords.second),
                                style = NetrazeTypography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val request = PendingScanRequest(
                            mode = mode,
                            spatialPositionId = position.position.id
                        )
                        selectedPosition = null
                        startOrRequestScan(request)
                    },
                    enabled = !isEmulator && !uiState.isScanning
                ) {
                    Text("Scan Again", color = PrimaryDark, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedPosition = null }) {
                    Text("Close", color = TextSecondary)
                }
            }
        )
    }
}
