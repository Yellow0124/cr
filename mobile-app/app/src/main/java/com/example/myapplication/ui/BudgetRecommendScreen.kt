package com.example.myapplication.ui

import android.util.Log
import android.content.Intent
import android.net.Uri
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
import java.text.NumberFormat
import java.util.Calendar
import java.util.Locale

private const val PERIOD_BUDGET_DEFAULT = "15000"
private const val DEFAULT_PERIOD_MONTHS = 3
private data class PurchaseDecision(val title: String, val reason: String)

@Composable
fun BudgetRecommendScreen(
    token: String,
    apiGetExecutor: (String, (String) -> Unit, (String) -> Unit) -> Unit,
    apiPostExecutor: (String, String, (String) -> Unit, (String) -> Unit) -> Unit,
    onLogout: () -> Unit = {},
    onBack: () -> Unit
) {
    val context = LocalContext.current
    
    val pageState = remember { mutableStateOf(BudgetPage.Home) }
    var page by pageState
    var budget by remember { mutableStateOf(PERIOD_BUDGET_DEFAULT) }
    var periodMonths by remember { mutableStateOf(DEFAULT_PERIOD_MONTHS) }
    var keyword by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var strategy by remember { mutableStateOf(BudgetStrategy.Balanced) }
    var loading by remember { mutableStateOf(false) }
    var online by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf(BudgetRecommendResult()) }
    var selectedId by remember { mutableStateOf("") }
    var selectedPlanType by remember { mutableStateOf("") }
    val savedIdsState = remember { mutableStateOf(setOf<String>()) }
    var savedIds by savedIdsState
    val recordsState = remember { mutableStateOf(listOf<TicketRecord>()) }
    var records by recordsState
    var singleLimit by remember { mutableStateOf("3000") }
    var includeExtra by remember { mutableStateOf(true) }
    var peopleCount by remember { mutableStateOf(1) }
    val resetBudgetStateAndLogout = {
        handleLogout(context, pageState, savedIdsState, recordsState)
        budget = PERIOD_BUDGET_DEFAULT
        periodMonths = DEFAULT_PERIOD_MONTHS
        keyword = ""
        location = ""
        strategy = BudgetStrategy.Balanced
        loading = false
        online = false
        result = BudgetRecommendResult()
        selectedId = ""
        selectedPlanType = ""
        singleLimit = "3000"
        includeExtra = true
        peopleCount = 1
        onLogout()
    }

    // 🛡️ 強健的網路管線：包裹安全轉換器，徹底粉碎型態閃退
    val submitQuery: (Boolean) -> Unit = { showError ->
        val maxBudget = budget.filter { it.isDigit() }.toIntOrNull()
        if (maxBudget == null || maxBudget <= 0) {
            if (showError) Toast.makeText(context, "請輸入有效預算", Toast.LENGTH_SHORT).show()
        } else {
            loading = true
            val payload = JSONObject()
                .put("maxBudget", maxBudget)
                .put("budgetPeriod", budgetPeriodApiValue(periodMonths))
                .put("periodMonths", periodMonths.coerceIn(1, 12))
                .put("keyword", keyword.trim())
                .put("location", location.trim())
                .put("strategy", strategy.apiValue)
                .put("savedEventIds", JSONArray(savedIds.toList()))
                .put("recordedVenues", JSONArray(records.map { it.platform }.distinct().take(8)))
                .put("limit", 15)
                .toString()

            apiPostExecutor("/api/recommendations/budget", payload, { body ->
                // 🛡️ 使用安全的防禦性多重防護網包裹 JSON 解析
                runCatching {
                    val root = JSONObject(body)
                    val itemsArr = root.optJSONArray("items") ?: JSONArray()
                    val plansArr = root.optJSONArray("plans") ?: JSONArray()
                    val insightsArr = root.optJSONArray("insights") ?: JSONArray()
                    val filtersObj = root.optJSONObject("availableFilters") ?: JSONObject()
                    val list = mutableListOf<BudgetRecommendation>()
                    val plans = mutableListOf<BudgetPlan>()
                    val insights = mutableListOf<String>()
                    
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
                                starRating = obj.optInt("starRating", ((obj.optInt("score", 80) + 10) / 20).coerceIn(1, 5)),
                                reason = obj.optString("reason", "數據模型計算中"),
                                riskLevel = obj.optString("riskLevel", "low"),
                                riskMessage = obj.optString("riskMessage", "無顯著風險"),
                                url = obj.optString("url", ""),
                                scoreReasons = jsonStringList(obj.optJSONArray("scoreReasons")),
                                reminderStatus = obj.optString("reminderStatus", ""),
                                reminderLabel = obj.optString("reminderLabel", ""),
                                priceSource = obj.optString("priceSource", obj.optString("priceText", ""))
                            )
                        )
                    }

                    for (i in 0 until plansArr.length()) {
                        val obj = plansArr.getJSONObject(i)
                        val idsArr = obj.optJSONArray("eventIds") ?: JSONArray()
                        val ids = mutableListOf<String>()
                        for (j in 0 until idsArr.length()) {
                            ids.add(idsArr.opt(j)?.toString() ?: "")
                        }
                        plans.add(
                            BudgetPlan(
                                type = obj.optString("type", "custom"),
                                title = obj.optString("title", "推薦方案"),
                                description = obj.optString("description", ""),
                                suitableFor = obj.optString("suitableFor", ""),
                                actionLabel = obj.optString("actionLabel", "查看活動"),
                                estimatedSpend = obj.optInt("estimatedSpend", 0),
                                budgetLeft = obj.optInt("budgetLeft", 0),
                                eventIds = ids.filter { it.isNotBlank() },
                                decisionLabel = obj.optString("decisionLabel", ""),
                                itemSummaries = jsonStringList(obj.optJSONArray("itemSummaries"))
                            )
                        )
                    }

                    for (i in 0 until insightsArr.length()) {
                        insights.add(insightsArr.optString(i, "").trim())
                    }
                    
                    loading = false
                    online = true
                    result = BudgetRecommendResult(
                        items = list,
                        plans = plans,
                        insights = insights.filter { it.isNotBlank() },
                        availableFilters = BudgetAvailableFilters(
                            cities = jsonStringList(filtersObj.optJSONArray("cities")),
                            venues = jsonStringList(filtersObj.optJSONArray("venues")),
                            platforms = jsonStringList(filtersObj.optJSONArray("platforms"))
                        )
                    )
                }.onFailure { err ->
                    loading = false
                    online = false
                    result = BudgetRecommendResult()
                    Log.e("TicketFlow", "預算推薦 JSON 解析失敗，已關閉 Demo 降級", err)
                    if (showError) Toast.makeText(context, "資料解析失敗，請檢查 API 回傳欄位", Toast.LENGTH_SHORT).show()
                }
            }, { errBody ->
                Handler(Looper.getMainLooper()).post {
                    loading = false
                    online = false
                    result = BudgetRecommendResult()
                    Log.e("TicketFlow", "預算推薦 API 失敗，已關閉 Demo 降級：$errBody")
                    if (showError) Toast.makeText(context, "資料連線失敗，請檢查後端 API 與資料庫", Toast.LENGTH_SHORT).show()
                }
            })
        }
    }

    LaunchedEffect(Unit) {
        apiGetExecutor("/api/recommendations/budget/user-data", { body ->
            runCatching {
                val root = JSONObject(body)
                val idsArr = root.optJSONArray("savedEventIds") ?: JSONArray()
                val loadedIds = mutableSetOf<String>()
                for (i in 0 until idsArr.length()) {
                    idsArr.opt(i)?.toString()?.takeIf { it.isNotBlank() }?.let { loadedIds.add(it) }
                }

                val recordsArr = root.optJSONArray("ticketRecords") ?: JSONArray()
                val loadedRecords = mutableListOf<TicketRecord>()
                for (i in 0 until recordsArr.length()) {
                    val obj = recordsArr.getJSONObject(i)
                    loadedRecords.add(
                        TicketRecord(
                            id = obj.optString("id", "ticket-$i"),
                            title = obj.optString("title", "未命名活動"),
                            date = obj.optString("date", ""),
                            platform = obj.optString("platform", ""),
                            price = obj.optInt("price", 0),
                            quantity = obj.optInt("quantity", 1).coerceAtLeast(1),
                            fee = obj.optInt("fee", 0),
                            note = obj.optString("note", "")
                        )
                    )
                }

                savedIds = loadedIds
                records = loadedRecords
            }.onFailure { err ->
                Log.e("TicketFlow", "預算使用者資料解析失敗", err)
            }
            submitQuery(false)
        }, {
            submitQuery(false)
        })
    }

    val maxBudgetInt = budget.filter { it.isDigit() }.toIntOrNull() ?: PERIOD_BUDGET_DEFAULT.toInt()
    val periodLabel = budgetPeriodLabel(periodMonths)
    val periodRecords = records.filter { isInSelectedPeriod(it.date, periodMonths) }
    val spent = periodRecords.sumOf { it.total }
    val items = result.items.take(50)
    val savedItems = items.filter { savedIds.contains(it.id) }
    val planned = savedItems.sumOf { it.estimatedSpend }
    val plans = result.plans

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFFF6F1E8))) {
        when (page) {
            BudgetPage.Login -> BudgetLoginPage { page = BudgetPage.Home }
            BudgetPage.Home -> BudgetHomePage(maxBudgetInt, periodLabel, spent, planned, online, loading, items.firstOrNull(), items.size, savedItems.size, periodRecords.size, { page = BudgetPage.Saved }, { selectedId = ""; page = BudgetPage.Evaluate }, { page = BudgetPage.Records }, { page = BudgetPage.Settings }, { page = BudgetPage.Recommend }, { selectedId = it.id; page = BudgetPage.Evaluate })
            BudgetPage.Recommend -> BudgetRecommendPage(budget, { budget = it }, periodMonths, { periodMonths = it.coerceIn(1, 12) }, keyword, { keyword = it }, location, { location = it }, strategy, { strategy = it }, loading, online, items, plans, result.insights, result.availableFilters, savedIds, { page = BudgetPage.Home }, { submitQuery(true) }, { selectedId = it.id; page = BudgetPage.Evaluate }, { plan -> selectedPlanType = plan.type; page = BudgetPage.PlanDetail }, { item -> savedIds = if (savedIds.contains(item.id)) savedIds - item.id else savedIds + item.id })
            BudgetPage.PlanDetail -> BudgetPlanDetailPage(plans.find { it.type == selectedPlanType }, items, maxBudgetInt, periodLabel, { page = BudgetPage.Recommend }, { selectedId = it.id; page = BudgetPage.Evaluate }, { item -> savedIds = if (savedIds.contains(item.id)) savedIds - item.id else savedIds + item.id }, savedIds)
            BudgetPage.Evaluate -> BudgetEvaluatePage(items.find { it.id == selectedId }, items, maxBudgetInt, periodLabel, spent, peopleCount, includeExtra, savedIds.contains(selectedId), { page = BudgetPage.Home }, { selectedId = it.id }, { if (selectedId.isNotBlank()) savedIds = if (savedIds.contains(selectedId)) savedIds - selectedId else savedIds + selectedId }, { val item = items.find { it.id == selectedId }; if (item != null) { records = listOf(TicketRecord(id = System.currentTimeMillis().toString(), title = item.title, date = item.activityTime.ifBlank { item.saleTime }, platform = item.source, price = item.estimatedSpend, quantity = peopleCount, fee = if (includeExtra) 150 else 0, note = "由評估頁加入")) + records; page = BudgetPage.Records } })
            BudgetPage.Records -> BudgetRecordsPage(maxBudgetInt, periodLabel, periodRecords, { page = BudgetPage.Home }, { page = BudgetPage.AddRecord }, { r -> records = records.filterNot { it.id == r.id } })
            BudgetPage.AddRecord -> BudgetAddRecordPage({ page = BudgetPage.Records }, { records = listOf(it) + records; page = BudgetPage.Records })
            BudgetPage.Saved -> BudgetSavedPage(maxBudgetInt, periodLabel, savedItems, { page = BudgetPage.Home }, { page = BudgetPage.Recommend }, { selectedId = it.id; page = BudgetPage.Evaluate }, { item -> savedIds = savedIds - item.id })
            BudgetPage.Settings -> BudgetSettingsPage(budget, { budget = it }, periodMonths, { periodMonths = it.coerceIn(1, 12) }, singleLimit, { singleLimit = it }, includeExtra, { includeExtra = it }, peopleCount, { peopleCount = it }, strategy, { strategy = it }, { page = BudgetPage.Home }, { submitQuery(true); page = BudgetPage.Recommend }, resetBudgetStateAndLogout)
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
        BudgetStatusChip(if (online) "已更新" else "整理中", online)
    }
}

