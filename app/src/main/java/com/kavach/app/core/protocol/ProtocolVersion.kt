package com.kavach.app.core.protocol

/**
 * ProtocolVersion — Client/server capability negotiation.
 *
 * Defines the versioned API contract so older clients and newer
 * backends can coexist without silent behavioral drift.
 *
 * On WebSocket connect:
 *   1. Client sends CLIENT_HELLO with its protocol version
 *   2. Server responds with negotiated version (min of both)
 *   3. Client adjusts behavior for negotiated version
 *   4. Features above negotiated version are gracefully degraded
 *
 * Version history:
 *   1.0 — Base: auth, orders, training, broadcasts
 *   2.0 — Added: personnel management, approval workflows
 *   3.0 — Added: WebSocket events, live conference
 *   4.0 — Added: threat sequencing, signed events, reconciliation
 *        ← CURRENT CLIENT VERSION
 */

enum class ProtocolVersion(
    val major            : Int,
    val minor            : Int,
    val label            : String,
    val supportsWsEvents : Boolean,
    val supportsSignedCmds: Boolean,
    val supportsReconcile: Boolean,
    val supportsSequencing: Boolean
) {
    V1_0(1, 0, "BASE",
        supportsWsEvents    = false,
        supportsSignedCmds  = false,
        supportsReconcile   = false,
        supportsSequencing  = false
    ),
    V2_0(2, 0, "PERSONNEL",
        supportsWsEvents    = false,
        supportsSignedCmds  = false,
        supportsReconcile   = false,
        supportsSequencing  = false
    ),
    V3_0(3, 0, "LIVE_OPS",
        supportsWsEvents    = true,
        supportsSignedCmds  = false,
        supportsReconcile   = false,
        supportsSequencing  = false
    ),
    V4_0(4, 0, "TACTICAL_RUNTIME",
        supportsWsEvents    = true,
        supportsSignedCmds  = true,
        supportsReconcile   = true,
        supportsSequencing  = true
    );

    val versionString get() = "$major.$minor"

    companion object {
        /** The version this client binary implements. */
        val CURRENT = V4_0

        /** Parse a version string from the server handshake. */
        fun parse(versionString: String): ProtocolVersion? = entries.find {
            it.versionString == versionString
        }

        /**
         * Negotiate: returns the highest version both client and server support.
         * Client always degrades to server capability — never assumes server is current.
         */
        fun negotiate(serverVersionString: String): NegotiationResult {
            val serverVersion = parse(serverVersionString)
                ?: return NegotiationResult.Incompatible(
                    reason = "Unknown server protocol: '$serverVersionString'"
                )

            // Use the lower of client vs server version
            val negotiated = entries.filter {
                it.major <= CURRENT.major && it.major <= serverVersion.major
            }.maxByOrNull { it.major * 10 + it.minor }
                ?: return NegotiationResult.Incompatible("No compatible version found")

            val degradedFeatures = mutableListOf<String>()
            if (!negotiated.supportsWsEvents)    degradedFeatures.add("Real-time WebSocket events")
            if (!negotiated.supportsSignedCmds)  degradedFeatures.add("Cryptographic command signing")
            if (!negotiated.supportsReconcile)   degradedFeatures.add("State reconciliation on reconnect")
            if (!negotiated.supportsSequencing)  degradedFeatures.add("Event monotonic ordering")

            return NegotiationResult.Agreed(negotiated, degradedFeatures)
        }
    }
}

sealed class NegotiationResult {
    data class Agreed(
        val version          : ProtocolVersion,
        val degradedFeatures : List<String>    // Empty = full capability
    ) : NegotiationResult() {
        val isFullCapability get() = degradedFeatures.isEmpty()
    }
    data class Incompatible(val reason: String) : NegotiationResult()
}
