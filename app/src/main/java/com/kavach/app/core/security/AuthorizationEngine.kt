package com.kavach.app.core.security

import com.kavach.app.core.events.EventBus
import com.kavach.app.core.events.SystemEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AuthorizationEngine — Permission escalation for critical actions.
 *
 * Before any high-authority action executes, it must be validated here.
 * Actions at or above [AuthorityLevel.ELEVATED] require secondary confirmation
 * (biometric / command token). The engine tracks pending challenges and
 * their resolution, emitting SystemEvents on approval or denial.
 *
 * Usage:
 *   1. Call [requestAuthorization] — engine checks if challenge is required
 *   2. If [AuthorizationResult.CHALLENGE_REQUIRED], surface biometric/PIN UI
 *   3. Call [resolveChallenge(granted = true/false)] with the result
 *   4. On GRANTED → proceed with action. On DENIED → abort and log.
 */

// ── Action Catalog ────────────────────────────────────────────

enum class CriticalAction(
    val label         : String,
    val requiredLevel : AuthorityLevel,
    val requiresBio   : Boolean,
    val auditRequired : Boolean,
    val requiresReauth: Boolean = false
) {
    // Field-level: any authenticated user can trigger
    ACKNOWLEDGE_ORDER       ("Acknowledge Order",        AuthorityLevel.USER,     false, true),
    ACKNOWLEDGE_BROADCAST   ("Acknowledge Broadcast",    AuthorityLevel.USER,     false, true),

    // Pilot-level: requires PILOT or above
    SEND_REMINDER           ("Send Reminder to Officer", AuthorityLevel.PILOT,    false, true),
    REVOKE_DEVICE           ("Revoke Device Binding",    AuthorityLevel.PILOT,    true,  true, true),
    APPROVE_PERSONNEL_CHANGE("Approve Personnel Change", AuthorityLevel.PILOT,    true,  true, true),
    ADD_USER                ("Add New User/Officer",     AuthorityLevel.PILOT,    true,  true, true),

    // Command-level: requires ADMIN/CO
    BROADCAST_ALL           ("Broadcast to All Units",   AuthorityLevel.ADMIN,    true,  true),
    FORCE_LOGOUT_USER       ("Force Remote Logout",      AuthorityLevel.ADMIN,    true,  true, true),
    ESCALATE_THREAT         ("Manual Threat Escalation", AuthorityLevel.ADMIN,    true,  true),

    // Senanayak-level: absolute authority
    ACTIVATE_LOCKDOWN       ("Activate Lockdown Protocol", AuthorityLevel.SUPERUSER, true, true),
    DEPLOYMENT_FREEZE       ("Freeze Active Deployment",   AuthorityLevel.SUPERUSER, true, true),
    COMMAND_OVERRIDE        ("Issue Command Override",     AuthorityLevel.SUPERUSER, true, true),
}

enum class AuthorityLevel(val rank: Int) {
    USER(0), PILOT(1), ADMIN(2), SUPERUSER(3)
}

/** Point 8 FIX: Sensitive Action Audit Chain Requirement */
data class AuditEntry(
    val actorId     : String,
    val actionLabel : String,
    val timestamp   : Long,
    val deviceId    : String,
    val threatState : String,
    val result      : String
)

fun String.toAuthorityLevel(): AuthorityLevel = when (this.uppercase()) {
    "SUPERUSER", "SENANAYAK"   -> AuthorityLevel.SUPERUSER
    "COMMANDING_OFFICER","ADMIN" -> AuthorityLevel.ADMIN
    "PILOT"                    -> AuthorityLevel.PILOT
    else                       -> AuthorityLevel.USER
}

// ── Authorization Result ──────────────────────────────────────

sealed class AuthorizationResult {
    object Granted                       : AuthorizationResult()
    object Denied                        : AuthorizationResult()
    object InsufficientAuthority         : AuthorizationResult()
    data class ChallengeRequired(
        val action      : CriticalAction,
        val requiresBio : Boolean
    ) : AuthorizationResult()
}

// ── Pending Challenge ─────────────────────────────────────────

data class PendingChallenge(
    val action      : CriticalAction,
    val actorRole   : AuthorityLevel,
    val requiresBio : Boolean,
    val onResolved  : (Boolean) -> Unit
)

// ── Engine ────────────────────────────────────────────────────

