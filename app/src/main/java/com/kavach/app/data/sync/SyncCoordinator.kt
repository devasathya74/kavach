package com.kavach.app.data.sync

import com.kavach.app.data.repository.UserManagementRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

enum class SystemMode {
    NORMAL, DEGRADED, OFFLINE, INCIDENT, LOCKDOWN
}

@Singleton
class SyncCoordinator @Inject constructor(
    private val userRepository: UserManagementRepository
) {
    private val scope = CoroutineScope(Dispatchers.IO)
    
    private val _mode = MutableStateFlow(SystemMode.NORMAL)
    val mode = _mode.asStateFlow()

    fun onRealtimeEvent(type: String, payload: Map<String, Any>) {
        when (type) {
            "GovernanceApplied" -> {
                // Coalesced Sync: Refresh personnel data
                scope.launch { userRepository.refreshUsers(page = 1) }
            }
            "IncidentCreated" -> {
                _mode.value = SystemMode.INCIDENT
                // Trigger incident refresh
            }
            "DeviceRevoked" -> {
                // Potential force logout logic
            }
            "SystemLockdown" -> {
                _mode.value = SystemMode.LOCKDOWN
            }
        }
    }
    
    fun setMode(newMode: SystemMode) {
        _mode.value = newMode
    }
}
