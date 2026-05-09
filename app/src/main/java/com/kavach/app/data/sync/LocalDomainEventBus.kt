package com.kavach.app.data.sync

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

enum class SubscriberPolicy {
    DROP_OLDEST,      // Bounded realtime
    REPLAY_LATEST,    // UI Hydration
    COALESCE,         // Telemetry/Status
    GUARANTEED_BUFFER, // Governance/Critical
    STICKY_SINGLE     // Platform Mode
}
 
enum class EventDeliveryMode {
    FIRE_AND_FORGET,
    AT_LEAST_ONCE,
    EXACTLY_ONCE
}

data class DomainEventWrapper(
    val event: DomainEvent,
    val deliveryMode: EventDeliveryMode = EventDeliveryMode.FIRE_AND_FORGET,
    val subscriberPolicy: SubscriberPolicy = SubscriberPolicy.DROP_OLDEST,
    val sequence: Long = System.currentTimeMillis()
)

sealed class DomainEvent {
    data class IncidentCreated(val incidentId: String, val severity: String) : DomainEvent()
    data class OfficerUpdated(val pno: String, val field: String) : DomainEvent()
    data class GovernanceApplied(val changeId: Int) : DomainEvent()
    data class PresenceChanged(val pno: String, val confidence: Int) : DomainEvent()
    data class ModeChanged(val oldMode: String, val newMode: String) : DomainEvent()
    object SystemLockdown : DomainEvent()
}

@Singleton
class LocalDomainEventBus @Inject constructor() {
    // Configurable buffer to prevent memory explosion
    private val _events = MutableSharedFlow<DomainEventWrapper>(
        extraBufferCapacity = 64,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
    )
    val events = _events.asSharedFlow()

    suspend fun publish(
        event: DomainEvent, 
        deliveryMode: EventDeliveryMode = EventDeliveryMode.FIRE_AND_FORGET,
        policy: SubscriberPolicy = SubscriberPolicy.DROP_OLDEST
    ) {
        _events.emit(DomainEventWrapper(event, deliveryMode, policy))
    }
}
