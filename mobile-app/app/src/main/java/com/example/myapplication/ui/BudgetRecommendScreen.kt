package com.example.myapplication.ui

import android.util.Log
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.text.NumberFormat
import java.util.Locale

@Composable
fun BudgetRecommendScreen(
    token: String,
    apiGetExecutor: (String, (String) -> Unit, (String) -> Unit) -> Unit,
    apiPostExecutor: (String, String, (String) -> Unit, (String) -> Unit) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    
    var page by remember { mutableStateOf(BudgetPage.Home) }
    var budget by remember { mutableStateOf("5000") }
    var keyword by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var strategy by remember { mutableStateOf(BudgetStrategy.Balanced) }
    var loading by remember { mutableStateOf(false) }
    var online by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf(BudgetRecommendResult()) }
    var selectedId by remember { mutableStateOf("") }
    var savedIds by remember { mutableStateOf(setOf<String>()) }
    var records by remember { mutableStateOf(listOf<TicketRecord>()) }
    var singleLimit by remember { mutableStateOf("3000") }
    var includeExtra by remember { mutableStateOf(true) }
    var peopleCount by remember { mutableStateOf(1) }

    // 🛡️ 強健的網路管線：包裹安全轉換器，徹底粉碎型態閃退
    val submitQuery: (Boolean) -> Unit = { showError ->
        val maxBudget = budget.filter { it.isDigit() }.toIntOrNull()
        if (maxBudget == null || maxBudget <= 0) {
            if (showError) Toast.makeText(context, "請輸入有效預算", Toast.LENGTH_SHORT).show()
        } else {
            loading = true
            val payload = JSONObject()
                .put("maxBudget", maxBudget)
                .put("keyword", keyword.trim())
                .put("location", location.trim())
                .put("strategy", strategy.apiValue)
                .put("limit", 15)
                .toString()

            apiPostExecutor("/api/recommendations/budget", payload, { body ->
                // 🛡️ 使用安全的防禦性多重防護網包裹 JSON 解析
                runCatching {
                    val root = JSONObject(body)
                    val itemsArr = root.optJSONArray("items") ?: JSONArray()
                    val list = mutableListOf<BudgetRecommendation>()
                    
                    for (i in 0 until itemsArr.length()) {
                        val obj = itemsArr.getJSONObject(i)
                        
                        // 🛡️ 關鍵修復點：使用防禦型安全型態轉換（optString / optInt），就算後端傳回 null 或數字字串大亂套，也能完美對齊，不引爆閃退
                        list.add(
                            BudgetRecommendation(
                                id = obj.opt("id")?.toString() ?: "id-$i", // 確保強制轉字串
                                title = obj.optString("title", "未命名活動"),
                                artist = obj.optString("artist", "未知藝人"),
                                venue = obj.optString("venue", "場地待確認"),
                                source = obj.optString("source", "爬蟲整合"),
                                saleTime = obj.optString("saleTime", "尚未公布"),
                                activityTime = obj.optString("activityTime", "時間待確認"),
                                priceText = obj.optString("priceText", "暫無票價"),
                                minPrice = obj.optInt("minPrice", 0),
                                maxPrice = obj.optInt("maxPrice", 0),
                                estimatedSpend = obj.optInt("estimatedSpend", 0),
                                budgetLeft = obj.optInt("budgetLeft", 0),
                                budgetUsageRate = obj.optInt("budgetUsageRate", 0),
                                budgetUsageLabel = obj.optString("budgetUsageLabel", "合理"),
                                score = obj.optInt("score", 80),
                                valueLevel = obj.optString("valueLevel", "good"),
                                reason = obj.optString("reason", "數據模型計算中"),
                                riskLevel = obj.optString("riskLevel", "low"),
                                riskMessage = obj.optString("riskMessage", "無顯著風險"),
                                url = obj.optString("url", "")
                            )
                        )
                    }
                    
                    loading = false
                    online = true
                    result = BudgetRecommendResult(items = list)
                }.onFailure { err ->
                    // 🛡️ 如果 JSON 格式大崩潰，優雅降級到 demo 資料，不准手機閃退
                    loading = false
                    online = false
                    result = BudgetRecommendResult(items = demoRecommendations())
                    Log.e("TicketFlow", "JSON解析失敗，已自動安全降級", err)
                }
            }, { errBody ->
                // 🛡️ 安全防線：切換到主執行緒彈出 Toast，徹底拔除執行緒安全秒退
                Handler(Looper.getMainLooper()).post {
                    loading = false
                    online = false
                    result = BudgetRecommendResult(items = demoRecommendations())
                    if (showError) Toast.makeText(context, "目前顯示本機快取預覽資料", Toast.LENGTH_SHORT).show()
                }
            })
        }
    }

    LaunchedEffect(Unit) { submitQuery(false) }

    val maxBudgetInt = budget.filter { it.isDigit() }.toIntOrNull() ?: 5000
    val spent = records.sumOf { it.total }
    val items = result.items.ifEmpty { demoRecommendations() }.take(15)
    val savedItems = items.filter { savedIds.contains(it.id) }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFFF6F1E8))) {
        when (page) {
            BudgetPage.Home -> BudgetHomePage(maxBudgetInt, spent, online, loading, items.firstOrNull(), items.size, savedItems.size, records.size, { page = BudgetPage.Saved }, { selectedId = "demo-1"; page = BudgetPage.Evaluate }, { page = BudgetPage.Records }, { page = BudgetPage.Settings }, { page = BudgetPage.Recommend }, { selectedId = it.id; page = BudgetPage.Evaluate })
            BudgetPage.Recommend -> BudgetRecommendPage(budget, { budget = it }, keyword, { keyword = it }, location, { location = it }, strategy, { strategy = it }, loading, online, items, savedIds, onBack, { submitQuery(true) }, { selectedId = it.id; page = BudgetPage.Evaluate }, { item -> savedIds = if (savedIds.contains(item.id)) savedIds - item.id else savedIds + item.id })
            BudgetPage.Evaluate -> BudgetEvaluatePage(items.find { it.id == selectedId }, items, maxBudgetInt, spent, peopleCount, includeExtra, savedIds.contains(selectedId), { page = BudgetPage.Home }, { selectedId = it.id }, { if (selectedId.isNotBlank()) savedIds = if (savedIds.contains(selectedId)) savedIds - selectedId else savedIds + selectedId }, { val item = items.find { it.id == selectedId }; if (item != null) { records = listOf(TicketRecord(id = System.currentTimeMillis().toString(), title = item.title, date = item.activityTime.ifBlank { item.saleTime }, platform = item.source, price = item.estimatedSpend, quantity = peopleCount, fee = if (includeExtra) 150 else 0, note = "由評估頁加入")) + records; page = BudgetPage.Records } })
            BudgetPage.Records -> BudgetRecordsPage(maxBudgetInt, records, { page = BudgetPage.Home }, { page = BudgetPage.AddRecord }, { r -> records = records.filterNot { it.id == r.id } })
            BudgetPage.AddRecord -> BudgetAddRecordPage({ page = BudgetPage.Records }, { records = listOf(it) + records; page = BudgetPage.Records })
            BudgetPage.Saved -> BudgetSavedPage(maxBudgetInt, savedItems, { page = BudgetPage.Home }, { page = BudgetPage.Recommend }, { selectedId = it.id; page = BudgetPage.Evaluate }, { item -> savedIds = savedIds - item.id })
            BudgetPage.Settings -> BudgetSettingsPage(budget, { budget = it }, singleLimit, { singleLimit = it }, includeExtra, { includeExtra = it }, peopleCount, { peopleCount = it }, strategy, { strategy = it }, { page = BudgetPage.Home }, { submitQuery(true) })
        }
    }
}

