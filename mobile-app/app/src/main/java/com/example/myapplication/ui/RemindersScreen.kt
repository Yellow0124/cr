package com.example.myapplication.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.ui.ReminderItem

@Composable
fun RemindersScreen(
    reminders: List<ReminderItem>,
    onRefresh: () -> Unit,
    onDelete: (ReminderItem) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Spacer(modifier = Modifier.height(18.dp)) }
        
        item {
            FeatureHeader(
                eyebrow = "提醒工作台",
                title = "搶票提醒",
                subtitle = "集中管理正在追蹤的開賣時間，讓加入、查看、移除都有明確回饋。",
                icon = Icons.Rounded.Notifications,
                accent = Gold,
                action = "更新",
                actionIcon = Icons.Rounded.Refresh,
                onAction = onRefresh
            )
        }
        
        item {
            Card(
                modifier = Modifier.fillMaxWidth(), 
                colors = CardDefaults.cardColors(containerColor = StageBlack), 
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("🤖 AI 搶票防漏推播優化中", color = Gold, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("針對熱門秒殺活動（如 TWS、BABYMONSTER），系統已自動調高通知權重，將於開賣前 60、30、10 分鐘透過常駐通知進行強力喚醒。", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                }
            }
        }
        
        item { ReminderCoachCard(reminders.size) }
        
        if (reminders.isEmpty()) {
            item { EmptyStateCard("沒有提醒", "在活動卡片點選加入提醒，這裡會建立你的搶票清單。") }
        }
        
        items(reminders) { reminder ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SurfaceIvory),
                shape = RoundedCornerShape(24.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                border = BorderStroke(1.dp, FineLine.copy(alpha = 0.70f))
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .background(GoldSoft, RoundedCornerShape(14.dp))
                                .padding(10.dp)
                        ) {
                            Icon(Icons.Rounded.Notifications, contentDescription = null, tint = Gold, modifier = Modifier.size(22.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(reminder.title, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Ink, maxLines = 2)
                            Text(formatReminderOffsets(reminder.offsetsMinutes), color = Muted, fontSize = 13.sp)
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    InfoLine(Icons.Rounded.CalendarMonth, "提醒時間", reminder.saleAt)
                    Spacer(modifier = Modifier.height(14.dp))
                    OutlinedButton(
                        onClick = { onDelete(reminder) },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Rose)
                    ) {
                        Icon(Icons.Rounded.DeleteOutline, contentDescription = null, modifier = Modifier.size(19.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("移除提醒")
                    }
                }
            }
        }
    }
}

@Composable
internal fun ReminderCoachCard(count: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = StageBlack),
        shape = RoundedCornerShape(26.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f))
    ) {
        Column(
            modifier = Modifier
                .background(Brush.linearGradient(listOf(StageBlack, Color(0xFF18263D))))
                .padding(20.dp)
        ) {
            Text("提醒節奏", color = Gold, fontSize = 12.sp, fontWeight = FontWeight.Black)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                if (count > 0) "目前追蹤 $count 筆活動" else "先從活動卡片加入提醒",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Black
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "系統會用 60、30、10 分鐘三段提醒概念，呈現完整的搶票流程設計。",
                color = Color.White.copy(alpha = 0.78f),
                fontSize = 13.sp,
                lineHeight = 20.sp
            )
            Spacer(modifier = Modifier.height(14.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                HeroPill("60 分鐘前")
                HeroPill("30 分鐘前")
                HeroPill("10 分鐘前")
            }
        }
    }
}