fun handleLogout(
    context: android.content.Context,
    pageState: MutableState<BudgetPage>,
    savedIds: MutableState<Set<String>>,
    records: MutableState<List<TicketRecord>>
) {
    context.getSharedPreferences("user_session", android.content.Context.MODE_PRIVATE).edit().clear().apply()
    context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE).edit()
        .remove("auth_token")
        .remove("token")
        .remove("user_id")
        .remove("email")
        .apply()
    savedIds.value = emptySet()
    records.value = emptyList()
    pageState.value = BudgetPage.Login
}

@Composable
fun BudgetLoginPage(onLogin: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().background(Color(0xFFF6F1E8)).padding(18.dp), contentAlignment = Alignment.Center) {
        BudgetPanel {
            Icon(Icons.Rounded.Lock, null, tint = Color(0xFF245CFF), modifier = Modifier.size(42.dp))
            Text("已登出", color = Color(0xFF111827), fontSize = 24.sp, fontWeight = FontWeight.Black)
            Text("登入狀態已清除，請重新進入預算推薦功能。", color = Color(0xFF6B7280), fontSize = 13.sp)
            Button(
                onClick = onLogin,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF245CFF)),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("回到預算首頁", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun BudgetPanel(content: @Composable ColumnScope.() -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFCF6)), shape = RoundedCornerShape(22.dp), border = BorderStroke(1.dp, Color(0xFFD8CBBE)), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp), modifier = Modifier.fillMaxWidth()) {
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
fun BudgetHeroCard(budget: Int, periodLabel: String, spent: Int, remaining: Int, usedRate: Int) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.Transparent), shape = RoundedCornerShape(24.dp)) {
        Column(modifier = Modifier.background(Brush.linearGradient(listOf(Color(0xFF245CFF), Color(0xFF173BAE), Color(0xFFD8A84E)))).padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("${periodLabel}活動預算", color = Color.White.copy(alpha = 0.82f), fontWeight = FontWeight.Bold)
                    Text(formatMoney(budget), color = Color.White, fontSize = 35.sp, fontWeight = FontWeight.Black)
                }
                Box(modifier = Modifier.size(76.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                    Text("${usedRate}%", color = Color.White, fontWeight = FontWeight.Black)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                BudgetHeroMetric("期間已使用", formatMoney(spent), Modifier.weight(1f))
                BudgetHeroMetric("期間剩餘", formatMoney(remaining), Modifier.weight(1f))
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
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFCF6)), shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, Color(0xFFD8CBBE)), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp, pressedElevation = 0.dp), modifier = modifier.heightIn(min = 90.dp).clickable { onClick() }) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 12.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
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
            OutlinedButton(onClick = onSecondary, modifier = Modifier.weight(1f).height(46.dp), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, Color(0xFFD8CBBE))) { Text(secondaryLabel, color = Color(0xFF173BAE), fontWeight = FontWeight.Black) }
            Button(onClick = onPrimary, modifier = Modifier.weight(1f).height(46.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF245CFF)), shape = RoundedCornerShape(16.dp)) { Text(primaryLabel, fontWeight = FontWeight.Black) }
        }
    }
}

