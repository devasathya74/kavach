package com.kavach.app.data.remote.repository

import com.kavach.app.data.local.dao.QuizDao
import com.kavach.app.data.local.dao.TrainingDao
import com.kavach.app.data.local.entity.QuizQuestionEntity
import com.kavach.app.data.local.entity.TrainingEntity
import com.kavach.app.data.remote.api.KavachApiService
import com.kavach.app.data.remote.dto.QuizSubmitRequest
import com.kavach.app.domain.model.*
import com.kavach.app.utils.Resource
import com.kavach.app.utils.safeCall
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
    suspend fun refreshTrainings(): Resource<Unit> = safeCall {
        val resp = api.getTrainings()
        if (resp.isSuccessful) {
            val dtos = resp.body()?.data ?: emptyList()
            trainingDao.upsertAll(dtos.map {
                TrainingEntity(
                    id = it.id, title = it.title, description = it.description,
                    videoUrl = it.videoUrl, duration = it.duration,
                    isMandatory = it.isMandatory, status = it.status
                )
            })
            Resource.Success(Unit)
        } else {
            Resource.Error("Failed to fetch trainings: ${resp.code()}")
        }
    }

    /** Mark training as started on server. */
    suspend fun startTraining(trainingId: Int): Resource<Unit> = safeCall {
        api.startTraining(mapOf("training_id" to trainingId))
        trainingDao.updateStatus(trainingId, "IN_PROGRESS")
        Resource.Success(Unit)
    }

    /** Mark training complete on server. */
    suspend fun completeTraining(trainingId: Int): Resource<Unit> = safeCall {
        api.completeTraining(mapOf("training_id" to trainingId))
        trainingDao.updateStatus(trainingId, "COMPLETED")
        Resource.Success(Unit)
    }

    /** Fetch quiz questions for a training (cached). */
    suspend fun getQuizQuestions(trainingId: Int): Resource<List<QuizQuestion>> = safeCall {
        val cached = quizDao.getQuestionsForTraining(trainingId)
        if (cached.isNotEmpty()) {
            return@safeCall Resource.Success(cached.map { it.toDomain() })
        }
        val resp = api.getQuizQuestions(trainingId)
        if (resp.isSuccessful) {
            val dtos = resp.body()?.data ?: emptyList()
            quizDao.upsertAll(dtos.map {
                QuizQuestionEntity(
                    id = it.id, trainingId = it.trainingId,
                    question = it.question, optionA = it.optionA,
                    optionB = it.optionB, optionC = it.optionC,
                    optionD = it.optionD, correctOption = it.correctOption
                )
            })
            Resource.Success(dtos.map {
                QuizQuestion(it.id, it.trainingId, it.question,
                    it.optionA, it.optionB, it.optionC, it.optionD, it.correctOption)
            })
        } else {
            Resource.Error("Failed to load quiz")
        }
    }

    /** Submit quiz answers to server. */
    suspend fun submitQuiz(trainingId: Int, answers: Map<Int, String>): Resource<QuizResult> = safeCall {
        val resp = api.submitQuiz(QuizSubmitRequest(trainingId, answers))
        val dto  = resp.body()?.data
        if (resp.isSuccessful && dto != null) {
            if (dto.passed) trainingDao.updateStatus(trainingId, "COMPLETED")
            else trainingDao.updateStatus(trainingId, "FAILED")
            Resource.Success(QuizResult(dto.score, dto.passed, dto.total))
        } else {
            Resource.Error("Quiz submission failed")
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
