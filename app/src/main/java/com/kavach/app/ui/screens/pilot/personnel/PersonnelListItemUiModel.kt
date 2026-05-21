package com.kavach.app.ui.screens.pilot.personnel

import com.kavach.app.data.remote.dto.personnel.OfficerDto

data class PersonnelListItemUiModel(
    val id: String,
    val pno: String,
    val name: String,
    val rank: String,
    val company: String,
    val status: String,
    val role: String,
    val lastActive: String,
    val unitCode: String
)

fun OfficerDto.toUiModel(): PersonnelListItemUiModel {
    return PersonnelListItemUiModel(
        id         = this.id,
        pno        = this.pno,
        name       = this.profile?.name ?: "Unknown",
        rank       = this.profile?.rank?.name ?: "Unknown",
        company    = this.profile?.company?.name ?: "No Coy",
        status     = this.profile?.serviceStatus ?: if (this.isActive) "Active" else "Inactive",
        role       = this.role,
        lastActive = this.devices.maxByOrNull { it.lastActive ?: "" }?.lastActive ?: "Never",
        unitCode   = this.unit?.code ?: "N/A"
    )
}