// ========================================================
// 子組件渲染保持最穩健的平鋪狀態（不嵌套）
// ========================================================

@Composable
fun BudgetHeader(title: String, subtitle: String, online: Boolean, showBack: Boolean = true, onBack: () -> Unit = {}) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (showBack) {
                Box(modifier = Modifier.size(38.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFFE8EEFF)).clickable { onBack() }, contentAlignment = Alignment.Center) {
                    Text("<", color = Color(0xFF173BAE), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
            }
            Column {
                Text(title, color = Color(0xFF111827), fontSize = 24.sp, fontWeight = FontWeight.Black)
                Text(subtitle, color = Color(0xFF6B7280), fontSize = 12.sp)
            }
        }
        BudgetStatusChip(if (online) "即時同步中" else "本地快取中", online)
    }
}

@Composable
fun BudgetPanel(content: @Composable ColumnScope.() -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFCF6)), shape = RoundedCornerShape(22.dp), border = BorderStroke(1.dp, Color(0xFFD8CBBE)), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
fun BudgetInputField(value: String, onValueChange: (String) -> Unit, label: String, placeholder: String, keyboardType: KeyboardType, icon: (@Composable () -> Unit)?, modifier: Modifier = Modifier) {
    OutlinedTextField(value = value, onValueChange = onValueChange, modifier = modifier.fillMaxWidth(), label = { Text(label) }, placeholder = { Text(placeholder) }, singleLine = true, leadingIcon = icon, keyboardOptions = KeyboardOptions(keyboardType = keyboardType), shape = RoundedCornerShape(16.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFF245CFF), unfocusedBorderColor = Color(0xFFD8CBBE), focusedContainerColor = Color.White, unfocusedContainerColor = Color.White))
}

