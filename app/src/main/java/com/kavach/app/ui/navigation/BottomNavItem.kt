package com.kavach.app.ui.navigation

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Announcement
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Person

/**
 * Data class representing a single item in the bottom navigation bar.
 *
 * @param label Human‑readable title shown below the icon.
 * @param route Navigation route defined in [Screen].
 * @param icon Material icon to display.
 */
data class BottomNavItem(
    val label: String,
    val route: String,
    val icon: ImageVector
)

/**
 * DO NOT reorder without operational review.
 * The bottom navigation order is critical for muscle‑memory in tactical usage.
 */
val bottomNavItems = listOf(
    BottomNavItem(
        label = "Home",
        route = Screen.Dashboard.route,
        icon = Icons.Default.Home
    ),
    BottomNavItem(
        label = "Alerts",
        route = Screen.IncidentCenter.route,
        // Notifications icon conveys general alerts, not only warnings.
        icon = Icons.Default.Notifications
    ),
    BottomNavItem(
        label = "Broadcast",
        route = Screen.BroadcastInbox.route,
        // Announcement icon provides an operational feel, avoiding consumer‑style imagery.
        icon = Icons.Default.Announcement
    ),
    BottomNavItem(
        label = "Units",
        route = Screen.UserManagement.route,
        icon = Icons.Default.Groups
    ),
    BottomNavItem(
        label = "Profile",
        route = Screen.Profile.route,
        icon = Icons.Default.Person
    )
)

/**
 * NOTE: Ensure the Gradle dependency "material-icons-extended" is added to the app module
 * if any of the icons (Notifications, Announcement, Groups) are not available in the core set.
 */
