package com.kavach.app.core.graph

import com.kavach.app.core.authority.AuthoritySource
import com.kavach.app.core.clock.TrustedClock
import com.kavach.app.core.events.EventBus
import com.kavach.app.core.events.SystemEvent
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * CommandGraphEngine — Real-time authority graph data model.
 *
 * Builds a live directed graph of active commands, their propagation
 * paths, and acknowledgment states. This IS the strategic visibility layer.
 *
 * Graph structure:
 *   DirectiveNode — a command/event that was issued
 *   PropagationEdge — from issuer to recipient with ack state
 *   CommandGraph — the full observable graph at a point in time
 *
 * Updated automatically from EventBus. Displayed in the Audit Console
 * and Pilot Command Center as a real-time authority visualization.
 */

// ── Graph Data Model ──────────────────────────────────────────

enum class NodeType {
    ROOT_COMMAND,      // Senanayak / Admin top-level directive
    PROPAGATION_RELAY, // Intermediate relay (pilot passing to officers)
    LEAF_TARGET,       // End recipient (field officer)
    SYSTEM_EVENT       // Automated system-generated node
}

enum class AckStatus { PENDING, ACKNOWLEDGED, FAILED, SUPERSEDED }

data class DirectiveNode(
    val id          : String,
    val label       : String,
    val type        : NodeType,
    val authority   : AuthoritySource,
    val issuedAtMs  : Long,
    val issuedAtIso : String,
    val actor       : String,
    val ackStatus   : AckStatus = AckStatus.PENDING,
    val isActive    : Boolean   = true
)

data class PropagationEdge(
    val edgeId      : String,
    val sourceNodeId: String,
    val targetNodeId: String,
    val ackStatus   : AckStatus = AckStatus.PENDING,
    val latencyMs   : Long?     = null   // ms from issue to ack (null if pending)
)

data class CommandGraph(
    val nodes       : List<DirectiveNode>  = emptyList(),
    val edges       : List<PropagationEdge> = emptyList(),
    val lastUpdated : Long                 = 0L
) {
    val activeNodes  get() = nodes.filter { it.isActive }
    val pendingNodes get() = nodes.filter { it.ackStatus == AckStatus.PENDING && it.isActive }
    val rootNodes    get() = nodes.filter { it.type == NodeType.ROOT_COMMAND }
}

// ── Engine ────────────────────────────────────────────────────

@Singleton
class CommandGraphEngine @Inject constructor(
    private val eventBus    : EventBus,
    private val trustedClock: TrustedClock
) {

    companion object {
        private const val MAX_NODES = 200
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _graph = MutableStateFlow(CommandGraph())
    val graph: StateFlow<CommandGraph> = _graph.asStateFlow()

    private var nodeCounter = 0
    private var edgeCounter = 0

    init { observeEvents() }

    private fun observeEvents() = scope.launch {
        eventBus.events.collect { event ->
            when (event) {
                is SystemEvent.LockdownActivated -> addNode(
                    label     = "LOCKDOWN: ${event.issuedBy}",
                    type      = NodeType.ROOT_COMMAND,
                    authority = AuthoritySource.SENANAYAK,
                    actor     = event.issuedBy
                )
                is SystemEvent.CommandOverride -> addNode(
                    label     = event.title,
                    type      = NodeType.ROOT_COMMAND,
                    authority = when {
                        event.issuedBy.contains("SENANAYAK", ignoreCase = true) -> AuthoritySource.SENANAYAK
                        event.issuedBy.contains("COMMAND", ignoreCase = true)   -> AuthoritySource.COMMAND
                        event.issuedBy.contains("PILOT", ignoreCase = true)     -> AuthoritySource.PILOT
                        event.issuedBy.contains("SYSTEM", ignoreCase = true)    -> AuthoritySource.SYSTEM
                        else                                                     -> AuthoritySource.FIELD
                    },
                    actor     = event.issuedBy
                )
                is SystemEvent.EmergencyBroadcast -> addNode(
                    label     = "[${event.priority}] ${event.broadcastId}",
                    type      = NodeType.SYSTEM_EVENT,
                    authority = AuthoritySource.EMERGENCY,
                    actor     = "BROADCAST SYSTEM"
                )
                is SystemEvent.LockdownLifted -> markSupersedeAll()
                is SystemEvent.ThreatCleared  -> markAllAcked()
                else -> Unit
            }
        }
    }

    /** Add a node to the graph. Returns the new node ID. */
    fun addNode(
        label     : String,
        type      : NodeType,
        authority : AuthoritySource,
        actor     : String
    ): String {
        val id   = "N${++nodeCounter}"
        val node = DirectiveNode(
            id          = id,
            label       = label,
            type        = type,
            authority   = authority,
            issuedAtMs  = trustedClock.nowMs(),
            issuedAtIso = trustedClock.nowIso(),
            actor       = actor
        )
        val currentNodes = _graph.value.nodes.toMutableList().also { it.add(0, node) }
        if (currentNodes.size > MAX_NODES) currentNodes.subList(MAX_NODES, currentNodes.size).clear()
        _graph.value = _graph.value.copy(nodes = currentNodes, lastUpdated = trustedClock.nowMs())
        return id
    }

    /** Add a directed propagation edge between two nodes. */
    fun addEdge(sourceId: String, targetId: String): String {
        val id   = "E${++edgeCounter}"
        val edge = PropagationEdge(edgeId = id, sourceNodeId = sourceId, targetNodeId = targetId)
        _graph.value = _graph.value.copy(
            edges       = _graph.value.edges + edge,
            lastUpdated = trustedClock.nowMs()
        )
        return id
    }

    /** Record acknowledgment from a target node. */
    fun acknowledge(nodeId: String) {
        val nowMs = trustedClock.nowMs()
        val nodes = _graph.value.nodes.map { node ->
            if (node.id == nodeId) node.copy(ackStatus = AckStatus.ACKNOWLEDGED) else node
        }
        val edges = _graph.value.edges.map { edge ->
            if (edge.targetNodeId == nodeId) {
                val source    = _graph.value.nodes.find { it.id == edge.sourceNodeId }
                val latency   = source?.let { nowMs - it.issuedAtMs }
                edge.copy(ackStatus = AckStatus.ACKNOWLEDGED, latencyMs = latency)
            } else edge
        }
        _graph.value = _graph.value.copy(nodes = nodes, edges = edges, lastUpdated = nowMs)
    }

    /** Mark all active nodes as SUPERSEDED (e.g., lockdown lifted). */
    fun markSupersedeAll() {
        val nodes = _graph.value.nodes.map { it.copy(ackStatus = AckStatus.SUPERSEDED, isActive = false) }
        _graph.value = _graph.value.copy(nodes = nodes, lastUpdated = trustedClock.nowMs())
    }

    /** Mark all active nodes as acknowledged. */
    fun markAllAcked() {
        val nodes = _graph.value.nodes.map {
            if (it.ackStatus == AckStatus.PENDING) it.copy(ackStatus = AckStatus.ACKNOWLEDGED)
            else it
        }
        _graph.value = _graph.value.copy(nodes = nodes, lastUpdated = trustedClock.nowMs())
    }

    /** Clear all nodes and edges. */
    fun clear() {
        _graph.value = CommandGraph(lastUpdated = trustedClock.nowMs())
    }
}
