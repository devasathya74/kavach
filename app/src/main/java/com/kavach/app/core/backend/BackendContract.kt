package com.kavach.app.core.backend

/**
 * BackendContract — Authoritative integration surface definition.
 *
 * This file is the SINGLE SOURCE OF TRUTH for what the real Django
 * backend must implement to replace all simulated systems.
 *
 * It defines:
 *   1. WebSocket message envelope schema
 *   2. Event type routing (WS vs REST)
 *   3. REST API endpoint surface
 *   4. Authentication token format
 *   5. Reconciliation state endpoint schema
 *   6. Server-side sequencing requirements
 *
 * The Django backend engineer reads this file.
 * The Android client is built against this file.
 * Any deviation is a CONTRACT VIOLATION.
 *
 * Version: 1.0 — matches KAVACH client v4.0
 */

// ══════════════════════════════════════════════════════════════
// 1. WEBSOCKET ENVELOPE
// ══════════════════════════════════════════════════════════════
// URL: wss://{host}/ws/command/?token={jwt}
//
// All WebSocket messages use this JSON envelope:
// {
//   "seq"       : <monotonic Long>,      ← server-assigned sequence ID
//   "ts"        : <epoch_ms Long>,       ← server trusted timestamp
//   "nonce"     : "<string>",            ← unique per-message replay prevention
//   "sig"       : "<hmac_b64>",          ← HMAC-SHA256 of "seq:ts:type:payload"
//   "type"      : "<EventType>",         ← see WsEventType below
//   "payload"   : { ... }               ← type-specific JSON object
// }

object BackendContract {

    // ── WebSocket Event Types ─────────────────────────────────

    /**
     * Events the SERVER sends to the client over WebSocket.
     * Client must handle all of these via EventBus propagation.
     */
    object ServerToClient {
        const val LOCKDOWN_ACTIVATED      = "lockdown.activated"
        const val LOCKDOWN_LIFTED         = "lockdown.lifted"
        const val COMMAND_OVERRIDE        = "command.override"
        const val EMERGENCY_BROADCAST     = "broadcast.emergency"
        const val THREAT_LEVEL_CHANGED    = "threat.level_changed"
        const val FORCE_LOGOUT            = "session.force_logout"
        const val DEPLOYMENT_FREEZE       = "deployment.freeze"
        const val ADMIN_ESCALATION        = "admin.escalation"
        const val UPLINK_PING             = "uplink.ping"         // Server → client heartbeat
        const val STATE_RECONCILIATION    = "state.reconciliation" // Server pushes auth state
        const val CONFERENCE_INVITE       = "conference.invite"
    }

    /**
     * Events the CLIENT sends to the server over WebSocket.
     */
    object ClientToServer {
        const val UPLINK_PONG             = "uplink.pong"         // Client → server heartbeat ack
        const val COMMAND_ACK             = "command.ack"         // Acknowledge a command override
        const val LOCKDOWN_ACK            = "lockdown.ack"        // Acknowledge lockdown receipt
        const val BROADCAST_ACK           = "broadcast.ack"       // Acknowledge emergency broadcast
        const val TELEMETRY_REPORT        = "telemetry.report"    // Client reports its own RTT/loss
        const val SESSION_HEARTBEAT       = "session.heartbeat"   // Keep session alive
    }

    // ── WebSocket Payload Schemas ─────────────────────────────
    // Document each payload as a comment schema. Kotlin data classes
    // for deserialization live in the data layer, not here.

    /**
     * lockdown.activated payload:
     * { "issued_by": "SENANAYAK", "reason": "string", "timestamp_ms": Long }
     */

    /**
     * command.override payload:
     * { "title": "string", "body": "string", "issued_by": "string",
     *   "requires_ack": Boolean, "command_id": "string" }
     */

    /**
     * broadcast.emergency payload:
     * { "broadcast_id": "string", "message": "string",
     *   "priority": "CRITICAL|ELEVATED|WARNING", "expires_at_ms": Long }
     */

    /**
     * threat.level_changed payload:
     * { "level": "SECURE|WARNING|ELEVATED|CRITICAL|COMPROMISED",
     *   "triggered_by": "string", "reason": "string" }
     */

