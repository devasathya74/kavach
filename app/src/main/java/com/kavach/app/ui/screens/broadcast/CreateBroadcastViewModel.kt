package com.kavach.app.ui.screens.broadcast

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kavach.app.data.local.BroadcastFileManager
import com.kavach.app.data.local.dao.BroadcastDao
import com.kavach.app.data.local.dao.OfficerDao
import com.kavach.app.data.local.dao.UnitSummary
import com.kavach.app.data.local.entity.BroadcastAttachmentEntity
import com.kavach.app.data.local.entity.BroadcastDispatchQueueEntity
import com.kavach.app.data.local.entity.BroadcastDraftEntity
import com.kavach.app.data.local.entity.OfficerWithProfile
import com.kavach.app.data.remote.worker.BroadcastDispatchWorker
import com.kavach.app.data.remote.worker.UploadWorker
import com.kavach.app.data.repository.BroadcastRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

/**
 * Dispatch state machine for the Operational Order Dispatch Console.
 *
 * Transitions:
 *   DRAFT → QUEUED (no attachment)
 *   DRAFT → UPLOADING (with attachment) → QUEUED → DISPATCHING → SENT
 *   Any state → FAILED (on error, can retry)
 */
enum class DispatchState {
    DRAFT,       // Composing
    UPLOADING,   // Image uploading via UploadWorker
    QUEUED,      // In dispatch_queue, WorkManager pending
    DISPATCHING, // Worker running
    SENT,        // Dispatch confirmed
    FAILED       // Error, retry available
}

data class DispatchUiState(
    // --- Title & Message ---
    val title: String = "",
    val content: String = "",

    // --- Image Attachment ---
    val attachmentLocalPath: String? = null,   // app-private absolute path
    val attachmentMimeType: String? = null,
    val attachmentRemoteUrl: String? = null,
    val isImageCopying: Boolean = false,        // while copying content:// → private

    // --- Unit / Company Filter ---
    val availableUnits: List<UnitSummary> = emptyList(),
    val selectedUnit: UnitSummary? = null,
    val availableCompanies: List<String> = emptyList(),
    val selectedCompany: String? = null,

    // --- Personnel Search ---
    val searchQuery: String = "",
    val personnelList: List<OfficerWithProfile> = emptyList(),
    val isPersonnelLoading: Boolean = false,

    // --- Selection (IDs only — no object refs) ---
    val selectedOfficerIds: Set<String> = emptySet(),

    // --- Delivery Mode ---
    val requireAck: Boolean = false,
    val isHighPriority: Boolean = false,
    val isEmergency: Boolean = false,

    // --- Dispatch ---
    val dispatchState: DispatchState = DispatchState.DRAFT,
    val error: String? = null,

    // --- Internal draft tracking ---
    val draftId: String = UUID.randomUUID().toString()
)

