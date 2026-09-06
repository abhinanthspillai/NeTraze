package com.netraze.app.ui.canvas

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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

private fun hasLocationSurveyPermissions(context: Context): Boolean {
    val hasFine = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
    val hasCoarse = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_COARSE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
    val hasNearbyWifi = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.NEARBY_WIFI_DEVICES
        ) == PackageManager.PERMISSION_GRANTED
    return (hasFine || hasCoarse) && hasNearbyWifi
}

private fun locationSurveyPermissionsToRequest(): Array<String> {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.NEARBY_WIFI_DEVICES
        )
    } else {
        arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
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
    var pendingLocationSurveyScan by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val isEmulator = remember { checkIsEmulator() }
    val survey = uiState.survey
    val mode = survey?.mode ?: "location_survey"
    val modeText = mode.replace("_", " ").uppercase()
    val syncStateText = (survey?.syncState ?: "pending").uppercase()
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        if (hasLocationSurveyPermissions(context) && pendingLocationSurveyScan) {
            pendingLocationSurveyScan = false
            viewModel.addPositionAndScan(surveyId = surveyId, mode = mode)
        } else {
            pendingLocationSurveyScan = false
            viewModel.showPermissionDenied()
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

            // Action Buttons Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    PrimaryButton(
                        text = if (isEmulator) "Field Scan (Physical Only)" else if (uiState.isScanning) "Scanning..." else "+ Scan Wi-Fi",
                        onClick = {
                            if (!isEmulator) {
                                if (mode == "location_survey") {
                                    if (hasLocationSurveyPermissions(context)) {
                                        viewModel.addPositionAndScan(surveyId = surveyId, mode = mode)
                                    } else {
                                        pendingLocationSurveyScan = true
                                        locationPermissionLauncher.launch(locationSurveyPermissionsToRequest())
                                    }
                                } else {
                                    viewModel.addPositionAndScan(surveyId = surveyId, mode = mode)
                                }
                            }
                        },
                        enabled = !isEmulator && !uiState.isScanning,
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
                        val xVal = pos.position.floorPlanX ?: pos.position.simpleMapX ?: 0.0
                        val yVal = pos.position.floorPlanY ?: pos.position.simpleMapY ?: 0.0
                        InfoCard(
                            isHighEmphasis = false,
                            modifier = Modifier.fillMaxWidth().clickable { selectedPosition = pos }
                        ) {
                            Column {
                                Text(
                                    text = "$posLabel (X: $xVal, Y: $yVal)",
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
}