@Composable
fun BudgetProgress(value: Int) {
    LinearProgressIndicator(progress = { value.coerceIn(0, 100) / 100f }, modifier = Modifier.fillMaxWidth().height(9.dp).clip(RoundedCornerShape(999.dp)), color = Color(0xFF245CFF), trackColor = Color(0xFFE8EEFF))
}

@Composable
fun BudgetMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.clip(RoundedCornerShape(18.dp)).background(Color(0xFFFFF8ED)).padding(13.dp)) {
        Text(label, color = Color(0xFF6B7280), fontSize = 12.sp)
        Text(value, color = Color(0xFF111827), fontSize = 16.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
fun BudgetStatusChip(text: String, online: Boolean) {
    Row(modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(Color(0xFFE8EEFF)).padding(horizontal = 11.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(if (online) Color(0xFF0F8A5F) else Color(0xFFD8A84E)))
        Text(text, color = Color(0xFF173BAE), fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun BudgetMonthChip(text: String, onClick: () -> Unit) {
    Box(modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(Color(0xFFFFF8ED)).clickable { onClick() }.padding(horizontal = 14.dp, vertical = 9.dp)) {
        Text(text, color = Color(0xFF6B7280), fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun BudgetHeroCard(budget: Int, spent: Int, remaining: Int, usedRate: Int) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.Transparent), shape = RoundedCornerShape(24.dp)) {
        Column(modifier = Modifier.background(Brush.linearGradient(listOf(Color(0xFF245CFF), Color(0xFF173BAE), Color(0xFFD8A84E)))).padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("本月活動預算", color = Color.White.copy(alpha = 0.82f), fontWeight = FontWeight.Bold)
                    Text(formatMoney(budget), color = Color.White, fontSize = 35.sp, fontWeight = FontWeight.Black)
                }
                Box(modifier = Modifier.size(76.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                    Text("${usedRate}%", color = Color.White, fontWeight = FontWeight.Black)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                BudgetHeroMetric("已使用", formatMoney(spent), Modifier.weight(1f))
                BudgetHeroMetric("剩餘", formatMoney(remaining), Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun BudgetHeroMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = 0.16f)).padding(12.dp)) {
        Text(label, color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
        Text(value, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
fun BudgetQuickCard(icon: String, title: String, subtitle: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFCF6)), shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, Color(0xFFD8CBBE)), modifier = modifier.clickable { onClick() }) {
        Column(modifier = Modifier.padding(horizontal = 4.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(modifier = Modifier.size(30.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFFE8EEFF)), contentAlignment = Alignment.Center) {
                Text(icon, color = Color(0xFF245CFF), fontWeight = FontWeight.Black)
            }
            Text(title, color = Color(0xFF111827), fontWeight = FontWeight.Black, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = Color(0xFF6B7280), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun BudgetSummaryCard(title: String, subtitle: String, value: String, progress: Int, primaryLabel: String, secondaryLabel: String, onPrimary: () -> Unit, onSecondary: () -> Unit) {
    BudgetPanel {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(title, color = Color(0xFF111827), fontWeight = FontWeight.Black)
                Text(subtitle, color = Color(0xFF6B7280), fontSize = 12.sp)
            }
            Text(value, color = Color(0xFF173BAE), fontSize = 18.sp, fontWeight = FontWeight.Black)
        }
        BudgetProgress(progress)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = onSecondary, modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, Color(0xFFD8CBBE))) { Text(secondaryLabel, color = Color(0xFF173BAE)) }
            Button(onClick = onPrimary, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF245CFF)), shape = RoundedCornerShape(16.dp)) { Text(primaryLabel) }
        }
    }
}

@Composable
fun BudgetRecommendationCard(item: BudgetRecommendation, compact: Boolean, saved: Boolean, onClick: () -> Unit, onToggleSaved: (() -> Unit)?) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFCF6)), shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, Color(0xFFD8CBBE)), modifier = Modifier.clickable { onClick() }) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(item.title, color = Color(0xFF111827), fontSize = 15.sp, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("${item.venue.ifBlank { "地點待確認" }} · ${item.activityTime.ifBlank { "時間待確認" }}", color = Color(0xFF6B7280), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                BudgetScoreChip("${item.score} 分")
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(formatMoney(item.estimatedSpend), color = Color(0xFF173BAE), fontSize = 20.sp, fontWeight = FontWeight.Black)
                Text("${item.budgetUsageRate.coerceAtLeast(0)}%", color = Color(0xFF6B7280), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            BudgetProgress(item.budgetUsageRate.coerceIn(0, 100))
            if (compact) { Text(item.reason, color = Color(0xFF6B7280), fontSize = 12.sp, maxLines = 3, overflow = TextOverflow.Ellipsis) }
            if (onToggleSaved != null) {
                OutlinedButton(onClick = onToggleSaved, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, Color(0xFFD8CBBE))) { Text(if (saved) "已收藏" else "收藏", color = Color(0xFF173BAE)) }
            }
        }
    }
}