@Composable
fun BudgetOutlookCard(budget: Int, spent: Int, planned: Int, onOpenRecommend: () -> Unit, onOpenSaved: () -> Unit) {
    val committed = spent + planned
    val available = (budget - committed).coerceAtLeast(0)
    val pressure = if (budget > 0) (committed * 100 / budget).coerceIn(0, 100) else 0
    val status = when {
        pressure >= 90 -> "建議先控管支出"
        pressure >= 70 -> "預算壓力偏高"
        planned > 0 -> "可安排更多活動"
        else -> "期間預算健康"
    }
    BudgetPanel {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("預算展望", color = Color(0xFF111827), fontWeight = FontWeight.Black)
                Text(status, color = Color(0xFF6B7280), fontSize = 12.sp)
            }
            BudgetScoreChip("${pressure}%")
        }
        BudgetProgress(pressure)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            BudgetMetric("已花費", formatMoney(spent), Modifier.weight(1f))
            BudgetMetric("收藏計畫", formatMoney(planned), Modifier.weight(1f))
        }
        BudgetMetric("扣除計畫後可用", formatMoney(available), Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = onOpenSaved, modifier = Modifier.weight(1f).height(46.dp), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, Color(0xFFD8CBBE))) { Text("看收藏", color = Color(0xFF173BAE), fontWeight = FontWeight.Black) }
            Button(onClick = onOpenRecommend, modifier = Modifier.weight(1f).height(46.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF245CFF)), shape = RoundedCornerShape(16.dp)) { Text("找活動", fontWeight = FontWeight.Black) }
        }
    }
}