    /**
     * state.reconciliation payload (full authoritative state push):
     * {
     *   "threat_level"      : "ELEVATED",
     *   "command_mode"      : "ELEVATED_MONITORING",
     *   "lockdown_active"   : false,
     *   "pending_override"  : null | "string",
     *   "server_sequence_id": Long,
     *   "server_timestamp_ms": Long
     * }
     */

    /**
     * telemetry.report payload (client → server):
     * { "device_id": "string", "api_rtt_ms": Long, "ws_latency_ms": Long,
     *   "packet_loss_pct": Float, "uplink_status": "string",
     *   "timestamp_ms": Long }
     */

    // ══════════════════════════════════════════════════════════
    // 2. REST API SURFACE
    // ══════════════════════════════════════════════════════════
    // Base URL: https://{host}/api/v2/
    // Auth: Authorization: Bearer {jwt_token}
    // Content-Type: application/json

    object Endpoints {

        // ── Authentication ─────────────────────────────────
        const val LOGIN_REQUEST_OTP  = "POST /auth/login/"
        // body: { "pno": "string", "device_id": "string" }
        // response: { "status": "otp_sent" }

        const val LOGIN_VERIFY_OTP   = "POST /auth/verify/"
        // body: { "pno": "string", "otp": "string", "device_id": "string" }
        // response: { "token": "jwt", "role": "string", "pno": "string",
        //             "server_timestamp_ms": Long }  ← for TrustedClock.sync()

        const val LOGOUT             = "POST /auth/logout/"
        // body: {} (token in header)

        const val REFRESH_TOKEN      = "POST /auth/refresh/"
        // body: { "refresh": "string" }
        // response: { "access": "string" }

        // ── System State ───────────────────────────────────
        const val SYSTEM_STATE       = "GET /system/state/"
        // response: state.reconciliation payload (see above)
        // Called by StateReconciliationEngine on reconnect

        const val SERVER_TIME        = "GET /system/time/"
        // response: { "timestamp_ms": Long, "iso": "string" }
        // Called by TrustedClock on first sync

        const val HEALTH_PING        = "GET /system/health/"
        // response: { "status": "ok", "version": "string" }
        // Used by TelemetryManager for RTT measurement

        // ── Personnel ──────────────────────────────────────
        const val PERSONNEL_LIST     = "GET /personnel/"
        const val PERSONNEL_DETAIL   = "GET /personnel/{pno}/"
        const val PERSONNEL_UPDATE   = "PATCH /personnel/{pno}/"
        const val APPROVAL_LIST      = "GET /personnel/approvals/"
        const val APPROVAL_DECIDE    = "POST /personnel/approvals/{id}/decide/"
        // body: { "action": "approve|reject", "reason": "string" }

        // ── Orders ─────────────────────────────────────────
        const val ORDER_LIST         = "GET /orders/"
        const val ORDER_DETAIL       = "GET /orders/{id}/"
        const val ORDER_ACKNOWLEDGE  = "POST /orders/{id}/acknowledge/"
        // body: { "pno": "string", "timestamp_ms": Long }

        // ── Broadcasts ─────────────────────────────────────
        const val BROADCAST_LIST     = "GET /broadcasts/"
        const val BROADCAST_CREATE   = "POST /broadcasts/"
        // body: { "message": "string", "priority": "string",
        //         "target": "ALL|SECTOR|UNIT", "expires_in_sec": Int }
        const val BROADCAST_ACK      = "POST /broadcasts/{id}/acknowledge/"

        // ── Training ───────────────────────────────────────
        const val TRAINING_LIST      = "GET /training/"
        const val TRAINING_DETAIL    = "GET /training/{id}/"
        const val TRAINING_COMPLETE  = "POST /training/{id}/complete/"
        const val QUIZ_SUBMIT        = "POST /training/{id}/quiz/submit/"
        // body: { "answers": [ { "q_id": Int, "selected": Int } ] }
        const val QUIZ_RESULT        = "GET /training/{id}/quiz/result/"

