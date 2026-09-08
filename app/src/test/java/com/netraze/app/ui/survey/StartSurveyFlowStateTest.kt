package com.netraze.app.ui.survey

import com.netraze.app.data.local.entity.SurveyEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class StartSurveyFlowStateTest {

    @Test
    fun testCreateNewSurveyIsAvailableWithoutExistingSurveys() {
        assertTrue(continueExistingSurveys(emptyList()).isEmpty())
        assertEquals(
            listOf(StartSurveySection.CreateNewSurvey),
            startSurveySectionOrder(hasContinueSurveys = false)
        )
    }

    @Test
    fun testContinueExistingAppearsOnlyWhenApplicable() {
        val active = survey(status = "in_progress", syncState = "synced")
        val pending = survey(status = "completed", syncState = "pending")
        val completed = survey(status = "completed", syncState = "synced")

        val result = continueExistingSurveys(listOf(active, pending, completed))

        assertEquals(listOf(active, pending), result)
    }

    @Test
    fun testCreateNewSurveyAppearsBeforePendingSurveys() {
        assertEquals(
            listOf(StartSurveySection.CreateNewSurvey, StartSurveySection.ContinueSurvey),
            startSurveySectionOrder(hasContinueSurveys = true)
        )
    }

    @Test
    fun testCascadeClearsStaleChildren() {
        val initial = SurveyStartSelection(
            projectId = UUID.randomUUID(),
            buildingId = UUID.randomUUID(),
            floorId = UUID.randomUUID(),
            surveyAreaId = UUID.randomUUID()
        )

        val afterProject = initial.selectProject(UUID.randomUUID())
        assertNull(afterProject.buildingId)
        assertNull(afterProject.floorId)
        assertNull(afterProject.surveyAreaId)

        val afterBuilding = initial.selectBuilding(UUID.randomUUID())
        assertNull(afterBuilding.floorId)
        assertNull(afterBuilding.surveyAreaId)

        val afterFloor = initial.selectFloor(UUID.randomUUID())
        assertNull(afterFloor.surveyAreaId)
    }

    @Test
    fun testContinueRequiresCompleteHierarchySelection() {
        val projectId = UUID.randomUUID()
        val buildingId = UUID.randomUUID()
        val floorId = UUID.randomUUID()
        val areaId = UUID.randomUUID()

        assertFalse(SurveyStartSelection(projectId = projectId).isComplete)
        assertFalse(SurveyStartSelection(projectId, buildingId, floorId, null).isComplete)
        assertTrue(SurveyStartSelection(projectId, buildingId, floorId, areaId).isComplete)
    }

    @Test
    fun testNewSurveyHierarchyDraftProgressivelyEnablesFields() {
        val empty = NewSurveyHierarchyDraft()
        assertFalse(empty.isProjectValid)
        assertFalse(empty.canEnterBuilding)
        assertFalse(empty.canEnterFloor)
        assertFalse(empty.canEnterSurveyArea)
        assertFalse(empty.isComplete)

        val projectOnly = empty.copy(projectName = " MCA Block Wi-Fi Survey ")
        assertTrue(projectOnly.canEnterBuilding)
        assertFalse(projectOnly.canEnterFloor)

        val withBuilding = projectOnly.copy(buildingName = "MCA Block")
        assertTrue(withBuilding.canEnterFloor)
        assertFalse(withBuilding.canEnterSurveyArea)

        val withFloor = withBuilding.copy(floorName = "Second Floor")
        assertTrue(withFloor.canEnterSurveyArea)
        assertFalse(withFloor.isComplete)

        assertTrue(withFloor.copy(surveyAreaName = "Lab 204").isComplete)
    }

    @Test
    fun testHierarchyDraftValuesAreTemporaryAndSurveyTitleIsSeparate() {
        val draft = NewSurveyHierarchyDraft(
            projectName = "College Network Survey",
            buildingName = "MCA Block",
            floorName = "Second Floor",
            surveyAreaName = "Lab 204"
        )
        val surveyTitle = "Morning Wi-Fi Test"

        assertTrue(draft.isComplete)
        assertEquals("College Network Survey", draft.projectName)
        assertEquals("Morning Wi-Fi Test", surveyTitle)
        assertFalse(draft.projectName == surveyTitle)
    }

    @Test
    fun testBackNavigationStages() {
        assertEquals(SurveyStartStage.SelectLocation, previousSurveyStartStage(SurveyStartStage.SurveyDetails))
        assertEquals(SurveyStartStage.Start, previousSurveyStartStage(SurveyStartStage.SelectLocation))
        assertNull(previousSurveyStartStage(SurveyStartStage.Start))
    }

    @Test
    fun testSurveyModeIdentifiersRemainSupported() {
        assertTrue(isSupportedSurveyMode("location_survey"))
        assertTrue(isSupportedSurveyMode("floor_plan"))
        assertTrue(isSupportedSurveyMode("simple_map"))
        assertFalse(isSupportedSurveyMode("simple-map"))
    }

    @Test
    fun testSurveyModeUiOptionsUseFriendlyLabelsWithInternalMappings() {
        val options = surveyModeUiOptions()

        assertEquals(listOf("Location Survey", "Floor Plan", "Simple Map"), options.map { it.title })
        assertEquals(listOf("location_survey", "floor_plan", "simple_map"), options.map { it.mode })
        assertTrue(options.all { "_" !in it.title && "_" !in it.description })
        assertTrue(options.all { "(" !in it.title && ")" !in it.title })
    }

    private fun survey(status: String, syncState: String): SurveyEntity {
        return SurveyEntity(
            id = UUID.randomUUID(),
            surveyAreaId = UUID.randomUUID(),
            title = "Survey",
            mode = "location_survey",
            status = status,
            floorPlanId = null,
            simpleMapId = null,
            createdBy = UUID.randomUUID(),
            startedAt = 1000L,
            completedAt = null,
            createdAt = 1000L,
            updatedAt = 1000L,
            syncState = syncState
        )
    }
}
