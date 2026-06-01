package com.example.myapplication.ui

import androidx.compose.ui.graphics.Color

val Canvas = Color(0xFFF6F1E8)
val SurfaceIvory = Color(0xFFFFFCF6)
val StageBlack = Color(0xFF08111F)
val Ink = Color(0xFF111827)
val Muted = Color(0xFF6B7280)
val Mist = Color(0xFFE9DFD1)
val FineLine = Color(0xFFD8CBBE)
val Porcelain = Color(0xFFFFFFFF)
val Gold = Color(0xFFD8A84E)
val GoldSoft = Color(0xFFFFF2CF)
val Cobalt = Color(0xFF245CFF)
val CobaltSoft = Color(0xFFE8EEFF)
val Success = Color(0xFF0F8A5F)
val Rose = Color(0xFFD64545)
val Plum = Color(0xFF7C3AED)

data class AuthSession(
    val token: String,
    val userId: Int,
    val email: String
)

data class WalletTicket(
    val id: Long,
    val title: String,
    val date: String,
    val location: String,
    val seat: String,
    val cast: String,
    val platform: String,
    val price: String,
    val notes: String,
    val imagePath: String = ""
)

data class SummaryStats(
    val events: Int = 0,
    val artists: Int = 0,
    val venues: Int = 0,
    val reminders: Int = 0
)

data class EventItem(
    val id: Int,
    val title: String,
    val artist: String,
    val saleTime: String,
    val activityTime: String,
    val venue: String,
    val address: String,
    val price: String,
    val ticketType: String,
    val url: String,
    val source: String
)

data class ReminderItem(
    val id: Int,
    val title: String,
    val saleAt: String,
    val offsetsMinutes: String,
    val enabled: Boolean
)