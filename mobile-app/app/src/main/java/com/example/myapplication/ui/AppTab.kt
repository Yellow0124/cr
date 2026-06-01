package com.example.myapplication.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Analytics
import androidx.compose.material.icons.rounded.ConfirmationNumber
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Search
import androidx.compose.ui.graphics.vector.ImageVector

enum class AppTab(val title: String, val icon: ImageVector) {
    Home("探索", Icons.Rounded.Home),
    Search("搜尋", Icons.Rounded.Search),
    Wallet("票券", Icons.Rounded.ConfirmationNumber),
    Reminders("提醒", Icons.Rounded.Notifications),
    Analysis("洞察", Icons.Rounded.Analytics),
    Account("帳號", Icons.Rounded.AccountCircle)
}