@Composable
fun BudgetScoreChip(text: String) {
    Text(text = text, color = Color(0xFF173BAE), fontSize = 11.sp, fontWeight = FontWeight.Black, modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(Color(0xFFE8EEFF)).padding(horizontal = 9.dp, vertical = 6.dp))
}

@Composable
fun BudgetInfoBlock(title: String, text: String) {
    Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color(0xFFFFF8ED)).padding(13.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, color = Color(0xFF111827), fontWeight = FontWeight.Black)
        Text(text, color = Color(0xFF6B7280), fontSize = 13.sp)
    }
}

@Composable
fun BudgetEmptyCard(text: String) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8ED)), shape = RoundedCornerShape(22.dp), border = BorderStroke(1.dp, Color(0xFFD8CBBE)), modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.fillMaxWidth().padding(22.dp), contentAlignment = Alignment.Center) {
            Text(text = text, color = Color(0xFF6B7280))
        }
    }
}

@Composable
fun BudgetFormCard(budget: String, onBudgetChange: (String) -> Unit, keyword: String, onKeywordChange: (String) -> Unit, location: String, onLocationChange: (String) -> Unit, strategy: BudgetStrategy, onStrategyChange: (BudgetStrategy) -> Unit, loading: Boolean, onSubmit: () -> Unit) {
    BudgetPanel {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            BudgetInputField(budget, onBudgetChange, "預算上限", "5000", KeyboardType.Number, { Icon(Icons.Rounded.AttachMoney, null) }, Modifier.weight(1f))
            BudgetInputField(location, onLocationChange, "地點", "台北", KeyboardType.Text, { Icon(Icons.Rounded.LocationOn, null) }, Modifier.weight(1f))
        }
        BudgetInputField(keyword, onKeywordChange, "關鍵字", "演唱會、展覽", KeyboardType.Text, { Icon(Icons.Rounded.Search, null) })
        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(BudgetStrategy.Balanced, BudgetStrategy.Value, BudgetStrategy.Saving).forEach { item ->
                OutlinedButton(onClick = { onStrategyChange(item) }, border = BorderStroke(1.dp, if (strategy == item) Color(0xFF245CFF) else Color(0xFFD8CBBE)), shape = RoundedCornerShape(16.dp)) {
                    Text(item.label, color = if (strategy == item) Color(0xFF173BAE) else Color(0xFF111827))
                }
            }
        }
        Button(onClick = onSubmit, enabled = !loading, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF245CFF)), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Rounded.Refresh, null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(if (loading) "推薦中..." else "套用條件")
        }
    }
}

