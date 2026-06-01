package com.example.myapplication.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocalActivity
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Stars
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
        
        // 🌟 整合功能五：組員開發的搶票美食應援補給站
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SurfaceIvory),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, FineLine.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("搶票應援補給站 🍗", fontWeight = FontWeight.Black, color = Ink, fontSize = 16.sp)
                    Text("結合訂單大數據，推薦淡水區最受資管系歡迎的搶票應援熱門美食：", color = Muted, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("• 櫻鴨館搶票必勝鴨肉飯", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Success, modifier = Modifier.weight(1f))
                        Text("• 肯德基補給蛋撻餐", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Rose, modifier = Modifier.weight(1f))
                    }
                }
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
fun StageHero(summary: SummaryStats) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(30.dp),
        colors = CardDefaults.cardColors(containerColor = StageBlack),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f))
    ) {
        Column(
            modifier = Modifier
                .background(
                    Brush.linearGradient(
                        listOf(StageBlack, Color(0xFF10284B), Color(0xFF1F1710))
                    )
                )
                .padding(24.dp)
        ) {
            Text(
                "售票資訊平台",
                color = Gold,
                fontSize = 12.sp,
                fontWeight = FontWeight.Black
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "跨網站統合搜尋",
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                lineHeight = 34.sp
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                "整合全台灣各大售票網站的活動資訊，讓你一次掌握所有演唱會與展覽，不再錯過任何售票時段。",
                color = Color.White.copy(alpha = 0.78f),
                fontSize = 15.sp,
                lineHeight = 22.sp
            )
        }
    }
}