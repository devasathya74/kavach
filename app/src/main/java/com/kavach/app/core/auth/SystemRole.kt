package com.kavach.app.core.auth

enum class SystemRole(val authority: Int) {
    ADMIN(100),
    PILOT(80),
    USER(10);

    companion object {
        fun fromString(role: String?): SystemRole = when (role?.uppercase()) {
            "ADMIN", "COMMANDING_OFFICER" -> ADMIN
            "PILOT" -> PILOT
            else -> USER
        }
    }

    fun canManagePersonnel(): Boolean = this == ADMIN || this == PILOT
    fun isCommand(): Boolean = this == ADMIN || this == PILOT
}
