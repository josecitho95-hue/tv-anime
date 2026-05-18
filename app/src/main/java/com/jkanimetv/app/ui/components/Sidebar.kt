package com.jkanimetv.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import com.jkanimetv.app.ui.screens.AccentRed
import com.jkanimetv.app.ui.screens.SurfaceElevated
import com.jkanimetv.app.ui.screens.TextSecondary

data class SidebarTab(
    val route: String,
    val label: String,
    val icon: ImageVector
)

val DefaultSidebarTabs = listOf(
    SidebarTab("home",      "Inicio",    Icons.Filled.Home),
    SidebarTab("schedule",  "Horario",   Icons.Filled.DateRange),
    SidebarTab("browse",    "Explorar",  Icons.Filled.Explore),
    SidebarTab("search",    "Buscar",    Icons.Filled.Search),
    SidebarTab("favorites", "Favoritos", Icons.Filled.Favorite)
)

/**
 * Vertical sidebar with five tabs. Collapsed (icons only) by default;
 * expands to show labels when any item in the sidebar holds focus.
 *
 * @param expanded `true` while the sidebar (or any item) has focus.
 * @param currentRoute the route currently shown by the NavHost (drives the active pill).
 * @param onSidebarFocusChange callback fired when the whole sidebar gains/loses focus.
 * @param onTabActivated invoked with the route string when an item is clicked or focused.
 */
@Composable
fun Sidebar(
    expanded: Boolean,
    currentRoute: String?,
    tabs: List<SidebarTab> = DefaultSidebarTabs,
    onSidebarFocusChange: (Boolean) -> Unit,
    onTabActivated: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .background(SurfaceElevated)
            .focusGroup()
            .onFocusChanged { onSidebarFocusChange(it.hasFocus) }
            .padding(vertical = 24.dp, horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        SidebarHeader(expanded = expanded)
        Spacer(Modifier.height(20.dp))
        tabs.forEach { tab ->
            SidebarItem(
                tab = tab,
                expanded = expanded,
                selected = currentRoute == tab.route,
                onActivate = { onTabActivated(tab.route) }
            )
        }
    }
}

@Composable
private fun SidebarHeader(expanded: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(36.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "JK",
                color = AccentRed,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold)
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandHorizontally(tween(180)) + fadeIn(tween(180)),
            exit = shrinkHorizontally(tween(140)) + fadeOut(tween(140))
        ) {
            Text(
                text = "Anime TV",
                color = AccentRed,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                modifier = Modifier.padding(start = 6.dp)
            )
        }
    }
}

@Composable
private fun SidebarItem(
    tab: SidebarTab,
    expanded: Boolean,
    selected: Boolean,
    onActivate: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val bg = when {
        selected -> AccentRed
        focused -> Color.White.copy(alpha = 0.10f)
        else -> Color.Transparent
    }
    val contentColor = when {
        selected -> Color.White
        focused -> Color.White
        else -> TextSecondary
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(bg)
            .border(
                width = if (focused && !selected) 1.dp else 0.dp,
                color = Color.White.copy(alpha = 0.35f),
                shape = RoundedCornerShape(22.dp)
            )
            .onFocusChanged { focused = it.isFocused || it.hasFocus }
            // Both onClick (mouse) and focus (D-pad) trigger navigation —
            // the NavController.launchSingleTop dedupes if both fire.
            .clickable { onActivate() }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = tab.icon,
            contentDescription = tab.label,
            tint = contentColor,
            modifier = Modifier.size(22.dp)
        )
        AnimatedVisibility(
            visible = expanded,
            enter = expandHorizontally(tween(180)) + fadeIn(tween(180)),
            exit = shrinkHorizontally(tween(140)) + fadeOut(tween(140))
        ) {
            Text(
                text = tab.label,
                color = contentColor,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                ),
                modifier = Modifier.padding(start = 12.dp)
            )
        }
    }
    // Fire navigation on focus so D-pad doesn't require an extra OK press.
    // LaunchedEffect only re-runs when `focused` changes, so we navigate
    // once per focus transition (false → true).
    LaunchedEffect(focused) {
        if (focused) onActivate()
    }
}
