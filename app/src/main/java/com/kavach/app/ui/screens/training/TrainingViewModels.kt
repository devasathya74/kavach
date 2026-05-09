package com.kavach.app.ui.screens.training

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kavach.app.data.local.SessionDataStore
import com.kavach.app.data.repository.TrainingRepository
import com.kavach.app.domain.model.QuizQuestion
import com.kavach.app.domain.model.QuizResult
import com.kavach.app.domain.model.Training
import com.kavach.app.utils.BehaviorTracker
import com.kavach.app.utils.ApiResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

// ── Training List ─────────────────────────────────────────

data class TrainingListUiState(
    val trainings  : List<Training> = emptyList(),
    val isLoading  : Boolean = false,
    val error      : String? = null
)

@HiltViewModel
class TrainingListViewModel @Inject constructor(
    private val repository: TrainingRepository
) : ViewModel() {

    val uiState: StateFlow<TrainingListUiState> = repository.getAllTrainings()
        .map { TrainingListUiState(trainings = it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TrainingListUiState(isLoading = true))

    init { refresh() }

    fun refresh() = viewModelScope.launch {
        repository.refreshTrainings()
    }
}

// ── Video Player ──────────────────────────────────────────

data class VideoPlayerUiState(
    val training   : Training? = null,
    val pno        : String    = "",      // ← for watermark
    val isLoading  : Boolean   = true,
    val error      : String?   = null,
    val videoReady : Boolean   = false
)

@HiltViewModel
class VideoPlayerViewModel @Inject constructor(
    private val repository      : TrainingRepository,
    private val sessionStore    : SessionDataStore,
    private val behaviorTracker : BehaviorTracker,
    savedStateHandle            : SavedStateHandle
) : ViewModel() {

    private val trainingId = savedStateHandle.get<Int>("trainingId") ?: 0

    private val _uiState = MutableStateFlow(VideoPlayerUiState())
    val uiState: StateFlow<VideoPlayerUiState> = _uiState.asStateFlow()

    init {
        loadTraining()
        notifyStarted()
    }

    private fun loadTraining() = viewModelScope.launch {
        val pno = sessionStore.pno.firstOrNull() ?: ""
        repository.getAllTrainings()
            .map { list -> list.firstOrNull { it.id == trainingId } }
            .collect { training ->
                _uiState.value = VideoPlayerUiState(
                    training   = training,
                    pno        = pno,
                    isLoading  = training == null,
                    videoReady = training != null
                )
            }
    }

    private fun notifyStarted() = viewModelScope.launch {
        repository.startTraining(trainingId)
    }

    fun onVideoCompleted() = viewModelScope.launch {
        repository.completeTraining(trainingId)
    }

    /** Called by VideoPlayerScreen when the seek guard snaps position back. */
    fun onSeekAttemptBlocked(positionMs: Long, maxAllowedMs: Long) {
        behaviorTracker.logSeekAttempt(trainingId, positionMs, maxAllowedMs)
    }

    /** Called when app goes to background during playback. */
    fun onAppBackgrounded(elapsedSeconds: Long) {
        behaviorTracker.logAppBackground(trainingId, elapsedSeconds)
    }
}

// ── Quiz ──────────────────────────────────────────────────

private const val QUIZ_MAX_ATTEMPTS       = 3
private const val MIN_ANSWER_TIME_SECONDS = 5L   // user must spend ≥5s per question

data class QuizUiState(
    val questions      : List<QuizQuestion> = emptyList(),
    val answers        : Map<Int, String>   = emptyMap(),
    val answerTimestamps: Map<Int, Long>    = emptyMap(),  // questionId → time when answered
    val questionOpenedAt: Map<Int, Long>    = emptyMap(),  // questionId → time when shown
    val isLoading      : Boolean = true,
    val isSubmitting   : Boolean = false,
    val attemptsLeft   : Int     = QUIZ_MAX_ATTEMPTS,
    val error          : String? = null,
    val result         : QuizResult? = null
)

@HiltViewModel
class QuizViewModel @Inject constructor(
    private val repository      : TrainingRepository,
    private val behaviorTracker : BehaviorTracker,
    savedStateHandle            : SavedStateHandle
) : ViewModel() {

    private val trainingId = savedStateHandle.get<Int>("trainingId") ?: 0

    private val _uiState = MutableStateFlow(QuizUiState())
    val uiState: StateFlow<QuizUiState> = _uiState.asStateFlow()

    init { loadQuestions() }

    private fun loadQuestions() = viewModelScope.launch {
        _uiState.value = QuizUiState(isLoading = true)
        when (val result = repository.getQuizQuestions(trainingId)) {
            is ApiResult.Success -> {
                val shuffled = result.data.shuffled()
                val openedAt = shuffled.associate { it.id to System.currentTimeMillis() }
                _uiState.value = QuizUiState(
                    questions        = shuffled,
                    questionOpenedAt = openedAt,
                    isLoading        = false
                )
            }
            is ApiResult.Error -> _uiState.value = QuizUiState(isLoading = false, error = result.message)
            else -> {}
        }
    }

    fun selectAnswer(questionId: Int, option: String) {
        val state     = _uiState.value
        val openedAt  = state.questionOpenedAt[questionId] ?: System.currentTimeMillis()
        val elapsedMs = System.currentTimeMillis() - openedAt

        if (elapsedMs < MIN_ANSWER_TIME_SECONDS * 1000) {
            // Log fast answer attempt to behavior system
            behaviorTracker.logQuizFastAnswer(trainingId, questionId, elapsedMs)
            _uiState.value = state.copy(
                error = "⏱ प्रश्न ध्यान से पढ़ें — ${MIN_ANSWER_TIME_SECONDS}s बाद उत्तर दें"
            )
            return
        }

        _uiState.value = state.copy(
            answers          = state.answers + (questionId to option),
            answerTimestamps = state.answerTimestamps + (questionId to System.currentTimeMillis()),
            error            = null
        )
    }

    fun onQuestionVisible(questionId: Int) {
        val state = _uiState.value
        if (!state.questionOpenedAt.containsKey(questionId)) {
            _uiState.value = state.copy(
                questionOpenedAt = state.questionOpenedAt + (questionId to System.currentTimeMillis())
            )
        }
    }

    fun submitQuiz() = viewModelScope.launch {
        val state = _uiState.value
        if (state.answers.size < state.questions.size) {
            _uiState.value = state.copy(error = "सभी प्रश्नों का उत्तर दें")
            return@launch
        }
        if (state.attemptsLeft <= 0) {
            _uiState.value = state.copy(error = "अधिकतम प्रयास समाप्त हो गए हैं")
            return@launch
        }
        _uiState.value = state.copy(isSubmitting = true, error = null)
        when (val result = repository.submitQuiz(trainingId, state.answers)) {
            is ApiResult.Success -> {
                // Log quiz attempt to behavior system
                behaviorTracker.logQuizAttempt(trainingId, result.data.score, result.data.passed)
                _uiState.value = state.copy(
                    isSubmitting = false,
                    result       = result.data,
                    attemptsLeft = state.attemptsLeft - 1
                )
            }
            is ApiResult.Error -> _uiState.value = state.copy(
                isSubmitting = false,
                error        = result.message,
                attemptsLeft = state.attemptsLeft - 1
            )
            else -> {}
        }
    }

    fun retryQuiz() {
        _uiState.value = QuizUiState(isLoading = false).copy(
            attemptsLeft = _uiState.value.attemptsLeft
        )
        loadQuestions()
    }
}