@OptIn(FlowPreview::class)
@HiltViewModel
class CreateBroadcastViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val broadcastDao: BroadcastDao,
    private val officerDao: OfficerDao,
    private val broadcastRepository: BroadcastRepository,
    private val fileManager: BroadcastFileManager
) : ViewModel() {

    private val _state = MutableStateFlow(DispatchUiState())
    val uiState: StateFlow<DispatchUiState> = _state.asStateFlow()

    // Debounce buffer for search
    private val _searchQuery = MutableStateFlow("")

    init {
        observeUnits()
        observeSearchWithDebounce()
    }

    // ── Unit / Company Filter ─────────────────────────────────

    private fun observeUnits() {
        viewModelScope.launch {
            broadcastDao.observeAvailableUnits().collect { units ->
                _state.value = _state.value.copy(availableUnits = units)
            }
        }
    }

    fun onUnitSelected(unit: UnitSummary?) {
        _state.value = _state.value.copy(
            selectedUnit = unit,
            selectedCompany = null,
            availableCompanies = emptyList(),
            personnelList = emptyList()
        )
        if (unit != null) {
            observeCompaniesForUnit(unit.unitCode)
            loadPersonnelForUnit(unit.unitCode)
        }
    }

    private fun observeCompaniesForUnit(unitCode: String) {
        viewModelScope.launch {
            broadcastDao.observeCompaniesForUnit(unitCode).collect { companies ->
                _state.value = _state.value.copy(availableCompanies = companies)
            }
        }
    }

    fun onCompanySelected(company: String?) {
        _state.value = _state.value.copy(selectedCompany = company)
        val unit = _state.value.selectedUnit
        if (company != null) {
            loadPersonnelForCompany(company)
        } else if (unit != null) {
            loadPersonnelForUnit(unit.unitCode)
        }
    }

    private fun loadPersonnelForUnit(unitCode: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isPersonnelLoading = true)
            officerDao.observePersonnelByUnit(unitCode)
                .catch { Timber.e(it, "loadPersonnelForUnit failed") }
                .collect { list ->
                    _state.value = _state.value.copy(
                        personnelList = list,
                        isPersonnelLoading = false
                    )
                }
        }
    }

    private fun loadPersonnelForCompany(companyName: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isPersonnelLoading = true)
            officerDao.observePersonnelByCompany(companyName)
                .catch { Timber.e(it, "loadPersonnelForCompany failed") }
                .collect { list ->
                    _state.value = _state.value.copy(
                        personnelList = list,
                        isPersonnelLoading = false
                    )
                }
        }
    }

    // ── Search (debounced, DB-backed) ─────────────────────────

    private fun observeSearchWithDebounce() {
        viewModelScope.launch {
            _searchQuery
                .debounce(300)
                .distinctUntilChanged()
                .flatMapLatest { query ->
                    if (query.isBlank()) {
                        // If unit/company selected, show those; else empty
                        val unit = _state.value.selectedUnit
                        val company = _state.value.selectedCompany
                        when {
                            company != null -> officerDao.observePersonnelByCompany(company)
                            unit != null    -> officerDao.observePersonnelByUnit(unit.unitCode)
                            else            -> flowOf(emptyList())
                        }
                    } else {
                        officerDao.searchPersonnel(query)
                    }
                }
                .catch { Timber.e(it, "search flow error") }
                .collect { list ->
                    _state.value = _state.value.copy(
                        personnelList = list,
                        isPersonnelLoading = false
                    )
                }
        }
    }

    fun onSearchQueryChange(query: String) {
        _state.value = _state.value.copy(searchQuery = query, isPersonnelLoading = query.isNotBlank())
        _searchQuery.value = query
    }

    // ── Title / Content ───────────────────────────────────────

    fun onTitleChange(title: String) {
        _state.value = _state.value.copy(title = title, error = null)
    }

    fun onContentChange(content: String) {
        _state.value = _state.value.copy(content = content, error = null)
    }

    // ── Image Attachment ──────────────────────────────────────

    /**
     * Called when user picks an image from gallery.
     * Copies content:// URI → app-private storage on IO thread.
     * NEVER stores the raw content:// URI.
     */
    fun onImageSelected(uri: Uri, mimeType: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isImageCopying = true, error = null)
            try {
                val privatePath = fileManager.copyToPrivateStorage(uri, mimeType)
                _state.value = _state.value.copy(
                    attachmentLocalPath = privatePath,
                    attachmentMimeType = mimeType,
                    attachmentRemoteUrl = null,
                    isImageCopying = false
                )
                Timber.d("Image copied to private storage: $privatePath")
            } catch (e: Exception) {
                Timber.e(e, "Failed to copy image to private storage")
                _state.value = _state.value.copy(
                    isImageCopying = false,
                    error = "Image copy failed: ${e.localizedMessage}"
                )
            }
        }
    }

    fun onImageRemoved() {
        val path = _state.value.attachmentLocalPath
        if (path != null) {
            fileManager.deletePrivateFile(path)
        }
        _state.value = _state.value.copy(
            attachmentLocalPath = null,
            attachmentMimeType = null,
            attachmentRemoteUrl = null
        )
    }

    // ── Selection (IDs only) ──────────────────────────────────

    fun toggleOfficerSelection(officerId: String) {
        val current = _state.value.selectedOfficerIds.toMutableSet()
        if (!current.remove(officerId)) current.add(officerId)
        _state.value = _state.value.copy(selectedOfficerIds = current)

        // Persist selection to DB immediately (process-death safe)
        viewModelScope.launch {
            broadcastDao.setDraftRecipients(_state.value.draftId, current)
        }
    }

    // ── Delivery Mode ─────────────────────────────────────────

    fun onRequireAckChanged(v: Boolean) { _state.value = _state.value.copy(requireAck = v) }
    fun onHighPriorityChanged(v: Boolean) { _state.value = _state.value.copy(isHighPriority = v) }
    fun onEmergencyChanged(v: Boolean) { _state.value = _state.value.copy(isEmergency = v) }

    // ── Dispatch ──────────────────────────────────────────────

    /**
     * DISPATCH FLOW:
     *   1. Validate inputs
     *   2. Save draft to DB (with all fields)
     *   3. If attachment → save BroadcastAttachmentEntity, enqueue UploadWorker
     *   4. If no attachment → enqueue BroadcastDispatchWorker directly
     *   5. Update state → QUEUED
     *
     * WorkManager handles: network loss, app kill, reboot survival.
     */
    fun dispatch() {
        val s = _state.value

        // Validation
        if (s.title.isBlank()) {
            _state.value = s.copy(error = "Title is required")
            return
        }
        if (s.content.isBlank() && s.attachmentLocalPath == null) {
            _state.value = s.copy(error = "Message or image is required")
            return
        }

        viewModelScope.launch {
            val priority = when {
                s.isEmergency    -> "EMERGENCY"
                s.isHighPriority -> "HIGH"
                else             -> "NORMAL"
            }
            val type = if (s.isEmergency) "EMERGENCY" else "GENERAL"

            // 1. Persist recipients to DB
            broadcastDao.setDraftRecipients(s.draftId, s.selectedOfficerIds)

            // 2. Save draft
            val draft = BroadcastDraftEntity(
                draftId              = s.draftId,
                title                = s.title,
                content              = s.content,
                priority             = priority,
                type                 = type,
                selectedUserIdsJson  = "[]", // superseded by broadcast_draft_recipients table
                targetUnit           = s.selectedUnit?.unitCode,
                targetCompany        = s.selectedCompany,
                requireAck           = s.requireAck,
                isHighPriority       = s.isHighPriority,
                isEmergency          = s.isEmergency,
                attachmentLocalPath  = s.attachmentLocalPath,
                attachmentMimeType   = s.attachmentMimeType,
                attachmentRemoteUrl  = s.attachmentRemoteUrl
            )
            broadcastRepository.saveDraft(draft)

            // 3. Create dispatch queue entry
            val dispatchId     = UUID.randomUUID().toString()
            val correlationId  = UUID.randomUUID().toString()

            val queueEntry = BroadcastDispatchQueueEntity(
                dispatchId    = dispatchId,
                draftId       = s.draftId,
                correlationId = correlationId,
                status        = "QUEUED"
            )
            broadcastRepository.enqueueDispatch(queueEntry)

            // 4. If attachment exists → save attachment entity + enqueue UploadWorker
            if (s.attachmentLocalPath != null && s.attachmentMimeType != null) {
                val attachment = BroadcastAttachmentEntity(
                    localId        = UUID.randomUUID().toString(),
                    broadcastLocalId = s.draftId,
                    uri            = s.attachmentLocalPath,
                    mimeType       = s.attachmentMimeType,
                    uploadStatus   = "PENDING"
                )
                broadcastRepository.saveAttachment(attachment)

                _state.value = _state.value.copy(dispatchState = DispatchState.UPLOADING)
                UploadWorker.enqueue(appContext, dispatchId)
            } else {
                // 5. No attachment → dispatch directly
                _state.value = _state.value.copy(dispatchState = DispatchState.QUEUED)
                BroadcastDispatchWorker.enqueue(appContext, dispatchId)
            }

            Timber.d("Broadcast queued: draftId=${s.draftId} dispatchId=$dispatchId")
        }
    }

    // ── Cleanup ───────────────────────────────────────────────

    override fun onCleared() {
        super.onCleared()
        // Note: Draft and recipients remain in DB until WorkManager completes or user cancels
    }
}
