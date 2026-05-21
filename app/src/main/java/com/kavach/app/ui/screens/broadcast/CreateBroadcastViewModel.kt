package com.kavach.app.ui.screens.broadcast

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kavach.app.data.local.BroadcastFileManager
import com.kavach.app.data.local.dao.BroadcastDao
import com.kavach.app.data.local.dao.UnitSummary
import com.kavach.app.data.local.dao.OfficerDao
import com.kavach.app.data.local.entity.BroadcastAttachmentEntity
import com.kavach.app.data.local.entity.BroadcastDispatchQueueEntity
import com.kavach.app.data.local.entity.BroadcastDraftEntity
import com.kavach.app.data.local.entity.OfficerWithProfile
import com.kavach.app.data.remote.worker.BroadcastDispatchWorker
import com.kavach.app.data.remote.worker.UploadWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

// ── Sealed state classes ──────────────────────────────────────────────────────

sealed class AttachmentState {
    object None : AttachmentState()
    /** File copied to private storage, not yet uploaded */
    data class Selected(val localPath: String, val mimeType: String) : AttachmentState()
    object Uploading : AttachmentState()
    data class Ready(val localPath: String, val remoteUrl: String) : AttachmentState()
    data class Failed(val reason: String) : AttachmentState()
}

sealed class DispatchState {
    object Idle : DispatchState()
    object Saving : DispatchState()
    object Queued : DispatchState()
    data class Failed(val reason: String) : DispatchState()
}

