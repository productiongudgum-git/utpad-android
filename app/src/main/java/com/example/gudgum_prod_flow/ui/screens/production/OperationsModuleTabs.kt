package com.example.gudgum_prod_flow.ui.screens.production

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.PrecisionManufacturing
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.gudgum_prod_flow.ui.navigation.AppRoute

private data class ModuleTab(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

private val moduleTabs = listOf(
    ModuleTab(AppRoute.Inwarding, "Inwarding", Icons.Outlined.Inventory2),
    ModuleTab(AppRoute.Production, "Production", Icons.Outlined.PrecisionManufacturing),
    ModuleTab(AppRoute.Packing, "Packing", Icons.Outlined.Widgets),
    ModuleTab(AppRoute.Dispatch, "Dispatch", Icons.Outlined.LocalShipping),
)

@Composable
fun OperationsModuleTabs(
    currentRoute: String,
    allowedRoutes: Set<String>,
    onNavigateToRoute: (String) -> Unit,
) {
    val visibleTabs = moduleTabs.filter { it.route in allowedRoutes }

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 4.dp),
    ) {
        items(visibleTabs) { tab ->
            FilterChip(
                selected = currentRoute == tab.route,
                onClick = {
                    if (currentRoute != tab.route) {
                        onNavigateToRoute(tab.route)
                    }
                },
                label = { Text(tab.label, style = MaterialTheme.typography.labelLarge) },
                leadingIcon = {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = null,
                    )
                },
            )
        }
    }
}
