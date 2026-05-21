package com.kavach.app.ui.screens.pilot.broadcast

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kavach.app.data.local.entity.BroadcastDispatchQueueEntity
import com.kavach.app.data.local.entity.BroadcastDraftEntity
import com.kavach.app.data.repository.BroadcastRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class OperationalComposerViewModel @Inject constructor(
    private val repository: BroadcastRepository,
    private val savedStateHandle: SavedStateHandle,
    // Dependency on SelectionEngineViewModel can be injected in UI or managed here, 
    // but typically ViewModels are independent and UI passes data between them.
    // We'll manage draft save logic here.
) : ViewModel() {

    private val draftId: String = savedStateHandle.get<String>("draftId") ?: UUID.randomUUID().toString()

    private val _title = MutableStateFlow(savedStateHandle.get<String>("title") ?: "")
    val title: StateFlow<String> = _title.asStateFlow()

    private val _content = MutableStateFlow(savedStateHandle.get<String>("content") ?: "")
    val content: StateFlow<String> = _content.asStateFlow()

    private val _priority = MutableStateFlow(savedStateHandle.get<String>("priority") ?: "INFO")
    val priority: StateFlow<String> = _priority.asStateFlow()
    
    private val _type = MutableStateFlow(savedStateHandle.get<String>("type") ?: "TEXT")
    val type: StateFlow<String> = _type.asStateFlow()

    // Load existing draft if present
    init {
        viewModelScope.launch {
            val existing = repository.getDraft(draftId)
            if (existing != null) {
                _title.value = existing.title
                _content.value = existing.content
                _priority.value = existing.priority
                _type.value = existing.type
                // UI will need to re-populate SelectionEngineViewModel from existing.selectedUserIdsJson
            }
        }
    }

    fun updateTitle(t: String) {
        _title.value = t
        savedStateHandle["title"] = t
    }

    fun updateContent(c: String) {
        _content.value = c
        savedStateHandle["content"] = c
    }

    fun updatePriority(p: String) {
        _priority.value = p
        savedStateHandle["priority"] = p
    }

    fun saveDraft(selectedUserIds: Set<String>) {
        viewModelScope.launch {
            val jsonArray = JSONArray()
            selectedUserIds.forEach { jsonArray.put(it) }
            
            val draft = BroadcastDraftEntity(
                draftId = draftId,
                title = title.value,
                content = content.value,
                priority = priority.value,
                type = type.value,
                selectedUserIdsJson = jsonArray.toString()
            )
            repository.saveDraft(draft)
        }
    }

    fun deleteDraft() {
        viewModelScope.launch {
            repository.deleteDraft(draftId)
        }
    }

    fun enqueueDispatch(selectedUserIds: Set<String>, onSuccess: () -> Unit, onError: (String) -> Unit) {
        if (title.value.isBlank()) {
            onError("Title is required")
            return
        }
        if (selectedUserIds.isEmpty()) {
            onError("At least one recipient must be selected")
            return
        }

        viewModelScope.launch {
            // Ensure draft is saved before enqueuing
            saveDraft(selectedUserIds)
            
            val correlationId = UUID.randomUUID().toString()
            val job = BroadcastDispatchQueueEntity(
                dispatchId = UUID.randomUUID().toString(),
                draftId = draftId,
                correlationId = correlationId,
                status = "QUEUED"
            )
            repository.enqueueDispatch(job)
            
            // Trigger worker here (implementation done in Worker phase)
            // e.g. BroadcastDispatchWorker.enqueue(context, job.dispatchId)
            
            onSuccess()
        }
    }
}
