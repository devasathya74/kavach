package com.kavach.app.domain.model

/**
 * IncidentStatus — The operational lifecycle of an incident.
 */
sealed interface IncidentStatus {
    object Draft : IncidentStatus        // Local only, being edited
    object PendingSync : IncidentStatus  // Queued for upload
    object Syncing : IncidentStatus      // Upload in progress
    object Active : IncidentStatus       // Accepted by server, active in field
    object Resolved : IncidentStatus     // Closed/Finished
    object Failed : IncidentStatus       // Sync failed, needs manual retry
    object Conflicted : IncidentStatus   // Diverged from server state
}

/**
 * Incident — Domain model representing a field incident.
 */
data class Incident(
    val localId: String,
    val serverId: String?,
    val correlationId: String,
    val title: String,
    val summary: String,
    val type: String,
    val severity: String,
    val status: IncidentStatus,
    val occurredAt: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val latitude: Double?,
    val longitude: Double?,
    val attachments: List<IncidentAttachment> = emptyList()
)

data class IncidentAttachment(
    val localId: String,
    val remoteUrl: String?,
    val localUri: String?,
    val mediaType: String,
    val uploadProgress: Int,
    val isUploaded: Boolean
)