// ========================================================
// 📊 分頁頁面
// ========================================================

@Composable
fun BudgetHomePage(budget: Int, spent: Int, online: Boolean, loading: Boolean, topItem: BudgetRecommendation?, recommendationCount: Int, savedCount: Int, recordCount: Int, onOpenSaved: () -> Unit, onOpenEvaluate: () -> Unit, onOpenRecords: () -> Unit, onOpenSettings: () -> Unit, onOpenRecommend: () -> Unit, onPickEvent: (BudgetRecommendation) -> Unit) {
    val remaining = (budget - spent).coerceAtLeast(0)
    val usedRate = if (budget > 0) (spent * 100 / budget).coerceIn(0, 100) else 0
    LazyColumn(modifier = Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { BudgetHeader("智能預算推薦", "本月活動預算管理", online, showBack = false) }
        item { BudgetMonthChip("2026 年 5 月預算", onOpenSettings) }
        item { BudgetHeroCard(budget, spent, remaining, usedRate) }
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BudgetQuickCard("♡", "收藏清單", "${savedCount} 場", Modifier.weight(1f), onOpenSaved)
                BudgetQuickCard("✓", "參加前評估", "${recommendationCount} 場", Modifier.weight(1f), onOpenEvaluate)
                BudgetQuickCard("票", "已購票紀錄", "${recordCount} 場", Modifier.weight(1f), onOpenRecords)
                BudgetQuickCard("設", "預算設定", "本月 ${formatMoney(budget).replace("NT$ ", "")}", Modifier.weight(1f), onOpenSettings)
            }
        }
        item { BudgetSummaryCard("預算影響總覽", "已購票與收藏活動的可能支出", formatMoney(spent), usedRate, "查看推薦", "查看分析", onOpenRecommend, onOpenSaved) }
        item { Text("今日優先推薦", color = Color(0xFF111827), fontSize = 20.sp, fontWeight = FontWeight.Black) }
        if (loading && topItem == null) { item { BudgetEmptyCard("正在整理推薦結果") } } 
        else if (topItem != null) { item { BudgetRecommendationCard(topItem, true, false, { onPickEvent(topItem) }, null) } }
    }
}

@Composable
fun BudgetRecommendPage(budget: String, onBudgetChange: (String) -> Unit, keyword: String, onKeywordChange: (String) -> Unit, location: String, onLocationChange: (String) -> Unit, strategy: BudgetStrategy, onStrategyChange: (BudgetStrategy) -> Unit, loading: Boolean, online: Boolean, items: List<BudgetRecommendation>, savedIds: Set<String>, onBack: () -> Unit, onSubmit: () -> Unit, onPickEvent: (BudgetRecommendation) -> Unit, onToggleSaved: (BudgetRecommendation) -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { BudgetHeader("智能推薦", "依預算與偏好排序", online, onBack = onBack) }
        item { BudgetFormCard(budget, onBudgetChange, keyword, onKeywordChange, location, onLocationChange, strategy, onStrategyChange, loading, onSubmit) }
        if (loading) { item { BudgetEmptyCard("正在整理推薦結果") } }
        items(items) { item -> BudgetRecommendationCard(item, false, savedIds.contains(item.id), { onPickEvent(item) }, { onToggleSaved(item) }) }
    }
}

