package com.example.myapplication.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocalActivity
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Stars
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.myapplication.EventItem
import com.example.myapplication.data.SummaryStats

@Composable
fun HomeScreen(
    summary: SummaryStats,
    events: List<EventItem>,
    loading: Boolean,
    onRefresh: () -> Unit,
    onOpenUrl: (EventItem) -> Unit,
    onAddReminder: (EventItem) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { Spacer(modifier = Modifier.height(16.dp)) }
        
        // 英雄區塊 - 主打資訊卡
        item { StageHero(summary) }
        
        // 指標卡片 - 4格布局
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricCard(
                    "活動資料", 
                    summary.events.toString(), 
                    "筆整合活動", 
                    Icons.Rounded.LocalActivity, 
                    Modifier.weight(1f)
                )
                MetricCard(
                    "藝人檔案", 
                    summary.artists.toString(), 
                    "組演出藝人", 
                    Icons.Rounded.Stars, 
                    Modifier.weight(1f)
                )
            }
        }
        
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricCard(
                    "合作場地", 
                    summary.venues.toString(), 
                    "個活動場館", 
                    Icons.Rounded.LocationOn, 
                    Modifier.weight(1f)
                )
                MetricCard(
                    "搶票提醒", 
                    summary.reminders.toString(), 
                    "筆追蹤提醒", 
                    Icons.Rounded.Notifications, 
                    Modifier.weight(1f)
                )
            }
        }
        
        // 分類標題
        item {
            SectionHeader(
                title = "精選售票活動",
                subtitle = "依資料完整度與近期活動排序，先看最適合展示的內容。",
                action = "更新",
                actionIcon = Icons.Rounded.Refresh,
                onAction = onRefresh
            )
        }
        
        // 加載狀態或活動列表
        if (loading) {
            item { LoadingCard("正在整理推薦活動") }
        }
        
        items(events) { event ->
            EventCard(
                event = event,
                onOpenUrl = { onOpenUrl(event) },
                onAddReminder = { onAddReminder(event) }
            )
        }
        
        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

@Composable
internal fun StageHero(summary: SummaryStats) {
    androidx.compose.material3.Card(
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(30.dp),
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = StageBlack),
        elevation = androidx.compose.material3.CardDefaults.cardElevation(defaultElevation = 4.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, androidx.compose.ui.graphics.Color.White.copy(alpha = 0.10f))
    ) {
        Column(
            modifier = Modifier
                .background(
                    androidx.compose.ui.graphics.Brush.linearGradient(
                        listOf(StageBlack, androidx.compose.ui.graphics.Color(0xFF10284B), androidx.compose.ui.graphics.Color(0xFF1F1710))
                    )
                )
                .padding(24.dp)
        ) {
            androidx.compose.material3.Text(
                "售票資訊平台",
                color = Gold,
                fontSize = androidx.compose.ui.unit.sp(12),
                fontWeight = androidx.compose.ui.text.font.FontWeight.Black
            )
            Spacer(modifier = Modifier.height(6.dp))
            androidx.compose.material3.Text(
                "跨網站統合搜尋",
                color = androidx.compose.ui.graphics.Color.White,
                fontSize = androidx.compose.ui.unit.sp(28),
                fontWeight = androidx.compose.ui.text.font.FontWeight.Black,
                lineHeight = androidx.compose.ui.unit.sp(34)
            )
            Spacer(modifier = Modifier.height(10.dp))
            androidx.compose.material3.Text(
                "整合全台灣各大售票網站的活動資訊，讓你一次掌握所有演唱會與展覽，不再錯過任何售票時段。",
                color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.78f),
                fontSize = androidx.compose.ui.unit.sp(15),
                lineHeight = androidx.compose.ui.unit.sp(22)
            )
        }
    }
}
