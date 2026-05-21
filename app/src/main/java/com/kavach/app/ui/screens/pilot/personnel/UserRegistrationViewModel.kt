package com.kavach.app.ui.screens.pilot.personnel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kavach.app.data.remote.dto.personnel.OfficerDto
import com.kavach.app.data.remote.dto.v2.CreateUserRequest
import com.kavach.app.data.remote.dto.v2.UpdateUserRequest
import com.kavach.app.data.repository.UserManagementRepository
import com.kavach.app.utils.onFailure
import com.kavach.app.utils.onSuccess
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class UserFormState(
    val name: String = "",
    val pno: String = "",
    val password: String = "",
    val role: String = "USER",
    val rankId: String = "CONSTABLE",
    val unitType: String = "HQ",
    val unitId: String = "HQ_UP_PAC",
    val companyId: String? = null,
    val platoonId: String? = null,
    val phone: String = "",
    val email: String = "",
    
    val isLoading: Boolean = false,
    val isSubmitting: Boolean = false,
    val error: String? = null,
    val isSuccess: Boolean = false,
    val isEditMode: Boolean = false
)

@HiltViewModel
class UserRegistrationViewModel @Inject constructor(
    private val repository: UserManagementRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val officerId: String? = savedStateHandle["userId"]
    
    private val _state = MutableStateFlow(UserFormState(isEditMode = officerId != null))
    val state = _state.asStateFlow()

    init {
        if (officerId != null) {
            loadOfficer(officerId)
        }
    }

    private fun loadOfficer(id: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            repository.getUserDetailNetwork(id)
                .onSuccess { officer ->
                    _state.update { 
                        it.copy(
                            isLoading = false,
                            name = officer.profile?.name ?: "",
                            pno = officer.pno,
                            role = officer.role,
                            rankId = officer.profile?.rank?.code ?: "CONSTABLE",
                            unitType = officer.unit?.type ?: "HQ",
                            unitId = officer.unit?.code ?: "HQ_UP_PAC",
                            companyId = officer.profile?.company?.code,
                            platoonId = officer.profile?.platoon?.number?.toString(),
                            phone = officer.profile?.phone ?: "",
                            email = officer.profile?.email ?: ""
                        )
                    }
                }
                .onFailure { e ->
                    _state.update { it.copy(isLoading = false, error = e.message) }
                }
        }
    }

    fun onNameChange(v: String) = _state.update { it.copy(name = v) }
    fun onPnoChange(v: String) = _state.update { it.copy(pno = v) }
    fun onPasswordChange(v: String) = _state.update { it.copy(password = v) }
    fun onRoleChange(v: String) = _state.update { it.copy(role = v) }
    fun onRankChange(v: String) = _state.update { it.copy(rankId = v) }
    fun onUnitTypeChange(v: String) {
        _state.update { 
            it.copy(
                unitType = v,
                unitId = if (v == "HQ") "HQ_UP_PAC" else it.unitId,
                companyId = if (v != "BATTALION") null else it.companyId,
                platoonId = if (v != "BATTALION") null else it.platoonId
            )
        }
    }
    fun onUnitIdChange(v: String) = _state.update { it.copy(unitId = v) }
    fun onCompanyChange(v: String?) = _state.update { it.copy(companyId = v) }
    fun onPlatoonChange(v: String?) = _state.update { it.copy(platoonId = v) }
    fun onPhoneChange(v: String) = _state.update { it.copy(phone = v) }
    fun onEmailChange(v: String) = _state.update { it.copy(email = v) }

    fun submit() {
        viewModelScope.launch {
            _state.update { it.copy(isSubmitting = true, error = null) }
            
            val result = if (officerId != null) {
                // EDIT MODE
                repository.updateUser(
                    officerId,
                    UpdateUserRequest(
                        name = state.value.name,
                        role = state.value.role,
                        rankId = state.value.rankId,
                        unitId = state.value.unitId,
                        companyId = state.value.companyId,
                        platoonId = state.value.platoonId,
                        phone = state.value.phone,
                        email = state.value.email
                    )
                )
            } else {
                // CREATE MODE
                repository.createUser(
                    CreateUserRequest(
                        name = state.value.name,
                        pno = state.value.pno,
                        password = state.value.password,
                        role = state.value.role,
                        rankId = state.value.rankId,
                        unitId = state.value.unitId,
                        companyId = state.value.companyId,
                        platoonId = state.value.platoonId,
                        phone = state.value.phone,
                        email = state.value.email
                    )
                )
            }

            result.onSuccess {
                _state.update { it.copy(isSubmitting = false, isSuccess = true) }
            }.onFailure { e ->
                _state.update { it.copy(isSubmitting = false, error = e.message) }
            }
        }
    }
}
