package com.kavach.app.core.events

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * EventBus — Global operational event pipeline.
 *
 * Singleton SharedFlow that any component can emit to or collect from.
 * Replay = 0: events are live only; missed events are not replayed
 * (use ThreatStateManager for persistent state).
 *
 * Usage:
 *   Emit:   eventBus.emit(SystemEvent.LockdownActivated("CO"))
 *   Listen: eventBus.events.collect { event -> ... }
 */
@Singleton
class EventBus @Inject constructor() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _events = MutableSharedFlow<SystemEvent>(
        replay          = 0,
        extraBufferCapacity = 64
    )

    /** Observe all system events. Collect in a lifecycle-aware scope. */
    val events: SharedFlow<SystemEvent> = _events.asSharedFlow()

    /**
     * Emit an event. Fire-and-forget — safe to call from any thread.
     */
    fun emit(event: SystemEvent) {
        scope.launch { _events.emit(event) }
    }

    /**
     * Suspend emit — use when you need back-pressure awareness.
     */
    suspend fun send(event: SystemEvent) {
        _events.emit(event)
    }
}
