package com.kavach.app.ui.screens.dashboard

import com.kavach.app.core.auth.SystemRole

/**
 * Authority Logic for Dashboard.
 * Decouples role checks from UI layout.
 */
class DashboardAuthority(private val roleString: String) {
    private val systemRole = SystemRole.fromString(roleString)

    fun canSeePersonnelManagement(): Boolean {
        return systemRole.canManagePersonnel()
    }

    fun canSeeCommandCenter(): Boolean {
        return systemRole == SystemRole.ADMIN
    }

    fun getRoleDisplayName(): String {
        return systemRole.name
    }
}