@Composable
fun BudgetPlanCard(plan: BudgetPlan, onClick: () -> Unit) {
    BudgetPanel {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(plan.title, color = Color(0xFF111827), fontSize = 16.sp, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(plan.suitableFor.ifBlank { "依預算與推薦星等產生" }, color = Color(0xFF6B7280), fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Text(formatMoney(plan.estimatedSpend), color = Color(0xFF173BAE), fontSize = 17.sp, fontWeight = FontWeight.Black)
        }
        Text(plan.description, color = Color(0xFF6B7280), fontSize = 13.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
        if (plan.decisionLabel.isNotBlank()) {
            BudgetInfoBlock("方案判斷", plan.decisionLabel)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            BudgetMetric("包含活動", "${plan.eventIds.size} 場", Modifier.weight(1f))
            BudgetMetric("預估剩餘", formatMoney(plan.budgetLeft), Modifier.weight(1f))
        }
        Button(onClick = onClick, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF245CFF)), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().height(46.dp)) {
            Text(plan.actionLabel.ifBlank { "查看方案" }, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
fun BudgetInsightCard(insights: List<String>, filters: BudgetAvailableFilters) {
    if (insights.isEmpty()) return
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFCF6)), shape = RoundedCornerShape(22.dp), border = BorderStroke(1.dp, Color(0xFFD8CBBE)), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("推薦摘要", color = Color(0xFF111827), fontWeight = FontWeight.Black)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                insights.take(3).forEach { text ->
                    Text("• $text", color = Color(0xFF6B7280), fontSize = 13.sp, lineHeight = 18.sp)
                }
            }
        }
    }
}

