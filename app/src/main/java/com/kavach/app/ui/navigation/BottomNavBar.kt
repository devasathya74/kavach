package com.kavach.app.ui.navigation

import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.NavGraph.Companion.findStartDestination

/**
 * Persistent bottom navigation bar used in the main Scaffold.
 *
 * - No auto‑hide, no animations, no label shifting – the bar is always visible
 *   and static to preserve operational predictability.
 * - The [navController] is passed in from the parent Scaffold/NavHost to keep a
 *   single source of navigation state.
 * - Navigation actions use `launchSingleTop`, `popUpTo(startDestination) { saveState = true }`
 *   and `restoreState = true` to maintain a deterministic back stack.
 */
@Composable
fun BottomNavBar(
    navController: NavController
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination: NavDestination? = navBackStackEntry?.destination

    NavigationBar {
        bottomNavItems.forEach { item ->
            val selected = currentDestination?.route == item.route
            NavigationBarItem(
                icon = { Icon(imageVector = item.icon, contentDescription = item.label) },
                label = { Text(text = item.label) },
                selected = selected,
                onClick = {
                    if (!selected) {
                        navController.navigate(item.route) {
                            // Ensure we do not create multiple copies of the same destination.
                            launchSingleTop = true
                            // Pop up to the start destination of the graph to avoid a deep stack.
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            // Restore any saved state when re‑selecting a previously visited destination.
                            restoreState = true
                        }
                    }
                }
            )
        }
    }
}
