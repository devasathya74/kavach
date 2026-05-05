package com.kavach.app.domain.model

/**
 * Domain model — Training video session.
 */
data class Training(
    val id          : Int,
    val title       : String,
    val description : String,
    val videoUrl    : String,
    val duration    : Int,        // seconds
    val isMandatory : Boolean,
    val status      : TrainingStatus
)

enum class TrainingStatus { PENDING, IN_PROGRESS, COMPLETED, FAILED }

/**
 * Domain model — Single quiz question.
 */
data class QuizQuestion(
    val id            : Int,
    val trainingId    : Int,
    val question      : String,
    val optionA       : String,
    val optionB       : String,
    val optionC       : String,
    val optionD       : String,
    val correctOption : String    // "A" | "B" | "C" | "D"
)

/**
 * Domain model — Result of a quiz attempt.
 */
data class QuizResult(
    val score  : Int,
    val passed : Boolean,
    val total  : Int
)