@Composable
fun BudgetRecommendationCard(item: BudgetRecommendation, compact: Boolean, saved: Boolean, onClick: () -> Unit, onToggleSaved: (() -> Unit)?) {
    val context = LocalContext.current
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFCF6)), shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, Color(0xFFD8CBBE)), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp, pressedElevation = 0.dp), modifier = Modifier.fillMaxWidth().clickable { onClick() }) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                    Text(item.title, color = Color(0xFF111827), fontSize = 15.sp, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("${item.venue.ifBlank { "地點待確認" }} · ${item.activityTime.ifBlank { "時間待確認" }}", color = Color(0xFF6B7280), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                BudgetStarChip(item.starRating)
            }
            BudgetTicketInfoGrid(item)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(formatMoney(item.estimatedSpend), color = Color(0xFF173BAE), fontSize = 20.sp, fontWeight = FontWeight.Black)
                Text("${item.budgetUsageRate.coerceAtLeast(0)}%", color = Color(0xFF6B7280), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            BudgetProgress(item.budgetUsageRate.coerceIn(0, 100))
            if (item.reminderLabel.isNotBlank()) {
                Text(item.reminderLabel, color = Color(0xFF173BAE), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            if (compact) { Text(item.reason, color = Color(0xFF6B7280), fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            if (!compact && item.scoreReasons.isNotEmpty()) {
                Text(item.scoreReasons.take(2).joinToString(" · "), color = Color(0xFF6B7280), fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (onToggleSaved != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = onToggleSaved, modifier = Modifier.weight(1f).height(44.dp), shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, Color(0xFFD8CBBE))) { Text(if (saved) "已收藏" else "收藏", color = Color(0xFF173BAE), fontWeight = FontWeight.Black) }
                    if (item.url.isNotBlank()) {
                        Button(onClick = { openTicketUrl(context, item.url) }, modifier = Modifier.weight(1f).height(44.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF245CFF)), shape = RoundedCornerShape(14.dp)) { Text("售票頁") }
                    }
                }
            }
        }
    }
}

@Composable
fun BudgetTicketInfoGrid(item: BudgetRecommendation) {
    Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0xFFFFF8ED)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            BudgetMiniInfo("售票平台", cleanDisplay(item.source, "待確認"), Modifier.weight(1f))
            BudgetMiniInfo("售票時間", cleanDisplay(item.saleTime, "待公布"), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            BudgetMiniInfo("活動時間", cleanDisplay(item.activityTime, "待確認"), Modifier.weight(1f))
            BudgetMiniInfo("票價", recommendationPriceText(item), Modifier.weight(1f))
        }
    }
}

@Composable
fun BudgetMiniInfo(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(label, color = Color(0xFF6B7280), fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Text(value, color = Color(0xFF111827), fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun BudgetScoreChip(text: String) {
    Box(modifier = Modifier.widthIn(min = 52.dp).height(32.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFFE8EEFF)).padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
        Text(text = text, color = Color(0xFF173BAE), fontSize = 11.sp, fontWeight = FontWeight.Black, maxLines = 1)
    }
}

@Composable
fun BudgetStarChip(stars: Int) {
    val count = stars.coerceIn(1, 5)
    Row(modifier = Modifier.widthIn(min = 76.dp).height(32.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFFE8EEFF)).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        repeat(5) { index ->
            Text(if (index < count) "★" else "☆", color = Color(0xFF173BAE), fontSize = 12.sp, fontWeight = FontWeight.Black)
        }
    }
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
            BudgetInputField(budget, onBudgetChange, "預算上限", PERIOD_BUDGET_DEFAULT, KeyboardType.Number, { Icon(Icons.Rounded.AttachMoney, null) }, Modifier.weight(1f))
            BudgetInputField(location, onLocationChange, "地點", "台北", KeyboardType.Text, { Icon(Icons.Rounded.LocationOn, null) }, Modifier.weight(1f))
        }
        BudgetInputField(keyword, onKeywordChange, "關鍵字", "演唱會、展覽", KeyboardType.Text, { Icon(Icons.Rounded.Search, null) })
        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(BudgetStrategy.Balanced, BudgetStrategy.Saving, BudgetStrategy.Value).forEach { item ->
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

@Composable
fun BudgetPeriodSelector(periodMonths: Int, onPeriodMonthsChange: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("預算期間", color = Color(0xFF111827), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text("目前期間：${budgetPeriodLabel(periodMonths)}", color = Color(0xFF6B7280), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(1, 3, 6, 12).forEach { month ->
                OutlinedButton(
                    onClick = { onPeriodMonthsChange(month) },
                    border = BorderStroke(1.dp, if (periodMonths == month) Color(0xFF245CFF) else Color(0xFFD8CBBE)),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = if (periodMonths == month) Color(0xFFE8EEFF) else Color.White),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(budgetPeriodLabel(month), color = Color(0xFF173BAE), fontWeight = FontWeight.Bold)
                }
            }
        }
        BudgetInputField(
            value = periodMonths.toString(),
            onValueChange = { text ->
                val value = text.filter { it.isDigit() }.toIntOrNull()
                if (value != null) onPeriodMonthsChange(value.coerceIn(1, 12))
            },
            label = "自訂月份",
            placeholder = "1-12",
            keyboardType = KeyboardType.Number,
            icon = { Icon(Icons.Rounded.DateRange, null) }
        )
    }
}

// ========================================================
// 📊 分頁頁面
// ========================================================

@Composable
fun BudgetHomePage(budget: Int, periodLabel: String, spent: Int, planned: Int, online: Boolean, loading: Boolean, topItem: BudgetRecommendation?, recommendationCount: Int, savedCount: Int, recordCount: Int, onOpenSaved: () -> Unit, onOpenEvaluate: () -> Unit, onOpenRecords: () -> Unit, onOpenSettings: () -> Unit, onOpenRecommend: () -> Unit, onPickEvent: (BudgetRecommendation) -> Unit) {
    val remaining = (budget - spent).coerceAtLeast(0)
    val usedRate = if (budget > 0) (spent * 100 / budget).coerceIn(0, 100) else 0
    LazyColumn(modifier = Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { BudgetHeader("智能預算推薦", "彈性月份預算管理", online, showBack = false) }
        item { BudgetMonthChip(periodLabel, onOpenSettings) }
        item { BudgetHeroCard(budget, periodLabel, spent, remaining, usedRate) }
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BudgetQuickCard("♡", "收藏清單", "${savedCount} 場", Modifier.weight(1f), onOpenSaved)
                BudgetQuickCard("✓", "參加前評估", "${recommendationCount} 場", Modifier.weight(1f), onOpenEvaluate)
                BudgetQuickCard("票", "已購票紀錄", "${recordCount} 場", Modifier.weight(1f), onOpenRecords)
                BudgetQuickCard("設", "預算設定", "${periodLabel} ${formatMoney(budget).replace("NT$ ", "")}", Modifier.weight(1f), onOpenSettings)
            }
        }
        item { BudgetOutlookCard(budget, spent, planned, onOpenRecommend, onOpenSaved) }
        item { Text("今日優先推薦", color = Color(0xFF111827), fontSize = 20.sp, fontWeight = FontWeight.Black) }
        if (loading && topItem == null) { item { BudgetEmptyCard("正在整理推薦結果") } } 
        else if (topItem != null) { item { BudgetRecommendationCard(topItem, true, false, { onPickEvent(topItem) }, null) } }
        else { item { BudgetEmptyCard("目前沒有推薦資料\n請點選「找活動」或檢查 API 連線") } }
    }
}

