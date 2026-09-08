package com.netraze.app.ui.survey

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
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
    var preparedLocation by remember { mutableStateOf<SurveyLocationContext?>(null) }

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
                            surveyModeUiOptions().forEach { option ->
                                SurveyModeOption(
                                    title = option.title,
                                    description = option.description,
                                    icon = when (option.mode) {
                                        "floor_plan" -> Icons.Rounded.Map
                                        "simple_map" -> Icons.Rounded.Navigation
                                        else -> Icons.Rounded.Place
                                    },
                                    selected = selectedMode == option.mode,
                                    onClick = { selectedMode = option.mode }
                                )
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
                            val existingLocation = preparedLocation
                            if (existingLocation != null) {
                                onCreateSurvey(existingLocation, surveyTitle.trim(), selectedMode)
                            } else {
                                hierarchyViewModel.createLocationHierarchy(
                                    projectName = hierarchyDraft.projectName,
                                    buildingName = hierarchyDraft.buildingName,
                                    floorName = hierarchyDraft.floorName,
                                    surveyAreaName = hierarchyDraft.surveyAreaName
                                ) { project, building, floor, area ->
                                    val location = SurveyLocationContext(
                                        surveyArea = area,
                                        floor = floor,
                                        building = building,
                                        project = project
                                    )
                                    preparedLocation = location
                                    onCreateSurvey(location, surveyTitle.trim(), selectedMode)
                                }
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
    title: String,
    description: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) PrimaryDark else TextSecondary.copy(alpha = 0.2f),
                shape = RoundedCornerShape(12.dp)
            )
            .background(
                if (selected) PrimaryDark.copy(alpha = 0.05f) else Color.Transparent,
                RoundedCornerShape(12.dp)
            )
            .clickable { onClick() }
            .padding(12.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            colors = RadioButtonDefaults.colors(selectedColor = PrimaryDark, unselectedColor = TextSecondary)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = if (selected) PrimaryDark else TextSecondary
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(
                text = title,
                style = NetrazeTypography.labelLarge,
                color = if (selected) PrimaryDark else TextPrimary
            )
            Text(
                text = description,
                style = NetrazeTypography.bodySmall,
                color = TextSecondary
            )
        }
    }
}
