package com.kavach.app.ui.screens.pilot.incident

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kavach.app.data.local.db.KavachDatabase
import com.kavach.app.data.local.entity.DraftSyncState
import com.kavach.app.data.local.entity.EvidenceUploadEntity
import com.kavach.app.data.local.entity.IncidentDraftEntity
import com.kavach.app.data.remote.api.KavachApiV2
import com.kavach.app.data.remote.dto.incident.IncidentDto
import com.kavach.app.data.remote.worker.IncidentSyncWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.util.*
import javax.inject.Inject

data class IncidentCenterState(
    val incidents: List<IncidentDto> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val page: Int = 1,
    val endOfPaginationReached: Boolean = false,
    val filterStatus: String? = null,
    val filterSeverity: String? = null
)

@HiltViewModel
class IncidentCenterViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: KavachApiV2,
    private val db: KavachDatabase
) : ViewModel() {

    private val _state = MutableStateFlow(IncidentCenterState())
    val state = _state.asStateFlow()

    init {
        loadIncidents()
    }

    fun loadIncidents(isRefresh: Boolean = false) {
        viewModelScope.launch {
            if (isRefresh) {
                _state.update { it.copy(isRefreshing = true, page = 1, endOfPaginationReached = false) }
            } else {
                _state.update { it.copy(isLoading = true) }
            }

            try {
                val response = api.getIncidents(
                    page = _state.value.page,
                    status = _state.value.filterStatus,
                    severity = _state.value.filterSeverity
                )
                
                val mapped = response.results.map { v2 ->
                    IncidentDto(
                        id          = v2.id,
                        title       = v2.title,
                        description = v2.description,
                        status      = v2.status,
                        severity    = v2.severity,
                        reporterPno = v2.reporterPno,
                        createdAt   = v2.createdAt,
                        updatedAt   = v2.updatedAt,
                        mediaUrl    = v2.mediaUrl
                    )
                }
                _state.update { current ->
                    current.copy(
                        incidents = if (isRefresh) mapped else current.incidents + mapped,
                        isLoading = false,
                        isRefreshing = false,
                        page = current.page + 1,
                        endOfPaginationReached = response.next == null
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, isRefreshing = false, error = e.message) }
            }
        }
    }

    fun createIncident(
        title: String, 
        summary: String, 
        severity: String, 
        type: String,
        imageUri: Uri? = null
    ) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            try {
                val localId = UUID.randomUUID().toString()
                
                // 1. If there's an image, copy it to internal storage
                if (imageUri != null) {
                    val internalFile = File(context.filesDir, "evidence_${localId}.jpg")
                    context.contentResolver.openInputStream(imageUri)?.use { input ->
                        FileOutputStream(internalFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    
                    val evidence = EvidenceUploadEntity(
                        localId = UUID.randomUUID().toString(),
                        incidentLocalId = localId,
                        filePath = internalFile.absolutePath,
                        mediaType = "IMAGE"
                    )
                    db.incidentDao().insertEvidence(evidence)
                }

                // 2. Save Draft to Room
                val draft = IncidentDraftEntity(
                    localId = localId,
                    type = type,
                    title = title,
                    summary = summary,
                    severity = severity,
                    latitude = null, // TODO: Get current location
                    longitude = null,
                    occurredAt = System.currentTimeMillis(),
                    syncState = DraftSyncState.PENDING_SYNC
                )
                db.incidentDao().insertDraft(draft)

                // 3. Schedule Worker
                IncidentSyncWorker.schedule(context)

                _state.update { it.copy(isLoading = false, error = null) }
                Timber.d("Incident created and queued for sync: $localId")
                
                // Refresh list (might not show the new one until synced, but we could add a "pending" section)
                loadIncidents(isRefresh = true)
            } catch (e: Exception) {
                Timber.e(e, "Failed to create incident draft")
                _state.update { it.copy(isLoading = false, error = "Failed to queue incident: ${e.message}") }
            }
        }
    }
}
