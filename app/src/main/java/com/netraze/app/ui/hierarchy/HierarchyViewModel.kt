package com.netraze.app.ui.hierarchy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.netraze.app.data.local.entity.BuildingEntity
import com.netraze.app.data.local.entity.FloorEntity
import com.netraze.app.data.local.entity.ProjectEntity
import com.netraze.app.data.local.entity.SurveyAreaEntity
import com.netraze.app.data.repository.HierarchyRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

class HierarchyViewModel @Inject constructor(
    private var repository: HierarchyRepository?
) : ViewModel() {

    constructor() : this(null)

    private val _projects = MutableStateFlow<List<ProjectEntity>>(emptyList())
    val projects: StateFlow<List<ProjectEntity>> = _projects.asStateFlow()

    private val _buildings = MutableStateFlow<List<BuildingEntity>>(emptyList())
    val buildings: StateFlow<List<BuildingEntity>> = _buildings.asStateFlow()

    private val _floors = MutableStateFlow<List<FloorEntity>>(emptyList())
    val floors: StateFlow<List<FloorEntity>> = _floors.asStateFlow()

    private val _surveyAreas = MutableStateFlow<List<SurveyAreaEntity>>(emptyList())
    val surveyAreas: StateFlow<List<SurveyAreaEntity>> = _surveyAreas.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun setRepository(hierarchyRepository: HierarchyRepository) {
        this.repository = hierarchyRepository
    }

    fun clearError() {
        _error.update { null }
    }

    fun loadProjects() {
        val repo = repository ?: return
        _isLoading.update { true }
        _error.update { null }
        viewModelScope.launch {
            val result = repo.getProjects()
            _isLoading.update { false }
            result.onSuccess { list ->
                _projects.update { list }
            }.onFailure { ex ->
                _error.update { ex.message ?: "Failed to load projects" }
            }
        }
    }

    fun createProject(name: String, onSuccess: () -> Unit = {}) {
        val repo = repository ?: return
        if (name.isBlank()) return
        _isLoading.update { true }
        _error.update { null }
        viewModelScope.launch {
            val result = repo.createProject(name)
            _isLoading.update { false }
            result.onSuccess {
                loadProjects()
                onSuccess()
            }.onFailure { ex ->
                _error.update { ex.message ?: "Failed to create project" }
            }
        }
    }

    fun createProjectAndSelect(name: String, onSuccess: (ProjectEntity) -> Unit = {}) {
        val repo = repository ?: return
        if (name.isBlank()) return
        _isLoading.update { true }
        _error.update { null }
        viewModelScope.launch {
            val result = repo.createProject(name)
            _isLoading.update { false }
            result.onSuccess { project ->
                loadProjects()
                onSuccess(project)
            }.onFailure { ex ->
                _error.update { ex.message ?: "Failed to create project" }
            }
        }
    }

    fun loadBuildings(projectId: UUID) {
        val repo = repository ?: return
        _isLoading.update { true }
        _error.update { null }
        viewModelScope.launch {
            val result = repo.getBuildings(projectId)
            _isLoading.update { false }
            result.onSuccess { list ->
                _buildings.update { list }
            }.onFailure { ex ->
                _error.update { ex.message ?: "Failed to load buildings" }
            }
        }
    }

    fun createBuilding(projectId: UUID, name: String, onSuccess: () -> Unit = {}) {
        val repo = repository ?: return
        if (name.isBlank()) return
        _isLoading.update { true }
        _error.update { null }
        viewModelScope.launch {
            val result = repo.createBuilding(projectId, name)
            _isLoading.update { false }
            result.onSuccess {
                loadBuildings(projectId)
                onSuccess()
            }.onFailure { ex ->
                _error.update { ex.message ?: "Failed to create building" }
            }
        }
    }

    fun createBuildingAndSelect(projectId: UUID, name: String, onSuccess: (BuildingEntity) -> Unit = {}) {
        val repo = repository ?: return
        if (name.isBlank()) return
        _isLoading.update { true }
        _error.update { null }
        viewModelScope.launch {
            val result = repo.createBuilding(projectId, name)
            _isLoading.update { false }
            result.onSuccess { building ->
                loadBuildings(projectId)
                onSuccess(building)
            }.onFailure { ex ->
                _error.update { ex.message ?: "Failed to create building" }
            }
        }
    }

    fun loadFloors(buildingId: UUID) {
        val repo = repository ?: return
        _isLoading.update { true }
        _error.update { null }
        viewModelScope.launch {
            val result = repo.getFloors(buildingId)
            _isLoading.update { false }
            result.onSuccess { list ->
                _floors.update { list }
            }.onFailure { ex ->
                _error.update { ex.message ?: "Failed to load floors" }
            }
        }
    }

    fun createFloor(buildingId: UUID, name: String, onSuccess: () -> Unit = {}) {
        val repo = repository ?: return
        if (name.isBlank()) return
        _isLoading.update { true }
        _error.update { null }
        viewModelScope.launch {
            val result = repo.createFloor(buildingId, name)
            _isLoading.update { false }
            result.onSuccess {
                loadFloors(buildingId)
                onSuccess()
            }.onFailure { ex ->
                _error.update { ex.message ?: "Failed to create floor" }
            }
        }
    }

    fun createFloorAndSelect(buildingId: UUID, name: String, onSuccess: (FloorEntity) -> Unit = {}) {
        val repo = repository ?: return
        if (name.isBlank()) return
        _isLoading.update { true }
        _error.update { null }
        viewModelScope.launch {
            val result = repo.createFloor(buildingId, name)
            _isLoading.update { false }
            result.onSuccess { floor ->
                loadFloors(buildingId)
                onSuccess(floor)
            }.onFailure { ex ->
                _error.update { ex.message ?: "Failed to create floor" }
            }
        }
    }

    fun loadSurveyAreas(floorId: UUID) {
        val repo = repository ?: return
        _isLoading.update { true }
        _error.update { null }
        viewModelScope.launch {
            val result = repo.getSurveyAreas(floorId)
            _isLoading.update { false }
            result.onSuccess { list ->
                _surveyAreas.update { list }
            }.onFailure { ex ->
                _error.update { ex.message ?: "Failed to load survey areas" }
            }
        }
    }

    fun createSurveyArea(floorId: UUID, name: String, onSuccess: () -> Unit = {}) {
        val repo = repository ?: return
        if (name.isBlank()) return
        _isLoading.update { true }
        _error.update { null }
        viewModelScope.launch {
            val result = repo.createSurveyArea(floorId, name)
            _isLoading.update { false }
            result.onSuccess {
                loadSurveyAreas(floorId)
                onSuccess()
            }.onFailure { ex ->
                _error.update { ex.message ?: "Failed to create survey area" }
            }
        }
    }

    fun createSurveyAreaAndSelect(floorId: UUID, name: String, onSuccess: (SurveyAreaEntity) -> Unit = {}) {
        val repo = repository ?: return
        if (name.isBlank()) return
        _isLoading.update { true }
        _error.update { null }
        viewModelScope.launch {
            val result = repo.createSurveyArea(floorId, name)
            _isLoading.update { false }
            result.onSuccess { area ->
                loadSurveyAreas(floorId)
                onSuccess(area)
            }.onFailure { ex ->
                _error.update { ex.message ?: "Failed to create survey area" }
            }
        }
    }

    fun createLocationHierarchy(
        projectName: String,
        buildingName: String,
        floorName: String,
        surveyAreaName: String,
        onSuccess: (ProjectEntity, BuildingEntity, FloorEntity, SurveyAreaEntity) -> Unit
    ) {
        val repo = repository ?: return
        val project = projectName.trim()
        val building = buildingName.trim()
        val floor = floorName.trim()
        val area = surveyAreaName.trim()
        if (project.isBlank() || building.isBlank() || floor.isBlank() || area.isBlank()) return

        _isLoading.update { true }
        _error.update { null }
        viewModelScope.launch {
            val projectResult = repo.createProject(project)
            if (projectResult.isFailure) {
                _isLoading.update { false }
                _error.update { projectResult.exceptionOrNull()?.message ?: "Failed to create project" }
                return@launch
            }

            val createdProject = projectResult.getOrThrow()
            val buildingResult = repo.createBuilding(createdProject.id, building)
            if (buildingResult.isFailure) {
                _isLoading.update { false }
                _error.update { buildingResult.exceptionOrNull()?.message ?: "Failed to create building" }
                return@launch
            }

            val createdBuilding = buildingResult.getOrThrow()
            val floorResult = repo.createFloor(createdBuilding.id, floor)
            if (floorResult.isFailure) {
                _isLoading.update { false }
                _error.update { floorResult.exceptionOrNull()?.message ?: "Failed to create floor" }
                return@launch
            }

            val createdFloor = floorResult.getOrThrow()
            val areaResult = repo.createSurveyArea(createdFloor.id, area)
            if (areaResult.isFailure) {
                _isLoading.update { false }
                _error.update { areaResult.exceptionOrNull()?.message ?: "Failed to create survey area" }
                return@launch
            }

            val createdArea = areaResult.getOrThrow()
            loadProjects()
            loadBuildings(createdProject.id)
            loadFloors(createdBuilding.id)
            loadSurveyAreas(createdFloor.id)
            _isLoading.update { false }
            onSuccess(createdProject, createdBuilding, createdFloor, createdArea)
        }
    }
}
