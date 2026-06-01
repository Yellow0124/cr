package com.example.myapplication.ui

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun TicketBottomBar(selectedTab: AppTab, onSelect: (AppTab) -> Unit) {
    NavigationBar(
        containerColor = SurfaceIvory,
        tonalElevation = 0.dp
    ) {
        AppTab.entries.forEach { tab ->
            NavigationBarItem(
                selected = selectedTab == tab,
                onClick = { onSelect(tab) },
                icon = {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = tab.title,
                        modifier = Modifier.size(24.dp)
                    )
                },
                label = { Text(tab.title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = StageBlack,
                    selectedTextColor = StageBlack,
                    indicatorColor = GoldSoft,
                    unselectedIconColor = Muted,
                    unselectedTextColor = Muted
                )
            )
        }
    }
}
