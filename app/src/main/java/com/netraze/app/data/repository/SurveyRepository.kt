package com.netraze.app.data.repository

import com.netraze.app.data.local.dao.SurveyDao
import com.netraze.app.data.local.entity.SurveyEntity
import com.netraze.app.data.remote.api.SurveyApi
import com.netraze.app.data.remote.dto.CreateSurveyRequestDto
import com.netraze.app.data.remote.dto.SurveyDto
import com.netraze.app.data.security.SecureSessionStore
import com.netraze.app.data.sync.SyncManager
import java.util.UUID

interface SurveyRepository {
    suspend fun createSurvey(
        surveyAreaId: UUID,
        title: String?,
        mode: String,
        floorPlanId: UUID? = null,
        simpleMapId: UUID? = null
    ): Result<SurveyEntity>

    suspend fun getSurveysForArea(surveyAreaId: UUID): Result<List<SurveyEntity>>
    suspend fun getAllSurveys(): Result<List<SurveyEntity>>
    suspend fun getSurvey(surveyId: UUID): Result<SurveyEntity?>
    suspend fun completeSurvey(surveyId: UUID): Result<SurveyEntity>
}

class SurveyRepositoryImpl(
    private val surveyApi: SurveyApi,
    private val surveyDao: SurveyDao,
    private val sessionStore: SecureSessionStore,
    private val syncManager: SyncManager? = null
) : SurveyRepository {

    override suspend fun createSurvey(
        surveyAreaId: UUID,
        title: String?,
        mode: String,
        floorPlanId: UUID?,
        simpleMapId: UUID?
    ): Result<SurveyEntity> {
        val now = System.currentTimeMillis()
        val currentUserId = sessionStore.getSession()?.userId ?: UUID.randomUUID()

        // 1. Android Canonical UUID Generation FIRST
        val surveyId = UUID.randomUUID()
        val resolvedFloorPlanId = floorPlanId ?: if (mode == "floor_plan") UUID.randomUUID() else null
        val resolvedSimpleMapId = simpleMapId ?: if (mode == "simple_map") UUID.randomUUID() else null

        val localEntity = SurveyEntity(
            id = surveyId,
            surveyAreaId = surveyAreaId,
            title = title ?: "",
            mode = mode,
            status = "in_progress",
            floorPlanId = resolvedFloorPlanId,
            simpleMapId = resolvedSimpleMapId,
            createdBy = currentUserId,
            startedAt = now,
            completedAt = null,
            createdAt = now,
            updatedAt = now,
            syncState = "pending"
        )

        // 2. Persist in local Room DB immediately
        try {
            surveyDao.insertSurvey(localEntity)
        } catch (e: Exception) {
            return Result.failure(e)
        }

        // 3. Application API online sync attempt (optional online path)
        try {
            val request = CreateSurveyRequestDto(
                id = surveyId, // Preserves Android-generated canonical UUID
                title = title,
                mode = mode,
                floorPlanId = resolvedFloorPlanId,
                simpleMapId = resolvedSimpleMapId
            )
            val remoteDto = surveyApi.createSurvey(surveyAreaId, request)
            surveyDao.updateSyncState(surveyId, "synced")
            return Result.success(localEntity.copy(syncState = "synced"))
        } catch (e: Exception) {
            // Offline or network error: retain local Room entity as pending sync
            return Result.success(localEntity)
        }
    }

    override suspend fun getSurveysForArea(surveyAreaId: UUID): Result<List<SurveyEntity>> {
        return try {
            val remoteDtos = surveyApi.getSurveysForArea(surveyAreaId)
            val entities = remoteDtos.map { dto -> dto.toEntity() }
            surveyDao.upsertSurveys(entities)
            Result.success(surveyDao.getSurveysForArea(surveyAreaId))
        } catch (e: Exception) {
            // Offline fallback to local Room cache
            Result.success(surveyDao.getSurveysForArea(surveyAreaId))
        }
    }

    override suspend fun getAllSurveys(): Result<List<SurveyEntity>> {
        return try {
            Result.success(surveyDao.getAllSurveys())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getSurvey(surveyId: UUID): Result<SurveyEntity?> {
        return try {
            val remoteDto = surveyApi.getSurvey(surveyId)
            val entity = remoteDto.toEntity()
            surveyDao.upsertSurvey(entity)
            Result.success(entity)
        } catch (e: Exception) {
            Result.success(surveyDao.getSurveyById(surveyId))
        }
    }

    override suspend fun completeSurvey(surveyId: UUID): Result<SurveyEntity> {
        // D080 Completion Barrier: Drain pending sync FIRST
        syncManager?.drainPendingSync(surveyId)

        val now = System.currentTimeMillis()
        return try {
            val remoteDto = surveyApi.completeSurvey(surveyId)
            val updatedEntity = remoteDto.toEntity()
            surveyDao.upsertSurvey(updatedEntity)
            Result.success(updatedEntity)
        } catch (e: Exception) {
            // Local fallback completion
            surveyDao.updateSurveyCompletion(surveyId, "completed", now, now)
            val local = surveyDao.getSurveyById(surveyId)
            if (local != null) Result.success(local) else Result.failure(e)
        }
    }

    private fun SurveyDto.toEntity(): SurveyEntity {
        return SurveyEntity(
            id = id,
            surveyAreaId = surveyAreaId,
            title = title ?: "",
            mode = mode,
            status = status,
            floorPlanId = floorPlanId,
            simpleMapId = simpleMapId,
            createdBy = createdBy,
            startedAt = parseIsoToMillis(startedAt),
            completedAt = completedAt?.let { parseIsoToMillis(it) },
            createdAt = parseIsoToMillis(createdAt),
            updatedAt = parseIsoToMillis(updatedAt),
            syncState = "synced"
        )
    }

    private fun parseIsoToMillis(isoString: String): Long {
        return try {
            java.time.Instant.parse(isoString).toEpochMilli()
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    }
}