@Composable
fun BudgetRecommendPage(budget: String, onBudgetChange: (String) -> Unit, periodMonths: Int, onPeriodMonthsChange: (Int) -> Unit, keyword: String, onKeywordChange: (String) -> Unit, location: String, onLocationChange: (String) -> Unit, strategy: BudgetStrategy, onStrategyChange: (BudgetStrategy) -> Unit, loading: Boolean, online: Boolean, items: List<BudgetRecommendation>, plans: List<BudgetPlan>, insights: List<String>, filters: BudgetAvailableFilters, savedIds: Set<String>, onBack: () -> Unit, onSubmit: () -> Unit, onPickEvent: (BudgetRecommendation) -> Unit, onPickPlan: (BudgetPlan) -> Unit, onToggleSaved: (BudgetRecommendation) -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { BudgetHeader("智能推薦", "依預算與偏好排序", online, onBack = onBack) }
        item { BudgetFormCard(budget, onBudgetChange, keyword, onKeywordChange, location, onLocationChange, strategy, onStrategyChange, loading, onSubmit) }
        item { BudgetPeriodSelector(periodMonths, onPeriodMonthsChange) }
        item { BudgetInsightCard(insights, filters) }
        if (plans.isNotEmpty()) {
            item { Text("預算方案", color = Color(0xFF111827), fontSize = 20.sp, fontWeight = FontWeight.Black) }
            items(plans.take(3)) { plan -> BudgetPlanCard(plan) { onPickPlan(plan) } }
        }
        item { Text("推薦活動", color = Color(0xFF111827), fontSize = 20.sp, fontWeight = FontWeight.Black) }
        if (loading) { item { BudgetEmptyCard("正在整理推薦結果") } }
        else if (items.isEmpty()) { item { BudgetEmptyCard("資料連線失敗或資料庫目前沒有符合條件的活動") } }
        items(items) { item -> BudgetRecommendationCard(item, false, savedIds.contains(item.id), { onPickEvent(item) }, { onToggleSaved(item) }) }
    }
}

@Composable
fun BudgetPlanDetailPage(plan: BudgetPlan?, items: List<BudgetRecommendation>, budget: Int, periodLabel: String, onBack: () -> Unit, onPickEvent: (BudgetRecommendation) -> Unit, onToggleSaved: (BudgetRecommendation) -> Unit, savedIds: Set<String>) {
    val planItems = plan?.eventIds.orEmpty().mapNotNull { id -> items.find { it.id == id } }
    val total = planItems.sumOf { it.estimatedSpend }
    val left = (budget - total).coerceAtLeast(0)
    LazyColumn(modifier = Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { BudgetHeader("方案詳情", plan?.title ?: "預算方案", true, onBack = onBack) }
        if (plan == null) {
            item { BudgetEmptyCard("找不到這個方案") }
        } else {
            item {
                BudgetPanel {
                    Text(plan.title, color = Color(0xFF111827), fontSize = 22.sp, fontWeight = FontWeight.Black)
                    Text(plan.description, color = Color(0xFF6B7280), fontSize = 13.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        BudgetMetric("方案總花費", formatMoney(total), Modifier.weight(1f))
                        BudgetMetric("期間剩餘", formatMoney(left), Modifier.weight(1f))
                    }
                    if (plan.itemSummaries.isNotEmpty()) {
                        BudgetInfoBlock("方案重點", plan.itemSummaries.take(3).joinToString("\n"))
                    }
                }
            }
            if (planItems.isEmpty()) {
                item { BudgetEmptyCard("此方案目前沒有可顯示活動") }
            } else {
                items(planItems) { item ->
                    BudgetRecommendationCard(item, true, savedIds.contains(item.id), { onPickEvent(item) }, { onToggleSaved(item) })
                }
            }
        }
    }
}