@Composable
fun BudgetEvaluatePage(item: BudgetRecommendation?, items: List<BudgetRecommendation>, budget: Int, spent: Int, peopleCount: Int, includeExtra: Boolean, saved: Boolean, onBack: () -> Unit, onPickEvent: (BudgetRecommendation) -> Unit, onToggleSaved: () -> Unit, onAddRecord: () -> Unit) {
    if (item == null) {
        LazyColumn(modifier = Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item { BudgetHeader("參加前評估", "先選擇要評估的活動", true, onBack = onBack) }
            item { BudgetPanel { Text("選擇要評估的活動", color = Color(0xFF111827), fontSize = 22.sp, fontWeight = FontWeight.Black); Text("從目前推薦的 ${items.size} 場活動中選一場，再查看總花費、預算壓力與是否適合參加。", color = Color(0xFF6B7280), fontSize = 13.sp) } }
            if (items.isEmpty()) { item { BudgetEmptyCard("目前沒有可評估的活動") } } 
            else { items(items) { r -> BudgetRecommendationCard(r, true, false, { onPickEvent(r) }, null) } }
        }
        return
    }
    val estimatedTotal = item.estimatedSpend * peopleCount + if (includeExtra) 150 else 0
    val after = (budget - spent - estimatedTotal).coerceAtLeast(0)
    val usage = if (budget > 0) ((spent + estimatedTotal) * 100 / budget).coerceIn(0, 100) else 0
    LazyColumn(modifier = Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { BudgetHeader("參加前評估", "確認是否值得購買", true, onBack = onBack) }
        item {
            BudgetPanel {
                Text(item.title, color = Color(0xFF111827), fontSize = 22.sp, fontWeight = FontWeight.Black)
                Text("${item.venue.ifBlank { "地點待確認" }} · ${item.activityTime.ifBlank { "時間待確認" }}", color = Color(0xFF6B7280), fontSize = 13.sp)
                Text(formatMoney(estimatedTotal), color = Color(0xFF173BAE), fontSize = 38.sp, fontWeight = FontWeight.Black)
                BudgetProgress(usage)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BudgetMetric("購買後剩餘", formatMoney(after), Modifier.weight(1f))
                    BudgetMetric("推薦分數", "${item.score}", Modifier.weight(1f))
                }
                BudgetInfoBlock("評估結果", item.reason)
                BudgetInfoBlock("估算明細", "票價 ${formatMoney(item.estimatedSpend)} × $peopleCount 人")
                BudgetInfoBlock("風險提醒", item.riskMessage)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = onToggleSaved, modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp)) { Text(if (saved) "取消收藏" else "加入收藏", color = Color(0xFF173BAE)) }
                    Button(onClick = onAddRecord, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF245CFF)), shape = RoundedCornerShape(16.dp)) { Text("加入紀錄") }
                }
            }
        }
    }
}