        // ── Audit & Forensics ──────────────────────────────
        const val AUDIT_TIMELINE     = "GET /audit/timeline/"
        // query: ?since_ms=Long&category=string&limit=Int
        const val INCIDENT_UPLOAD    = "POST /audit/incidents/"
        // body: IncidentPackage JSON (from IncidentPackageExporter.toJson())
        const val FORENSIC_SNAPSHOTS = "GET /audit/snapshots/"

        // ── Conference ─────────────────────────────────────
        const val CONFERENCE_LIST    = "GET /conference/"
        const val CONFERENCE_JOIN    = "POST /conference/{id}/join/"
        const val CONFERENCE_LEAVE   = "POST /conference/{id}/leave/"
    }

    // ══════════════════════════════════════════════════════════
    // 3. SEQUENCING REQUIREMENTS (Server)
    // ══════════════════════════════════════════════════════════
    // The server MUST:
    //   1. Assign monotonically increasing "seq" per connected session
    //   2. Include "ts" as authoritative UTC epoch-ms
    //   3. Generate cryptographically random "nonce" (≥ 16 bytes hex)
    //   4. Sign "seq:ts:type:{payload_hash}" with the session HMAC key
    //   5. Reject client messages with "ts" outside ±5 minute window
    //   6. Track client nonces for replay prevention (sliding 512-nonce window)

    // ══════════════════════════════════════════════════════════
    // 4. AUTHENTICATION TOKEN FORMAT
    // ══════════════════════════════════════════════════════════
    // JWT payload:
    // {
    //   "pno"        : "PNO-0042",
    //   "role"       : "PILOT|ADMIN|COMMANDING_OFFICER|SUPERUSER|USER",
    //   "device_id"  : "string",
    //   "iat"        : epoch_sec,
    //   "exp"        : epoch_sec,
    //   "iss"        : "kavach-command"
    // }

    // ══════════════════════════════════════════════════════════
    // 5. REAL TELEMETRY INTEGRATION (OkHttp)
    // ══════════════════════════════════════════════════════════
    // TelemetryManager expects these methods to be called by interceptors:
    //
    // OkHttp Interceptor (ApiInterceptor.kt):
    //   val startMs = System.currentTimeMillis()
    //   val response = chain.proceed(request)
    //   telemetryManager.recordApiRtt(System.currentTimeMillis() - startMs)
    //   return response
    //
    // WebSocket Listener (KavachWsListener.kt):
    //   override fun onMessage(ws: WebSocket, text: String) {
    //       val latency = System.currentTimeMillis() - lastPingMs
    //       telemetryManager.recordWsLatency(latency)
    //       // parse envelope → EventBus
    //   }
    //   override fun onFailure(...) { telemetryManager.onWsReconnecting(++attempt) }
    //   override fun onOpen(...)   { telemetryManager.onWsConnected() }
    //   override fun onClosed(...) { telemetryManager.onUplinkOffline() }

    // ══════════════════════════════════════════════════════════
    // 6. ERROR CODES
    // ══════════════════════════════════════════════════════════
    object ErrorCodes {
        const val TOKEN_EXPIRED        = 401
        const val INSUFFICIENT_ROLE    = 403
        const val NOT_FOUND            = 404
        const val RATE_LIMITED         = 429
        const val SERVER_ERROR         = 500
        const val MAINTENANCE          = 503
    }

    // ══════════════════════════════════════════════════════════
    // 7. BACKEND IMPLEMENTATION CHECKLIST
    // ══════════════════════════════════════════════════════════
    // Django backend must implement:
    //   [ ] Django Channels WebSocket consumer at /ws/command/
    //   [ ] JWT authentication via djangorestframework-simplejwt
    //   [ ] Per-session monotonic sequence counter (Redis or DB)
    //   [ ] HMAC-SHA256 message signing with per-session key
    //   [ ] Nonce tracking (Redis SET with 5-min TTL)
    //   [ ] GET /system/state/ returning AuthoritativeState schema
    //   [ ] GET /system/time/ returning trusted server timestamp
    //   [ ] POST /audit/incidents/ accepting IncidentPackage JSON
    //   [ ] WebSocket broadcast for all admin-issued command events
    //   [ ] Role-based command authorization on server side
    //   [ ] Server-side LOCKDOWN state stored in Redis (survives restart)
}
