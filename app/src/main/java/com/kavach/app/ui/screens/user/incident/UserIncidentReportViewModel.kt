package com.kavach.app.ui.screens.user.incident

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kavach.app.data.local.SessionDataStore
import com.kavach.app.data.local.dao.UserIncidentDao
import com.kavach.app.data.local.entity.UserIncidentAttachmentEntity
import com.kavach.app.data.local.entity.UserIncidentDraftEntity
import com.kavach.app.data.remote.worker.SosWorker
import com.kavach.app.data.remote.worker.UserIncidentSyncWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import javax.inject.Inject

enum class IncidentType(val label: String) {
    FIELD_REPORT("Field Report"),
    EQUIPMENT_ISSUE("Equipment Issue"),
    PERSONNEL_INCIDENT("Personnel Incident"),
    EMERGENCY("EMERGENCY")
}

enum class IncidentSeverity(val label: String) {
    LOW("Low"),
    MEDIUM("Medium"),
    HIGH("High"),
    CRITICAL("CRITICAL")
}

data class IncidentFormState(
    val title: String = "",
    val description: String = "",
    val type: IncidentType = IncidentType.FIELD_REPORT,
    val severity: IncidentSeverity = IncidentSeverity.MEDIUM,
    val attachmentPath: String? = null,    // Private storage path
    val attachmentUri: Uri? = null,         // For preview only
    val isSubmitting: Boolean = false,
    val submitSuccess: Boolean = false,
    val error: String? = null
) {
    val canSubmit: Boolean
        get() = title.isNotBlank() && description.isNotBlank() && !isSubmitting
}

/**
 * UserIncidentReportViewModel — Field incident reporting.
 *
 * RULES:
 * 1. Photo → private storage immediately (no content:// in DB)
 * 2. Draft saved to DB before any network attempt
 * 3. SyncStatus = QUEUED → WorkManager picks up (Phase 2)
 * 4. NEVER queries other personnel, global incidents
 */
@HiltViewModel
class UserIncidentReportViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sessionDataStore: SessionDataStore,
    private val userIncidentDao: UserIncidentDao
) : ViewModel() {

    private val _state = MutableStateFlow(IncidentFormState())
    val state: StateFlow<IncidentFormState> = _state.asStateFlow()

    fun setTitle(value: String) = _state.update { it.copy(title = value) }
    fun setDescription(value: String) = _state.update { it.copy(description = value) }
    fun setType(value: IncidentType) = _state.update { it.copy(type = value) }
    fun setSeverity(value: IncidentSeverity) = _state.update { it.copy(severity = value) }

    fun onPhotoSelected(uri: Uri) {
        viewModelScope.launch {
            try {
                // Copy to private storage immediately — content:// URI expires
                val ext = context.contentResolver.getType(uri)?.substringAfter("/") ?: "jpg"
                val fileName = "incident_${UUID.randomUUID()}.$ext"
                val destFile = File(
                    File(context.filesDir, "incident_photos").also { it.mkdirs() },
                    fileName
                )

                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                }

                _state.update { it.copy(
                    attachmentPath = destFile.absolutePath,
                    attachmentUri = uri
                )}
                Timber.d("IncidentReport: Photo copied to private storage: ${destFile.absolutePath}")
            } catch (e: Exception) {
                Timber.e(e, "IncidentReport: Failed to copy photo")
                _state.update { it.copy(error = "Photo selection failed. Try again.") }
            }
        }
    }

    fun removePhoto() = _state.update { it.copy(attachmentPath = null, attachmentUri = null) }

    fun submit() {
        val form = _state.value
        if (!form.canSubmit) return

        viewModelScope.launch {
            _state.update { it.copy(isSubmitting = true, error = null) }

            try {
                val pno = sessionDataStore.pno.firstOrNull() ?: ""
                val unit = sessionDataStore.unit.firstOrNull() ?: ""

                val localId = UUID.randomUUID().toString()
                val correlationId = UUID.randomUUID().toString()

                // Step 1: Commit draft to DB (process-death safe)
                userIncidentDao.insertDraft(UserIncidentDraftEntity(
                    localId = localId,
                    correlationId = correlationId,
                    reporterPno = pno,
                    reporterUnit = unit,
                    title = form.title,
                    description = form.description,
                    type = form.type.name,
                    severity = form.severity.name,
                    createdAt = System.currentTimeMillis(),
                    syncStatus = "QUEUED"
                ))

                // Step 2: Save attachment reference (if any)
                form.attachmentPath?.let { path ->
                    userIncidentDao.insertAttachment(UserIncidentAttachmentEntity(
                        localId = UUID.randomUUID().toString(),
                        incidentLocalId = localId,
                        localPath = path,
                        mimeType = "image/jpeg",
                        createdAt = System.currentTimeMillis()
                    ))
                }

                // Step 3: WorkManager sync
                UserIncidentSyncWorker.schedule(context)

                Timber.i("IncidentReport: Draft committed. localId=$localId, correlationId=$correlationId")
                _state.update { it.copy(isSubmitting = false, submitSuccess = true) }

            } catch (e: Exception) {
                Timber.e(e, "IncidentReport: Submit failed")
                _state.update { it.copy(isSubmitting = false, error = "Submission failed. Report saved locally.") }
            }
        }
    }

    fun clearError() = _state.update { it.copy(error = null) }
}
