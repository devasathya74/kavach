package com.kavach.app.data.repository

import com.kavach.app.data.local.dao.QuizDao
import com.kavach.app.data.local.dao.TrainingDao
import com.kavach.app.data.local.entity.QuizQuestionEntity
import com.kavach.app.data.local.entity.TrainingEntity
import com.kavach.app.data.remote.api.KavachApiService
import com.kavach.app.data.remote.dto.training.QuizSubmitRequest
import com.kavach.app.domain.model.*
import com.kavach.app.utils.ApiResult
import com.kavach.app.utils.safeApiCall
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Training repository — offline-first pattern.
 * Fetches from network, caches in Room, serves from cache via Flow.
 */
@Singleton
class TrainingRepository @Inject constructor(
    private val api         : KavachApiService,
    private val trainingDao : TrainingDao,
    private val quizDao     : QuizDao
) {

    /** Returns a live Flow of all trainings from local DB. */
    fun getAllTrainings(): Flow<List<Training>> =
        trainingDao.getAllTrainings().map { list ->
            list.map { it.toDomain() }
        }

    /** Refresh trainings from network and cache locally. */
    suspend fun refreshTrainings(): ApiResult<Unit> = safeApiCall {
        val resp = api.getTrainings()
        if (resp.isSuccessful) {
            val dtos = resp.body()?.data ?: emptyList()
            trainingDao.upsertAll(dtos.map {
                TrainingEntity(
                    id = it.id, title = it.title, description = it.description ?: "",
                    videoUrl = it.videoPath ?: "", duration = it.durationSec,
                    isMandatory = it.isMandatory, status = "NEW"
                )
            })
            ApiResult.Success(Unit)
        } else {
            ApiResult.Error("Failed to fetch trainings: ${resp.code()}", code = resp.code())
        }
    }

    /** Mark training as started on server. */
    suspend fun startTraining(trainingId: String): ApiResult<Unit> = safeApiCall {
        api.startTraining(mapOf("training_id" to trainingId))
        trainingDao.updateStatus(trainingId, "IN_PROGRESS")
        ApiResult.Success(Unit)
    }

    /** Mark training complete on server. */
    suspend fun completeTraining(trainingId: String): ApiResult<Unit> = safeApiCall {
        api.completeTraining(mapOf("training_id" to trainingId))
        trainingDao.updateStatus(trainingId, "COMPLETED")
        ApiResult.Success(Unit)
    }

    /** Fetch quiz questions for a training (cached). */
    suspend fun getQuizQuestions(trainingId: String): ApiResult<List<QuizQuestion>> = safeApiCall {
        val cached = quizDao.getQuestionsForTraining(trainingId)
        if (cached.isNotEmpty()) {
            return@safeApiCall ApiResult.Success(cached.map { it.toDomain() })
        }
        val resp = api.getQuizQuestions(trainingId)
        if (resp.isSuccessful) {
            val dtos = resp.body()?.data ?: emptyList()
            quizDao.upsertAll(dtos.map {
                QuizQuestionEntity(
                    id = it.id, trainingId = it.trainingId,
                    question = it.question, optionA = it.options.getOrNull(0) ?: "",
                    optionB = it.options.getOrNull(1) ?: "", optionC = it.options.getOrNull(2) ?: "",
                    optionD = it.options.getOrNull(3) ?: "", correctOption = ""
                )
            })
            ApiResult.Success(dtos.map {
                QuizQuestion(it.id, it.trainingId, it.question,
                    it.options.getOrNull(0) ?: "", it.options.getOrNull(1) ?: "", it.options.getOrNull(2) ?: "", it.options.getOrNull(3) ?: "", "")
            })
        } else {
            ApiResult.Error("Failed to load quiz: ${resp.code()}", code = resp.code())
        }
    }

    /** Submit quiz answers to server. */
    suspend fun submitQuiz(trainingId: String, answers: Map<String, String>): ApiResult<QuizResult> = safeApiCall {
        val resp = api.submitQuiz(QuizSubmitRequest(trainingId, answers))
        val dto  = resp.body()?.data
        if (resp.isSuccessful && dto != null) {
            if (dto.passed) trainingDao.updateStatus(trainingId, "COMPLETED")
            else trainingDao.updateStatus(trainingId, "FAILED")
            ApiResult.Success(QuizResult(dto.score, dto.passed, dto.total))
        } else {
            ApiResult.Error("Quiz submission failed: ${resp.code()}", code = resp.code())
        }
    }

    // ── Mappers ───────────────────────────────────────────

    private fun TrainingEntity.toDomain() = Training(
        id = id, title = title, description = description,
        videoUrl = videoUrl, duration = duration, isMandatory = isMandatory,
        status = TrainingStatus.valueOf(status)
    )

    private fun QuizQuestionEntity.toDomain() = QuizQuestion(
        id = id, trainingId = trainingId, question = question,
        optionA = optionA, optionB = optionB, optionC = optionC,
        optionD = optionD, correctOption = correctOption
    )
}
