package com.kavach.app.core.latency

import com.kavach.app.core.clock.TrustedClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * CommandLatencyTracker — Operational propagation visibility.
 *
 * Tracks every high-authority command from dispatch through
 * final delivery confirmation. Provides delivery confidence scores
 * that the UI surfaces so operators know command state.
 *
 * Without this: operator issues lockdown → sees no feedback → re-issues →
 *               system receives duplicate commands → chaos.
 *
 * With this: operator sees "PROPAGATING — 3/7 acknowledged" in real time.
 *
 * Pipeline:
 *   DISPATCHED → PROPAGATING → PARTIALLY_ACKED → DELIVERED | FAILED
 */

enum class CommandDeliveryState {
    DISPATCHED,       // Sent, awaiting network propagation
    PROPAGATING,      // In flight to recipients
    PARTIALLY_ACKED,  // Some recipients confirmed, awaiting rest
    DELIVERED,        // All expected acknowledgments received
    FAILED,           // Timeout exhausted without full delivery
    CANCELLED         // Superseded by higher-priority command
}

data class CommandTrace(
    val commandId       : String,
    val commandLabel    : String,
    val issuedByRole    : String,
    val dispatchMs      : Long,
    val expectedAckCount: Int,
    val receivedAckCount: Int       = 0,
    val state           : CommandDeliveryState = CommandDeliveryState.DISPATCHED,
    val propagationMs   : Long?     = null,   // ms from dispatch to first ack
    val deliveryMs      : Long?     = null,   // ms from dispatch to full delivery
    val failureReason   : String?   = null
) {
    /** Delivery confidence: 0–100. Based on ack ratio and state. */
    val confidencePct: Int get() = when (state) {
        CommandDeliveryState.DELIVERED       -> 100
        CommandDeliveryState.PARTIALLY_ACKED -> ((receivedAckCount.toFloat() / expectedAckCount.toFloat()) * 90f).toInt()
        CommandDeliveryState.PROPAGATING     -> 40
        CommandDeliveryState.DISPATCHED      -> 20
        CommandDeliveryState.FAILED          -> 0
        CommandDeliveryState.CANCELLED       -> 0
    }

    val confidenceLabel: String get() = when {
        confidencePct >= 100 -> "DELIVERED"
        confidencePct >= 80  -> "HIGH CONFIDENCE"
        confidencePct >= 50  -> "PARTIAL DELIVERY"
        confidencePct >= 20  -> "PROPAGATING"
        else                 -> "UNCONFIRMED"
    }
}

@Singleton
class CommandLatencyTracker @Inject constructor(
    private val trustedClock: TrustedClock
) {

    companion object {
        private const val MAX_TRACKED  = 50
        private const val TIMEOUT_MS   = 30_000L
    }

    private val _traces = MutableStateFlow<List<CommandTrace>>(emptyList())
    val traces: StateFlow<List<CommandTrace>> = _traces.asStateFlow()

    /** Active traces only (not yet DELIVERED/FAILED). */
    val activeTraces get() = _traces.value.filter {
        it.state !in listOf(CommandDeliveryState.DELIVERED, CommandDeliveryState.FAILED, CommandDeliveryState.CANCELLED)
    }

    /**
     * Begin tracking a dispatched command.
     * @return The [commandId] to use for subsequent ack/fail calls.
     */
    fun dispatch(
        commandLabel    : String,
        issuedByRole    : String,
        expectedAckCount: Int = 1
    ): String {
        val id = UUID.randomUUID().toString().take(8).uppercase()
        val trace = CommandTrace(
            commandId        = id,
            commandLabel     = commandLabel,
            issuedByRole     = issuedByRole,
            dispatchMs       = trustedClock.nowMs(),
            expectedAckCount = expectedAckCount,
            state            = CommandDeliveryState.DISPATCHED
        )
        append(trace)
        return id
    }

    /**
     * Record an acknowledgment from a recipient device.
     */
    fun acknowledge(commandId: String) {
        update(commandId) { trace ->
            val nowMs       = trustedClock.nowMs()
            val newAckCount = trace.receivedAckCount + 1
            val propagMs    = if (trace.propagationMs == null) nowMs - trace.dispatchMs else trace.propagationMs
            val isComplete  = newAckCount >= trace.expectedAckCount

            trace.copy(
                receivedAckCount = newAckCount,
                propagationMs    = propagMs,
                deliveryMs       = if (isComplete) nowMs - trace.dispatchMs else null,
                state            = when {
                    isComplete                  -> CommandDeliveryState.DELIVERED
                    trace.state == CommandDeliveryState.DISPATCHED -> CommandDeliveryState.PROPAGATING
                    else                        -> CommandDeliveryState.PARTIALLY_ACKED
                }
            )
        }
    }

    /** Mark command as failed (timeout or explicit rejection). */
    fun fail(commandId: String, reason: String = "Timeout") {
        update(commandId) { it.copy(state = CommandDeliveryState.FAILED, failureReason = reason) }
    }

    /** Mark command as superseded. */
    fun cancel(commandId: String) {
        update(commandId) { it.copy(state = CommandDeliveryState.CANCELLED) }
    }

    /** Get a specific trace for display. */
    fun getTrace(commandId: String): CommandTrace? =
        _traces.value.find { it.commandId == commandId }

    private fun update(commandId: String, transform: (CommandTrace) -> CommandTrace) {
        val updated = _traces.value.map { if (it.commandId == commandId) transform(it) else it }
        _traces.value = updated
    }

    private fun append(trace: CommandTrace) {
        val current = _traces.value.toMutableList()
        current.add(0, trace)
        if (current.size > MAX_TRACKED) current.subList(MAX_TRACKED, current.size).clear()
        _traces.value = current
    }
}
