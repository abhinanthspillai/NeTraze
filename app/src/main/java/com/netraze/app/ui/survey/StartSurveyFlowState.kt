package com.netraze.app.ui.survey

import com.netraze.app.data.local.entity.SurveyEntity
import java.util.UUID

enum class SurveyStartStage {
    Start,
    SelectLocation,
    SurveyDetails
}

enum class StartSurveySection {
    CreateNewSurvey,
    ContinueSurvey
}

data class NewSurveyHierarchyDraft(
    val projectName: String = "",
    val buildingName: String = "",
    val floorName: String = "",
    val surveyAreaName: String = ""
) {
    val isProjectValid: Boolean
        get() = projectName.trim().isNotBlank()

    val isBuildingValid: Boolean
        get() = buildingName.trim().isNotBlank()

    val isFloorValid: Boolean
        get() = floorName.trim().isNotBlank()

    val isSurveyAreaValid: Boolean
        get() = surveyAreaName.trim().isNotBlank()

    val canEnterBuilding: Boolean
        get() = isProjectValid

    val canEnterFloor: Boolean
        get() = isProjectValid && isBuildingValid

    val canEnterSurveyArea: Boolean
        get() = isProjectValid && isBuildingValid && isFloorValid

    val isComplete: Boolean
        get() = canEnterSurveyArea && isSurveyAreaValid
}

data class SurveyStartSelection(
    val projectId: UUID? = null,
    val buildingId: UUID? = null,
    val floorId: UUID? = null,
    val surveyAreaId: UUID? = null
) {
    val isComplete: Boolean
        get() = projectId != null && buildingId != null && floorId != null && surveyAreaId != null

    fun selectProject(id: UUID): SurveyStartSelection {
        return copy(projectId = id, buildingId = null, floorId = null, surveyAreaId = null)
    }

    fun selectBuilding(id: UUID): SurveyStartSelection {
        return copy(buildingId = id, floorId = null, surveyAreaId = null)
    }

    fun selectFloor(id: UUID): SurveyStartSelection {
        return copy(floorId = id, surveyAreaId = null)
    }

    fun selectSurveyArea(id: UUID): SurveyStartSelection {
        return copy(surveyAreaId = id)
    }
}

fun previousSurveyStartStage(stage: SurveyStartStage): SurveyStartStage? {
    return when (stage) {
        SurveyStartStage.SurveyDetails -> SurveyStartStage.SelectLocation
        SurveyStartStage.SelectLocation -> SurveyStartStage.Start
        SurveyStartStage.Start -> null
    }
}

fun isSupportedSurveyMode(mode: String): Boolean {
    return mode == "location_survey" || mode == "floor_plan" || mode == "simple_map"
}

fun continueExistingSurveys(surveys: List<SurveyEntity>): List<SurveyEntity> {
    return surveys.filter {
        it.status.equals("in_progress", ignoreCase = true) ||
            it.syncState.equals("pending", ignoreCase = true)
    }
}

fun startSurveySectionOrder(hasContinueSurveys: Boolean): List<StartSurveySection> {
    return if (hasContinueSurveys) {
        listOf(StartSurveySection.CreateNewSurvey, StartSurveySection.ContinueSurvey)
    } else {
        listOf(StartSurveySection.CreateNewSurvey)
    }
}