@Composable
fun BudgetEvaluatePage(item: BudgetRecommendation?, items: List<BudgetRecommendation>, budget: Int, periodLabel: String, spent: Int, peopleCount: Int, includeExtra: Boolean, saved: Boolean, onBack: () -> Unit, onPickEvent: (BudgetRecommendation) -> Unit, onToggleSaved: () -> Unit, onAddRecord: () -> Unit) {
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
    val decision = purchaseDecision(usage, after, item.score)
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
                    BudgetMetric("推薦星等", "${item.starRating} / 5", Modifier.weight(1f))
                }
                BudgetInfoBlock("購買建議", "${decision.title}：${decision.reason}")
                if (item.reminderLabel.isNotBlank()) {
                    BudgetInfoBlock("售票提醒", item.reminderLabel)
                }
                BudgetInfoBlock("售票資訊", listOf(
                    "平台：${cleanDisplay(item.source, "待確認")}",
                    "售票時間：${cleanDisplay(item.saleTime, "待公布")}",
                    "活動時間：${cleanDisplay(item.activityTime, "待確認")}",
                    "票價：${recommendationPriceText(item)}"
                ).joinToString("\n"))
                BudgetInfoBlock("評估結果", item.reason)
                if (item.scoreReasons.isNotEmpty()) {
                    BudgetInfoBlock("推薦原因", item.scoreReasons.take(4).joinToString("\n"))
                }
                BudgetInfoBlock("估算明細", "票價 ${formatMoney(item.estimatedSpend)} × $peopleCount 人，納入${periodLabel}預算控管")
                BudgetInfoBlock("風險提醒", item.riskMessage)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = onToggleSaved, modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp)) { Text(if (saved) "取消收藏" else "加入收藏", color = Color(0xFF173BAE)) }
                    Button(onClick = onAddRecord, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF245CFF)), shape = RoundedCornerShape(16.dp)) { Text("加入紀錄") }
                }
                if (item.url.isNotBlank()) {
                    val context = LocalContext.current
                    OutlinedButton(onClick = { openTicketUrl(context, item.url) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, Color(0xFFD8CBBE))) {
                        Text("查看售票頁", color = Color(0xFF173BAE), fontWeight = FontWeight.Black)
                    }
                }
            }
        }
    }
}

