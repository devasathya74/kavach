package com.kavach.app.core.export

import com.kavach.app.core.clock.TrustedClock
import com.kavach.app.core.forensics.ForensicSnapshot
import com.kavach.app.core.forensics.ForensicSnapshotSystem
import com.kavach.app.core.security.ThreatStateManager
import com.kavach.app.core.security.CommandModeController
import com.kavach.app.core.telemetry.NetworkTelemetry
import com.kavach.app.core.telemetry.TelemetryManager
import com.kavach.app.core.timeline.CommandTimelineEngine
import com.kavach.app.core.timeline.TimelineEntry
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * IncidentPackageExporter — Standardized operational record export.
 *
 * Bundles all relevant system state into a structured IncidentPackage
 * for forensic investigation, postmortem analysis, and audit review.
 *
 * Package includes:
 *   - Package metadata (ID, export time, exporting device/role)
 *   - Current system state (threat, command mode)
 *   - Live network telemetry snapshot
 *   - Forensic incident snapshots (last 20)
 *   - Command timeline slice (last 100 entries or since [sinceMs])
 *   - Device state summary
 *
 * Output format: JSON string (caller is responsible for encryption/storage).
 * Future: encrypt with CommandSignatureEngine key before export.
 */

data class IncidentPackage(
    val packageId         : String,
    val exportedAtIso     : String,
    val exportedAtMs      : Long,
    val exportedByRole    : String,
    val deviceId          : String,
    val threatLevel       : String,
    val commandMode       : String,
    val telemetry         : NetworkTelemetry,
    val forensicSnapshots : List<ForensicSnapshot>,
    val timelineEntries   : List<TimelineEntry>,
    val deviceState       : DeviceStateSummary
)

data class DeviceStateSummary(
    val androidVersion    : String  = android.os.Build.VERSION.RELEASE,
    val deviceModel       : String  = android.os.Build.MODEL,
    val isEmulator        : Boolean = isEmulator(),
    val capturedAtIso     : String  = ""
)

private fun isEmulator(): Boolean = android.os.Build.FINGERPRINT.startsWith("generic")
        || android.os.Build.MODEL.contains("Emulator")
        || android.os.Build.MODEL.contains("Android SDK")

