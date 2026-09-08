package com.netraze.app.data.repository

import com.netraze.app.data.local.dao.HierarchyDao
import com.netraze.app.data.local.entity.BuildingEntity
import com.netraze.app.data.local.entity.FloorEntity
import com.netraze.app.data.local.entity.ProjectEntity
import com.netraze.app.data.local.entity.SurveyAreaEntity
import com.netraze.app.data.remote.api.HierarchyApi
import com.netraze.app.data.remote.dto.BuildingDto
import com.netraze.app.data.remote.dto.CreateBuildingRequestDto
import com.netraze.app.data.remote.dto.CreateFloorRequestDto
import com.netraze.app.data.remote.dto.CreateProjectRequestDto
import com.netraze.app.data.remote.dto.CreateSurveyAreaRequestDto
import com.netraze.app.data.remote.dto.FloorDto
import com.netraze.app.data.remote.dto.ProjectDto
import com.netraze.app.data.remote.dto.SurveyAreaDto
import retrofit2.HttpException
import java.io.IOException
import java.util.UUID

interface HierarchyRepository {
    suspend fun getProjects(): Result<List<ProjectEntity>>
    suspend fun createProject(name: String): Result<ProjectEntity>
    suspend fun getBuildings(projectId: UUID): Result<List<BuildingEntity>>
    suspend fun createBuilding(projectId: UUID, name: String): Result<BuildingEntity>
    suspend fun getFloors(buildingId: UUID): Result<List<FloorEntity>>
    suspend fun createFloor(buildingId: UUID, name: String): Result<FloorEntity>
    suspend fun getSurveyAreas(floorId: UUID): Result<List<SurveyAreaEntity>>
    suspend fun createSurveyArea(floorId: UUID, name: String): Result<SurveyAreaEntity>
}

class HierarchyRepositoryImpl(
    private val hierarchyApi: HierarchyApi,
    private val hierarchyDao: HierarchyDao
) : HierarchyRepository {

    override suspend fun getProjects(): Result<List<ProjectEntity>> {
        return try {
            val dtos = hierarchyApi.getProjects()
            val entities = dtos.map { it.toEntity() }
            hierarchyDao.insertProjects(entities)
            Result.success(hierarchyDao.getActiveProjects())
        } catch (e: Exception) {
            val cached = hierarchyDao.getActiveProjects()
            if (cached.isNotEmpty()) {
                Result.success(cached)
            } else {
                Result.failure(e)
            }
        }
    }

    override suspend fun createProject(name: String): Result<ProjectEntity> {
        return try {
            val dto = hierarchyApi.createProject(CreateProjectRequestDto(name))
            val entity = dto.toEntity()
            hierarchyDao.insertProjects(listOf(entity))
            Result.success(entity)
        } catch (e: Exception) {
            Result.failure(hierarchyMutationFailure(e))
        }
    }

    override suspend fun getBuildings(projectId: UUID): Result<List<BuildingEntity>> {
        return try {
            val dtos = hierarchyApi.getBuildings(projectId)
            val entities = dtos.map { it.toEntity() }
            hierarchyDao.insertBuildings(entities)
            Result.success(hierarchyDao.getBuildingsForProject(projectId))
        } catch (e: Exception) {
            val cached = hierarchyDao.getBuildingsForProject(projectId)
            if (cached.isNotEmpty()) {
                Result.success(cached)
            } else {
                Result.failure(e)
            }
        }
    }

    override suspend fun createBuilding(projectId: UUID, name: String): Result<BuildingEntity> {
        return try {
            val dto = hierarchyApi.createBuilding(projectId, CreateBuildingRequestDto(name))
            val entity = dto.toEntity()
            hierarchyDao.insertBuildings(listOf(entity))
            Result.success(entity)
        } catch (e: Exception) {
            Result.failure(hierarchyMutationFailure(e))
        }
    }

    override suspend fun getFloors(buildingId: UUID): Result<List<FloorEntity>> {
        return try {
            val dtos = hierarchyApi.getFloors(buildingId)
            val entities = dtos.map { it.toEntity() }
            hierarchyDao.insertFloors(entities)
            Result.success(hierarchyDao.getFloorsForBuilding(buildingId))
        } catch (e: Exception) {
            val cached = hierarchyDao.getFloorsForBuilding(buildingId)
            if (cached.isNotEmpty()) {
                Result.success(cached)
            } else {
                Result.failure(e)
            }
        }
    }

    override suspend fun createFloor(buildingId: UUID, name: String): Result<FloorEntity> {
        return try {
            val dto = hierarchyApi.createFloor(buildingId, CreateFloorRequestDto(name))
            val entity = dto.toEntity()
            hierarchyDao.insertFloors(listOf(entity))
            Result.success(entity)
        } catch (e: Exception) {
            Result.failure(hierarchyMutationFailure(e))
        }
    }

    override suspend fun getSurveyAreas(floorId: UUID): Result<List<SurveyAreaEntity>> {
        return try {
            val dtos = hierarchyApi.getSurveyAreas(floorId)
            val entities = dtos.map { it.toEntity() }
            hierarchyDao.insertSurveyAreas(entities)
            Result.success(hierarchyDao.getSurveyAreasForFloor(floorId))
        } catch (e: Exception) {
            val cached = hierarchyDao.getSurveyAreasForFloor(floorId)
            if (cached.isNotEmpty()) {
                Result.success(cached)
            } else {
                Result.failure(e)
            }
        }
    }

    override suspend fun createSurveyArea(floorId: UUID, name: String): Result<SurveyAreaEntity> {
        return try {
            val dto = hierarchyApi.createSurveyArea(floorId, CreateSurveyAreaRequestDto(name))
            val entity = dto.toEntity()
            hierarchyDao.insertSurveyAreas(listOf(entity))
            Result.success(entity)
        } catch (e: Exception) {
            Result.failure(hierarchyMutationFailure(e))
        }
    }

    private fun hierarchyMutationFailure(e: Exception): Exception {
        return when (e) {
            is HttpException -> {
                if (e.code() == 403) {
                    Exception("Unable to create the survey. You don't have permission to modify this project.", e)
                } else {
                    Exception("Unable to create the survey hierarchy (${e.code()}).", e)
                }
            }
            is IOException -> Exception("Unable to create the survey while offline. Your entered details have been kept.", e)
            else -> e
        }
    }

    private fun ProjectDto.toEntity() = ProjectEntity(
        id = id,
        ownerId = ownerId,
        name = name,
        isActive = isActive,
        createdAt = System.currentTimeMillis(),
        updatedAt = System.currentTimeMillis()
    )

    private fun BuildingDto.toEntity() = BuildingEntity(
        id = id,
        projectId = projectId,
        name = name,
        createdAt = System.currentTimeMillis(),
        updatedAt = System.currentTimeMillis()
    )

    private fun FloorDto.toEntity() = FloorEntity(
        id = id,
        buildingId = buildingId,
        name = name,
        createdAt = System.currentTimeMillis(),
        updatedAt = System.currentTimeMillis()
    )

    private fun SurveyAreaDto.toEntity() = SurveyAreaEntity(
        id = id,
        floorId = floorId,
        name = name,
        createdAt = System.currentTimeMillis(),
        updatedAt = System.currentTimeMillis()
    )
}
