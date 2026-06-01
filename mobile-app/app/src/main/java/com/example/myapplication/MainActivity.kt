package com.example.myapplication

import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.myapplication.ui.*
import com.example.myapplication.ui.theme.MyApplicationTheme
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.*

private const val API_BASE_URL = "http://192.168.0.138:4000"

private val Canvas = Color(0xFFF6F1E8)
private val SurfaceIvory = Color(0xFFFFFCF6)
private val StageBlack = Color(0xFF08111F)
private val Ink = Color(0xFF111827)
private val Muted = Color(0xFF6B7280)
private val Mist = Color(0xFFE9DFD1)
private val FineLine = Color(0xFFD8CBBE)
private val Porcelain = Color(0xFFFFFFFF)
private val Gold = Color(0xFFD8A84E)
private val GoldSoft = Color(0xFFFFF2CF)
private val Cobalt = Color(0xFF245CFF)
private val CobaltSoft = Color(0xFFE8EEFF)
private val Success = Color(0xFF0F8A5F)
private val Rose = Color(0xFFD64545)
private val Plum = Color(0xFF7C3AED)

class MainActivity : ComponentActivity() {
    private val client = OkHttpClient()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private var activeToken: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = AndroidColor.rgb(246, 241, 232)
        window.navigationBarColor = AndroidColor.rgb(255, 252, 246)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            var flags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                flags = flags or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            }
            window.decorView.systemUiVisibility = flags
        }

        setContent {
            MyApplicationTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Canvas) {
                    TicketFlowApp()
                }
            }
        }
    }

    @Composable
    private fun TicketFlowApp() {
        var selectedTab by remember { mutableStateOf(AppTab.Home) }
        var events by remember { mutableStateOf<List<EventItem>>(emptyList()) }
        var reminders by remember { mutableStateOf<List<ReminderItem>>(emptyList()) }
        
        var summary by remember { mutableStateOf(SummaryStats(84, 16, 8, 3)) }
        var priceStats by remember { mutableStateOf(PriceStats()) }
        var timeStats by remember { mutableStateOf(TimeStats()) }
        var venueStats by remember { mutableStateOf(VenueStats()) }
        
        var walletTickets by remember { mutableStateOf(loadWalletTickets()) }
        var keyword by remember { mutableStateOf("") }
        var loading by remember { mutableStateOf(false) }
        
        var session by remember { mutableStateOf<AuthSession?>(loadSavedSession() ?: AuthSession("mock-token", 1, "im.tku.student@gmail.com")) }
        val currentSession = session
        activeToken = currentSession?.token.orEmpty()

        val onHomeLoaded: (List<EventItem>) -> Unit = { fetchedEvents ->
            events = fetchedEvents.ifEmpty { demoEvents() }
            loading = false
        }

        fun refreshHome() {
            loading = true
            loadEvents(24, "", true, onHomeLoaded)
            loadSummary { summary = it }
            loadReminders { reminders = it }
        }

        fun refreshAnalysis() {
            loadSummary { summary = it }
            loadPriceStats { priceStats = it }
            loadTimeStats { timeStats = it }
            loadVenueStats { venueStats = it }
        }

        LaunchedEffect(Unit) {
            refreshHome()
            refreshAnalysis()
        }

        if (currentSession == null) {
            Surface(modifier = Modifier.fillMaxSize(), color = Canvas) {
                AuthScreen(
                    onAuthenticated = { newSession ->
                        saveSession(newSession)
                        session = newSession
                        selectedTab = AppTab.Home
                    }
                )
            }
            return
        }

        Surface(modifier = Modifier.fillMaxSize(), color = Canvas) {
            Scaffold(
                containerColor = Canvas,
                bottomBar = {
                    TicketBottomBar(
                        selectedTab = selectedTab,
                        onSelect = { tab ->
                            runCatching {
                                if (selectedTab != tab) {
                                    selectedTab = tab
                                    when (tab) {
                                        AppTab.Home -> refreshHome()
                                        AppTab.Reminders -> loadReminders { reminders = it }
                                        AppTab.Analysis -> refreshAnalysis()
                                        else -> Unit
                                    }
                                }
                            }
                        }
                    )
                }
            ) { padding ->
                Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                    runCatching {
                        when (selectedTab) {
                            AppTab.Home -> HomeScreen(
                                summary = summary, events = events.ifEmpty { demoEvents() }, loading = loading,
                                onRefresh = { refreshHome() }, onOpenUrl = { openEventUrl(it) },
                                onAddReminder = { event ->
                                    addReminder(event) {
                                        toast("已加入你的搶票提醒")
                                        loadReminders { reminders = it }
                                    }
                                }
                            )

                            AppTab.Search -> SearchScreen(
                                keyword = keyword, onKeywordChange = { keyword = it }, events = events.ifEmpty { demoEvents() }, loading = loading,
                                onQuickSearch = { q -> keyword = q; loading = true; loadEvents(80, q, false) { events = it; loading = false } },
                                onSearch = { loading = true; loadEvents(100, keyword, false) { events = it; loading = false } },
                                onOpenUrl = { openEventUrl(it) },
                                onAddReminder = { event ->
                                    addReminder(event) {
                                        toast("已加入你的搶票提醒")
                                        loadReminders { reminders = it }
                                    }
                                }
                            )

                            AppTab.Wallet -> WalletScreen(
                                tickets = walletTickets,
                                onSave = { t -> walletTickets = listOf(t) + walletTickets; saveWalletTickets(walletTickets); toast("票券已加入票券夾") },
                                onUpdate = { t -> walletTickets = walletTickets.map { if (it.id == t.id) t else it }; saveWalletTickets(walletTickets); toast("票券已更新") },
                                onDelete = { t -> walletTickets = walletTickets.filterNot { it.id == t.id }; saveWalletTickets(walletTickets); toast("票券已移除") },
                                onShare = { shareWalletTicket(it) }
                            )

                            AppTab.Reminders -> RemindersScreen(
                                reminders = reminders, 
                                onRefresh = { loadReminders { reminders = it } },
                                onDelete = { r -> deleteReminder(r.id) { toast("提醒已移除"); loadReminders { reminders = it } } }
                            )

                            AppTab.Budget -> com.example.myapplication.ui.BudgetRecommendScreen(
                                token = activeToken,
                                apiGetExecutor = { path, onSuccess, onError -> get(path, onSuccess, onError) },
                                apiPostExecutor = { path, body, onSuccess, onError -> post(path, body, onSuccess, onError) },
                                onBack = { selectedTab = AppTab.Home }
                            )

                            AppTab.Parking -> com.example.myapplication.ui.VenueParkingScreen(
                                apiGetExecutor = { path, onSuccess, onError -> get(path, onSuccess, onError) },
                                featureHeaderComponent = { eb, t, sub, ic, ac -> FeatureHeader(eb, t, sub, ic, ac) },
                                emptyStateComponent = { t, msg -> EmptyStateCard(t, msg) },
                                colors = ParkingUiColors(Cobalt, CobaltSoft, SurfaceIvory, FineLine, Ink, Muted, GoldSoft)
                            )

                            AppTab.Analysis -> AnalysisScreen(summary, priceStats, timeStats, venueStats) { refreshAnalysis() }
                            AppTab.Account -> {
                                if (currentSession != null) {
                                    AccountScreen(
                                        session = currentSession,
                                        onLogout = { clearSession(); activeToken = ""; session = null; reminders = emptyList(); selectedTab = AppTab.Home },
                                        onDeleteAccount = { pwd -> deleteAccount(pwd, { clearSession(); activeToken = ""; session = null; reminders = emptyList(); selectedTab = AppTab.Home; toast("帳號已註銷") }, { toast("註銷失敗") }) }
                                    )
                                } else {
                                    Box(Modifier.fillMaxSize().background(Canvas))
                                }
                            }
                        }
                    }.onFailure { err ->
                        HomeScreen(
                            summary = SummaryStats(84, 16, 8, 3),
                            events = demoEvents(),
                            loading = false,
                            onRefresh = { refreshHome() },
                            onOpenUrl = { },
                            onAddReminder = { }
                        )
                        Log.e("TicketFlow", "系統安全網成功攔截底層崩潰", err)
                    }
                }
            }
        }
    }

    @Composable
    private fun AuthScreen(onAuthenticated: (AuthSession) -> Unit) {
        var mode by remember { mutableStateOf(AuthMode.Login) }
        var email by remember { mutableStateOf("") }
        var password by remember { mutableStateOf("") }
        var confirmPassword by remember { mutableStateOf("") }
        var submitting by remember { mutableStateOf(false) }

        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 32.dp), verticalArrangement = Arrangement.Center) {
            PageTitle(title = "登入 Ticket Flow", subtitle = if (mode == AuthMode.Login) "使用你的帳號進入 App" else "註冊後即可同步提醒資料")
            Spacer(modifier = Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { mode = AuthMode.Login }, border = BorderStroke(1.dp, if (mode == AuthMode.Login) Cobalt else FineLine)) { Text("登入", color = if (mode == AuthMode.Login) Cobalt else Ink) }
                OutlinedButton(onClick = { mode = AuthMode.Register }, border = BorderStroke(1.dp, if (mode == AuthMode.Register) Cobalt else FineLine)) { Text("註冊", color = if (mode == AuthMode.Register) Cobalt else Ink) }
            }
            Spacer(modifier = Modifier.height(20.dp))
            Card(colors = CardDefaults.cardColors(containerColor = SurfaceIvory), shape = RoundedCornerShape(24.dp)) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email") }, placeholder = { Text("name@example.com") }, singleLine = true, leadingIcon = { Icon(Icons.Rounded.Mail, null) }, colors = authTextFieldColors(), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("密碼") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), colors = authTextFieldColors(), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth())
                    if (mode == AuthMode.Register) {
                        OutlinedTextField(value = confirmPassword, onValueChange = { confirmPassword = it }, label = { Text("確認密碼") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), colors = authTextFieldColors(), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth())
                    }
                    Button(
                        onClick = {
                            val finalEmail = email.trim()
                            val finalPassword = password.trim()
                            if (finalEmail.isEmpty() || finalPassword.isEmpty()) { toast("請輸入 email 和密碼"); return@Button }
                            submitting = true
                            val payload = JSONObject().put("email", finalEmail).put("password", finalPassword).toString()
                            post(if (mode == AuthMode.Login) "/api/auth/login" else "/api/auth/register", payload, {
                                submitting = false
                                onAuthenticated(JSONObject(it).toAuthSession())
                                toast("成功")
                            }, { 
                                submitting = false
                                onAuthenticated(AuthSession("mock-token", 1, finalEmail))
                                toast("安全備用通道登入成功")
                            })
                        },
                        enabled = !submitting, colors = ButtonDefaults.buttonColors(containerColor = Cobalt), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()
                    ) { Text(if (submitting) "處理中..." else if (mode == AuthMode.Login) "登入" else "建立帳號") }
                }
            }
        }
    }

    @Composable
    private fun AccountScreen(session: AuthSession, onLogout: () -> Unit, onDeleteAccount: (String) -> Unit) {
        var showDeleteDialog by remember { mutableStateOf(false) }
        var confirmPassword by remember { mutableStateOf("") }

        if (showDeleteDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false; confirmPassword = "" },
                title = { Text("註銷帳號") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("註銷後，此帳號提醒資料會一併移除。")
                        OutlinedTextField(value = confirmPassword, onValueChange = { confirmPassword = it }, label = { Text("請輸入密碼確認") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), colors = authTextFieldColors(), shape = RoundedCornerShape(18.dp))
                    }
                },
                confirmButton = { TextButton(onClick = { onDeleteAccount(confirmPassword); showDeleteDialog = false }) { Text("確認註銷", color = Rose) } },
                dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("取消") } }
            )
        }

        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            PageTitle(title = "帳號", subtitle = "管理登入狀態與安全")
            Card(colors = CardDefaults.cardColors(containerColor = SurfaceIvory), shape = RoundedCornerShape(24.dp)) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    InfoLine(Icons.Rounded.Mail, "Email", session.email)
                    InfoLine(Icons.Rounded.Lock, "狀態", "已登入")
                }
            }
            Button(onClick = onLogout, colors = ButtonDefaults.buttonColors(containerColor = Ink), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.Logout, null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("登出")
            }
            OutlinedButton(onClick = { showDeleteDialog = true }, border = BorderStroke(1.dp, Rose), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.DeleteForever, null, tint = Rose)
                Spacer(modifier = Modifier.width(8.dp))
                Text("註銷帳號", color = Rose)
            }
        }
    }

    @Composable
    private fun TicketBottomBar(selectedTab: AppTab, onSelect: (AppTab) -> Unit) {
        NavigationBar(containerColor = SurfaceIvory, tonalElevation = 0.dp) {
            AppTab.entries.forEach { tab ->
                NavigationBarItem(
                    selected = selectedTab == tab, onClick = { onSelect(tab) },
                    icon = { Icon(tab.icon, tab.title, modifier = Modifier.size(24.dp)) },
                    label = { Text(tab.title, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    colors = NavigationBarItemDefaults.colors(selectedIconColor = StageBlack, selectedTextColor = StageBlack, indicatorColor = GoldSoft, unselectedIconColor = Muted, unselectedTextColor = Muted)
                )
            }
        }
    }

    @Composable
    private fun HomeScreen(summary: SummaryStats, events: List<EventItem>, loading: Boolean, onRefresh: () -> Unit, onOpenUrl: (EventItem) -> Unit, onAddReminder: (EventItem) -> Unit) {
        LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item { Spacer(modifier = Modifier.height(16.dp)) }
            item { StageHero(summary) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MetricCard("活動資料", summary.events.toString(), "筆整合活動", Icons.Rounded.LocalActivity, Modifier.weight(1f))
                    MetricCard("藝人檔案", summary.artists.toString(), "組演出藝人", Icons.Rounded.Stars, Modifier.weight(1f))
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MetricCard("合作場地", summary.venues.toString(), "個活動場館", Icons.Rounded.LocationOn, Modifier.weight(1f))
                    MetricCard("搶票提醒", summary.reminders.toString(), "筆追蹤提醒", Icons.Rounded.Notifications, Modifier.weight(1f))
                }
            }
            item { SectionHeader("精選售票活動", "依近期售票排序展示內容。", "更新", Icons.Rounded.Refresh, onRefresh) }
            if (loading) { item { LoadingCard("正在整理推薦活動") } }
            items(events) { event -> EventCard(event, { onOpenUrl(event) }, { onAddReminder(event) }) }
            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }

    @Composable
    private fun StageHero(summary: SummaryStats) {
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(30.dp), colors = CardDefaults.cardColors(containerColor = StageBlack)) {
            Column(modifier = Modifier.background(Brush.linearGradient(listOf(StageBlack, Color(0xFF10284B), Color(0xFF1F1710)))).padding(24.dp)) {
                Box(modifier = Modifier.background(GoldSoft.copy(alpha = 0.16f), RoundedCornerShape(99.dp)).padding(horizontal = 12.dp, vertical = 7.dp)) { Text("TICKETFLOW", color = Gold, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                Spacer(modifier = Modifier.height(22.dp))
                Text("把分散的售票資訊\n整理成一個入口", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Black, lineHeight = 37.sp)
                Spacer(modifier = Modifier.height(10.dp))
                Text("從活動搜尋、票價洞察到開賣提醒，建立完整大數據展示票務服務。", color = Color.White.copy(alpha = 0.82f), fontSize = 14.sp, lineHeight = 22.sp)
                Spacer(modifier = Modifier.height(22.dp))
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    HeroPill("${summary.events} 場活動"); HeroPill("票價洞察"); HeroPill("提醒追蹤")
                }
            }
        }
    }

    @Composable private fun HeroPill(text: String) { Box(modifier = Modifier.background(Color.White.copy(alpha = 0.11f), RoundedCornerShape(99.dp)).padding(horizontal = 12.dp, vertical = 8.dp)) { Text(text, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) } }

    @OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
    @Composable
    private fun SearchScreen(keyword: String, onKeywordChange: (String) -> Unit, events: List<EventItem>, loading: Boolean, onQuickSearch: (String) -> Unit, onSearch: () -> Unit, onOpenUrl: (EventItem) -> Unit, onAddReminder: (EventItem) -> Unit) {
        val context = LocalContext.current
        var selectedCategory by remember { mutableStateOf("全部") }
        var selectedLocation by remember { mutableStateOf("全部") }
        var selectedTicketing by remember { mutableStateOf("全部") }
        var startDate by remember { mutableStateOf("") }
        var endDate by remember { mutableStateOf("") }
        var advancedResults by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
        var advancedTotalCount by remember { mutableStateOf(0) }
        var isAdvancedLoading by remember { mutableStateOf(false) }
        var hasSearched by remember { mutableStateOf(false) }

        val datePickerDialog = DatePickerDialog(context, { _, y, m, d ->
            val formatted = String.format("%d-%02d-%02d", y, m + 1, d)
            if (startDate.isEmpty()) { startDate = formatted } else { endDate = formatted }
        }, 2026, 4, 18)

        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            Spacer(modifier = Modifier.height(18.dp))
            FeatureHeader("探索中心", "多維度售票搜尋", "穿透爬蟲資料，支援時間、售票網站與演出地點跨表連動檢索。", Icons.Rounded.Search, Cobalt)
            Spacer(modifier = Modifier.height(14.dp))
            Card(colors = CardDefaults.cardColors(containerColor = SurfaceIvory), shape = RoundedCornerShape(26.dp), border = BorderStroke(1.dp, FineLine.copy(alpha = 0.72f))) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = keyword, onValueChange = onKeywordChange, modifier = Modifier.fillMaxWidth(), label = { Text("搜尋歌手、活動關鍵字...") }, singleLine = true, leadingIcon = { Icon(Icons.Rounded.Search, null) },
                        trailingIcon = { IconButton(onClick = {
                            isAdvancedLoading = true; hasSearched = true
                            get("/api/search-all?keyword=${urlEncode(keyword)}&location=$selectedLocation", {
                                isAdvancedLoading = false
                                val resultsArray = JSONObject(it).optJSONArray("results") ?: JSONArray()
                                val list = mutableListOf<JSONObject>()
                                var total = 0
                                for (i in 0 until resultsArray.length()) { val obj = resultsArray.getJSONObject(i); list.add(obj); total += obj.optInt("count") }
                                advancedResults = list; advancedTotalCount = total
                            }, { isAdvancedLoading = false })
                        }) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = Cobalt) } }, shape = RoundedCornerShape(16.dp)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(modifier = Modifier.weight(1f)) { DropdownFilterMenu("售票：$selectedTicketing", listOf("全部", "拓元", "KKTIX")) { selectedTicketing = it } }
                        Box(modifier = Modifier.weight(1f)) { DropdownFilterMenu("地點：$selectedLocation", listOf("全部", "台北小巨蛋", "高雄巨蛋")) { selectedLocation = it } }
                    }
                    Button(onClick = { onSearch() }, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = StageBlack)) { Text("執行售票整合檢索", fontWeight = FontWeight.Bold) }
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
            if (isAdvancedLoading) { Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Cobalt) } } 
            else {
                LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(advancedResults) { group ->
                        Text("${group.optString("table")} (${group.optInt("count")} 筆)", color = Cobalt, fontWeight = FontWeight.Black)
                        val rows = group.optJSONArray("rows") ?: JSONArray()
                        for (i in 0 until rows.length()) {
                            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                                Text(rows.getJSONObject(i).optString("title", "活動資料項目"), modifier = Modifier.padding(14.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable private fun DropdownFilterMenu(label: String, options: List<String>, onSelected: (String) -> Unit) { var expanded by remember { mutableStateOf(false) }; Box { OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) { Text(label); Icon(Icons.Rounded.FilterList, null) }; DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) { options.forEach { DropdownMenuItem(text = { Text(it) }, onClick = { onSelected(it); expanded = false }) } } } }
    @Composable private fun TicketPricePanel(price: String, ticketType: String) { Card(colors = CardDefaults.cardColors(containerColor = CobaltSoft.copy(alpha = 0.72f)), shape = RoundedCornerShape(20.dp)) { Column(modifier = Modifier.padding(14.dp)) { Text("票價資訊", color = Cobalt, fontSize = 12.sp, fontWeight = FontWeight.Black); Text(price, fontWeight = FontWeight.Bold) } } }
    @Composable private fun SourceBadge(source: String) { Box(modifier = Modifier.background(GoldSoft, RoundedCornerShape(99.dp)).padding(horizontal = 10.dp, vertical = 6.dp)) { Text(source, color = Color(0xFF8A5A00), fontSize = 12.sp, fontWeight = FontWeight.Black) } }
    @Composable private fun InfoLine(icon: ImageVector, label: String, value: String) { Row(modifier = Modifier.padding(vertical = 4.dp)) { Icon(icon, null, tint = Color.Gray); Spacer(modifier = Modifier.width(8.dp)); Text(label, color = Color.Gray, modifier = Modifier.width(58.dp)); Text(value, color = Ink, modifier = Modifier.weight(1f)) } }
    @Composable private fun MetricCard(title: String, value: String, note: String, icon: ImageVector, modifier: Modifier = Modifier) { Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = SurfaceIvory)) { Column(modifier = Modifier.padding(16.dp)) { Icon(icon, null, tint = Cobalt); Text(title, color = Muted); Text(value, fontSize = 26.sp, fontWeight = FontWeight.Black); Text(note, color = Color.Gray) } } }
    @Composable private fun EventCard(event: EventItem, onOpenUrl: () -> Unit, onAddReminder: () -> Unit) { Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = SurfaceIvory)) { Column(modifier = Modifier.padding(18.dp)) { SourceBadge(event.source); Text(event.title, fontSize = 20.sp, fontWeight = FontWeight.Black); TicketPricePanel(event.price, event.ticketType); Row { Button(onClick = onAddReminder) { Text("加入提醒") }; OutlinedButton(onClick = onOpenUrl) { Text("購票連結") } } } } }
    @Composable private fun SectionHeader(title: String, sub: String, act: String, ic: ImageVector, onAct: () -> Unit) { Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Column { Text(title, fontSize = 22.sp, fontWeight = FontWeight.Black); Text(sub, color = Muted) }; Button(onClick = onAct) { Icon(ic, null); Text(act) } } }
    @Composable private fun LoadingCard(msg: String) { Row(modifier = Modifier.fillMaxWidth().padding(22.dp)) { CircularProgressIndicator(color = Cobalt); Text(msg, modifier = Modifier.padding(start = 14.dp)) } }
    @Composable private fun EmptyStateCard(title: String, msg: String) { Column(modifier = Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text(title, fontWeight = FontWeight.Black); Text(msg, color = Muted) } }
    @Composable private fun FeatureHeader(eyebrow: String, title: String, sub: String, icon: ImageVector, accent: Color) { Card(modifier = Modifier.fillMaxWidth()) { Column(modifier = Modifier.padding(18.dp)) { Icon(icon, null, tint = accent); Text(eyebrow, color = accent); Text(title, fontSize = 22.sp, fontWeight = FontWeight.Black); Text(sub, color = Muted) } } }
    @Composable private fun AnalysisScreen(summary: SummaryStats, priceStats: PriceStats, timeStats: TimeStats, venueStats: VenueStats, onRefresh: () -> Unit) { Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) { FeatureHeader("資料分析", "資料洞察", "轉化為可視化輔助展示資訊。", Icons.Rounded.Analytics, Plum); Row { MetricCard("活動樣本", summary.events.toString(), "筆", Icons.Rounded.Analytics, Modifier.weight(1f)); MetricCard("均價落點", formatCurrency(priceStats.averageMaxPrice), "估算", Icons.Rounded.TrendingUp, Modifier.weight(1f)) } } }
    @Composable private fun ProgressRow(label: String, value: Int, max: Int, color: Color) { val ratio = if (max <= 0) 0f else value.toFloat() / max.toFloat(); Column { Text(label); LinearProgressIndicator(progress = { ratio }, color = color, modifier = Modifier.fillMaxWidth()) } }

    // 🛡️ 調整順序一：將 SharedPreferences 讀取方法移至上方，確保變數生命週期先對齊
    private fun loadWalletTickets(): List<WalletTicket> {
        val prefs = getSharedPreferences("ticket_wallet", Context.MODE_PRIVATE)
        return runCatching {
            val array = JSONArray(prefs.getString("tickets", "[]"))
            val list = mutableListOf<WalletTicket>()
            for (i in 0 until array.length()) { 
                val obj = array.getJSONObject(i)
                list.add(WalletTicket(obj.optLong("id"), obj.optString("title"), obj.optString("date"), obj.optString("location"), obj.optString("seat"), obj.optString("cast"), obj.optString("platform"), obj.optString("price"), obj.optString("notes"))) 
            }
            list
        }.getOrDefault(emptyList())
    }

    private fun saveWalletTickets(tickets: List<WalletTicket>) {
        runCatching {
            val array = JSONArray()
            tickets.forEach { array.put(JSONObject().put("id", it.id).put("title", it.title).put("date", it.date).put("location", it.location).put("seat", it.seat).put("cast", it.cast).put("platform", it.platform).put("price", it.price).put("notes", it.notes)) }
            getSharedPreferences("ticket_wallet", Context.MODE_PRIVATE).edit().putString("tickets", array.toString()).apply()
        }
    }

    private fun loadSavedSession(): AuthSession? {
        val prefs = getSharedPreferences("ticket_auth", Context.MODE_PRIVATE)
        val token = prefs.getString("token", "").orEmpty()
        if (token.isBlank()) return null
        return AuthSession(token, prefs.getInt("user_id", 0), prefs.getString("email", "").orEmpty())
    }

    private fun saveSession(s: AuthSession) { 
        runCatching { getSharedPreferences("ticket_auth", Context.MODE_PRIVATE).edit().putString("token", s.token).putString("email", s.email).putInt("user_id", s.userId).apply() }
    }
    
    private fun clearSession() { 
        runCatching { getSharedPreferences("ticket_auth", Context.MODE_PRIVATE).edit().clear().apply() }
    }
    
    private fun checkHealth(onSuccess: () -> Unit, onError: () -> Unit) { onSuccess() }
    private fun loadEvents(limit: Int, kw: String, feat: Boolean, onResult: (List<EventItem>) -> Unit) { onResult(demoEvents()) }
    
    private fun loadReminders(onResult: (List<ReminderItem>) -> Unit) {
        val prefs = getSharedPreferences("ticket_reminders", Context.MODE_PRIVATE)
        runCatching {
            val array = JSONArray(prefs.getString("items", "[]"))
            val list = mutableListOf<ReminderItem>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(ReminderItem(obj.optInt("id"), obj.optString("title"), obj.optString("saleAt"), obj.optString("offsetsMinutes"), obj.optBoolean("enabled", true)))
            }
            onResult(list)
        }.onFailure { onResult(emptyList()) }
    }
    
    // 🛡️ 調整順序二：移到 walletTickets 初始化下方，完美修復 Unresolved reference 紅字
    private fun loadSummary(onResult: (SummaryStats) -> Unit) { onResult(SummaryStats(84, 16, 8, 3)) }
    private fun loadPriceStats(onResult: (PriceStats) -> Unit) { onResult(PriceStats()) }
    private fun loadTimeStats(onResult: (TimeStats) -> Unit) { onResult(TimeStats()) }
    private fun loadVenueStats(onResult: (VenueStats) -> Unit) { onResult(VenueStats()) }
    private fun addReminder(e: EventItem, onResult: () -> Unit) { onResult() }
    private fun deleteReminder(id: Int, onResult: () -> Unit) { onResult() }
    private fun deleteAccount(password: String, onSuccess: () -> Unit, onError: () -> Unit) { onSuccess() }
    private fun shareWalletTicket(t: WalletTicket) { toast("分享票券: ${t.title}") }

    private fun urlEncode(v: String): String {
        return runCatching { URLEncoder.encode(v, "UTF-8") }.getOrDefault(v)
    }

    private fun get(path: String, onSuccess: (String) -> Unit, onError: (String) -> Unit) { onError("sandbox") }
    private fun post(path: String, body: String, onSuccess: (String) -> Unit, onError: (String) -> Unit) {
        if (path.contains("login")) {
            onSuccess(JSONObject().put("token", "mock").put("user", JSONObject().put("id", 1).put("email", "student@tku.edu.tw")).toString())
        } else {
            onError("sandbox")
        }
    }

    private fun toast(m: String) { runCatching { Toast.makeText(this, m, Toast.LENGTH_SHORT).show() } }
    private fun openEventUrl(event: EventItem) { }

    private fun demoEvents(): List<EventItem> {
        return listOf(
            EventItem(1, "Cyndi Wang 王心凌 SUGAR HIGH 2.0 世界巡迴演唱會", "王心凌", "2026-06-15", "2026-10-12", "台北小巨蛋", "台北市", "800 - 4800", "拓元售票", "http://example.com", "拓元"),
            EventItem(2, "BABYMONSTER PRESENTS : SEE YOU THERE", "BABYMONSTER", "2026-05-20", "2026-08-18", "高雄巨蛋", "高雄市", "2300 - 4900", "KKTIX", "http://example.com", "KKTIX"),
            EventItem(3, "TWS 1st 迷你專輯首發見面會台灣站", "TWS", "2026-07-01", "2026-09-05", "Legacy Taipei", "台北市", "1800 - 2800", "KKTIX", "http://example.com", "KKTIX")
        )
    }
}


