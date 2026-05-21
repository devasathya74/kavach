package com.kavach.app.ui.data

/**
 * Immutable fake data provider for the tactical UI.
 * All collections use `val` so they are read‑only and safe for previews or tests.
 */
object FakeDataProvider {

    // Quick action buttons – short, operational labels.
    val QUICK_ACTIONS = listOf(
        "Backup",
        "SOS",
        "Check-In",
        "Status"
    )

    // Alert data model and sample alerts.
    data class Alert(val id: Int, val title: String, val description: String, val timestamp: Long)
    val ALERTS = listOf(
        Alert(1, "PERIMETER BREACH", "Unauthorized entry detected at sector 4.", 1700000000000L),
        Alert(2, "LOW BATTERY", "Device battery dropped below 20%.", 1700000500000L),
        Alert(3, "NETWORK LOSS", "Lost connection to central server.", 1700001000000L)
    )

    // Broadcast data model and sample broadcasts with tactical titles.
    data class Broadcast(val id: Int, val title: String, val content: String, val timestamp: Long)
    val BROADCASTS = listOf(
        Broadcast(1, "MISSION COMMENCE", "All units, commence operation at 0400 hrs.", 1700002000000L),
        Broadcast(2, "WEATHER ALERT", "Heavy rain expected in sector 2.", 1700002500000L),
        Broadcast(3, "EXTRACTION UPDATE", "Designated extraction at grid X12Y8.", 1700003000000L)
    )

    // Units information – static list of unit identifiers.
    data class UnitInfo(val id: String, val role: String, val status: String)
    val UNITS = listOf(
        UnitInfo("U-01", "Sniper", "Ready"),
        UnitInfo("U-02", "Medic", "En Route"),
        UnitInfo("U-03", "Engineer", "On Site")
    )

    // Operational profile information.
    data class ProfileInfo(val unit: String, val deviceStatus: String, val appVersion: String)
    val PROFILE = ProfileInfo(
        unit = "UNIT: ALPHA",
        deviceStatus = "DEVICE STATUS: ACTIVE",
        appVersion = "APP VERSION: 1.0"
    )
}