@Singleton
class IncidentPackageExporter @Inject constructor(
    private val trustedClock          : TrustedClock,
    private val threatStateManager    : ThreatStateManager,
    private val commandModeController : CommandModeController,
    private val telemetryManager      : TelemetryManager,
    private val forensicSystem        : ForensicSnapshotSystem,
    private val timelineEngine        : CommandTimelineEngine
) {

    companion object {
        private const val FORENSIC_SNAPSHOT_LIMIT = 20
        private const val TIMELINE_ENTRY_LIMIT    = 100
    }

    /**
     * Export current operational state as an IncidentPackage.
     *
     * @param exportedByRole  Role of the operator triggering export
     * @param deviceId        Unique device identifier
     * @param sinceMs         Only include timeline entries after this timestamp (0 = all)
     * @param notes           Optional investigator notes appended to package
     */
    fun export(
        exportedByRole : String,
        deviceId       : String,
        sinceMs        : Long   = 0L,
        notes          : String = ""
    ): IncidentPackage {
        val now = trustedClock.nowMs()

        val timeline = if (sinceMs > 0) {
            timelineEngine.since(sinceMs).take(TIMELINE_ENTRY_LIMIT)
        } else {
            timelineEngine.timeline.value.take(TIMELINE_ENTRY_LIMIT)
        }

        return IncidentPackage(
            packageId         = "INC-${now.toString().takeLast(8)}",
            exportedAtIso     = trustedClock.nowIso(),
            exportedAtMs      = now,
            exportedByRole    = exportedByRole,
            deviceId          = deviceId,
            threatLevel       = threatStateManager.currentLevel.value.name,
            commandMode       = commandModeController.currentMode.value.name,
            telemetry         = telemetryManager.telemetry.value,
            forensicSnapshots = forensicSystem.snapshots.value.take(FORENSIC_SNAPSHOT_LIMIT),
            timelineEntries   = timeline,
            deviceState       = DeviceStateSummary(capturedAtIso = trustedClock.nowIso())
        )
    }

    /**
     * Serialize an IncidentPackage to a JSON string.
     * Caller must encrypt before writing to disk or transmitting.
     */
    fun toJson(pkg: IncidentPackage): String {
        val root = JSONObject().apply {
            put("package_id",      pkg.packageId)
            put("exported_at",     pkg.exportedAtIso)
            put("exported_by",     pkg.exportedByRole)
            put("device_id",       pkg.deviceId)
            put("threat_level",    pkg.threatLevel)
            put("command_mode",    pkg.commandMode)

            put("telemetry", JSONObject().apply {
                put("api_rtt_ms",        pkg.telemetry.apiRttMs)
                put("ws_latency_ms",     pkg.telemetry.wsLatencyMs)
                put("packet_loss_pct",   pkg.telemetry.packetLossPct)
                put("uplink_status",     pkg.telemetry.uplinkStatus.name)
                put("sync_queue_size",   pkg.telemetry.syncQueueSize)
                put("quality_label",     pkg.telemetry.qualityLabel)
            })

            put("device_state", JSONObject().apply {
                put("android_version",   pkg.deviceState.androidVersion)
                put("device_model",      pkg.deviceState.deviceModel)
                put("is_emulator",       pkg.deviceState.isEmulator)
                put("captured_at",       pkg.deviceState.capturedAtIso)
            })

            put("forensic_snapshots", JSONArray().also { arr ->
                pkg.forensicSnapshots.forEach { snap ->
                    arr.put(JSONObject().apply {
                        put("seq",            snap.sequenceNo)
                        put("timestamp",      snap.timestampIso)
                        put("trigger",        snap.triggerEvent)
                        put("threat_level",   snap.threatLevel.name)
                        put("rtt_ms",         snap.telemetry.apiRttMs)
                        put("packet_loss",    snap.telemetry.packetLossPct)
                        put("uplink",         snap.telemetry.uplinkStatus.name)
                        if (snap.notes.isNotBlank()) put("notes", snap.notes)
                    })
                }
            })

            put("timeline", JSONArray().also { arr ->
                pkg.timelineEntries.forEach { entry ->
                    arr.put(JSONObject().apply {
                        put("id",        entry.id)
                        put("timestamp", entry.timestampIso)
                        put("category",  entry.category.name)
                        put("severity",  entry.severity.name)
                        put("actor",     entry.actor)
                        put("subject",   entry.subject)
                        put("detail",    entry.detail)
                    })
                }
            })
        }
        return root.toString(2)  // Pretty-printed JSON
    }

    /**
     * Generate a human-readable incident report header (for quick field review).
     */
    fun toSummary(pkg: IncidentPackage): String = buildString {
        appendLine("╔══════════════════════════════════════════╗")
        appendLine("║      KAVACH INCIDENT PACKAGE SUMMARY     ║")
        appendLine("╚══════════════════════════════════════════╝")
        appendLine("Package ID    : ${pkg.packageId}")
        appendLine("Exported At   : ${pkg.exportedAtIso}")
        appendLine("Exported By   : ${pkg.exportedByRole}")
        appendLine("Device        : ${pkg.deviceState.deviceModel}")
        appendLine("Emulator      : ${pkg.deviceState.isEmulator}")
        appendLine()
        appendLine("── OPERATIONAL STATE ──────────────────────")
        appendLine("Threat Level  : ${pkg.threatLevel}")
        appendLine("Command Mode  : ${pkg.commandMode}")
        appendLine("Uplink        : ${pkg.telemetry.uplinkStatus.name}")
        appendLine("API RTT       : ${pkg.telemetry.apiRttMs}ms (${pkg.telemetry.qualityLabel})")
        appendLine("Packet Loss   : ${pkg.telemetry.packetLossPct}%")
        appendLine()
        appendLine("── RECORD COUNTS ──────────────────────────")
        appendLine("Forensic Snapshots : ${pkg.forensicSnapshots.size}")
        appendLine("Timeline Entries   : ${pkg.timelineEntries.size}")
        appendLine("══════════════════════════════════════════")
    }
}
