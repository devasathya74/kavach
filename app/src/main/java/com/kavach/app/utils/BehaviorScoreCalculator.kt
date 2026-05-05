package com.kavach.app.utils

import com.kavach.app.data.local.entity.BehaviorEventEntity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * BehaviorScoreCalculator — converts raw events into a 0–100 Discipline Score.
 *
 * Score meaning:
 *  90–100 : Excellent — no suspicious activity
 *  70–89  : Good — minor anomalies (1-2 events)
 *  50–69  : Warning — repeated issues, admin notified
 *  0–49   : Flagged — mandatory review, training may be reset
 *
 * Scoring deductions:
 *  SEEK_ATTEMPT        : -8 per event  (strong signal of intent to skip)
 *  QUIZ_FAST_ANSWER    : -5 per event  (didn't read question)
 *  APP_BACKGROUND      : -3 per event  (left during video)
 *  DEVICE_MISMATCH     : -30 flat      (security violation, one-time)
 *  QUIZ_ATTEMPT (fail) : -2 per fail   (reflects on engagement)
 *
 * Bonus:
 *  TRAINING_COMPLETE (first attempt) : +5
 *
 * Score is calculated per training session and also as cumulative lifetime score.
 */
@Singleton
class BehaviorScoreCalculator @Inject constructor() {

    data class ScoreResult(
        val score          : Int,             // 0–100
        val grade          : Grade,
        val deductions     : Map<String, Int>,// event_type → total points deducted
        val requiresReview : Boolean
    )

    enum class Grade(val label: String, val emoji: String) {
        EXCELLENT("उत्कृष्ट",  "🟢"),
        GOOD     ("अच्छा",    "🟡"),
        WARNING  ("चेतावनी",  "🟠"),
        FLAGGED  ("संदिग्ध",  "🔴")
    }

    private val deductionMap = mapOf(
        BehaviorTracker.Events.SEEK_ATTEMPT     to 8,
        BehaviorTracker.Events.QUIZ_FAST_ANSWER to 5,
        BehaviorTracker.Events.APP_BACKGROUND   to 3,
        BehaviorTracker.Events.DEVICE_MISMATCH  to 30,
        "QUIZ_ATTEMPT_FAIL"                     to 2
    )

    private val bonusMap = mapOf(
        BehaviorTracker.Events.TRAINING_COMPLETE to 5
    )

    fun calculate(events: List<BehaviorEventEntity>): ScoreResult {
        var score = 100
        val deductionsApplied = mutableMapOf<String, Int>()

        // ── Apply deductions ──────────────────────────────
        events.forEach { event ->
            val deduction = deductionMap[event.eventType] ?: 0
            if (deduction > 0) {
                score -= deduction
                deductionsApplied[event.eventType] =
                    (deductionsApplied[event.eventType] ?: 0) + deduction
            }
        }

        // ── Apply bonuses ─────────────────────────────────
        events.forEach { event ->
            val bonus = bonusMap[event.eventType] ?: 0
            if (bonus > 0) score += bonus
        }

        // ── Clamp to 0–100 ────────────────────────────────
        score = score.coerceIn(0, 100)

        val grade = when {
            score >= 90 -> Grade.EXCELLENT
            score >= 70 -> Grade.GOOD
            score >= 50 -> Grade.WARNING
            else        -> Grade.FLAGGED
        }

        return ScoreResult(
            score          = score,
            grade          = grade,
            deductions     = deductionsApplied,
            requiresReview = grade == Grade.FLAGGED || grade == Grade.WARNING
        )
    }

    /** Quick check — is this user's score below the flag threshold? */
    fun isFlagged(events: List<BehaviorEventEntity>): Boolean =
        calculate(events).grade == Grade.FLAGGED
}