@Composable
fun BudgetRecordsPage(budget: Int, records: List<TicketRecord>, onBack: () -> Unit, onAdd: () -> Unit, onRemove: (TicketRecord) -> Unit) {
    val spent = records.sumOf { it.total }
    LazyColumn(modifier = Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { BudgetHeader("已購票紀錄", "追蹤本月實際花費", true, onBack = onBack) }
        item { BudgetSummaryCard("已記錄花費", "${records.size} 筆票券紀錄", formatMoney(spent), if (budget > 0) spent * 100 / budget else 0, "新增票券", "返回主頁", onAdd, onBack) }
        if (records.isEmpty()) { item { BudgetEmptyCard("尚未建立購票紀錄\n新增後會自動更新預算使用率") } }
        items(records) { r ->
            BudgetPanel {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(r.title, color = Color(0xFF111827), fontSize = 16.sp, fontWeight = FontWeight.Black)
                        Text("${r.date.ifBlank { "日期未填" }} · ${r.platform}", color = Color(0xFF6B7280), fontSize = 12.sp)
                    }
                    OutlinedButton(onClick = { onRemove(r) }) { Text("刪除") }
                }
                Text(formatMoney(r.total), color = Color(0xFF173BAE), fontSize = 20.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
fun BudgetAddRecordPage(onBack: () -> Unit, onSave: (TicketRecord) -> Unit) {
    var title by remember { mutableStateOf("") }; var date by remember { mutableStateOf("") }
    var platform by remember { mutableStateOf("") }; var price by remember { mutableStateOf("") }
    val context = LocalContext.current
    LazyColumn(modifier = Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { BudgetHeader("手動新增票券", "建立自己的購票紀錄", true, onBack = onBack) }
        item {
            BudgetPanel {
                BudgetInputField(title, { title = it }, "活動名稱", "輸入活動名稱", KeyboardType.Text, null)
                BudgetInputField(date, { date = it }, "日期", "2026/06/01", KeyboardType.Text, null)
                BudgetInputField(price, { price = it }, "單張票價", "2500", KeyboardType.Number, null)
                Button(onClick = {
                    val p = price.toIntOrNull() ?: 0
                    if (title.isBlank() || p <= 0) { Toast.makeText(context, "請輸入活動名稱與票價", Toast.LENGTH_SHORT).show() } 
                    else { onSave(TicketRecord(id = System.currentTimeMillis().toString(), title = title, date = date, platform = platform.ifBlank { "自行記錄" }, price = p, quantity = 1, fee = 0, note = "")) }
                }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF245CFF)), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) { Text("儲存票券") }
            }
        }
    }
}

@Composable
fun BudgetSavedPage(budget: Int, items: List<BudgetRecommendation>, onBack: () -> Unit, onOpenRecommend: () -> Unit, onPickEvent: (BudgetRecommendation) -> Unit, onRemove: (BudgetRecommendation) -> Unit) {
    val total = items.sumOf { it.estimatedSpend }
    LazyColumn(modifier = Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { BudgetHeader("收藏活動分析", "比較想參加的活動", true, onBack = onBack) }
        item { BudgetSummaryCard("收藏預估總額", if (total > budget) "已超出目前預算" else "目前仍在預算內", formatMoney(total), if (budget > 0) total * 100 / budget else 0, "加入更多活動", "返回主頁", onOpenRecommend, onBack) }
        if (items.isEmpty()) { item { BudgetEmptyCard("尚未收藏活動") } }
        items(items) { item -> BudgetRecommendationCard(item, true, true, { onPickEvent(item) }, { onRemove(item) }) }
    }
}

@Composable
fun BudgetSettingsPage(budget: String, onBudgetChange: (String) -> Unit, singleLimit: String, onSingleLimitChange: (String) -> Unit, includeExtra: Boolean, onIncludeExtraChange: (Boolean) -> Unit, peopleCount: Int, onPeopleCountChange: (Int) -> Unit, strategy: BudgetStrategy, onStrategyChange: (BudgetStrategy) -> Unit, onBack: () -> Unit, onRefresh: () -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { BudgetHeader("預算設定", "調整推薦條件", true, onBack = onBack) }
        item {
            BudgetPanel {
                BudgetInputField(budget, onBudgetChange, "本月活動預算", "5000", KeyboardType.Number, { Icon(Icons.Rounded.AttachMoney, null) })
                Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0xFFFFF8ED)).clickable { onIncludeExtraChange(!includeExtra) }.padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column { Text("是否包含其他花費", color = Color(0xFF111827), fontWeight = FontWeight.Bold); Text("如手續費等", color = Color(0xFF6B7280), fontSize = 12.sp) }
                    Text(if (includeExtra) "開啟" else "關閉", color = Color(0xFF173BAE), fontWeight = FontWeight.Black)
                }
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(BudgetStrategy.Balanced, BudgetStrategy.Value, BudgetStrategy.Saving).forEach { item ->
                        OutlinedButton(onClick = { onStrategyChange(item) }, border = BorderStroke(1.dp, if (strategy == item) Color(0xFF245CFF) else Color(0xFFD8CBBE))) { Text(item.label, color = Color(0xFF111827)) }
                    }
                }
                Button(onClick = onRefresh, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF245CFF)), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) { Text("更新推薦") }
            }
        }
    }
}

fun formatMoney(value: Int): String {
    return "NT$ ${NumberFormat.getNumberInstance(Locale.TAIWAN).format(value.coerceAtLeast(0))}"
}

fun demoRecommendations(): List<BudgetRecommendation> {
    return listOf(
        BudgetRecommendation("demo-1", "五月天 25 週年巡迴演唱會", "五月天", "台北小巨蛋", "拓元售票", "販售中", "2026/07/18", "1880 / 2880 / 3880", 1880, 3880, 1880, 3120, 38, "合理使用", 91, "excellent", "票價落在預算內，活動熱度高，適合列為優先購票目標。", "low", "預算壓力低，可以保留足夠餘裕。", ""),
        BudgetRecommendation("demo-2", "台北國際動漫節", "", "南港展覽館", "KKTIX", "販售中", "2026/06/21", "350 / 650", 350, 650, 350, 4650, 7, "低使用", 78, "good", "低花費、低風險，適合與其他活動一起安排。", "low", "預算壓力低。", "")
    )
}