data class CreateBroadcastUiState(
    val draftId: String = UUID.randomUUID().toString(),
    val title: String = "",
    val content: String = "",
    val priority: String = "NORMAL",

    // Attachment — state machine, not raw URI
    val attachmentState: AttachmentState = AttachmentState.None,

    // Filter — loaded from DB, not hard-coded
    val availableUnits: List<UnitSummary> = emptyList(),
    val selectedUnit: String? = null,
    val availableCompanies: List<String> = emptyList(),
    val selectedCompany: String? = null,

    // Search — debounced input
    val searchQuery: String = "",
    val searchResults: List<OfficerWithProfile> = emptyList(),
    val isSearching: Boolean = false,

    /**
     * CANONICAL IDs ONLY — never store OfficerWithProfile objects here.
     * Persisted to broadcast_draft_recipients table on every toggle.
     * Survives process death via DB.
     */
    val selectedUserIds: Set<String> = emptySet(),

    // Delivery mode
    val requireAck: Boolean = false,
    val isHighPriority: Boolean = false,
    val isEmergency: Boolean = false,

    // State
    val dispatchState: DispatchState = DispatchState.Idle,
    val validationError: String? = null
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class CreateBroadcastViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val broadcastDao: BroadcastDao,
    private val officerDao: OfficerDao,
    private val fileManager: BroadcastFileManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(CreateBroadcastUiState())
    val uiState: StateFlow<CreateBroadcastUiState> = _uiState.asStateFlow()

    // Separate flow for debounced search to avoid re-triggering on other state changes
    private val _searchQuery = MutableStateFlow("")

    init {
        observeAvailableUnits()
        observeDebouncedSearch()
    }

    // ── Initialization ────────────────────────────────────────────────────────

    private fun observeAvailableUnits() {
        viewModelScope.launch {
            broadcastDao.observeAvailableUnits().collectLatest { units ->
                _uiState.update { it.copy(availableUnits = units) }
            }
        }
    }

    private fun observeDebouncedSearch() {
        viewModelScope.launch {
            _searchQuery
                .debounce(300L)
                .distinctUntilChanged()
                .flatMapLatest { query ->
                    if (query.isBlank()) {
                        // If no search query but unit/company selected → show filtered list
                        val state = _uiState.value
                        when {
                            state.selectedCompany != null ->
                                officerDao.observePersonnelByCompany(state.selectedCompany)
                            state.selectedUnit != null ->
                                officerDao.observePersonnelByUnit(state.selectedUnit)
                            else -> flowOf(emptyList())
                        }
                    } else {
                        _uiState.update { it.copy(isSearching = true) }
                        officerDao.searchPersonnel(query)
                    }
                }
                .collectLatest { results ->
                    _uiState.update { it.copy(searchResults = results, isSearching = false) }
                }
        }
    }

    // ── Title / Content ───────────────────────────────────────────────────────

    fun onTitleChange(title: String) {
        _uiState.update { it.copy(title = title, validationError = null) }
    }

    fun onContentChange(content: String) {
        _uiState.update { it.copy(content = content, validationError = null) }
    }

    fun onPriorityChange(priority: String) {
        _uiState.update { it.copy(priority = priority) }
    }

    // ── Image Attachment ──────────────────────────────────────────────────────

    /**
     * Called immediately when user picks an image from the system picker.
     * Copies content:// URI to app-private storage on IO dispatcher.
     * NEVER stores content:// URI in state or DB.
     */
    fun onImagePicked(uri: Uri, mimeType: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val privatePath = fileManager.copyToPrivateStorage(uri, mimeType)
                _uiState.update { it.copy(
                    attachmentState = AttachmentState.Selected(privatePath, mimeType),
                    validationError = null
                )}
                Timber.d("CreateBroadcastVM: Image copied to private storage: $privatePath")
            } catch (e: Exception) {
                Timber.e(e, "CreateBroadcastVM: Failed to copy image")
                _uiState.update { it.copy(
                    attachmentState = AttachmentState.Failed("Failed to load image: ${e.message}")
                )}
            }
        }
    }

    fun onRemoveAttachment() {
        val current = _uiState.value.attachmentState
        _uiState.update { it.copy(attachmentState = AttachmentState.None) }
        // Clean up private file
        if (current is AttachmentState.Selected) {
            viewModelScope.launch(Dispatchers.IO) {
                fileManager.deletePrivateFile(current.localPath)
            }
        }
    }

    // ── Filter System ─────────────────────────────────────────────────────────

    fun onUnitSelected(unitCode: String?) {
        _uiState.update { it.copy(
            selectedUnit = unitCode,
            selectedCompany = null,
            availableCompanies = emptyList(),
            searchResults = emptyList()
        )}
        if (unitCode != null) {
            observeCompaniesForUnit(unitCode)
            // Re-trigger personnel list for selected unit
            _searchQuery.value = _searchQuery.value
        }
    }

    private fun observeCompaniesForUnit(unitCode: String) {
        viewModelScope.launch {
            broadcastDao.observeCompaniesForUnit(unitCode).collectLatest { companies ->
                _uiState.update { it.copy(availableCompanies = companies) }
            }
        }
    }

    fun onCompanySelected(company: String?) {
        _uiState.update { it.copy(selectedCompany = company, searchResults = emptyList()) }
        _searchQuery.value = _searchQuery.value // re-trigger search flow
    }

    // ── Search ────────────────────────────────────────────────────────────────

    fun onSearchQueryChange(query: String) {
        _uiState.update { it.copy(searchQuery = query, isSearching = query.isNotBlank()) }
        _searchQuery.value = query
    }

    // ── Selection ─────────────────────────────────────────────────────────────

    /**
     * Toggles officer selection and immediately persists to DB.
     * Survives process death — WorkerManager reads from broadcast_draft_recipients table.
     */
    fun toggleSelection(officerId: String) {
        val current = _uiState.value.selectedUserIds.toMutableSet()
        if (!current.remove(officerId)) current.add(officerId)
        val updated = current.toSet()

        _uiState.update { it.copy(selectedUserIds = updated, validationError = null) }

        // Persist immediately to DB — process-death safe
        viewModelScope.launch(Dispatchers.IO) {
            broadcastDao.setDraftRecipients(_uiState.value.draftId, updated)
        }
    }

    // ── Delivery Mode ─────────────────────────────────────────────────────────

    fun onRequireAckChange(value: Boolean) {
        _uiState.update { it.copy(requireAck = value) }
    }

    fun onHighPriorityChange(value: Boolean) {
        _uiState.update { it.copy(isHighPriority = value) }
    }

    fun onEmergencyChange(value: Boolean) {
        // Emergency implies high priority
        _uiState.update { it.copy(isEmergency = value, isHighPriority = if (value) true else it.isHighPriority) }
    }

    // ── Dispatch ──────────────────────────────────────────────────────────────

    /**
     * Validates state, saves draft + attachment to DB, enqueues WorkManager job.
     * NEVER calls API directly from ViewModel.
     *
     * Validation rules:
     * - title required
     * - at least one recipient required
     * - content OR image required (not both mandatory)
     */
    fun dispatchBroadcast() {
        val s = _uiState.value
        val hasContent = s.content.isNotBlank()
        val hasAttachment = s.attachmentState is AttachmentState.Selected
                || s.attachmentState is AttachmentState.Ready

        when {
            s.title.isBlank() -> {
                _uiState.update { it.copy(validationError = "Broadcast title is required") }
                return
            }
            s.selectedUserIds.isEmpty() -> {
                _uiState.update { it.copy(validationError = "Select at least one recipient") }
                return
            }
            !hasContent && !hasAttachment -> {
                _uiState.update { it.copy(validationError = "Add a message or upload an order image") }
                return
            }
        }

        _uiState.update { it.copy(dispatchState = DispatchState.Saving, validationError = null) }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val state = _uiState.value
                val dispatchId = UUID.randomUUID().toString()

                // 1. Save draft to DB
                val draft = BroadcastDraftEntity(
                    draftId = state.draftId,
                    title = state.title,
                    content = state.content,
                    priority = if (state.isEmergency) "CRITICAL"
                               else if (state.isHighPriority) "URGENT"
                               else state.priority,
                    type = if (state.attachmentState != AttachmentState.None) "ORDER_IMAGE" else "TEXT",
                    selectedUserIdsJson = "[]", // recipients stored in broadcast_draft_recipients table
                    attachmentLocalPath = (state.attachmentState as? AttachmentState.Selected)?.localPath,
                    attachmentMimeType = (state.attachmentState as? AttachmentState.Selected)?.mimeType,
                    targetUnit = state.selectedUnit,
                    targetCompany = state.selectedCompany,
                    requireAck = state.requireAck,
                    isHighPriority = state.isHighPriority,
                    isEmergency = state.isEmergency
                )
                broadcastDao.saveDraft(draft)

                // 2. Save attachment entity (if present)
                val attachPath = (state.attachmentState as? AttachmentState.Selected)?.localPath
                val attachMime = (state.attachmentState as? AttachmentState.Selected)?.mimeType
                if (attachPath != null && attachMime != null) {
                    broadcastDao.insertAttachment(
                        BroadcastAttachmentEntity(
                            localId = UUID.randomUUID().toString(),
                            broadcastLocalId = state.draftId,
                            uri = attachPath,             // app-private absolute path
                            mimeType = attachMime,
                            uploadStatus = "PENDING"
                        )
                    )
                }

                // 3. Enqueue dispatch job
                broadcastDao.enqueueDispatch(
                    BroadcastDispatchQueueEntity(
                        dispatchId = dispatchId,
                        draftId = state.draftId,
                        correlationId = UUID.randomUUID().toString(),
                        status = "QUEUED"
                    )
                )

                // 4. Enqueue WorkManager chain
                if (attachPath != null) {
                    // Upload first → then dispatch
                    UploadWorker.enqueue(context, dispatchId)
                } else {
                    // No attachment → dispatch directly
                    BroadcastDispatchWorker.enqueue(context, dispatchId)
                }

                _uiState.update { it.copy(dispatchState = DispatchState.Queued) }
                Timber.d("CreateBroadcastVM: Dispatch queued — dispatchId=$dispatchId")

            } catch (e: Exception) {
                Timber.e(e, "CreateBroadcastVM: Failed to enqueue dispatch")
                _uiState.update { it.copy(
                    dispatchState = DispatchState.Failed("Failed to queue dispatch: ${e.message}")
                )}
            }
        }
    }
}