@Composable
fun BudgetRecordsPage(budget: Int, periodLabel: String, records: List<TicketRecord>, onBack: () -> Unit, onAdd: () -> Unit, onRemove: (TicketRecord) -> Unit) {
    val spent = records.sumOf { it.total }
    LazyColumn(modifier = Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { BudgetHeader("已購票紀錄", "追蹤${periodLabel}實際花費", true, onBack = onBack) }
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
fun BudgetSavedPage(budget: Int, periodLabel: String, items: List<BudgetRecommendation>, onBack: () -> Unit, onOpenRecommend: () -> Unit, onPickEvent: (BudgetRecommendation) -> Unit, onRemove: (BudgetRecommendation) -> Unit) {
    val total = items.sumOf { it.estimatedSpend }
    LazyColumn(modifier = Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { BudgetHeader("收藏活動分析", "比較想參加的活動", true, onBack = onBack) }
        item { BudgetSummaryCard("收藏預估總額", if (total > budget) "已超出${periodLabel}預算" else "仍在${periodLabel}預算內", formatMoney(total), if (budget > 0) total * 100 / budget else 0, "加入更多活動", "返回主頁", onOpenRecommend, onBack) }
        if (items.isEmpty()) { item { BudgetEmptyCard("尚未收藏活動") } }
        items(items) { item -> BudgetRecommendationCard(item, true, true, { onPickEvent(item) }, { onRemove(item) }) }
    }
}

@Composable
fun BudgetSettingsPage(budget: String, onBudgetChange: (String) -> Unit, periodMonths: Int, onPeriodMonthsChange: (Int) -> Unit, singleLimit: String, onSingleLimitChange: (String) -> Unit, includeExtra: Boolean, onIncludeExtraChange: (Boolean) -> Unit, peopleCount: Int, onPeopleCountChange: (Int) -> Unit, strategy: BudgetStrategy, onStrategyChange: (BudgetStrategy) -> Unit, onBack: () -> Unit, onRefresh: () -> Unit, onLogout: () -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { BudgetHeader("預算設定", "調整推薦條件", true, onBack = onBack) }
        item {
            BudgetPanel {
                BudgetInputField(budget, onBudgetChange, "活動預算", PERIOD_BUDGET_DEFAULT, KeyboardType.Number, { Icon(Icons.Rounded.AttachMoney, null) })
                BudgetPeriodSelector(periodMonths, onPeriodMonthsChange)
                Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0xFFFFF8ED)).clickable { onIncludeExtraChange(!includeExtra) }.padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column { Text("是否包含其他花費", color = Color(0xFF111827), fontWeight = FontWeight.Bold); Text("如手續費等", color = Color(0xFF6B7280), fontSize = 12.sp) }
                    Text(if (includeExtra) "開啟" else "關閉", color = Color(0xFF173BAE), fontWeight = FontWeight.Black)
                }
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(BudgetStrategy.Balanced, BudgetStrategy.Saving, BudgetStrategy.Value).forEach { item ->
                        OutlinedButton(onClick = { onStrategyChange(item) }, border = BorderStroke(1.dp, if (strategy == item) Color(0xFF245CFF) else Color(0xFFD8CBBE))) { Text(item.label, color = Color(0xFF111827)) }
                    }
                }
                Button(onClick = onRefresh, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF245CFF)), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) { Text("更新推薦") }
                Button(
                    onClick = onLogout,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935)),
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("登出帳號", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

fun formatMoney(value: Int): String {
    return "NT$ ${NumberFormat.getNumberInstance(Locale.TAIWAN).format(value.coerceAtLeast(0))}"
}

private fun jsonStringList(array: JSONArray?): List<String> {
    if (array == null) return emptyList()
    val list = mutableListOf<String>()
    for (i in 0 until array.length()) {
        val value = array.optString(i, "").trim()
        if (value.isNotBlank()) list.add(value)
    }
    return list
}

private fun purchaseDecision(usageAfter: Int, after: Int, score: Int): PurchaseDecision {
    return when {
        after <= 0 || usageAfter >= 95 -> PurchaseDecision("建議觀望", "購買後預算壓力過高，建議等候更適合的票價或活動。")
        score >= 50 && usageAfter <= 80 -> PurchaseDecision("推薦購買", "推薦星等高，購買後仍保有足夠預算。")
        score >= 46 && usageAfter <= 90 -> PurchaseDecision("可以考慮", "活動整體條件不錯，購買前仍建議確認票種與總價。")
        score >= 41 && usageAfter <= 95 -> PurchaseDecision("可以考慮", "活動條件尚可，但仍建議先確認實際總價。")
        else -> PurchaseDecision("建議觀望", "目前推薦星等或預算餘裕不足，先收藏觀察會更穩妥。")
    }
}

private fun budgetPeriodApiValue(months: Int): String {
    return if (months.coerceIn(1, 12) == 3) "quarter" else "custom"
}

private fun budgetPeriodLabel(months: Int): String {
    val value = months.coerceIn(1, 12)
    return when (value) {
        1 -> "1 個月"
        3 -> "3 個月"
        6 -> "半年"
        12 -> "一年"
        else -> "$value 個月"
    }
}

private fun isInSelectedPeriod(dateText: String, months: Int): Boolean {
    val match = Regex("""(20\d{2}).?([0-9]{1,2}).?([0-9]{1,2})""").find(dateText)
    if (match != null) {
        val year = match.groupValues[1].toIntOrNull() ?: return false
        val month = match.groupValues[2].toIntOrNull() ?: return false
        val day = match.groupValues[3].toIntOrNull() ?: 1
        return runCatching {
            val target = Calendar.getInstance().apply {
                set(Calendar.YEAR, year)
                set(Calendar.MONTH, month - 1)
                set(Calendar.DAY_OF_MONTH, day)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val start = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val end = Calendar.getInstance().apply {
                add(Calendar.MONTH, months.coerceIn(1, 12))
                set(Calendar.HOUR_OF_DAY, 23)
                set(Calendar.MINUTE, 59)
                set(Calendar.SECOND, 59)
                set(Calendar.MILLISECOND, 999)
            }
            !target.before(start) && !target.after(end)
        }.getOrDefault(false)
    }
    return true
}

private fun cleanDisplay(value: String, fallback: String): String {
    val text = value.trim()
    return if (text.isBlank() || text == "未取得" || text == "Untitled Event") fallback else text
}

private fun recommendationPriceText(item: BudgetRecommendation): String {
    val sourceText = item.priceSource.ifBlank { item.priceText }.trim()
    if (sourceText.isNotBlank() && sourceText != "暫無票價") return sourceText
    return when {
        item.minPrice > 0 && item.maxPrice > item.minPrice -> "${formatMoney(item.minPrice)} - ${formatMoney(item.maxPrice)}"
        item.minPrice > 0 -> formatMoney(item.minPrice)
        item.estimatedSpend > 0 -> formatMoney(item.estimatedSpend)
        else -> "待確認"
    }
}

private fun openTicketUrl(context: android.content.Context, url: String) {
    val target = url.trim()
    if (target.isBlank()) return
    runCatching {
        val normalized = if (target.startsWith("http://") || target.startsWith("https://")) target else "https://$target"
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(normalized)))
    }.onFailure {
        Toast.makeText(context, "無法開啟售票頁", Toast.LENGTH_SHORT).show()
    }
}

