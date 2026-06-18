package com.example.myapplication

import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.ui.graphics.vector.ImageVector

// 驗證 Session 結構
data class AuthSession(val token: String, val userId: Int, val email: String)

// 月曆應援結構
data class WalletCalendarDay(val day: Int, val key: String, val inMonth: Boolean, val dayOfWeek: Int)

// 售票與提醒資料模型
data class EventItem(
    val id: Int, val title: String, val artist: String, val saleTime: String,
    val activityTime: String, val venue: String, val address: String,
    val price: String, val ticketType: String, val url: String, val source: String
)

data class ReminderItem(
    val id: Int, val title: String, val saleAt: String,
    val offsetsMinutes: String, val enabled: Boolean
)

data class WalletTicket(
    val id: Long, val title: String, val date: String, val location: String,
    val seat: String, val cast: String, val platform: String,
    val price: String, val notes: String, val imagePath: String = "", val time: String = ""
)

// 後端數據分析對齊
data class SummaryStats(val events: Int = 0, val artists: Int = 0, val venues: Int = 0, val reminders: Int = 0)
data class StatItem(val label: String, val total: Int)
data class PriceEvent(val title: String, val maxPrice: Int)
data class PriceStats(val total: Int = 0, val priced: Int = 0, val averageMaxPrice: Int = 0, val buckets: List<StatItem> = emptyList(), val topExpensive: List<PriceEvent> = emptyList())
data class TimeStats(val total: Int = 0, val busiestMonths: List<StatItem> = emptyList()) { fun maxMonthTotal(): Int = busiestMonths.maxOfOrNull { it.total } ?: 1 }
data class VenueStats(val venues: List<StatItem> = emptyList())

// 智能預算推薦
enum class BudgetPage { Login, Home, Recommend, PlanDetail, Evaluate, Records, AddRecord, Saved, Settings }
enum class BudgetStrategy(val apiValue: String, val label: String) { Balanced("balanced", "均衡"), Value("value", "高價位"), Saving("saving", "省預算") }
data class BudgetRecommendResult(
    val items: List<BudgetRecommendation> = emptyList(),
    val plans: List<BudgetPlan> = emptyList(),
    val insights: List<String> = emptyList(),
    val availableFilters: BudgetAvailableFilters = BudgetAvailableFilters()
)

data class BudgetPlan(
    val type: String,
    val title: String,
    val description: String,
    val suitableFor: String,
    val actionLabel: String,
    val estimatedSpend: Int,
    val budgetLeft: Int,
    val eventIds: List<String>,
    val decisionLabel: String = "",
    val itemSummaries: List<String> = emptyList()
)

data class BudgetAvailableFilters(
    val cities: List<String> = emptyList(),
    val venues: List<String> = emptyList(),
    val platforms: List<String> = emptyList()
)

data class TicketRecord(val id: String = System.currentTimeMillis().toString(), val title: String, val date: String, val platform: String, val price: Int, val quantity: Int, val fee: Int, val note: String) { val total: Int get() = price * quantity + fee }

data class BudgetRecommendation(
    val id: String, val title: String, val artist: String, val venue: String,
    val source: String, val saleTime: String, val activityTime: String,
    val priceText: String, val minPrice: Int, val maxPrice: Int,
    val estimatedSpend: Int, val budgetLeft: Int, val budgetUsageRate: Int,
    val budgetUsageLabel: String, val score: Int, val valueLevel: String,
    val reason: String, val riskLevel: String, val riskMessage: String, val url: String,
    val scoreReasons: List<String> = emptyList(),
    val reminderStatus: String = "",
    val reminderLabel: String = "",
    val priceSource: String = "",
    val starRating: Int = 1
)
