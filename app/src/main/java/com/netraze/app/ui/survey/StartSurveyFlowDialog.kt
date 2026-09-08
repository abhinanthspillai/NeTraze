package com.netraze.app.ui.survey

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.netraze.app.SurveyLocationContext
import com.netraze.app.data.local.entity.SurveyEntity
import com.netraze.app.ui.components.AppTextField
import com.netraze.app.ui.components.InfoCard
import com.netraze.app.ui.components.PrimaryButton
import com.netraze.app.ui.hierarchy.HierarchyViewModel
import com.netraze.app.ui.theme.NetrazeTypography
import com.netraze.app.ui.theme.PrimaryDark
import com.netraze.app.ui.theme.SurfaceLight
import com.netraze.app.ui.theme.TextPrimary
import com.netraze.app.ui.theme.TextSecondary

@Composable
fun StartSurveyFlowDialog(
    hierarchyViewModel: HierarchyViewModel,
    existingSurveys: List<SurveyEntity>,
    onDismiss: () -> Unit,
    onContinueSurvey: (SurveyEntity) -> Unit,
    onCreateSurvey: (SurveyLocationContext, String, String) -> Unit
) {
    val error by hierarchyViewModel.error.collectAsStateWithLifecycle()
    val isLoading by hierarchyViewModel.isLoading.collectAsStateWithLifecycle()

    var stage by remember { mutableStateOf(SurveyStartStage.Start) }
    var hierarchyDraft by remember { mutableStateOf(NewSurveyHierarchyDraft()) }
    var surveyTitle by remember { mutableStateOf("") }
    var selectedMode by remember { mutableStateOf("location_survey") }

    fun goBack() {
        val previousStage = previousSurveyStartStage(stage)
        if (previousStage != null) {
            stage = previousStage
        } else {
            onDismiss()
        }
    }

    BackHandler(enabled = true) {
        goBack()
    }

    val inProgressSurveys = continueExistingSurveys(existingSurveys)
    val canStartSurvey = surveyTitle.isNotBlank() && hierarchyDraft.isComplete && !isLoading

    AlertDialog(
        onDismissRequest = { goBack() },
        containerColor = SurfaceLight,
        title = {
            Text(
                text = when (stage) {
                    SurveyStartStage.Start -> "Start Survey"
                    SurveyStartStage.SelectLocation -> "Create New Survey"
                    SurveyStartStage.SurveyDetails -> "Survey Details"
                },
                style = NetrazeTypography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = if (stage == SurveyStartStage.Start) 460.dp else 520.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (error != null) {
                    item {
                        Text(
                            text = error ?: "",
                            style = NetrazeTypography.bodySmall,
                            color = TextPrimary
                        )
                    }
                }
                when (stage) {
                    SurveyStartStage.Start -> {
                        item {
                            Text(
                                text = "Create new survey",
                                style = NetrazeTypography.titleMedium,
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        item {
                            PrimaryButton(
                                text = "Create New Survey",
                                onClick = { stage = SurveyStartStage.SelectLocation },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        if (inProgressSurveys.isNotEmpty()) {
                            item {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Continue survey",
                                    style = NetrazeTypography.titleMedium,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            items(inProgressSurveys.take(4)) { survey ->
                                InfoCard(
                                    isHighEmphasis = false,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onContinueSurvey(survey) }
                                ) {
                                    Text(
                                        text = survey.title.ifBlank { "Untitled Survey" },
                                        style = NetrazeTypography.titleMedium,
                                        color = TextPrimary,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = survey.mode.replace("_", " "),
                                        style = NetrazeTypography.bodySmall,
                                        color = TextSecondary
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    PrimaryButton(
                                        text = "Continue",
                                        onClick = { onContinueSurvey(survey) },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                    SurveyStartStage.SelectLocation -> {
                        item {
                            AppTextField(
                                value = hierarchyDraft.projectName,
                                onValueChange = { hierarchyDraft = hierarchyDraft.copy(projectName = it) },
                                label = "Project Name",
                                placeholder = "Enter project name",
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        if (hierarchyDraft.canEnterBuilding) {
                            item {
                                AppTextField(
                                    value = hierarchyDraft.buildingName,
                                    onValueChange = { hierarchyDraft = hierarchyDraft.copy(buildingName = it) },
                                    label = "Building Name",
                                    placeholder = "Enter building name",
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                        if (hierarchyDraft.canEnterFloor) {
                            item {
                                AppTextField(
                                    value = hierarchyDraft.floorName,
                                    onValueChange = { hierarchyDraft = hierarchyDraft.copy(floorName = it) },
                                    label = "Floor Name",
                                    placeholder = "Enter floor name",
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                        if (hierarchyDraft.canEnterSurveyArea) {
                            item {
                                AppTextField(
                                    value = hierarchyDraft.surveyAreaName,
                                    onValueChange = { hierarchyDraft = hierarchyDraft.copy(surveyAreaName = it) },
                                    label = "Survey Area Name",
                                    placeholder = "Enter survey area name",
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                    SurveyStartStage.SurveyDetails -> {
                        item {
                            AppTextField(
                                value = surveyTitle,
                                onValueChange = { surveyTitle = it },
                                label = "Survey Title",
                                placeholder = "Enter survey title",
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        item {
                            Text(
                                text = "Survey Mode",
                                style = NetrazeTypography.labelMedium,
                                color = TextPrimary
                            )
                        }
                        item {
                            SurveyModeOption("Location Survey", "location_survey", Icons.Rounded.Place, selectedMode) {
                                selectedMode = "location_survey"
                            }
                            SurveyModeOption("Floor Plan", "floor_plan", Icons.Rounded.Map, selectedMode) {
                                selectedMode = "floor_plan"
                            }
                            SurveyModeOption("Simple Map", "simple_map", Icons.Rounded.Navigation, selectedMode) {
                                selectedMode = "simple_map"
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            when (stage) {
                SurveyStartStage.Start -> {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = TextSecondary)
                    }
                }
                SurveyStartStage.SelectLocation -> {
                    TextButton(
                        onClick = { stage = SurveyStartStage.SurveyDetails },
                        enabled = hierarchyDraft.isComplete
                    ) {
                        Text("Continue", color = PrimaryDark, fontWeight = FontWeight.Bold)
                    }
                }
                SurveyStartStage.SurveyDetails -> {
                    TextButton(
                        onClick = {
                            hierarchyViewModel.createLocationHierarchy(
                                projectName = hierarchyDraft.projectName,
                                buildingName = hierarchyDraft.buildingName,
                                floorName = hierarchyDraft.floorName,
                                surveyAreaName = hierarchyDraft.surveyAreaName
                            ) { project, building, floor, area ->
                                onCreateSurvey(
                                    SurveyLocationContext(
                                        surveyArea = area,
                                        floor = floor,
                                        building = building,
                                        project = project
                                    ),
                                    surveyTitle.trim(),
                                    selectedMode
                                )
                            }
                        },
                        enabled = canStartSurvey
                    ) {
                        Text(
                            text = if (isLoading) "Starting..." else "Start Survey",
                            color = PrimaryDark,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        },
        dismissButton = {
            if (stage != SurveyStartStage.Start) {
                TextButton(onClick = { goBack() }) {
                    Text("Back", color = TextSecondary)
                }
            }
        }
    )
}

@Composable
private fun SurveyModeOption(
    label: String,
    mode: String,
    icon: ImageVector,
    selectedMode: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        RadioButton(
            selected = selectedMode == mode,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(selectedColor = PrimaryDark)
        )
        Text(
            text = "$label ($mode)",
            style = NetrazeTypography.bodyMedium,
            color = TextPrimary
        )
    }
}