@Singleton
class AuthorizationEngine @Inject constructor(
    private val eventBus        : EventBus,
    private val sessionDataStore : com.kavach.app.data.local.SessionDataStore
) {

    private val _pendingChallenge = MutableStateFlow<PendingChallenge?>(null)
    val pendingChallenge: StateFlow<PendingChallenge?> = _pendingChallenge.asStateFlow()

    /**
     * Request authorization for a critical action.
     *
     * @param action      The action to authorize
     * @param actorRole   The current user's role string ("PILOT", "ADMIN", etc.)
     * @param onResolved  Callback called with true (granted) or false (denied)
     * @return Immediate result — either GRANTED (no challenge), CHALLENGE_REQUIRED, or INSUFFICIENT_AUTHORITY
     */
    fun requestAuthorization(
        action      : CriticalAction,
        actorRole   : String,
        isOffline   : Boolean = false,
        capability  : com.kavach.app.ui.navigation.CapabilityLevel = com.kavach.app.ui.navigation.CapabilityLevel.FULL,
        onResolved  : (Boolean) -> Unit
    ): AuthorizationResult {
        // Point 2 FIX: Progressive Capability Enforcement
        when (capability) {
            com.kavach.app.ui.navigation.CapabilityLevel.READ_ONLY -> {
                eventBus.emit(com.kavach.app.core.events.SystemEvent.AdminEscalation("SYSTEM", "READ_ONLY mode: Action blocked: ${action.label}"))
                onResolved(false)
                return AuthorizationResult.InsufficientAuthority
            }
            com.kavach.app.ui.navigation.CapabilityLevel.RESTRICTED -> {
                // Deny high-impact mutations in restricted mode
                if (action.requiredLevel.rank >= AuthorityLevel.PILOT.rank) {
                    eventBus.emit(com.kavach.app.core.events.SystemEvent.AdminEscalation("SYSTEM", "RESTRICTED mode: Mutation blocked: ${action.label}"))
                    onResolved(false)
                    return AuthorizationResult.InsufficientAuthority
                }
            }
            else -> {}
        }

        // Previous offline check (maintained for backward compatibility/redundancy)
        if (isOffline && action.requiredLevel.rank >= AuthorityLevel.PILOT.rank) {
            eventBus.emit(com.kavach.app.core.events.SystemEvent.AdminEscalation("SYSTEM", "Offline mode: Action denied: ${action.label}"))
            onResolved(false)
            return AuthorizationResult.InsufficientAuthority
        }

        val actorLevel = actorRole.toAuthorityLevel()

        // Check authority sufficiency
        if (actorLevel.rank < action.requiredLevel.rank) {
            eventBus.emit(SystemEvent.AdminEscalation("SYSTEM", "Insufficient authority for: ${action.label}"))
            onResolved(false)
            return AuthorizationResult.InsufficientAuthority
        }

        // Session Freshness Check (15 min window)
        val lastAuth = runBlocking { 
            sessionDataStore.lastAuthTime.first() 
        }
        val isStale = System.currentTimeMillis() - lastAuth > 15 * 60 * 1000
        val forceChallenge = action.requiresReauth && isStale

        // If no biometric challenge needed AND no forced reauth, auto-grant
        if (!action.requiresBio && !forceChallenge) {
            if (action.auditRequired) {
                eventBus.emit(SystemEvent.AuditCaptureActive)
            }
            onResolved(true)
            return AuthorizationResult.Granted
        }

        // Store pending challenge for UI to surface
        _pendingChallenge.value = PendingChallenge(
            action      = action,
            actorRole   = actorLevel,
            requiresBio = action.requiresBio,
            onResolved  = { granted ->
                _pendingChallenge.value = null
                if (granted && action.auditRequired) {
                    eventBus.emit(SystemEvent.AuditCaptureActive)
                }
                if (!granted) {
                    eventBus.emit(SystemEvent.AdminEscalation("SYSTEM", "Challenge denied: ${action.label}"))
                }
                onResolved(granted)
            }
        )

        return AuthorizationResult.ChallengeRequired(action, action.requiresBio)
    }

    /** Called when biometric/PIN result returns. */
    fun resolveChallenge(granted: Boolean) {
        _pendingChallenge.value?.onResolved?.invoke(granted)
    }

    /** Cancel without resolution (user dismissed). */
    fun cancelChallenge() {
        _pendingChallenge.value?.onResolved?.invoke(false)
        _pendingChallenge.value = null
    }
}