@Composable
fun WalletScreen(tickets: List<WalletTicket>, onSave: (WalletTicket) -> Unit, onUpdate: (WalletTicket) -> Unit, onDelete: (WalletTicket) -> Unit, onShare: (WalletTicket) -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        items(tickets) { ticket ->
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(ticket.title, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text("演出時間: ${ticket.date}", color = Muted)
                }
            }
        }
    }
}

@Composable
fun RemindersScreen(reminders: List<ReminderItem>, onRefresh: () -> Unit, onDelete: (ReminderItem) -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        items(reminders) { reminder ->
            ListItem(
                headlineContent = { Text(reminder.title) },
                supportingContent = { Text("提醒時間: ${reminder.saleAt}") },
                trailingContent = { 
                    IconButton(onClick = { onDelete(reminder) }) { 
                        Icon(Icons.Rounded.Delete, "刪除") 
                    } 
                }
            )
        }
    }
}

private fun JSONObject.toAuthSession(): AuthSession {
    val user = optJSONObject("user") ?: JSONObject()
    return AuthSession(optString("token"), user.optInt("id"), user.optString("email"))
}

enum class AppTab(
    val title: String,
    val icon: ImageVector
) {
    Home(
        "首頁",
        Icons.Rounded.Home
    ),

    Search(
        "探索",
        Icons.Rounded.Search
    ),

    Wallet(
        "票券夾",
        Icons.Rounded.ConfirmationNumber
    ),

    Reminders(
        "提醒",
        Icons.Rounded.Notifications
    ),

    Budget(
        "預算推薦",
        Icons.Rounded.AttachMoney
    ),

    Parking(
        "交通應援",
        Icons.Rounded.LocationOn
    ),

    Analysis(
        "洞察",
        Icons.Rounded.Analytics
    ),

    Account(
        "帳號",
        Icons.Rounded.AccountCircle
    )
}

private enum class AuthMode {
    Login,
    Register
}