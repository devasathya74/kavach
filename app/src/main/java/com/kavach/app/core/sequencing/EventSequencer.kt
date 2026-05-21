package com.kavach.app.core.sequencing

import com.kavach.app.core.clock.TrustedClock
import com.kavach.app.core.events.EventBus
import com.kavach.app.core.events.SystemEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * EventSequencer — Operational consistency guarantee.
 *
 * Ensures monotonic event ordering across the distributed command system.
 * Assigns sequence IDs to outbound events, validates inbound sequence,
 * and detects / rejects stale or replayed events.
 *
 * Problems this solves:
 *   - LOCKDOWN_RELEASE arriving before LOCKDOWN (network reordering)
 *   - Replayed FORCE_LOGOUT from a captured session
 *   - Stale COMMAND_OVERRIDE from a dropped connection
 *
 * Architecture:
 *   - Outbound: [stamp] assigns monotonic ID + timestamp
 *   - Inbound:  [validate] checks sequence + recency + replay window
 *   - Gap detection: [isGap] detects dropped events for reconciliation
 */

data class SequencedEvent(
    val sequenceId    : Long,
    val timestampMs   : Long,
    val nonce         : String,    // Unique per-event for replay prevention
    val event         : SystemEvent
)

sealed class SequenceValidation {
    object Valid                              : SequenceValidation()
    data class Stale(val gap: Long)           : SequenceValidation()   // sequence gap detected
    object Replayed                           : SequenceValidation()   // nonce already seen
    data class Expired(val ageSec: Long)      : SequenceValidation()   // too old
    object OutOfOrder                         : SequenceValidation()   // lower than expected
}

@Singleton
class EventSequencer @Inject constructor(
    private val trustedClock : TrustedClock
) {

    companion object {
        private const val MAX_EVENT_AGE_SEC  = 300L   // 5 min — reject older events
        private const val REPLAY_WINDOW_SIZE = 512    // Nonce ring buffer size
    }

    // Outbound sequence counter — strictly monotonic
    private val outboundSequence = AtomicLong(0L)

    // Inbound tracking
    private var lastInboundSequence = AtomicLong(0L)
    private val seenNonces          = ArrayDeque<String>(REPLAY_WINDOW_SIZE)

    // Sequence health — observable for diagnostic UI
    private val _sequenceHealth = MutableStateFlow(SequenceHealth())
    val sequenceHealth: StateFlow<SequenceHealth> = _sequenceHealth.asStateFlow()

    // ── Outbound ──────────────────────────────────────────────

    /**
     * Stamp an event with a monotonic sequence ID and trusted timestamp.
     * Call before emitting high-authority events (lockdown, override, etc.)
     */
    fun stamp(event: SystemEvent): SequencedEvent {
        val seq = outboundSequence.incrementAndGet()
        return SequencedEvent(
            sequenceId  = seq,
            timestampMs = trustedClock.nowMs(),
            nonce       = generateNonce(seq),
            event       = event
        )
    }

    // ── Inbound ───────────────────────────────────────────────

    /**
     * Validate an inbound sequenced event.
     * Returns [SequenceValidation.Valid] to proceed, or the rejection reason.
     */
    fun validate(incoming: SequencedEvent): SequenceValidation {
        val nowMs = trustedClock.nowMs()

        // 1. Age check — reject events older than MAX_EVENT_AGE_SEC
        val ageSec = (nowMs - incoming.timestampMs) / 1000L
        if (ageSec > MAX_EVENT_AGE_SEC) {
            return SequenceValidation.Expired(ageSec)
        }

        // 2. Replay check — reject if nonce already seen
        if (seenNonces.contains(incoming.nonce)) {
            return SequenceValidation.Replayed
        }

        // 3. Sequence order check
        val lastSeen = lastInboundSequence.get()
        if (incoming.sequenceId <= lastSeen && lastSeen > 0) {
            return SequenceValidation.OutOfOrder
        }

        // 4. Gap detection — warn if sequence jumped (events may have been dropped)
        val gap = incoming.sequenceId - lastSeen - 1
        val validation = if (gap > 0 && lastSeen > 0) {
            SequenceValidation.Stale(gap)  // Proceed but flag the gap
        } else {
            SequenceValidation.Valid
        }

        // Commit the event as seen
        lastInboundSequence.set(incoming.sequenceId)
        addNonce(incoming.nonce)

        _sequenceHealth.value = _sequenceHealth.value.copy(
            lastSequenceId = incoming.sequenceId,
            totalDropped   = _sequenceHealth.value.totalDropped + if (gap > 0) gap else 0
        )

        return validation
    }

    /** Reset state — call on fresh session/reconnect. */
    fun resetInbound() {
        lastInboundSequence.set(0L)
        seenNonces.clear()
        _sequenceHealth.value = SequenceHealth()
    }

    private fun generateNonce(seq: Long): String =
        "${trustedClock.nowMs()}-${seq}-${(Math.random() * 0xFFFF).toLong().toString(16)}"

    private fun addNonce(nonce: String) {
        if (seenNonces.size >= REPLAY_WINDOW_SIZE) seenNonces.removeFirst()
        seenNonces.addLast(nonce)
    }
}

data class SequenceHealth(
    val lastSequenceId : Long = 0L,
    val totalDropped   : Long = 0L,
    val isHealthy      : Boolean = true
)
