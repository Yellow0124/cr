package com.example.myapplication

import android.Manifest
import android.app.AlarmManager
import android.app.DatePickerDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import coil.compose.AsyncImage
import com.example.myapplication.ui.BudgetRecommendScreen
import com.example.myapplication.ui.theme.MyApplicationTheme
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.URLEncoder
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlinx.coroutines.delay
import kotlin.random.Random

private val AppBg = Color(0xFFF6F1E8)
private val CardBg = Color(0xFFFFFCF6)
private val Ink = Color(0xFF111827)
private val Muted = Color(0xFF6B7280)
private val FineLine = Color(0xFFD8CBBE)
private val Dark = Color(0xFF08111F)
private val Blue = Color(0xFF245CFF)
private val BlueSoft = Color(0xFFE4EBFF)
private val GoldSoft = Color(0xFFFFE8A3)
private val Purple = Color(0xFF7C3AED)
private const val API_BASE_URL = "http://172.20.10.3:4000"

class MainActivity : ComponentActivity() {
    private val httpClient = OkHttpClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createReminderChannel()
        setContent { MyApplicationTheme { Surface(Modifier.fillMaxSize(), color = AppBg) { AppRoot() } } }
    }

    @Composable
    private fun AppRoot() {
        val hasSession = remember { hasStoredAuthToken() }
        var tab by remember { mutableStateOf(AppTab.Home) }
        var page by remember { mutableStateOf(if (hasSession) "main" else "login") }
        var selectedEvent by remember { mutableStateOf<EventItem?>(null) }
        var reminderEvent by remember { mutableStateOf<EventItem?>(null) }
        var loggedOut by remember { mutableStateOf(!hasSession) }
        var tickets by remember { mutableStateOf(loadWalletTickets()) }
        var reminders by remember { mutableStateOf(loadLocalReminders()) }
        var dbEventTotal by remember { mutableStateOf(0) }
        val allEvents = remember { emptyList<EventItem>() }
        var randomEvents by remember { mutableStateOf(emptyList<EventItem>()) }
        LaunchedEffect(Unit) {
            refreshEventCount { total -> dbEventTotal = total }
            fetchRandomEvents { items -> randomEvents = items }
        }
        val logout = {
            performLogout()
            page = "login"
            tab = AppTab.Home
            selectedEvent = null
            reminderEvent = null
            loggedOut = true
            randomEvents = emptyList()
            toast(T.logoutDone)
        }
        val onAuthenticated = {
            loggedOut = false
            page = "main"
            tab = AppTab.Home
            selectedEvent = null
            reminderEvent = null
            fetchRandomEvents { items -> randomEvents = items }
        }

        val openDetail: (EventItem) -> Unit = { selectedEvent = it; page = "detail" }
        val openReminder: (EventItem) -> Unit = { reminderEvent = it; page = "reminder" }
        val saveReminder: (EventItem, Int) -> Unit = { event, minutes ->
            scheduleReminder(event, minutes)
            reminders = upsertReminder(reminders, event, minutes)
            saveLocalReminders(reminders)
            page = "main"
            tab = AppTab.Reminders
        }
        val goMain = { page = "main" }

        Scaffold(
            containerColor = AppBg,
            bottomBar = {
                if (!loggedOut && page != "login") {
                    BottomTabs(tab) { page = "main"; tab = it }
                }
            }
        ) { pad ->
            Box(Modifier.fillMaxSize().padding(pad)) {
                when (page) {
                    "login" -> LoginScreen(onAuthenticated)
                    "detail" -> selectedEvent?.let { EventDetailScreen(it, goMain, openReminder, ::openUrl) }
                    "reminder" -> reminderEvent?.let { ReminderSetupScreen(it, goMain, saveReminder) }
                    else -> if (loggedOut) {
                        LoginScreen(onAuthenticated)
                    } else when (tab) {
                        AppTab.Home -> HomeScreen(randomEvents, tickets, reminders, allEvents, dbEventTotal, { fetchRandomEvents { items -> randomEvents = items }; refreshEventCount { total -> dbEventTotal = total } }, { tab = AppTab.Wallet }, openDetail, openReminder)
                        AppTab.Search -> SearchScreen(allEvents, { refreshEventCount { total -> dbEventTotal = total } }, openDetail, openReminder)
                        AppTab.Wallet -> WalletScreen(
                            tickets = tickets,
                            onSave = { ticket ->
                                tickets = listOf(ticket) + tickets
                                saveWalletTickets(tickets)
                            },
                            onUpdate = { ticket ->
                                tickets = tickets.map { if (it.id == ticket.id) ticket else it }
                                saveWalletTickets(tickets)
                            },
                            onDelete = { ticket ->
                                tickets = tickets.filterNot { it.id == ticket.id }
                                saveWalletTickets(tickets)
                            }
                        )
                        AppTab.Reminders -> ReminderScreen(reminders, { event, minutes -> saveReminder(event, minutes) }, openReminder)
                        AppTab.More -> MoreScreen({ tab = it }, logout)
                        AppTab.SeatMap -> SeatMapScreen({ tab = AppTab.More }, ::openUrl)
                        AppTab.Budget -> BudgetRecommendScreen("", ::apiGet, ::apiPost, logout) { tab = AppTab.More }
                        AppTab.Parking -> TrafficScreen({ tab = AppTab.More }) { tab = AppTab.Food }
                        AppTab.Analysis -> AnalysisScreen(tickets) { tab = AppTab.More }
                        AppTab.Food -> FoodRecommendScreen { tab = AppTab.More }
                        AppTab.Venues -> VenueGuideScreen { tab = AppTab.More }
                        AppTab.Account -> PlaceholderScreen(T.accountSettings, Icons.Rounded.AccountCircle) { tab = AppTab.More }
                    }
                }
            }
        }
    }

    @Composable
    private fun BottomTabs(selected: AppTab, onSelect: (AppTab) -> Unit) {
        val core = listOf(AppTab.Home, AppTab.Search, AppTab.Wallet, AppTab.Reminders, AppTab.More)
        val moreTabs = setOf(AppTab.SeatMap, AppTab.Budget, AppTab.Parking, AppTab.Analysis, AppTab.Food, AppTab.Venues, AppTab.Account)
        NavigationBar(containerColor = CardBg, tonalElevation = 0.dp) {
            core.forEach { tab ->
                NavigationBarItem(
                    selected = selected == tab || (tab == AppTab.More && selected in moreTabs),
                    onClick = { onSelect(tab) },
                    icon = { Icon(tab.icon, tab.title, Modifier.size(24.dp)) },
                    label = { Text(tab.title, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Dark,
                        selectedTextColor = Dark,
                        indicatorColor = GoldSoft,
                        unselectedIconColor = Muted,
                        unselectedTextColor = Muted
                    )
                )
            }
        }
    }

    @Composable
    private fun HomeScreen(
        events: List<EventItem>,
        tickets: List<WalletTicket>,
        reminders: List<LocalReminder>,
        allEvents: List<EventItem>,
        dbEventTotal: Int,
        onRefresh: () -> Unit,
        openWallet: () -> Unit,
        openDetail: (EventItem) -> Unit,
        openReminder: (EventItem) -> Unit
    ) {
        val stats = remember(tickets, reminders, allEvents, dbEventTotal) { homeStats(tickets, reminders, allEvents, dbEventTotal) }
        LazyColumn(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(bottom = 92.dp)) {
            item { Header(T.appTitle, T.randomHomeSub, Icons.Rounded.LocalActivity, Blue) }
            item { HomeStatsRow(stats) }
            item { SummaryCard(tickets.sumOf { parsePrice(it.price) }) }
            item { SectionTitle(T.recentTickets) }
            if (tickets.isEmpty()) item { EmptyCard(T.noTickets, T.tapAddTicket, openWallet) } else items(tickets.take(3)) { TicketCard(it) {} }
            item {
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    SectionTitle(T.randomEvents)
                    Button(onClick = onRefresh, shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = Dark)) {
                        Icon(Icons.Rounded.Refresh, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(T.refresh, fontWeight = FontWeight.Bold)
                    }
                }
            }
            items(events) { EventCard(it, openDetail, openReminder) }
        }
    }

    @Composable
    private fun HomeStatsRow(stats: SummaryStats) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HomeStatBox(T.homeStatEvents, stats.events, Icons.Rounded.LocalActivity, Blue, Modifier.weight(1f))
            HomeStatBox(T.homeStatArtists, stats.artists, Icons.Rounded.Groups, Purple, Modifier.weight(1f))
            HomeStatBox(T.homeStatVenues, stats.venues, Icons.Rounded.LocationOn, Color(0xFF0F766E), Modifier.weight(1f))
            HomeStatBox(T.homeStatReminders, stats.reminders, Icons.Rounded.Notifications, Color(0xFFDB2777), Modifier.weight(1f))
        }
    }

    @Composable
    private fun HomeStatBox(label: String, count: Int, icon: ImageVector, accent: Color, modifier: Modifier = Modifier) {
        Card(modifier, colors = CardDefaults.cardColors(CardBg), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, FineLine)) {
            Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Icon(icon, null, tint = accent, modifier = Modifier.size(20.dp))
                Text(count.toString(), color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Black)
                Text(label, color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }

    @Composable
    private fun SearchScreen(seedEvents: List<EventItem>, onSynced: () -> Unit, openDetail: (EventItem) -> Unit, openReminder: (EventItem) -> Unit) {
        var keyword by remember { mutableStateOf("") }
        var selectedSources by remember { mutableStateOf<Set<String>>(emptySet()) }
        var selectedTime by remember { mutableStateOf(T.all) }
        var selectedPrice by remember { mutableStateOf(T.all) }
        var events by remember { mutableStateOf<List<EventItem>>(emptyList()) }
        var loading by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf("") }
        val sources = remember(events, seedEvents) { (events.ifEmpty { seedEvents }).map { it.source }.filter { it.isNotBlank() }.distinct().take(8) }
        val timeOptions = listOf(T.all, T.thisMonth, T.nextMonth, T.thisYear)
        val priceOptions = listOf(T.all, T.priceUnder1000, T.price1000To3000, T.priceOver3000)
        LaunchedEffect(keyword, selectedSources, selectedTime, selectedPrice) {
            loading = true
            error = ""
            apiGet(discoveryQuery(keyword, selectedSources.firstOrNull().orEmpty(), selectedTime, selectedPrice), { body ->
                val parsed = runCatching { parseEventsResponse(body) }.getOrDefault(emptyList())
                events = parsed
                loading = false
                error = if (parsed.isEmpty()) T.noData else ""
                onSynced()
            }, { message ->
                events = emptyList()
                loading = false
                error = message.ifBlank { T.dataConnectionFailed }
            })
        }
        LazyColumn(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 92.dp)) {
            item { Header(T.explore, T.searchSub, Icons.Rounded.Search, Blue) }
            item { OutlinedTextField(keyword, { keyword = it }, Modifier.fillMaxWidth(), label = { Text(T.keyword) }, singleLine = true) }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(T.ticketSite, color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        sources.forEach { source ->
                            FilterChip(
                                selected = selectedSources.contains(source),
                                onClick = { selectedSources = if (selectedSources.contains(source)) selectedSources - source else selectedSources + source },
                                label = { Text(source, maxLines = 1) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = BlueSoft, selectedLabelColor = Blue)
                            )
                        }
                    }
                    FilterChipRow(T.eventTime, timeOptions, selectedTime, { selectedTime = it }, Modifier.fillMaxWidth())
                    FilterChipRow(T.priceRange, priceOptions, selectedPrice, { selectedPrice = it }, Modifier.fillMaxWidth())
                    Text("${T.searchResults} ${events.size} ${T.countUnit}", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
            if (loading) {
                item { EmptyCard(T.loading, T.loading) }
            } else if (error.isNotBlank()) {
                item { EmptyCard(T.dataConnectionFailed, error) }
            } else {
                items(events) { EventCard(it, openDetail, openReminder) }
            }
        }
    }

    @Composable
    private fun EventCard(event: EventItem, openDetail: (EventItem) -> Unit, openReminder: (EventItem) -> Unit) {
        Card(Modifier.fillMaxWidth().clickable { openDetail(event) }, colors = CardDefaults.cardColors(CardBg), shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, FineLine)) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(event.title, color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("${event.artist} / ${event.venue}", color = Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(event.price, color = Blue, fontWeight = FontWeight.Bold)
                }
                IconButton(onClick = { openReminder(event) }) { Icon(Icons.Rounded.NotificationsActive, T.addReminder, tint = Purple) }
            }
        }
    }

    @Composable
    private fun EventDetailScreen(event: EventItem, onBack: () -> Unit, openReminder: (EventItem) -> Unit, openUrl: (String) -> Unit) {
        var artistProfile by remember(event.id) { mutableStateOf<ArtistProfile?>(null) }
        var artistNews by remember(event.id) { mutableStateOf<List<ArtistNews>>(emptyList()) }
        var artistCategories by remember(event.id) { mutableStateOf<List<ArtistCategory>>(emptyList()) }
        LaunchedEffect(event.id) {
            fetchArtistProfile(event.id) { profile, news, categories ->
                artistProfile = profile
                artistNews = news
                artistCategories = categories
            }
        }
        LazyColumn(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 92.dp)) {
            item { BackTitle(T.eventDetail, onBack) }
            item { Header(event.title, event.venue, Icons.Rounded.LocalActivity, Blue) }
            item { DetailBlock(T.basicInfo, listOf(T.ticketSite to event.source, T.artist to event.artist, T.place to event.venue, T.saleTime to event.saleTime, T.eventTime to event.activityTime, T.price to event.price)) }
            artistProfile?.takeIf { it.wikiIntro.isNotBlank() || it.wikiUrl.isNotBlank() || artistCategories.isNotEmpty() }?.let { profile ->
                item {
                    DetailBlock(
                        T.artistInfo,
                        listOf(
                            T.artist to profile.name,
                            T.artistIntro to profile.wikiIntro,
                            T.genre to artistCategories.map { it.genre }.filter { it.isNotBlank() }.distinct().joinToString("、"),
                            T.language to artistCategories.map { it.language }.filter { it.isNotBlank() }.distinct().joinToString("、"),
                            T.wikiUrl to profile.wikiUrl
                        )
                    )
                }
            }
            if (artistNews.isNotEmpty()) {
                item { ArtistNewsBlock(artistNews, openUrl) }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { openReminder(event) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = Purple)) {
                        Icon(Icons.Rounded.NotificationsActive, null); Spacer(Modifier.width(6.dp)); Text(T.addReminder)
                    }
                    OutlinedButton(onClick = { openUrl(event.url) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) {
                        Icon(Icons.Rounded.OpenInNew, null); Spacer(Modifier.width(6.dp)); Text(T.officialLink)
                    }
                }
            }
        }
    }

    @Composable
    private fun ReminderSetupScreen(event: EventItem, onBack: () -> Unit, onSave: (EventItem, Int) -> Unit) {
        var daysBefore by remember { mutableStateOf(0) }
        var hoursBefore by remember { mutableStateOf(1) }
        var minutesBefore by remember { mutableStateOf(0) }
        val selected = daysBefore * 1440 + hoursBefore * 60 + minutesBefore
        val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) onSave(event, selected) else toast(T.notificationDenied)
        }
        LazyColumn(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 92.dp)) {
            item { BackTitle(T.reminderSetting, onBack) }
            item { Header(event.title, event.saleTime, Icons.Rounded.NotificationsActive, Purple) }
            item {
                Card(colors = CardDefaults.cardColors(CardBg), shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, FineLine)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(T.chooseReminderTime, color = Ink, fontWeight = FontWeight.Black)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            ReminderDropDown(T.daysBefore, daysBefore, (0..7).toList(), { daysBefore = it }, Modifier.weight(1f))
                            ReminderDropDown(T.hoursBefore, hoursBefore, (0..23).toList(), { hoursBefore = it }, Modifier.weight(1f))
                            ReminderDropDown(T.minutesBefore, minutesBefore, (0..59).toList(), { minutesBefore = it }, Modifier.weight(1f))
                        }
                        Text(reminderLabel(selected), color = Purple, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            item {
                Button(
                    onClick = {
                        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            onSave(event, selected)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Blue)
                ) { Text(T.saveReminder, fontWeight = FontWeight.Bold) }
            }
        }
    }

    @Composable
    private fun DetailBlock(title: String, rows: List<Pair<String, String>>) {
        Card(colors = CardDefaults.cardColors(CardBg), shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, FineLine)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Black)
                rows.forEach { (label, value) ->
                    Column { Text(label, color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold); Text(value.ifBlank { "-" }, color = Ink) }
                }
            }
        }
    }

    @Composable
    private fun ArtistNewsBlock(news: List<ArtistNews>, openUrl: (String) -> Unit) {
        Card(colors = CardDefaults.cardColors(CardBg), shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, FineLine)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(T.artistNews, color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Black)
                news.forEach { item ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(enabled = item.url.isNotBlank()) { openUrl(item.url) }.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(item.title, color = Ink, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            if (item.publishedAt.isNotBlank()) Text(item.publishedAt, color = Muted, fontSize = 12.sp)
                        }
                        Icon(Icons.Rounded.OpenInNew, null, tint = Muted, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }

    @Composable
    private fun ReminderDropDown(label: String, value: Int, options: List<Int>, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
        var expanded by remember { mutableStateOf(false) }
        Box(modifier) {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth().height(58.dp), shape = RoundedCornerShape(14.dp), contentPadding = PaddingValues(horizontal = 10.dp)) {
                Column(horizontalAlignment = Alignment.Start, modifier = Modifier.weight(1f)) {
                    Text(label, color = Muted, fontSize = 11.sp, maxLines = 1)
                    Text(value.toString(), color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Black)
                }
                Icon(Icons.Rounded.ArrowDropDown, null, tint = Muted)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { option ->
                    DropdownMenuItem(text = { Text(option.toString()) }, onClick = { onSelect(option); expanded = false })
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun WalletScreen(
        tickets: List<WalletTicket>,
        onSave: (WalletTicket) -> Unit,
        onUpdate: (WalletTicket) -> Unit,
        onDelete: (WalletTicket) -> Unit
    ) {
        var page by remember { mutableStateOf("home") }
        var month by remember { mutableStateOf(Calendar.getInstance()) }
        var compact by remember { mutableStateOf(false) }
        var selected by remember { mutableStateOf<WalletTicket?>(null) }
        var selectedDate by remember { mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.TAIWAN).format(Calendar.getInstance().time)) }
        var editing by remember { mutableStateOf<WalletTicket?>(null) }
        val dateTickets = remember(selectedDate, tickets) { tickets.filter { it.date == selectedDate } }
        selected?.let {
            TicketDialog(
                t = it,
                close = { selected = null },
                onEdit = { ticket -> editing = ticket; selected = null; selectedDate = ""; page = "edit" },
                onDelete = { ticket -> onDelete(ticket); selected = null; selectedDate = "" }
            )
        }
        if (page == "add") { AddTicketScreen({ page = "home" }) { onSave(it); page = "home" }; return }
        if (page == "edit") {
            val ticket = editing
            if (ticket == null) {
                page = "home"
            } else {
                AddTicketScreen({ page = "home"; editing = null }, ticket) { updated ->
                    onUpdate(updated)
                    editing = null
                    page = "home"
                }
            }
            return
        }
        Scaffold(containerColor = AppBg, floatingActionButton = { FloatingActionButton({ page = "add" }, containerColor = BlueSoft, contentColor = Blue, shape = RoundedCornerShape(18.dp)) { Icon(Icons.Rounded.Add, null) } }) { pad ->
            LazyColumn(
                Modifier.fillMaxSize().padding(pad).padding(horizontal = 18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(top = 18.dp, bottom = 120.dp)
            ) {
                item { SummaryCard(tickets.sumOf { parsePrice(it.price) }) }
                item {
                    CalendarCard(
                        month = month,
                        tickets = tickets,
                        selectedDate = selectedDate,
                        compact = compact,
                        toggle = { compact = !compact },
                        prev = { month = shiftMonth(month, -1) },
                        next = { month = shiftMonth(month, 1) }
                    ) { date, _ -> selectedDate = date }
                }
                item { Text(T.sortByDateAsc, color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                if (dateTickets.isEmpty()) {
                    item { NoScheduleForDay() }
                } else {
                    items(dateTickets.sortedBy { it.time }) { TicketCard(it) { selected = it } }
                }
            }
        }
    }

    @Composable
    private fun AddTicketScreen(onBack: () -> Unit, initial: WalletTicket? = null, onSave: (WalletTicket) -> Unit) {
        val context = LocalContext.current
        var title by remember(initial?.id) { mutableStateOf(initial?.title.orEmpty()) }
        var date by remember(initial?.id) { mutableStateOf(initial?.date.orEmpty()) }
        var time by remember(initial?.id) { mutableStateOf(initial?.time.orEmpty()) }
        var place by remember(initial?.id) { mutableStateOf(initial?.location.orEmpty()) }
        var seat by remember(initial?.id) { mutableStateOf(initial?.seat.orEmpty()) }
        var cast by remember(initial?.id) { mutableStateOf(initial?.cast.orEmpty()) }
        var platform by remember(initial?.id) { mutableStateOf(initial?.platform.orEmpty()) }
        var price by remember(initial?.id) { mutableStateOf(initial?.price.orEmpty()) }
        var notes by remember(initial?.id) { mutableStateOf(initial?.notes.orEmpty()) }
        var imagePath by remember(initial?.id) { mutableStateOf(initial?.imagePath.orEmpty()) }
        var cameraUri by remember { mutableStateOf<Uri?>(null) }
        var correctionTarget by remember { mutableStateOf("") }
        if (correctionTarget.isNotBlank()) {
            CorrectionDialog(
                title = if (correctionTarget == "place") T.quickFixPlace else T.quickFixArtist,
                options = if (correctionTarget == "place") venueWhitelist() else artistWhitelist(),
                onPick = {
                    if (correctionTarget == "place") place = it else cast = it
                    correctionTarget = ""
                },
                onDismiss = { correctionTarget = "" }
            )
        }
        fun applyOcr(uri: Uri) {
            runTicketOcr(context, uri, { fields ->
                if (title.isBlank()) title = fields.title
                if (date.isBlank()) date = fields.date
                if (time.isBlank()) time = fields.time
                if (place.isBlank()) place = fields.place
                if (seat.isBlank()) seat = fields.seat
                if (cast.isBlank()) cast = fields.cast
                toast(if (fields.hasCoreData) T.ocrDone else T.ocrPartial)
            }, { toast(T.ocrFailed) })
        }
        val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) {
                val saved = persistTicketImage(context, uri)
                imagePath = saved.toString()
                applyOcr(saved)
            }
        }
        val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
            val uri = cameraUri
            if (ok && uri != null) {
                imagePath = uri.toString()
                applyOcr(uri)
            }
        }
        val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                val uri = createTicketCameraUri(context)
                cameraUri = uri
                cameraLauncher.launch(uri)
            } else {
                toast(T.cameraDenied)
            }
        }
        fun scanTicket() {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                val uri = createTicketCameraUri(context)
                cameraUri = uri
                cameraLauncher.launch(uri)
            } else {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
        fun datePicker() { val c = parseDate(date); DatePickerDialog(context, { _, y, m, d -> date = "%04d-%02d-%02d".format(y, m + 1, d) }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show() }
        fun timePicker() { val t = parseTime(time); TimePickerDialog(context, { _, h, m -> time = "%02d:%02d".format(h, m) }, t.first, t.second, true).show() }
        LazyColumn(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 92.dp)) {
            item { BackTitle(if (initial == null) T.addTicket else T.editTicket, onBack) }
            item {
                Button(
                    onClick = ::scanTicket,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Purple)
                ) {
                    Icon(Icons.Rounded.DocumentScanner, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(T.scanTicket, fontWeight = FontWeight.Bold)
                }
            }
            item { Box(Modifier.fillMaxWidth().height(170.dp).clip(RoundedCornerShape(18.dp)).background(BlueSoft).clickable { picker.launch("image/*") }, Alignment.Center) { if (imagePath.isNotBlank()) AsyncImage(imagePath, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) else Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Rounded.AddPhotoAlternate, null, tint = Blue, modifier = Modifier.size(42.dp)); Text(T.chooseTicketImage, color = Blue, fontWeight = FontWeight.Bold) } } }
            item { OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), label = { Text(T.ticketTitle) }, singleLine = true) }
            item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { PickerField(T.date, date.ifBlank { T.chooseDate }, Icons.Rounded.CalendarMonth, Modifier.weight(1f), ::datePicker); PickerField(T.time, time.ifBlank { T.chooseTime }, Icons.Rounded.Schedule, Modifier.weight(1f), ::timePicker) } }
            item { OutlinedTextField(place, { place = it }, Modifier.fillMaxWidth(), label = { Text(T.place) }, singleLine = true, trailingIcon = { IconButton({ correctionTarget = "place" }) { Icon(Icons.Rounded.Tune, T.quickFixPlace) } }) }
            item { OutlinedTextField(seat, { seat = it }, Modifier.fillMaxWidth(), label = { Text(T.seat) }, singleLine = true) }
            item { OutlinedTextField(cast, { cast = it }, Modifier.fillMaxWidth(), label = { Text(T.artist) }, singleLine = true, trailingIcon = { IconButton({ correctionTarget = "cast" }) { Icon(Icons.Rounded.Tune, T.quickFixArtist) } }) }
            item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(platform, { platform = it }, Modifier.weight(1f), label = { Text(T.ticketSite) }, singleLine = true); OutlinedTextField(price, { price = it }, Modifier.weight(1f), label = { Text(T.price) }, singleLine = true) } }
            item { OutlinedTextField(notes, { notes = it }, Modifier.fillMaxWidth(), label = { Text(T.notes) }, minLines = 2) }
            item { Button({ if (title.isBlank()) toast(T.enterTitle) else onSave(WalletTicket(initial?.id ?: System.currentTimeMillis(), title.trim(), date.trim(), place.trim(), seat.trim(), cast.trim(), platform.trim(), price.trim(), notes.trim(), imagePath, time.trim())) }, Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = Blue)) { Text(T.saveTicket, fontWeight = FontWeight.Bold) } }
        }
    }

    @Composable
    private fun MoreScreen(onSelect: (AppTab) -> Unit, onLogout: () -> Unit) {
        LazyColumn(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 92.dp)) {
            item { Header(T.moreFeatures, T.moreSub, Icons.Rounded.MoreHoriz, Purple) }
            item { MoreCard(T.seatMap, T.seatMapSub, Icons.Rounded.LocationOn, Blue) { onSelect(AppTab.SeatMap) } }
            item { MoreCard(T.traffic, T.trafficSub, Icons.Rounded.LocationOn, Color(0xFF0F766E)) { onSelect(AppTab.Parking) } }
            item { MoreCard(T.budget, T.budgetSub, Icons.Rounded.AttachMoney, Purple) { onSelect(AppTab.Budget) } }
            item { MoreCard(T.food, T.foodSub, Icons.Rounded.Restaurant, Color(0xFF0F766E)) { onSelect(AppTab.Food) } }
            item { MoreCard(T.venueGuide, T.venueGuideSub, Icons.Rounded.Stadium, Color(0xFF9333EA)) { onSelect(AppTab.Venues) } }
            item { MoreCard(T.analysis, T.analysisSub, Icons.Rounded.Analytics, Color(0xFFDB2777)) { onSelect(AppTab.Analysis) } }
            item { MoreCard(T.accountSettings, T.accountSub, Icons.Rounded.AccountCircle, Dark) { onSelect(AppTab.Account) } }
            item {
                Button(
                    onClick = onLogout,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935)),
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Rounded.Logout, null, tint = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text(T.logoutAccount, color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    @Composable
    private fun LoginScreen(onAuthenticated: () -> Unit) {
        var email by remember { mutableStateOf("") }
        var password by remember { mutableStateOf("") }
        var registerMode by remember { mutableStateOf(false) }
        var loading by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf<String?>(null) }
        val validEmail = remember(email) { isValidEmail(email) }
        val canSubmit = validEmail && password.length >= 6 && !loading

        Box(Modifier.fillMaxSize().padding(18.dp), contentAlignment = Alignment.Center) {
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(CardBg),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, FineLine),
                elevation = CardDefaults.cardElevation(8.dp)
            ) {
                Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(62.dp).background(BlueSoft, RoundedCornerShape(20.dp)), Alignment.Center) {
                        Icon(Icons.Rounded.Lock, null, tint = Blue, modifier = Modifier.size(32.dp))
                    }
                    Text(if (registerMode) T.registerTitle else T.loginTitle, color = Ink, fontSize = 28.sp, fontWeight = FontWeight.Black)
                    Text(T.loginSub, color = Muted, fontSize = 14.sp, textAlign = TextAlign.Center)
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it.trim(); error = null },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text(T.email) },
                        leadingIcon = { Icon(Icons.Rounded.Email, null, tint = Blue) },
                        isError = email.isNotBlank() && !validEmail,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        shape = RoundedCornerShape(16.dp)
                    )
                    if (email.isNotBlank() && !validEmail) {
                        Text(T.invalidEmail, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, modifier = Modifier.fillMaxWidth())
                    }
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it; error = null },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text(T.password) },
                        leadingIcon = { Icon(Icons.Rounded.Key, null, tint = Blue) },
                        visualTransformation = PasswordVisualTransformation(),
                        isError = password.isNotBlank() && password.length < 6,
                        shape = RoundedCornerShape(16.dp)
                    )
                    if (password.isNotBlank() && password.length < 6) {
                        Text(T.invalidPassword, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, modifier = Modifier.fillMaxWidth())
                    }
                    error?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp, modifier = Modifier.fillMaxWidth())
                    }
                    Button(
                        onClick = {
                            if (!canSubmit) return@Button
                            loading = true
                            authenticateUser(
                                email = email,
                                password = password,
                                register = registerMode,
                                onSuccess = {
                                    loading = false
                                    toast(if (registerMode) T.registerSuccess else T.loginSuccess)
                                    onAuthenticated()
                                },
                                onError = {
                                    loading = false
                                    error = it.ifBlank { T.loginFailed }
                                }
                            )
                        },
                        enabled = canSubmit,
                        colors = ButtonDefaults.buttonColors(containerColor = Blue, disabledContainerColor = Blue.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        if (loading) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                        } else {
                            Text(if (registerMode) T.register else T.login, color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                    TextButton(
                        onClick = {
                            registerMode = !registerMode
                            error = null
                        },
                        enabled = !loading
                    ) {
                        Text(if (registerMode) T.haveAccountLogin else T.createAccount, color = Blue, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    @Composable
    private fun SeatMapScreen(onBack: () -> Unit, onOpenUrl: (String) -> Unit) {
        var region by remember { mutableStateOf(T.north) }
        val regions = listOf(T.north, T.central, T.south)
        val venues = remember { seatVenueList() }.filter { it.region == region }
        LazyColumn(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 92.dp)) {
            item { BackTitle(T.seatMap, onBack, T.seatMapBrowse) }
            item { Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { regions.forEach { Button({ region = it }, shape = RoundedCornerShape(99.dp), colors = ButtonDefaults.buttonColors(if (region == it) Dark else CardBg, if (region == it) Color.White else Ink), border = BorderStroke(1.dp, FineLine)) { Text(it, fontWeight = FontWeight.Bold) } } } }
            items(venues) { VenueCard(it, onOpenUrl) }
        }
    }

    @Composable
    private fun VenueGuideScreen(onBack: () -> Unit) {
        var venues by remember { mutableStateOf<List<VenueInfo>>(emptyList()) }
        var selected by remember { mutableStateOf<VenueInfo?>(null) }
        var loading by remember { mutableStateOf(true) }
        var error by remember { mutableStateOf("") }
        LaunchedEffect(Unit) {
            fetchVenues { list, message ->
                venues = list
                error = message
                loading = false
            }
        }
        selected?.let { venue ->
            AlertDialog(
                onDismissRequest = { selected = null },
                title = { Text(venue.name, fontWeight = FontWeight.Black) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("${T.place}: ${venue.address.ifBlank { "-" }}", color = Ink)
                        if (venue.city.isNotBlank()) Text("${T.city}: ${venue.city}", color = Ink)
                        Text(venue.description.ifBlank { T.noVenueDescription }, color = Muted, lineHeight = 22.sp)
                    }
                },
                confirmButton = { TextButton({ selected = null }) { Text(T.close) } }
            )
        }
        LazyColumn(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 92.dp)) {
            item { BackTitle(T.venueGuide, onBack, T.venueGuideSub) }
            when {
                loading -> item { EmptyCard(T.loading, T.loading) }
                error.isNotBlank() -> item { EmptyCard(T.dataConnectionFailed, error) }
                venues.isEmpty() -> item { EmptyCard(T.noData, T.noDataSub) }
                else -> items(venues) { venue ->
                    MoreCard(venue.name, listOf(venue.city, venue.address).filter { it.isNotBlank() }.joinToString(" · "), Icons.Rounded.Stadium, Color(0xFF9333EA)) {
                        selected = venue
                    }
                }
            }
        }
    }

    @Composable
    private fun TrafficScreen(onBack: () -> Unit, openFood: () -> Unit) {
        var query by remember { mutableStateOf("") }
        var selectedVenue by remember { mutableStateOf(parkingVenues().first()) }
        var radius by remember { mutableStateOf(500f) }
        var placesLoading by remember { mutableStateOf(false) }
        var places by remember { mutableStateOf<List<ParkingLot>>(emptyList()) }
        val normalizedLat = normalizeCoordinate(selectedVenue.lat, -90.0, 90.0)
        val normalizedLng = normalizeCoordinate(selectedVenue.lng, -180.0, 180.0)
        LaunchedEffect(selectedVenue, radius, query) {
            placesLoading = true
            fetchPlaces("parking", normalizedLat, normalizedLng, radius.toInt(), query, { lots, _ ->
                places = lots
                placesLoading = false
            }, {
                places = emptyList()
                placesLoading = false
            })
        }
        val results = remember(selectedVenue, radius, query, places) {
            val source = places.ifEmpty { parkingLots() }
            source.filter { lot ->
                val keywordOk = query.isBlank() || lot.name.contains(query, true) || lot.address.contains(query, true)
                keywordOk && distanceMeters(normalizedLat, normalizedLng, lot.lat, lot.lng) <= radius
            }
        }
        LazyColumn(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(bottom = 120.dp)) {
            item {
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(Color(0xFFEDE7F6)), shape = RoundedCornerShape(20.dp)) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Rounded.DirectionsCar, null, tint = Blue, modifier = Modifier.size(30.dp))
                        Text(T.traffic, color = Blue, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text(T.parkingTitle, color = Ink, fontSize = 28.sp, fontWeight = FontWeight.Black)
                        Text(T.parkingIntro, color = Muted, fontSize = 17.sp, lineHeight = 28.sp)
                    }
                }
            }
            item {
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(CardBg), shape = RoundedCornerShape(24.dp), border = BorderStroke(1.dp, FineLine)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Rounded.Search, null, tint = Blue) },
                            trailingIcon = { TextButton({}) { Text(T.searchAction, color = Blue, fontWeight = FontWeight.Bold) } },
                            placeholder = { Text(T.parkingSearchHint) },
                            shape = RoundedCornerShape(18.dp)
                        )
                        Divider(color = FineLine.copy(alpha = 0.72f))
                        Text(T.hotVenueQuick, color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Black)
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            parkingVenues().forEach { venue ->
                                OutlinedButton(
                                    onClick = { selectedVenue = venue },
                                    shape = RoundedCornerShape(99.dp),
                                    border = BorderStroke(2.dp, if (selectedVenue.name == venue.name) Blue else FineLine),
                                    colors = ButtonDefaults.outlinedButtonColors(containerColor = if (selectedVenue.name == venue.name) BlueSoft else CardBg)
                                ) { Text(venue.name, color = if (selectedVenue.name == venue.name) Blue else Ink, fontWeight = FontWeight.Black) }
                            }
                        }
                        Divider(color = FineLine.copy(alpha = 0.72f))
                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                            Text(T.smartRadius, color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Black)
                            Text("${radius.toInt()} ${T.meterWithin}", color = Blue, fontSize = 18.sp, fontWeight = FontWeight.Black)
                        }
                        Slider(
                            value = radius,
                            onValueChange = { radius = (it / 100f).toInt().coerceIn(3, 20) * 100f },
                            valueRange = 300f..2000f,
                            steps = 16,
                            colors = SliderDefaults.colors(thumbColor = Blue, activeTrackColor = Blue, inactiveTrackColor = BlueSoft, activeTickColor = Color.White, inactiveTickColor = Ink)
                        )
                        Button(openFood, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F766E))) {
                            Icon(Icons.Rounded.Restaurant, null)
                            Spacer(Modifier.width(8.dp))
                            Text(T.findFoodByVenue, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            item { Text("${T.parkingResultPrefix} (${if (placesLoading) "..." else results.size} ${T.countUnit})", color = Muted, fontSize = 18.sp, fontWeight = FontWeight.Black) }
            if (results.isEmpty()) {
                item { EmptyCard(T.parkingNoData, T.parkingNoDataSub) }
            } else {
                if (results.any { it.source == "google-places" }) {
                    item { Text(T.googleAttribution, color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                }
                items(results) { ParkingLotCard(it, normalizedLat, normalizedLng, ::openParkingMap) }
            }
        }
    }

    @Composable
    private fun BudgetScreen(onBack: () -> Unit) {
        val savedEvents = remember { demoEvents().take(5) }
        val budgetLimit = 5000
        val estimatedTotal = 3300
        val usage = (estimatedTotal.toFloat() / budgetLimit.toFloat()).coerceIn(0f, 1f)
        AsyncContentScreen(
            title = T.budget,
            sub = T.budgetSub,
            icon = Icons.Rounded.AttachMoney,
            onBack = onBack,
            isEmpty = savedEvents.isEmpty()
        ) {
            BudgetHeroCard(budgetLimit, estimatedTotal, usage)
            BudgetShortcutRow(savedEvents.size, 15, 2, budgetLimit)
            BudgetImpactCard(estimatedTotal)
            BudgetAllocationCard()
        }
    }

    @Composable
    private fun AnalysisScreen(tickets: List<WalletTicket>, onBack: () -> Unit) {
        val analysis = remember(tickets) { getAnalysisData(tickets) }
        val hasData = tickets.isNotEmpty()
        AsyncContentScreen(
            title = T.analysis,
            sub = T.analysisSub,
            icon = Icons.Rounded.Analytics,
            onBack = onBack,
            isEmpty = !hasData
        ) {
            Header(T.analysis, "${T.ticketCount}${tickets.size}", Icons.Rounded.Analytics, Purple)
            AnalysisPlatformCard(analysis.platformStats)
            AnalysisMonthlyCard(analysis.monthlyStats)
            SimpleCard(T.averagePrice, "NT$ ${formatNumber(tickets.map { parsePrice(it.price) }.filter { it > 0 }.averageIntOrZero())}", Icons.Rounded.AttachMoney)
            SimpleCard(T.hotVenue, tickets.groupingBy { it.location }.eachCount().maxByOrNull { it.value }?.key.orEmpty().ifBlank { "-" }, Icons.Rounded.LocationOn)
        }
    }

    @Composable
    private fun AnalysisPlatformCard(platformStats: Map<String, Int>) {
        val total = platformStats.values.sum().coerceAtLeast(1)
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(CardBg), shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, FineLine)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(T.platformDistribution, color = Ink, fontSize = 20.sp, fontWeight = FontWeight.Black)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PieChart(platformStats, Modifier.size(118.dp))
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        platformStats.entries.sortedByDescending { it.value }.forEachIndexed { index, entry ->
                            val color = chartColors()[index % chartColors().size]
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(10.dp).background(color, RoundedCornerShape(99.dp)))
                                Spacer(Modifier.width(8.dp))
                                Text(entry.key, color = Ink, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${entry.value * 100 / total}%", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun PieChart(platformStats: Map<String, Int>, modifier: Modifier = Modifier) {
        val colors = chartColors()
        val total = platformStats.values.sum().coerceAtLeast(1)
        ComposeCanvas(modifier) {
            var start = -90f
            platformStats.values.forEachIndexed { index, value ->
                val sweep = 360f * value / total
                drawArc(colors[index % colors.size], start, sweep, true)
                start += sweep
            }
            drawCircle(CardBg, radius = size.minDimension * 0.28f)
        }
    }

    @Composable
    private fun AnalysisMonthlyCard(monthlyStats: Map<Int, Int>) {
        val max = monthlyStats.values.maxOrNull()?.coerceAtLeast(1) ?: 1
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(CardBg), shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, FineLine)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(T.monthlySpendTrend, color = Ink, fontSize = 20.sp, fontWeight = FontWeight.Black)
                Row(Modifier.fillMaxWidth().height(170.dp), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.Bottom) {
                    (1..12).forEach { month ->
                        val value = monthlyStats[month] ?: 0
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                            Box(Modifier.fillMaxWidth().height(((value.toFloat() / max) * 128).coerceAtLeast(if (value > 0) 8f else 2f).dp).clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)).background(if (value > 0) Blue else Color(0xFFE5E7EB)))
                            Spacer(Modifier.height(6.dp))
                            Text(month.toString(), color = Muted, fontSize = 10.sp)
                        }
                    }
                }
                Text("${T.totalSpend}: NT$ ${formatNumber(monthlyStats.values.sum())}", color = Blue, fontSize = 18.sp, fontWeight = FontWeight.Black)
            }
        }
    }

    @Composable
    private fun ToolListScreen(title: String, sub: String, icon: ImageVector, onBack: () -> Unit, rows: List<Pair<String, String>>) {
        AsyncContentScreen(title, sub, icon, onBack, rows.isEmpty()) {
            rows.forEach { (t, s) -> SimpleCard(t, s, icon) }
        }
    }

    @Composable
    private fun FoodRecommendScreen(onBack: () -> Unit) {
        var selectedVenue by remember { mutableStateOf(parkingVenues().first()) }
        var useVenueLocation by remember { mutableStateOf(true) }
        var placesLoading by remember { mutableStateOf(false) }
        var remoteRestaurants by remember { mutableStateOf<List<RestaurantItem>>(emptyList()) }
        val allRestaurants = if (remoteRestaurants.isNotEmpty()) remoteRestaurants else remember { restaurantSamples() }
        var price by remember { mutableStateOf(T.all) }
        var category by remember { mutableStateOf(T.all) }
        var city by remember { mutableStateOf(T.all) }
        var randomPick by remember { mutableStateOf<RestaurantItem?>(null) }
        val origin = if (useVenueLocation) selectedVenue else ParkingVenue(T.currentLocation, 25.0478, 121.5170)
        LaunchedEffect(origin, useVenueLocation) {
            placesLoading = true
            fetchPlaces("restaurant", normalizeCoordinate(origin.lat, -90.0, 90.0), normalizeCoordinate(origin.lng, -180.0, 180.0), 1200, "", { _, restaurants ->
                remoteRestaurants = restaurants
                placesLoading = false
            }, {
                remoteRestaurants = emptyList()
                placesLoading = false
            })
        }
        val filtered = allRestaurants.filter { item ->
            (price == T.all || item.priceBand == price) &&
                (category == T.all || item.category == category) &&
                (city == T.all || item.city == city)
        }
        LazyColumn(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 120.dp)) {
            item { BackTitle(T.food, onBack, T.foodSub) }
            item {
                Card(colors = CardDefaults.cardColors(CardBg), shape = RoundedCornerShape(22.dp), border = BorderStroke(1.dp, FineLine)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(56.dp).clip(RoundedCornerShape(18.dp)).background(Color(0xFFE6F7EF)), contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.Restaurant, null, tint = Color(0xFF0F766E), modifier = Modifier.size(30.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(T.foodRandomTitle, color = Ink, fontSize = 20.sp, fontWeight = FontWeight.Black)
                                Text(T.foodRandomSub, color = Muted, fontSize = 13.sp)
                            }
                        }
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton({ useVenueLocation = false }, shape = RoundedCornerShape(99.dp), border = BorderStroke(2.dp, if (!useVenueLocation) Blue else FineLine)) { Text(T.currentLocation) }
                            OutlinedButton({ useVenueLocation = true }, shape = RoundedCornerShape(99.dp), border = BorderStroke(2.dp, if (useVenueLocation) Blue else FineLine)) { Text(T.venueLocation) }
                            parkingVenues().forEach { venue ->
                                OutlinedButton({ selectedVenue = venue; useVenueLocation = true }, shape = RoundedCornerShape(99.dp), border = BorderStroke(2.dp, if (useVenueLocation && selectedVenue.name == venue.name) Blue else FineLine)) { Text(venue.name) }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChipRow(T.priceBand, listOf(T.all, T.priceLow, T.priceMid, T.priceHigh), price, { price = it }, Modifier.weight(1f))
                            FilterChipRow(T.foodType, listOf(T.all, T.foodCafe, T.foodMeal, T.foodDessert), category, { category = it }, Modifier.weight(1f))
                        }
                        FilterChipRow(T.city, listOf(T.all, T.taipeiCity, T.newTaipeiCity, T.kaohsiungCity), city, { city = it }, Modifier.fillMaxWidth())
                        Button(
                            onClick = { randomPick = filtered.ifEmpty { allRestaurants }.random(Random(System.nanoTime())) },
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F766E))
                        ) {
                            Icon(Icons.Rounded.Casino, null, Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(T.randomFood, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            randomPick?.let { item { FoodCard(it, highlight = true) } }
            item { SectionTitle(if (placesLoading) T.loading else T.foodResults) }
            if (filtered.isEmpty()) item { EmptyCard(T.noData, T.noDataSub) } else items(filtered) { FoodCard(it) }
        }
    }

    @Composable
    private fun FilterChipRow(label: String, options: List<String>, selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(label, color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                options.forEach { option ->
                    FilterChip(
                        selected = selected == option,
                        onClick = { onSelect(option) },
                        label = { Text(option, maxLines = 1) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = BlueSoft, selectedLabelColor = Blue)
                    )
                }
            }
        }
    }

    @Composable
    private fun FoodCard(item: RestaurantItem, highlight: Boolean = false) {
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(if (highlight) Color(0xFFE6F7EF) else CardBg), shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, if (highlight) Color(0xFF0F766E) else FineLine)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(item.name, color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(item.priceBand, color = Color(0xFF0F766E), fontWeight = FontWeight.Bold)
                }
                Text("${item.category} / ${item.city}", color = Muted, fontSize = 13.sp)
                Text(item.address, color = Ink, fontSize = 14.sp)
                if (item.openingHours.isNotBlank()) Text(item.openingHours, color = Muted, fontSize = 12.sp)
                if (item.source == "google-places") Text(T.googleAttribution, color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }

    @Composable
    private fun CorrectionDialog(title: String, options: List<String>, onPick: (String) -> Unit, onDismiss: () -> Unit) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(title, fontWeight = FontWeight.Black) },
            text = {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.heightIn(max = 360.dp)) {
                    items(options) { option ->
                        OutlinedButton(onClick = { onPick(option) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                            Text(option, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onDismiss) { Text(T.close) } }
        )
    }

    @Composable
    private fun ReminderScreen(reminders: List<LocalReminder>, onUpdate: (EventItem, Int) -> Unit, openReminder: (EventItem) -> Unit) {
        LazyColumn(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 92.dp)) {
            item { Header(T.reminders, T.saleReminders, Icons.Rounded.Notifications, Purple) }
            if (reminders.isEmpty()) {
                item { EmptyCard(T.noReminders, T.noRemindersSub) }
            } else {
                items(reminders.sortedBy { it.saleTime }) { reminder ->
                    ReminderManageCard(reminder, onUpdate, openReminder)
                }
            }
        }
    }

    @Composable
    private fun ReminderManageCard(reminder: LocalReminder, onUpdate: (EventItem, Int) -> Unit, openReminder: (EventItem) -> Unit) {
        val event = reminder.toEventItem()
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(CardBg), shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, FineLine)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(54.dp).clip(RoundedCornerShape(16.dp)).background(BlueSoft), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Notifications, null, tint = Blue)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(reminder.title, color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(reminder.saleTime, color = Muted, fontSize = 14.sp)
                        Text(reminderLabel(reminder.minutesBefore), color = Purple, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    IconButton(onClick = { openReminder(event) }) { Icon(Icons.Rounded.EditNotifications, T.editReminder, tint = Purple) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(15 to T.before15Min, 60 to T.before1Hour, 1440 to T.before1Day).forEach { (minutes, label) ->
                        OutlinedButton(
                            onClick = { onUpdate(event, minutes) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, if (reminder.minutesBefore == minutes) Purple else FineLine)
                        ) { Text(label, fontSize = 11.sp, maxLines = 1) }
                    }
                }
            }
        }
    }
    @Composable private fun PlaceholderScreen(title: String, icon: ImageVector, onBack: () -> Unit) = ToolListScreen(title, T.moreSub, icon, onBack, listOf(T.accountSettings to T.accountSub))

    @Composable private fun BackTitle(title: String, onBack: () -> Unit, sub: String = "") = Row(verticalAlignment = Alignment.CenterVertically) { IconButton(onBack) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, null) }; Column { Text(title, color = Ink, fontSize = 22.sp, fontWeight = FontWeight.Black); if (sub.isNotBlank()) Text(sub, color = Muted, fontSize = 13.sp) } }
    @Composable
    private fun AsyncContentScreen(
        title: String,
        sub: String,
        icon: ImageVector,
        onBack: () -> Unit,
        isEmpty: Boolean,
        content: @Composable ColumnScope.() -> Unit
    ) {
        var loading by remember(title) { mutableStateOf(true) }
        LaunchedEffect(title) {
            delay(350)
            loading = false
        }
        LazyColumn(
            Modifier.fillMaxSize().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 120.dp)
        ) {
            item { BackTitle(title, onBack, sub) }
            item {
                when {
                    loading -> LoadingCard()
                    isEmpty -> EmptyCard(T.noData, T.noDataSub)
                    else -> Column(verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
                }
            }
        }
    }

    @Composable
    private fun LoadingCard() = Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(CardBg), shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, FineLine)) {
        Row(Modifier.padding(22.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Blue, strokeWidth = 3.dp)
            Spacer(Modifier.width(14.dp))
            Text(T.loading, color = Ink, fontWeight = FontWeight.Bold)
        }
    }

    @Composable
    private fun ParkingLotCard(lot: ParkingLot, lat: Double, lng: Double, openMap: (ParkingLot) -> Unit) {
        val distance = distanceMeters(lat, lng, lot.lat, lot.lng).toInt()
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(CardBg), shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, FineLine), elevation = CardDefaults.cardElevation(2.dp)) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp).clip(RoundedCornerShape(15.dp)).background(BlueSoft), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.LocalParking, null, tint = Blue)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(lot.name, color = Ink, fontSize = 17.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(lot.address, color = Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${distance}m / ${lot.spaces} ${T.parkingSpaces}", color = Blue, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    if (lot.source == "google-places") Text(T.googleAttribution, color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Button(onClick = { openMap(lot) }, shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = Blue)) {
                    Icon(Icons.Rounded.Navigation, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(T.navigation, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    @Composable
    private fun BudgetHeroCard(budgetLimit: Int, used: Int, usage: Float) {
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(CardBg), shape = RoundedCornerShape(24.dp), border = BorderStroke(1.dp, FineLine), elevation = CardDefaults.cardElevation(4.dp)) {
            Row(Modifier.padding(22.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(T.monthBudget, color = Ink, fontSize = 20.sp, fontWeight = FontWeight.Black)
                    Text("NT$ ${formatNumber(budgetLimit)}", color = Ink, fontSize = 38.sp, fontWeight = FontWeight.Black)
                    Text("${T.usedBudget} NT$ ${formatNumber(used)} (${(usage * 100).toInt()}%)", color = Muted, fontSize = 14.sp)
                    Text("${T.remainingBudget} NT$ ${formatNumber((budgetLimit - used).coerceAtLeast(0))}", color = Ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
                BudgetRing(usage)
            }
        }
    }

    @Composable
    private fun BudgetRing(usage: Float) {
        Box(Modifier.size(116.dp), contentAlignment = Alignment.Center) {
            ComposeCanvas(Modifier.matchParentSize()) {
                val stroke = Stroke(width = 14.dp.toPx(), cap = StrokeCap.Butt)
                drawArc(Color(0xFFE5E7EB), -90f, 360f, false, style = stroke)
                drawArc(Blue, -90f, 360f * usage.coerceIn(0f, 1f), false, style = stroke)
            }
            Text("${(usage * 100).toInt()}%", color = Blue, fontSize = 24.sp, fontWeight = FontWeight.Black)
        }
    }

    @Composable
    private fun BudgetShortcutRow(saved: Int, evaluated: Int, records: Int, budgetLimit: Int) {
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(CardBg), shape = RoundedCornerShape(22.dp), border = BorderStroke(1.dp, FineLine), elevation = CardDefaults.cardElevation(2.dp)) {
            Row(Modifier.padding(vertical = 16.dp)) {
                BudgetMiniMetric(T.savedList, "$saved ${T.eventUnit}", Icons.Rounded.FavoriteBorder, Modifier.weight(1f))
                BudgetMiniMetric(T.evaluationCount, "$evaluated ${T.eventUnit}", Icons.Rounded.Check, Modifier.weight(1f))
                BudgetMiniMetric(T.ticketRecords, "$records ${T.eventUnit}", Icons.Rounded.ConfirmationNumber, Modifier.weight(1f))
                BudgetMiniMetric(T.budgetSettings, "NT$ ${formatNumber(budgetLimit)}", Icons.Rounded.Settings, Modifier.weight(1f))
            }
        }
    }

    @Composable
    private fun BudgetMiniMetric(title: String, value: String, icon: ImageVector, modifier: Modifier = Modifier) {
        Column(modifier.padding(horizontal = 6.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Icon(icon, null, tint = Blue, modifier = Modifier.size(24.dp))
            Text(title, color = Ink, fontSize = 12.sp, fontWeight = FontWeight.Black, maxLines = 1)
            Text(value, color = Muted, fontSize = 11.sp, maxLines = 1)
        }
    }

    @Composable
    private fun BudgetImpactCard(used: Int) {
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(CardBg), shape = RoundedCornerShape(22.dp), border = BorderStroke(1.dp, FineLine), elevation = CardDefaults.cardElevation(3.dp)) {
            Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(T.budgetImpact, color = Ink, fontSize = 20.sp, fontWeight = FontWeight.Black)
                    Text(T.budgetImpactSub, color = Muted, fontSize = 13.sp)
                    Spacer(Modifier.height(10.dp))
                    Text("NT$ ${formatNumber(used)}", color = Blue, fontSize = 30.sp, fontWeight = FontWeight.Black)
                }
                Button({}, shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = Blue)) { Text(T.viewRecommend, fontWeight = FontWeight.Bold) }
            }
        }
    }

    @Composable
    private fun BudgetAllocationCard() {
        val rows = listOf(
            BudgetAllocation(T.concert, 60, 3000, Blue),
            BudgetAllocation(T.exhibition, 20, 1000, Color(0xFF0F9F6E)),
            BudgetAllocation(T.sports, 10, 500, Color(0xFFEAB308)),
            BudgetAllocation(T.other, 10, 500, Color(0xFF64748B))
        )
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(CardBg), shape = RoundedCornerShape(22.dp), border = BorderStroke(1.dp, FineLine), elevation = CardDefaults.cardElevation(3.dp)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(T.budgetAllocation, color = Ink, fontSize = 20.sp, fontWeight = FontWeight.Black)
                Text(T.budgetAllocationSub, color = Muted, fontSize = 13.sp)
                rows.forEach { item ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(item.label, color = Ink, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(82.dp))
                        LinearProgressIndicator(progress = { item.percent / 100f }, modifier = Modifier.weight(1f).height(9.dp).clip(RoundedCornerShape(99.dp)), color = item.color, trackColor = Color(0xFFE5E7EB))
                        Spacer(Modifier.width(12.dp))
                        Text("${item.percent}%", color = Ink, fontWeight = FontWeight.Bold, modifier = Modifier.width(42.dp))
                        Text("NT$ ${formatNumber(item.amount)}", color = Ink, fontWeight = FontWeight.Black, textAlign = TextAlign.End, modifier = Modifier.width(86.dp))
                    }
                }
            }
        }
    }

    @Composable
    private fun BudgetSummaryRow(count: Int, estimatedTotal: Int, usage: Float) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            BudgetSummaryBox(T.savedEventCount, count.toString(), Icons.Rounded.Favorite, Blue, Modifier.weight(1f))
            BudgetSummaryBox(T.estimatedTotal, "NT$ ${formatNumber(estimatedTotal)}", Icons.Rounded.AttachMoney, Purple, Modifier.weight(1f))
            BudgetSummaryBox(T.budgetStatus, if (usage < 0.8f) T.budgetSafe else T.budgetWarning, Icons.Rounded.Speed, if (usage < 0.8f) Color(0xFF0F766E) else Color(0xFFDC2626), Modifier.weight(1f))
        }
    }

    @Composable
    private fun BudgetSummaryBox(title: String, value: String, icon: ImageVector, accent: Color, modifier: Modifier = Modifier) {
        Card(modifier, colors = CardDefaults.cardColors(CardBg), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, FineLine)) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(icon, null, tint = accent, modifier = Modifier.size(20.dp))
                Text(title, color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(value, color = Ink, fontSize = 15.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }

    @Composable
    private fun BudgetProgressCard(estimatedTotal: Int, budgetLimit: Int, usage: Float) {
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(CardBg), shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, FineLine)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                    Text(T.budgetUsage, color = Ink, fontWeight = FontWeight.Black)
                    Text("${(usage * 100).toInt()}%", color = Blue, fontWeight = FontWeight.Black)
                }
                LinearProgressIndicator(progress = { usage }, modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(99.dp)), color = if (usage < 0.8f) Blue else Color(0xFFDC2626), trackColor = BlueSoft)
                Text("${T.estimatedTotal}: NT$ ${formatNumber(estimatedTotal)} / ${T.budgetLimit}: NT$ ${formatNumber(budgetLimit)}", color = Muted, fontSize = 13.sp)
            }
        }
    }

    @Composable
    private fun BudgetEventCard(event: EventItem) {
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(CardBg), shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, FineLine)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.Top) {
                    Text(event.title, color = Ink, fontWeight = FontWeight.Black, fontSize = 17.sp, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Box(Modifier.background(BlueSoft, RoundedCornerShape(99.dp)).padding(horizontal = 10.dp, vertical = 5.dp)) {
                        Text(T.saved, color = Blue, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Text("${event.activityTime} / ${event.venue}", color = Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${event.source}  ${event.price}", color = Ink, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    }

    @Composable private fun WalletHeader() = Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(44.dp).background(BlueSoft, RoundedCornerShape(14.dp)), Alignment.Center) { Icon(Icons.Rounded.ConfirmationNumber, null, tint = Blue) }; Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(T.walletTitle, color = Ink, fontSize = 22.sp, fontWeight = FontWeight.Black); Text(T.walletSub, color = Muted, fontSize = 12.sp) }; IconButton({}) { Icon(Icons.Rounded.Search, null, tint = Ink) } }
    @Composable private fun Header(title: String, subtitle: String, icon: ImageVector, accent: Color) = Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(CardBg), shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, FineLine)) { Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(48.dp).background(accent.copy(alpha = 0.14f), RoundedCornerShape(14.dp)), Alignment.Center) { Icon(icon, null, tint = accent) }; Spacer(Modifier.width(14.dp)); Column { Text(title, color = Ink, fontSize = 22.sp, fontWeight = FontWeight.Black); Text(subtitle, color = Muted, fontSize = 13.sp) } } }
    @Composable private fun SummaryCard(total: Int) = Card(Modifier.fillMaxWidth().height(128.dp), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(Color.Transparent)) { Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Purple, Color(0xFF8B5CF6)))).padding(22.dp)) { Column(Modifier.align(Alignment.CenterStart)) { Text(T.annualTotal, color = Color.White.copy(alpha = 0.78f), fontSize = 14.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.height(12.dp)); Text("NT$ ${formatNumber(total)}", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Black) }; Icon(Icons.Rounded.AttachMoney, null, tint = Color.White.copy(alpha = 0.35f), modifier = Modifier.align(Alignment.TopEnd)) } }
    @Composable private fun SectionTitle(text: String) = Text(text, color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Black)
    @Composable private fun SimpleCard(title: String, sub: String, icon: ImageVector) = Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(CardBg), shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, FineLine)) { Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = Blue); Spacer(Modifier.width(12.dp)); Column { Text(title, color = Ink, fontWeight = FontWeight.Black); Text(sub, color = Muted, fontSize = 13.sp) } } }
    @Composable private fun EmptyCard(title: String, msg: String, onClick: (() -> Unit)? = null) = Card(Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier), colors = CardDefaults.cardColors(CardBg), shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, FineLine)) { Column(Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text(title, color = Ink, fontWeight = FontWeight.Black); Text(msg, color = Muted, textAlign = TextAlign.Center) } }
    @Composable private fun NoScheduleForDay() = Box(Modifier.fillMaxWidth().height(220.dp), Alignment.Center) { Text(T.noScheduleDay, color = Muted, fontSize = 14.sp) }
    @Composable private fun InfoLine(label: String, value: String) = Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(label, color = Color(0xFF6B7280), fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.width(60.dp)); Text(value.ifBlank { "-" }, modifier = Modifier.weight(1f), color = Ink, fontSize = 11.sp, lineHeight = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }

    @Composable
private fun TicketCard(ticket: WalletTicket, onClick: () -> Unit) = Card(Modifier.fillMaxWidth().height(152.dp).clickable(onClick = onClick), shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(Color.White), elevation = CardDefaults.cardElevation(3.dp), border = BorderStroke(1.dp, FineLine.copy(alpha = 0.55f))) {
        Row(Modifier.fillMaxSize().padding(0.dp)) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(0.35f).clip(RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp)).background(BlueSoft), Alignment.Center) {
                if (ticket.imagePath.isNotBlank()) AsyncImage(ticket.imagePath, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else Icon(Icons.Rounded.ConfirmationNumber, null, tint = Blue, modifier = Modifier.size(30.dp))
            }
            DashedDivider()
            Column(Modifier.weight(1f).widthIn(min = 0.dp).fillMaxHeight()) {
                Box(Modifier.fillMaxWidth().height(44.dp).background(Color(0xFF351016)).padding(horizontal = 12.dp), contentAlignment = Alignment.CenterStart) {
                    Text(ticket.title.ifBlank { T.untitledTicket }, color = Color.White, fontSize = 14.sp, lineHeight = 17.sp, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Column(
                    Modifier.fillMaxWidth().weight(1f).padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp, Alignment.Top)
                ) {
                    Text(ticketCompactDateTime(ticket).ifBlank { T.noDate }, color = Ink, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    InfoLine("Cast", ticket.cast)
                    InfoLine("Location", ticket.location)
                    InfoLine("Seat", ticket.seat)
                }
            }
        }
    }

    @Composable private fun DashedDivider() = ComposeCanvas(Modifier.width(2.dp).fillMaxHeight().background(Color.White)) { val dash = 7.dp.toPx(); val gap = 5.dp.toPx(); var y = 4.dp.toPx(); while (y < size.height) { drawLine(FineLine, Offset(size.width / 2f, y), Offset(size.width / 2f, (y + dash).coerceAtMost(size.height)), 1.5.dp.toPx()); y += dash + gap } }

    @Composable
    private fun CalendarCard(month: Calendar, tickets: List<WalletTicket>, selectedDate: String, compact: Boolean, toggle: () -> Unit, prev: () -> Unit, next: () -> Unit, onDate: (String, List<WalletTicket>) -> Unit) {
        val title = remember(month.timeInMillis) { SimpleDateFormat(T.monthPattern, Locale.TAIWAN).format(month.time) }
        val byDate = remember(tickets) { tickets.filter { it.date.isNotBlank() }.groupBy { it.date } }
        val days = remember(month.timeInMillis) { calendarDays(month) }
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(prev) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, null) }
                Text(title, Modifier.weight(1f), color = Ink, fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                OutlinedButton(toggle, shape = RoundedCornerShape(99.dp), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                    Text(if (compact) T.twoWeeks else T.month, fontSize = 12.sp)
                }
                IconButton(next) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null) }
            }
            Row {
                listOf(T.sun, T.mon, T.tue, T.wed, T.thu, T.fri, T.sat).forEachIndexed { index, label ->
                    val color = when (index) {
                        0 -> Color(0xFFFF3B30)
                        6 -> Color(0xFF2563EB)
                        else -> Muted
                    }
                    Text(label, Modifier.weight(1f), textAlign = TextAlign.Center, color = color, fontSize = 12.sp)
                }
            }
            (if (compact) days.chunked(7).take(2) else days.chunked(7)).forEach { week ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    week.forEach { day ->
                        val list = byDate[day.key].orEmpty()
                        DayCell(
                            day = day,
                            ticket = list.firstOrNull { it.imagePath.isNotBlank() } ?: list.firstOrNull(),
                            count = list.size,
                            selected = day.key == selectedDate,
                            onClick = { onDate(day.key, list) },
                            mod = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun DayCell(day: WalletCalendarDay, ticket: WalletTicket?, count: Int, selected: Boolean, onClick: () -> Unit, mod: Modifier) {
        val baseColor = when (day.dayOfWeek) {
            Calendar.SATURDAY -> Color(0xFF2563EB)
            Calendar.SUNDAY -> Color(0xFFFF3B30)
            else -> Ink
        }
        Box(mod.height(56.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick), Alignment.Center) {
            if (ticket != null && ticket.imagePath.isNotBlank()) {
                AsyncImage(ticket.imagePath, null, Modifier.size(46.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
                Text(day.day.toString(), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Black, modifier = Modifier.background(Dark.copy(alpha = 0.38f), RoundedCornerShape(99.dp)).padding(horizontal = 7.dp, vertical = 2.dp))
            } else {
                if (selected) {
                    Box(Modifier.size(38.dp).background(Color(0xFF6474D9), RoundedCornerShape(99.dp)), Alignment.Center) {
                        Text(day.day.toString(), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Text(day.day.toString(), color = if (day.inMonth) baseColor else baseColor.copy(alpha = 0.36f), fontSize = 13.sp)
                }
                if (ticket != null) Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp).size(6.dp).background(Blue, RoundedCornerShape(99.dp)))
            }
            if (count > 1) {
                Box(Modifier.align(Alignment.TopEnd).padding(2.dp).background(Dark.copy(alpha = 0.82f), RoundedCornerShape(99.dp)).padding(horizontal = 5.dp, vertical = 1.dp)) {
                    Text("+${count - 1}", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
    @Composable private fun PickerField(label: String, value: String, icon: ImageVector, mod: Modifier, click: () -> Unit) = OutlinedButton(click, mod.height(58.dp), shape = RoundedCornerShape(14.dp), contentPadding = PaddingValues(horizontal = 12.dp)) { Icon(icon, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Column(horizontalAlignment = Alignment.Start) { Text(label, fontSize = 11.sp, color = Muted); Text(value, fontSize = 13.sp, color = Ink, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis) } }
    @Composable private fun MoreCard(title: String, sub: String, icon: ImageVector, accent: Color, click: () -> Unit) = Card(Modifier.fillMaxWidth().clickable(onClick = click), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(CardBg), border = BorderStroke(1.dp, FineLine.copy(alpha = 0.7f))) { Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(46.dp).background(accent.copy(alpha = 0.14f), RoundedCornerShape(14.dp)), Alignment.Center) { Icon(icon, null, tint = accent) }; Spacer(Modifier.width(14.dp)); Column(Modifier.weight(1f)) { Text(title, color = Ink, fontSize = 17.sp, fontWeight = FontWeight.Black); Text(sub, color = Muted, fontSize = 13.sp) }; Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = Muted) } }
    @Composable private fun VenueCard(v: SeatVenue, open: (String) -> Unit) = Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(CardBg), border = BorderStroke(1.dp, FineLine.copy(alpha = 0.72f))) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(44.dp).background(v.color, RoundedCornerShape(14.dp)), Alignment.Center) { Icon(Icons.Rounded.LocationOn, null, tint = Color.White) }; Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(v.name, color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis); Text(v.subtitle, color = Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }; Text(v.region, color = v.color, fontSize = 12.sp, fontWeight = FontWeight.Bold) }; Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { v.links.forEach { OutlinedButton({ open(it.url) }, shape = RoundedCornerShape(12.dp)) { Text(it.label, fontWeight = FontWeight.Bold) } } } } }

    @Composable
    private fun DayTicketsSheet(date: String, tickets: List<WalletTicket>, onTicket: (WalletTicket) -> Unit, onClose: () -> Unit) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Column {
                    Text(date, color = Ink, fontSize = 22.sp, fontWeight = FontWeight.Black)
                    Text("${tickets.size} ${T.ticketCountUnit}", color = Muted, fontSize = 13.sp)
                }
                IconButton(onClose) { Icon(Icons.Rounded.Close, T.close) }
            }
            if (tickets.isEmpty()) {
                EmptyCard(T.noTicketsOnDate, T.noTicketsOnDateSub)
            } else {
                tickets.sortedBy { it.time }.forEach { ticket ->
                    TicketCard(ticket) { onTicket(ticket) }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    @Composable
    private fun TicketDialog(t: WalletTicket, close: () -> Unit, onEdit: (WalletTicket) -> Unit, onDelete: (WalletTicket) -> Unit) {
        var showShare by remember { mutableStateOf(false) }
        var shareColor by remember { mutableStateOf(ShareTheme.Purple) }
        if (showShare) {
            ShareTicketDialog(t, shareColor, { shareColor = it }, { showShare = false }) {
                shareTicketImage(t, shareColor)
                showShare = false
            }
        }
        AlertDialog(
            onDismissRequest = close,
            title = { Text(t.title.ifBlank { T.ticket }) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (t.imagePath.isNotBlank()) AsyncImage(t.imagePath, null, Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(14.dp)), contentScale = ContentScale.Crop)
                    Text("${T.date}: ${t.date} ${t.time}".trim())
                    Text("${T.place}: ${t.location}")
                    Text("${T.seat}: ${t.seat}")
                    Text("${T.artist}: ${t.cast}")
                    if (t.platform.isNotBlank()) Text("${T.ticketSite}: ${t.platform}")
                    if (t.price.isNotBlank()) Text("${T.price}: ${t.price}")
                }
            },
            dismissButton = { TextButton(close) { Text(T.close) } },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton({ onDelete(t) }) { Text(T.delete, color = Color(0xFFDC2626), fontWeight = FontWeight.Bold) }
                    OutlinedButton({ showShare = true }, shape = RoundedCornerShape(12.dp)) { Text(T.share, fontWeight = FontWeight.Bold) }
                    Button({ onEdit(t) }, shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = Blue)) { Text(T.edit, fontWeight = FontWeight.Bold) }
                }
            }
        )
    }

    @Composable
    private fun ShareTicketDialog(ticket: WalletTicket, selected: ShareTheme, onTheme: (ShareTheme) -> Unit, onClose: () -> Unit, onShare: () -> Unit) {
        AlertDialog(
            onDismissRequest = onClose,
            title = { Text(T.sharePreview, fontWeight = FontWeight.Black) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.fillMaxWidth().height(340.dp).clip(RoundedCornerShape(22.dp)).background(Color(selected.bgColor)), Alignment.Center) {
                        Column(Modifier.fillMaxWidth(0.82f).clip(RoundedCornerShape(18.dp)).background(Color.White)) {
                            Box(Modifier.fillMaxWidth().height(130.dp).background(BlueSoft), Alignment.Center) {
                                if (ticket.imagePath.isNotBlank()) AsyncImage(ticket.imagePath, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                else Icon(Icons.Rounded.ConfirmationNumber, null, tint = Blue, modifier = Modifier.size(42.dp))
                            }
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(ticket.title.ifBlank { T.untitledTicket }, color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Black, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                Divider(color = FineLine)
                                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                                    SharePreviewInfo("DATE", ticket.date.takeLast(5).replace("-", "/"))
                                    SharePreviewInfo("TIME", ticket.time)
                                    SharePreviewInfo("SEAT", ticket.seat)
                                }
                                Text(ticket.location.ifBlank { "-" }, color = Muted, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ShareTheme.values().forEach { theme ->
                            Box(
                                Modifier.size(34.dp).clip(RoundedCornerShape(99.dp)).background(Color(theme.bgColor)).clickable { onTheme(theme) },
                                Alignment.Center
                            ) {
                                if (theme == selected) Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            },
            dismissButton = { TextButton(onClose) { Text(T.close) } },
            confirmButton = { Button(onShare, colors = ButtonDefaults.buttonColors(containerColor = Blue), shape = RoundedCornerShape(12.dp)) { Text(T.shareToSocial) } }
        )
    }

    @Composable
    private fun SharePreviewInfo(label: String, value: String) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text(value.ifBlank { "-" }, color = Ink, fontSize = 15.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }

    private fun createReminderChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel(REMINDER_CHANNEL_ID, T.reminders, NotificationManager.IMPORTANCE_HIGH))
        }
    }

    private fun scheduleReminder(event: EventItem, minutesBefore: Int) {
        val saleMillis = parseEventTime(event.saleTime)
        val triggerAt = (saleMillis - minutesBefore * 60_000L).coerceAtLeast(System.currentTimeMillis() + 5_000L)
        val intent = Intent(this, ReminderReceiver::class.java).putExtra("title", event.title).putExtra("message", "${T.saleTime}: ${event.saleTime}")
        val pending = PendingIntent.getBroadcast(this, (event.id * 1000 + minutesBefore), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val alarm = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        runCatching {
            alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        }.onFailure {
            alarm.set(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        }
        toast(T.reminderSaved)
    }

    private fun loadLocalReminders(): List<LocalReminder> = runCatching {
        val array = JSONArray(getSharedPreferences("ticket_reminders", Context.MODE_PRIVATE).getString("items", "[]"))
        List(array.length()) { i ->
            val o = array.getJSONObject(i)
            LocalReminder(
                eventId = o.optInt("eventId"),
                title = o.optString("title"),
                artist = o.optString("artist"),
                saleTime = o.optString("saleTime"),
                activityTime = o.optString("activityTime"),
                venue = o.optString("venue"),
                price = o.optString("price"),
                source = o.optString("source"),
                url = o.optString("url"),
                minutesBefore = o.optInt("minutesBefore", 60)
            )
        }
    }.getOrDefault(emptyList())

    private fun saveLocalReminders(reminders: List<LocalReminder>) = runCatching {
        val array = JSONArray()
        reminders.forEach {
            array.put(
                JSONObject()
                    .put("eventId", it.eventId)
                    .put("title", it.title)
                    .put("artist", it.artist)
                    .put("saleTime", it.saleTime)
                    .put("activityTime", it.activityTime)
                    .put("venue", it.venue)
                    .put("price", it.price)
                    .put("source", it.source)
                    .put("url", it.url)
                    .put("minutesBefore", it.minutesBefore)
            )
        }
        getSharedPreferences("ticket_reminders", Context.MODE_PRIVATE).edit().putString("items", array.toString()).apply()
    }

    private fun loadWalletTickets(): List<WalletTicket> = runCatching { val array = JSONArray(getSharedPreferences("ticket_wallet", Context.MODE_PRIVATE).getString("tickets", "[]")); List(array.length()) { i -> val o = array.getJSONObject(i); WalletTicket(o.optLong("id"), o.optString("title"), o.optString("date"), o.optString("location"), o.optString("seat"), o.optString("cast"), o.optString("platform"), o.optString("price"), o.optString("notes"), o.optString("imagePath"), o.optString("time")) } }.getOrDefault(emptyList())
    private fun saveWalletTickets(tickets: List<WalletTicket>) = runCatching { val array = JSONArray(); tickets.forEach { array.put(JSONObject().put("id", it.id).put("title", it.title).put("date", it.date).put("location", it.location).put("seat", it.seat).put("cast", it.cast).put("platform", it.platform).put("price", it.price).put("notes", it.notes).put("imagePath", it.imagePath).put("time", it.time)) }; getSharedPreferences("ticket_wallet", Context.MODE_PRIVATE).edit().putString("tickets", array.toString()).apply() }
    private fun encryptedSessionPrefs() = EncryptedSharedPreferences.create(
        this,
        "user_session",
        MasterKey.Builder(this).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private fun currentAuthToken(): String = runCatching {
        encryptedSessionPrefs().getString("auth_token", null).orEmpty()
    }.getOrDefault("")

    private fun hasStoredAuthToken(): Boolean = currentAuthToken().isNotBlank()

    private fun saveAuthSession(token: String, user: JSONObject) {
        encryptedSessionPrefs().edit()
            .putString("auth_token", token)
            .putInt("user_id", user.optInt("id"))
            .putString("email", user.optString("email"))
            .apply()
        getSharedPreferences("app_prefs", Context.MODE_PRIVATE).edit()
            .putString("auth_token", token)
            .putString("email", user.optString("email"))
            .putInt("user_id", user.optInt("id"))
            .apply()
    }

    private fun clearAuthSession() {
        runCatching { encryptedSessionPrefs().edit().clear().apply() }
        listOf("app_prefs", "auth", "auth_session", "user_session").forEach { name ->
            getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().apply()
        }
        getSharedPreferences("app_prefs", Context.MODE_PRIVATE).edit()
            .remove("auth_token")
            .remove("token")
            .remove("user_id")
            .remove("email")
            .apply()
    }

    private fun authenticateUser(
        email: String,
        password: String,
        register: Boolean,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val payload = JSONObject()
            .put("email", email)
            .put("password", password)
            .toString()
        val path = if (register) "/api/auth/register" else "/api/auth/login"
        val request = Request.Builder()
            .url("$API_BASE_URL$path")
            .post(payload.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread { onError(e.message.orEmpty()) }
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    runOnUiThread { onError(authErrorMessage(body, response.code)) }
                    return
                }
                runCatching {
                    val json = JSONObject(body)
                    val token = json.optString("token")
                    if (token.isBlank()) error("missing token")
                    saveAuthSession(token, json.optJSONObject("user") ?: JSONObject())
                }.onSuccess {
                    runOnUiThread(onSuccess)
                }.onFailure {
                    runOnUiThread { onError(T.loginFailed) }
                }
            }
        })
    }

    private fun authErrorMessage(body: String, code: Int): String {
        val error = runCatching { JSONObject(body).optString("error") }.getOrDefault("")
        return when (error) {
            "invalid_credentials" -> T.invalidCredentials
            "email_exists" -> T.emailExists
            "invalid_fields" -> T.invalidRegisterFields
            "missing_fields" -> T.missingLoginFields
            else -> body.ifBlank { "${T.loginFailed} ($code)" }
        }
    }

    private fun isValidEmail(value: String): Boolean =
        Regex("""^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$""").matches(value)

    private fun performLogout() {
        clearAuthSession()
    }
    private fun openUrl(url: String) = runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }.onFailure { toast(T.cannotOpenLink) }
    private fun openParkingMap(lot: ParkingLot) {
        val navigationUri = Uri.parse("google.navigation:q=${lot.lat},${lot.lng}")
        val mapIntent = Intent(Intent.ACTION_VIEW, navigationUri).setPackage("com.google.android.apps.maps")
        runCatching {
            startActivity(mapIntent)
        }.onFailure {
            val webUri = Uri.parse("https://www.google.com/maps/dir/?api=1&destination=${lot.lat},${lot.lng}&travelmode=driving")
            runCatching { startActivity(Intent(Intent.ACTION_VIEW, webUri)) }.onFailure { toast(T.cannotOpenLink) }
        }
    }

    private fun fetchPlaces(
        type: String,
        lat: Double,
        lng: Double,
        radius: Int,
        keyword: String,
        onSuccess: (List<ParkingLot>, List<RestaurantItem>) -> Unit,
        onError: () -> Unit
    ) {
        val uri = Uri.parse("$API_BASE_URL/api/parking/places").buildUpon()
            .appendQueryParameter("type", type)
            .appendQueryParameter("lat", lat.toString())
            .appendQueryParameter("lng", lng.toString())
            .appendQueryParameter("radius", radius.toString())
            .appendQueryParameter("keyword", keyword)
            .build()
        val requestBuilder = Request.Builder().url(uri.toString())
        currentAuthToken().takeIf { it.isNotBlank() }?.let { requestBuilder.header("Authorization", "Bearer $it") }
        val request = requestBuilder.build()
        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread(onError)
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string().orEmpty()
                runCatching {
                    val payload = JSONObject(body)
                    val array = payload.optJSONArray("items") ?: JSONArray()
                    if (payload.optString("source") == "fallback") {
                        val googleError = payload.optString("googleError")
                        val message = payload.optString("message").ifBlank {
                            if (googleError.isNotBlank()) "Google Places: $googleError" else T.googleServiceUnavailable
                        }
                        runOnUiThread { toast(message) }
                    }
                    val parking = mutableListOf<ParkingLot>()
                    val restaurants = mutableListOf<RestaurantItem>()
                    for (i in 0 until array.length()) {
                        val obj = array.getJSONObject(i)
                        val name = obj.optString("name")
                        val address = obj.optString("address")
                        val pLat = obj.optDouble("latitude")
                        val pLng = obj.optDouble("longitude")
                        val source = obj.optString("source", "")
                        if (type == "restaurant") {
                            restaurants.add(RestaurantItem(name, T.foodMeal, T.priceMid, inferCityFromAddress(address), address, placeOpenText(obj), source))
                        } else {
                            parking.add(ParkingLot(name, address, pLat, pLng, obj.optInt("spaces", 0), source))
                        }
                    }
                    runOnUiThread { onSuccess(parking, restaurants) }
                }.onFailure {
                    runOnUiThread(onError)
                }
            }
        })
    }

    private fun refreshEventCount(onDone: (Int) -> Unit) {
        apiGet("/api/events/count", { body ->
            val total = runCatching { JSONObject(body).optInt("total", 0) }.getOrDefault(0)
            onDone(total)
        }, {
            onDone(0)
        })
    }

    private fun fetchRandomEvents(onDone: (List<EventItem>) -> Unit) {
        apiGet("/api/events/random?limit=5", { body ->
            onDone(runCatching { parseEventsResponse(body) }.getOrDefault(emptyList()))
        }, {
            onDone(emptyList())
        })
    }

    private fun fetchArtistProfile(eventId: Int, onDone: (ArtistProfile?, List<ArtistNews>, List<ArtistCategory>) -> Unit) {
        apiGet("/api/events/$eventId/artist-profile", { body ->
            runCatching {
                val root = JSONObject(body)
                val artistObj = root.optJSONObject("artist")
                val profile = artistObj?.let {
                    ArtistProfile(
                        id = it.optInt("id"),
                        name = it.optString("name"),
                        wikiIntro = it.optString("wikiIntro"),
                        wikiUrl = it.optString("wikiUrl")
                    )
                }
                val newsArray = root.optJSONArray("news") ?: JSONArray()
                val news = List(newsArray.length()) { i ->
                    val obj = newsArray.getJSONObject(i)
                    ArtistNews(obj.optString("title"), obj.optString("url"), obj.optString("publishedAt"))
                }
                val catArray = root.optJSONArray("categories") ?: JSONArray()
                val categories = List(catArray.length()) { i ->
                    val obj = catArray.getJSONObject(i)
                    ArtistCategory(obj.optString("genre"), obj.optString("language"))
                }
                onDone(profile, news, categories)
            }.onFailure { onDone(null, emptyList(), emptyList()) }
        }, {
            onDone(null, emptyList(), emptyList())
        })
    }

    private fun fetchVenues(onDone: (List<VenueInfo>, String) -> Unit) {
        apiGet("/api/venues?limit=150", { body ->
            runCatching {
                val array = JSONObject(body).optJSONArray("items") ?: JSONArray()
                val list = List(array.length()) { i ->
                    val obj = array.getJSONObject(i)
                    VenueInfo(
                        id = obj.optInt("id"),
                        name = obj.optString("name"),
                        address = obj.optString("address"),
                        city = obj.optString("city"),
                        description = obj.optString("description")
                    )
                }
                onDone(list, "")
            }.onFailure { onDone(emptyList(), T.dataConnectionFailed) }
        }, { message ->
            onDone(emptyList(), message.ifBlank { T.dataConnectionFailed })
        })
    }

    private fun discoveryQuery(keyword: String, source: String, timeRange: String, priceRange: String): String {
        val builder = Uri.parse("$API_BASE_URL/api/recommendations").buildUpon()
            .appendQueryParameter("limit", "120")
        keyword.trim().takeIf { it.isNotBlank() }?.let { builder.appendQueryParameter("keyword", it) }
        source.trim().takeIf { it.isNotBlank() }?.let { builder.appendQueryParameter("source", it) }
        discoveryDateRange(timeRange)?.let { (start, end) ->
            builder.appendQueryParameter("startDate", start)
            builder.appendQueryParameter("endDate", end)
        }
        discoveryPriceRange(priceRange)?.let { (min, max) ->
            if (min > 0) builder.appendQueryParameter("minPrice", min.toString())
            if (max > 0) builder.appendQueryParameter("maxPrice", max.toString())
        }
        return builder.build().toString()
    }

    private fun discoveryDateRange(timeRange: String): Pair<String, String>? {
        if (timeRange == T.all) return null
        val start = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val end = start.clone() as Calendar
        when (timeRange) {
            T.thisMonth -> {
                start.set(Calendar.DAY_OF_MONTH, 1)
                end.set(Calendar.DAY_OF_MONTH, end.getActualMaximum(Calendar.DAY_OF_MONTH))
            }
            T.nextMonth -> {
                start.add(Calendar.MONTH, 1)
                start.set(Calendar.DAY_OF_MONTH, 1)
                end.timeInMillis = start.timeInMillis
                end.set(Calendar.DAY_OF_MONTH, end.getActualMaximum(Calendar.DAY_OF_MONTH))
            }
            T.thisYear -> {
                start.set(Calendar.DAY_OF_YEAR, 1)
                end.set(Calendar.MONTH, Calendar.DECEMBER)
                end.set(Calendar.DAY_OF_MONTH, 31)
            }
            else -> return null
        }
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.TAIWAN)
        end.set(Calendar.HOUR_OF_DAY, 23)
        end.set(Calendar.MINUTE, 59)
        end.set(Calendar.SECOND, 59)
        return fmt.format(start.time) to fmt.format(end.time)
    }

    private fun discoveryPriceRange(priceRange: String): Pair<Int, Int>? = when (priceRange) {
        T.priceUnder1000 -> 0 to 1000
        T.price1000To3000 -> 1000 to 3000
        T.priceOver3000 -> 3000 to 0
        else -> null
    }

    private fun parseEventsResponse(body: String): List<EventItem> {
        val array = JSONObject(body).optJSONArray("items") ?: JSONArray()
        return List(array.length()) { i ->
            val obj = array.getJSONObject(i)
            EventItem(
                id = obj.optInt("id", i),
                title = obj.optString("title").ifBlank { obj.optString("name", T.untitledTicket) },
                artist = obj.optString("artist"),
                saleTime = obj.optString("saleTime"),
                activityTime = obj.optString("activityTime").ifBlank { obj.optString("event_time") },
                venue = obj.optString("venue"),
                address = obj.optString("address"),
                price = obj.optString("price").ifBlank { obj.optString("priceText") },
                ticketType = obj.optString("ticketType"),
                url = obj.optString("url"),
                source = obj.optString("source")
            )
        }
    }

    private fun shareTicketImage(ticket: WalletTicket, theme: ShareTheme) {
        val file = runCatching { renderShareTicket(ticket, theme) }.getOrElse {
            toast(T.shareFailed)
            return
        }
        val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching {
            startActivity(Intent.createChooser(shareIntent, T.shareToSocial))
        }.onFailure {
            toast(T.shareFailed)
        }
    }

    private fun renderShareTicket(ticket: WalletTicket, theme: ShareTheme): File {
        val width = 1080
        val height = 1920
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = theme.bgColor }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bg)

        val white = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AndroidColor.WHITE }
        val card = RectF(110f, 340f, 970f, 1540f)
        canvas.drawRoundRect(card, 54f, 54f, white)

        val imageRect = RectF(card.left, card.top, card.right, card.top + 570f)
        loadTicketBitmap(ticket.imagePath)?.let { source ->
            val src = centerCropRect(source.width, source.height, imageRect.width().toInt(), imageRect.height().toInt())
            canvas.drawBitmap(source, src, imageRect, null)
        } ?: run {
            val imgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AndroidColor.rgb(228, 235, 255) }
            canvas.drawRect(imageRect, imgPaint)
        }

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.rgb(17, 24, 39)
            textSize = 54f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        drawWrappedText(canvas, ticket.title.ifBlank { T.untitledTicket }, 190f, 1020f, 700f, titlePaint, 72f, 3)

        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AndroidColor.rgb(216, 203, 190); strokeWidth = 3f }
        canvas.drawLine(170f, 1160f, 910f, 1160f, linePaint)

        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.rgb(120, 120, 128)
            textSize = 28f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.BLACK
            textSize = 42f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        drawShareInfo(canvas, "DATE", ticket.date.takeLast(5).replace("-", "/"), 250f, 1260f, labelPaint, valuePaint)
        drawShareInfo(canvas, "TIME", ticket.time.ifBlank { "--:--" }, 540f, 1260f, labelPaint, valuePaint)
        drawShareInfo(canvas, "SEAT", ticket.seat.ifBlank { "-" }, 830f, 1260f, labelPaint, valuePaint)

        val venuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.rgb(120, 120, 128)
            textSize = 38f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(ticket.location.ifBlank { "-" }, 540f, 1450f, venuePaint)

        val topPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.argb(180, 255, 255, 255)
            textSize = 40f
            letterSpacing = 0.12f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText("TICKET KEEPER", 540f, 250f, topPaint)
        val hashPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.WHITE
            textSize = 38f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("#MyEventDiary", 540f, 1760f, hashPaint)

        val dir = File(cacheDir, "shared_tickets").apply { mkdirs() }
        val file = File(dir, "ticket_share_${ticket.id}_${System.currentTimeMillis()}.jpg")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 94, it) }
        return file
    }

    private fun loadTicketBitmap(path: String): Bitmap? = runCatching {
        if (path.isBlank()) return@runCatching null
        val uri = Uri.parse(path)
        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
    }.getOrNull()

    private fun centerCropRect(srcWidth: Int, srcHeight: Int, dstWidth: Int, dstHeight: Int): Rect {
        val srcRatio = srcWidth.toFloat() / srcHeight.toFloat()
        val dstRatio = dstWidth.toFloat() / dstHeight.toFloat()
        return if (srcRatio > dstRatio) {
            val newWidth = (srcHeight * dstRatio).toInt()
            val left = (srcWidth - newWidth) / 2
            Rect(left, 0, left + newWidth, srcHeight)
        } else {
            val newHeight = (srcWidth / dstRatio).toInt()
            val top = (srcHeight - newHeight) / 2
            Rect(0, top, srcWidth, top + newHeight)
        }
    }

    private fun drawShareInfo(canvas: Canvas, label: String, value: String, x: Float, y: Float, labelPaint: Paint, valuePaint: Paint) {
        canvas.drawText(label, x, y, labelPaint)
        canvas.drawText(value, x, y + 62f, valuePaint)
    }

    private fun drawWrappedText(canvas: Canvas, text: String, x: Float, y: Float, maxWidth: Float, paint: Paint, lineHeight: Float, maxLines: Int) {
        val words = text.chunked(1)
        var line = ""
        var currentY = y
        var lineCount = 0
        for (word in words) {
            val next = line + word
            if (paint.measureText(next) > maxWidth && line.isNotBlank()) {
                canvas.drawText(line, x, currentY, paint)
                currentY += lineHeight
                lineCount++
                line = word
                if (lineCount >= maxLines - 1) break
            } else {
                line = next
            }
        }
        if (line.isNotBlank()) {
            val finalLine = if (paint.measureText(line) > maxWidth) line.take(18) + "..." else line
            canvas.drawText(finalLine, x, currentY, paint)
        }
    }

    private fun apiGet(path: String, onSuccess: (String) -> Unit, onError: (String) -> Unit) {
        val url = if (path.startsWith("http")) path else "$API_BASE_URL$path"
        val requestBuilder = Request.Builder().url(url)
        currentAuthToken().takeIf { it.isNotBlank() }?.let { requestBuilder.header("Authorization", "Bearer $it") }
        val request = requestBuilder.build()
        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread { onError(e.message.orEmpty()) }
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string().orEmpty()
                runOnUiThread {
                    if (response.isSuccessful) onSuccess(body) else onError(body.ifBlank { response.message })
                }
            }
        })
    }

    private fun apiPost(path: String, payload: String, onSuccess: (String) -> Unit, onError: (String) -> Unit) {
        val url = if (path.startsWith("http")) path else "$API_BASE_URL$path"
        val requestBuilder = Request.Builder()
            .url(url)
            .post(payload.toRequestBody("application/json; charset=utf-8".toMediaType()))
        currentAuthToken().takeIf { it.isNotBlank() }?.let { requestBuilder.header("Authorization", "Bearer $it") }
        val request = requestBuilder.build()
        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread { onError(e.message.orEmpty()) }
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string().orEmpty()
                runOnUiThread {
                    if (response.isSuccessful) onSuccess(body) else onError(body.ifBlank { response.message })
                }
            }
        })
    }
    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    private fun demoEvents() = demoEventList()
}

private const val REMINDER_CHANNEL_ID = "ticket_reminders"

private data class LocalReminder(
    val eventId: Int,
    val title: String,
    val artist: String,
    val saleTime: String,
    val activityTime: String,
    val venue: String,
    val price: String,
    val source: String,
    val url: String,
    val minutesBefore: Int
) {
    fun toEventItem() = EventItem(eventId, title, artist, saleTime, activityTime, venue, "", price, source, url, source)
}

private data class ArtistProfile(val id: Int, val name: String, val wikiIntro: String, val wikiUrl: String)
private data class ArtistNews(val title: String, val url: String, val publishedAt: String)
private data class ArtistCategory(val genre: String, val language: String)
private data class VenueInfo(val id: Int, val name: String, val address: String, val city: String, val description: String)
private enum class ShareTheme(val bgColor: Int) {
    Purple(AndroidColor.rgb(49, 28, 126)),
    Pink(AndroidColor.rgb(237, 111, 153)),
    Dark(AndroidColor.rgb(12, 17, 31)),
    Blue(AndroidColor.rgb(36, 92, 255))
}

private data class OcrTicketFields(
    val title: String = "",
    val date: String = "",
    val time: String = "",
    val place: String = "",
    val seat: String = "",
    val cast: String = ""
) {
    val hasCoreData: Boolean get() = listOf(title, date, time, place).count { it.isNotBlank() } >= 3
}

private data class RestaurantItem(val name: String, val category: String, val priceBand: String, val city: String, val address: String, val openingHours: String, val source: String = "")
private data class SeatVenue(val id: String, val name: String, val subtitle: String, val color: Color, val region: String, val links: List<SeatVenueLink>)
private data class SeatVenueLink(val label: String, val url: String)
private data class ParkingVenue(val name: String, val lat: Double, val lng: Double)
private data class ParkingLot(val name: String, val address: String, val lat: Double, val lng: Double, val spaces: Int, val source: String = "")
private data class BudgetAllocation(val label: String, val percent: Int, val amount: Int, val color: Color)
private data class AnalysisResult(val platformStats: Map<String, Int>, val monthlyStats: Map<Int, Int>)

private fun upsertReminder(reminders: List<LocalReminder>, event: EventItem, minutes: Int): List<LocalReminder> {
    val item = LocalReminder(event.id, event.title, event.artist, event.saleTime, event.activityTime, event.venue, event.price, event.source, event.url, minutes)
    return listOf(item) + reminders.filterNot { it.eventId == event.id }
}

private fun reminderLabel(minutes: Int): String = when (minutes) {
    0 -> T.onTimeReminder
    15 -> T.before15Min
    60 -> T.before1Hour
    1440 -> T.before1Day
    else -> T.beforeCustom.replace("%d", minutes.toString())
}

private fun createTicketCameraUri(context: Context): Uri {
    val dir = File(context.filesDir, "ticket_images").apply { mkdirs() }
    val file = File(dir, "scan_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

private fun runTicketOcr(context: Context, uri: Uri, onDone: (OcrTicketFields) -> Unit, onError: () -> Unit) {
    val image = runCatching { InputImage.fromFilePath(context, uri) }.getOrElse { onError(); return }
    TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        .process(image)
        .addOnSuccessListener { result -> onDone(parseTicketText(result.text)) }
        .addOnFailureListener { onError() }
}

private fun parseTicketText(text: String): OcrTicketFields {
    val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
    val joined = lines.joinToString("\n")
    val date = extractDate(joined)
    val time = Regex("""\b([01]?\d|2[0-3])[:\uFF1A]([0-5]\d)\b""").find(joined)?.value.orEmpty()
    val rawPlace = findLabeledValue(lines, listOf("\u5730\u9ede", "\u5834\u5730", "Location", "Place", "Venue", "PLACE"))
        .ifBlank { lines.firstOrNull { it.contains("Arena", true) || it.contains("Center", true) || it.contains("Hall", true) || it.contains("\u5de8\u86cb") || it.contains("\u4e2d\u5fc3") || it.contains("\u516c\u5712") }.orEmpty() }
    val place = correctByWhitelist(rawPlace, joined, venueWhitelist())
    val seat = findLabeledValue(lines, listOf("\u5ea7\u4f4d", "Seat", "SEAT", "\u5340", "\u6392", "\u865f"))
    val labeledTitle = findLabeledValue(lines, listOf("\u7bc0\u76ee", "EVENT", "Event"))
    val title = labeledTitle.ifBlank {
        lines.firstOrNull { line ->
            line.length >= 4 &&
                !line.contains("FamilyMart", true) &&
                !line.contains("KKTIX", true) &&
                !line.contains(Regex("""\d{4}[-/]\d{1,2}[-/]\d{1,2}""")) &&
                !line.contains(Regex("""\b([01]?\d|2[0-3])[:\uFF1A]([0-5]\d)\b""")) &&
                !line.contains("\u7968\u50f9") &&
                !line.contains("\u5ea7\u4f4d") &&
                !line.contains("\u5730\u9ede")
        }.orEmpty()
    }
    val rawCast = extractCast(title)
    return OcrTicketFields(
        title = title,
        date = date,
        time = time,
        place = place,
        seat = seat,
        cast = correctByWhitelist(rawCast, joined, artistWhitelist())
    )
}

private fun extractDate(text: String): String {
    Regex("""(20\d{2})[-/.\u5e74 ](\d{1,2})[-/.\u6708 ](\d{1,2})""").find(text)?.let {
        return "%04d-%02d-%02d".format(it.groupValues[1].toInt(), it.groupValues[2].toInt(), it.groupValues[3].toInt())
    }
    Regex("""(\d{1,2})[-/.\u6708 ](\d{1,2})[-/.\u65e5, ]+(20\d{2})""").find(text)?.let {
        return "%04d-%02d-%02d".format(it.groupValues[3].toInt(), it.groupValues[1].toInt(), it.groupValues[2].toInt())
    }
    return ""
}

private fun findLabeledValue(lines: List<String>, labels: List<String>): String {
    val line = lines.firstOrNull { candidate -> labels.any { candidate.contains(it, ignoreCase = true) } }.orEmpty()
    return line.replace(Regex(labels.joinToString("|") { Regex.escape(it) }, RegexOption.IGNORE_CASE), "")
        .replace(":", "")
        .replace("\uFF1A", "")
        .trim()
}

private fun extractCast(title: String): String {
    if (title.isBlank()) return ""
    val separators = listOf(" ASIA ", " WORLD ", " TOUR", " LIVE", " CONCERT", "\u6f14\u5531\u6703")
    val index = separators.map { title.indexOf(it, ignoreCase = true) }.filter { it > 0 }.minOrNull()
    return if (index != null) title.take(index).trim() else title.split(" ").firstOrNull().orEmpty()
}

private fun venueWhitelist() = listOf(
    "TICC",
    "Taipei Arena",
    "Taipei Dome",
    "Taipei Music Center",
    "Legacy TERA",
    "Legacy Taipei",
    "Zepp New Taipei",
    "\u53f0\u5317\u5c0f\u5de8\u86cb",
    "\u53f0\u5317\u5927\u5de8\u86cb",
    "\u53f0\u5317\u6d41\u884c\u97f3\u6a02\u4e2d\u5fc3",
    "\u53f0\u5317\u5927\u4f73\u6cb3\u6ff1\u516c\u5712",
    "\u53f0\u5317\u5e02\u4e2d\u5c71\u5340\u6ff1\u6c5f\u885755\u865f",
    "\u9ad8\u96c4\u5de8\u86cb",
    "\u9ad8\u96c4\u6d41\u884c\u97f3\u6a02\u4e2d\u5fc3"
)

private fun artistWhitelist() = listOf(
    "QWER",
    "BABYMONSTER",
    "BLACKPINK",
    "Mayday",
    "Accusefive",
    "aespa",
    "JJ Lin",
    "Jay Chou",
    "NewJeans",
    "SEVENTEEN",
    "LE SSERAFIM",
    "YOASOBI",
    "IVE",
    "Utada Hikaru",
    "BanG Dream!"
)

private fun correctByWhitelist(value: String, fullText: String, whitelist: List<String>): String {
    val direct = whitelist.firstOrNull { fullText.contains(it, ignoreCase = true) }
    if (direct != null) return direct
    if (value.isBlank()) return ""
    val best = whitelist.map { it to similarity(value, it) }.maxByOrNull { it.second }
    return if (best != null && best.second >= 0.42f) best.first else value
}

private fun isChinese(value: String): Boolean = value.any { it in '\u4e00'..'\u9fff' }

private fun similarity(a: String, b: String): Float {
    val left = a.lowercase().filter { it.isLetterOrDigit() || isChinese(it.toString()) }
    val right = b.lowercase().filter { it.isLetterOrDigit() || isChinese(it.toString()) }
    if (left.isBlank() || right.isBlank()) return 0f
    val maxLen = maxOf(left.length, right.length)
    return 1f - levenshtein(left, right).toFloat() / maxLen.toFloat()
}

private fun levenshtein(a: String, b: String): Int {
    val costs = IntArray(b.length + 1) { it }
    for (i in a.indices) {
        var last = i
        costs[0] = i + 1
        for (j in b.indices) {
            val old = costs[j + 1]
            costs[j + 1] = minOf(costs[j + 1] + 1, costs[j] + 1, last + if (a[i] == b[j]) 0 else 1)
            last = old
        }
    }
    return costs[b.length]
}

private fun parkingVenues() = listOf(
    ParkingVenue("\u53f0\u5317\u5c0f\u5de8\u86cb", 25.0514, 121.5517),
    ParkingVenue("\u53f0\u5317\u5927\u5de8\u86cb", 25.0439, 121.5601),
    ParkingVenue("Zepp New Taipei", 25.0612, 121.4497),
    ParkingVenue("TICC", 25.0336, 121.5605)
)

private fun parkingLots() = listOf(
    ParkingLot("\u5c0f\u5de8\u86cb\u5730\u4e0b\u505c\u8eca\u5834", "\u53f0\u5317\u5e02\u677e\u5c71\u5340\u5357\u4eac\u6771\u8def", 25.0512, 121.5514, 42),
    ParkingLot("\u5317\u5be7\u8def\u505c\u8eca\u5834", "\u53f0\u5317\u5e02\u677e\u5c71\u5340\u5317\u5be7\u8def", 25.0528, 121.5532, 18),
    ParkingLot("\u677e\u7159\u5730\u4e0b\u505c\u8eca\u5834", "\u53f0\u5317\u5e02\u4fe1\u7fa9\u5340\u5149\u5fa9\u5357\u8def", 25.0436, 121.5605, 65),
    ParkingLot("\u65b0\u5317\u7522\u696d\u5712\u5340\u505c\u8eca\u5834", "\u65b0\u5317\u5e02\u65b0\u838a\u5340\u4e94\u5de5\u8def", 25.0618, 121.4509, 33)
)

private fun inferCityFromAddress(address: String): String = when {
    address.contains("\u65b0\u5317") || address.contains("New Taipei", true) -> T.newTaipeiCity
    address.contains("\u9ad8\u96c4") || address.contains("Kaohsiung", true) -> T.kaohsiungCity
    else -> T.taipeiCity
}

private fun placeOpenText(obj: JSONObject): String = when (obj.opt("openNow")) {
    true -> "\u71df\u696d\u4e2d"
    false -> "\u672a\u71df\u696d"
    else -> ""
}

private fun normalizeCoordinate(value: Double, min: Double, max: Double): Double = if (value.isFinite()) value.coerceIn(min, max) else 0.0

private fun distanceMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
    val radius = 6371000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLng = Math.toRadians(lng2 - lng1)
    val a = kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
        kotlin.math.cos(Math.toRadians(lat1)) * kotlin.math.cos(Math.toRadians(lat2)) *
        kotlin.math.sin(dLng / 2) * kotlin.math.sin(dLng / 2)
    return radius * 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
}

private fun demoEventList() = listOf(
    EventItem(1, "QWER 1ST WORLD TOUR", "QWER", "2026-01-02 12:00", "2026-02-14 17:00", "TICC", "Taipei", "NT$ 4200", "KKTIX", "https://kktix.com/events", "KKTIX"),
    EventItem(2, "BABYMONSTER PRESENTS SEE YOU THERE", "BABYMONSTER", "2026-05-20 12:00", "2026-08-18", "Kaohsiung Arena", "Kaohsiung", "NT$ 2300 - 4900", "KKTIX", "https://kktix.com/events", "KKTIX"),
    EventItem(3, "Mayday 25th Anniversary", "Mayday", "2026-03-01 12:00", "2026-09-12 19:30", "Taipei Dome", "Taipei", "NT$ 1880 - 5880", "tixCraft", "https://tixcraft.com/activity", "tixCraft"),
    EventItem(4, "BLACKPINK WORLD TOUR", "BLACKPINK", "2026-04-10 12:00", "2026-10-03 19:00", "Kaohsiung National Stadium", "Kaohsiung", "NT$ 2800 - 6800", "Live Nation", "https://www.livenation.com.tw", "Live Nation"),
    EventItem(5, "Accusefive Tour", "Accusefive", "2026-02-18 12:00", "2026-07-20 19:30", "Taichung Intercontinental Stadium", "Taichung", "NT$ 1200 - 3800", "tixCraft", "https://tixcraft.com/activity", "tixCraft"),
    EventItem(6, "aespa LIVE TOUR", "aespa", "2026-06-03 12:00", "2026-11-08 18:00", "Taipei Arena", "Taipei", "NT$ 2400 - 5800", "KKTIX", "https://kktix.com/events", "KKTIX"),
    EventItem(7, "JJ Lin JJ20", "JJ Lin", "2026-01-30 12:00", "2026-08-09 19:30", "Taipei Dome", "Taipei", "NT$ 1800 - 6600", "tixCraft", "https://tixcraft.com/activity", "tixCraft"),
    EventItem(8, "Jay Chou Carnival", "Jay Chou", "2026-07-11 12:00", "2026-12-19 19:30", "Kaohsiung Arena", "Kaohsiung", "NT$ 2280 - 6880", "tixCraft", "https://tixcraft.com/activity", "tixCraft"),
    EventItem(9, "NewJeans FAN MEETING", "NewJeans", "2026-05-02 12:00", "2026-09-26 18:00", "NTSU Arena", "New Taipei", "NT$ 2000 - 5200", "KKTIX", "https://kktix.com/events", "KKTIX"),
    EventItem(10, "SEVENTEEN TOUR", "SEVENTEEN", "2026-04-22 12:00", "2026-10-17 18:30", "Taoyuan Baseball Stadium", "Taoyuan", "NT$ 2500 - 6500", "tixCraft", "https://tixcraft.com/activity", "tixCraft"),
    EventItem(11, "LE SSERAFIM TOUR", "LE SSERAFIM", "2026-03-15 12:00", "2026-08-23 18:00", "Taipei Music Center", "Taipei", "NT$ 1800 - 4800", "KKTIX", "https://kktix.com/events", "KKTIX"),
    EventItem(12, "Sunset Rollercoaster", "Sunset Rollercoaster", "2026-02-28 12:00", "2026-06-28 19:00", "Legacy Taipei", "Taipei", "NT$ 1500 - 2800", "iNDIEVOX", "https://www.indievox.com", "iNDIEVOX"),
    EventItem(13, "IVE SHOW WHAT I HAVE", "IVE", "2026-06-20 12:00", "2026-11-21 18:00", "Taipei Arena", "Taipei", "NT$ 2200 - 5600", "KKTIX", "https://kktix.com/events", "KKTIX"),
    EventItem(14, "YOASOBI ASIA TOUR", "YOASOBI", "2026-05-30 12:00", "2026-09-06 19:00", "Xinzhuang Gymnasium", "New Taipei", "NT$ 1800 - 4800", "tixCraft", "https://tixcraft.com/activity", "tixCraft"),
    EventItem(15, "Utada Hikaru SCIENCE FICTION", "Utada Hikaru", "2026-07-01 12:00", "2026-12-05 19:00", "Taipei Music Center", "Taipei", "NT$ 2600 - 6200", "KKTIX", "https://kktix.com/events", "KKTIX")
)

private fun restaurantSamples() = listOf(
    RestaurantItem("Taipei Arena Cafe", T.foodCafe, T.priceMid, T.taipeiCity, "Taipei Arena, Nanjing E. Rd.", "11:00-21:00"),
    RestaurantItem("Songshan Beef Noodles", T.foodMeal, T.priceMid, T.taipeiCity, "Songshan District, Taipei", "10:30-20:30"),
    RestaurantItem("TICC Bistro", T.foodMeal, T.priceHigh, T.taipeiCity, "Xinyi District, Taipei", "11:30-22:00"),
    RestaurantItem("Xinzhuang Dessert Room", T.foodDessert, T.priceMid, T.newTaipeiCity, "Xinzhuang District, New Taipei", "12:00-20:00"),
    RestaurantItem("Banqiao Station Coffee", T.foodCafe, T.priceLow, T.newTaipeiCity, "Banqiao District, New Taipei", "08:00-19:00"),
    RestaurantItem("Kaohsiung Arena Rice Bowl", T.foodMeal, T.priceMid, T.kaohsiungCity, "Zuoying District, Kaohsiung", "11:00-21:30"),
    RestaurantItem("Pier-2 Dessert", T.foodDessert, T.priceLow, T.kaohsiungCity, "Yancheng District, Kaohsiung", "13:00-22:00")
)

private fun seatVenueList() = listOf(
    SeatVenue("big-dome", T.taipeiDome, "Taipei Dome", Color(0xFF003580), T.north, listOf(SeatVenueLink(T.officialSeatMap, "https://www.farglorydome.com.tw/park-detail/map/"), SeatVenueLink(T.fanView, "https://twconcertview.com/venue/taipei-dome/"))),
    SeatVenue("small-dome", T.taipeiArena, "Taipei Arena", Color(0xFF1A73E8), T.north, listOf(SeatVenueLink(T.officialSeatMap, "https://www.arena.taipei/cp.aspx?n=95731497B5FCEDDB&s=8CC41A1D7AFDBA9C"), SeatVenueLink(T.fanView, "https://twconcertview.com/"))),
    SeatVenue("tmc", T.taipeiMusicCenter, T.concertVenue, Color(0xFF4A5568), T.north, listOf(SeatVenueLink(T.officialSeatMap, "https://host.artogo.co/tmc/space/TMCConcertHall/DG4hqq9A8v1"), SeatVenueLink(T.fanView, "https://twconcertview.com/venue/taipei-music-center/"))),
    SeatVenue("ticc", "TICC", T.conventionCenter, Color(0xFF2D3748), T.north, listOf(SeatVenueLink(T.officialSeatMap, "https://www.ticc.com.tw/wSite/sp?xdUrl=/wSite/ap/lp_PlenaryHall.jsp&ctNode=323&CtUnit=100&BaseDSD=7&mp=1"), SeatVenueLink(T.fanView, "https://twconcertview.com/venue/ticc-taipei/"))),
    SeatVenue("ntt", T.ntt, T.centralVenue, Color(0xFFC53030), T.central, listOf(SeatVenueLink(T.officialSeatMap, "https://www.npac-ntt.org/360virtualtour/vr/GT/"), SeatVenueLink(T.fanView, "https://twconcertview.com/venue/taichung-opera-house/"))),
    SeatVenue("kao-arena", T.kaohsiungArena, "Kaohsiung Arena", Color(0xFF0D47A1), T.south, listOf(SeatVenueLink(T.officialSeatMap, "https://www.kaoarena.com.tw/Home/Seat"), SeatVenueLink(T.fanView, "https://twconcertview.com/venue/kaohsiung-arena/"))),
    SeatVenue("kmc", T.kmc, "KMC", Color(0xFF0F766E), T.south, listOf(SeatVenueLink(T.officialInfo, "https://kpmc.com.tw/venue-info/"), SeatVenueLink(T.fanView, "https://twconcertview.com/venue/kaohsiung-music-center/")))
)

private fun calendarDays(month: Calendar): List<WalletCalendarDay> { val first = (month.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }; val cursor = (first.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, -(get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY)) }; val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.TAIWAN); return List(42) { val item = WalletCalendarDay(cursor.get(Calendar.DAY_OF_MONTH), fmt.format(cursor.time), cursor.get(Calendar.MONTH) == first.get(Calendar.MONTH), cursor.get(Calendar.DAY_OF_WEEK)); cursor.add(Calendar.DAY_OF_MONTH, 1); item } }
private fun shiftMonth(month: Calendar, delta: Int) = (month.clone() as Calendar).apply { add(Calendar.MONTH, delta); set(Calendar.DAY_OF_MONTH, 1) }
private fun randomEventSample(events: List<EventItem>): List<EventItem> = events.shuffled(Random(System.nanoTime())).take(10)
private fun parsePrice(value: String) = Regex("""\d+""").findAll(value.replace(",", "")).mapNotNull { it.value.toIntOrNull() }.filter { it in 1..200000 }.maxOrNull() ?: 0
private fun homeStats(tickets: List<WalletTicket>, reminders: List<LocalReminder>, allEvents: List<EventItem>, dbEventTotal: Int): SummaryStats {
    val eventCount = dbEventTotal.takeIf { it > 0 } ?: tickets.size.takeIf { it > 0 } ?: allEvents.size
    val artists = tickets.map { it.cast }.filter { it.isNotBlank() }.distinct().size.takeIf { it > 0 } ?: allEvents.map { it.artist }.filter { it.isNotBlank() }.distinct().size
    val venues = tickets.map { it.location }.filter { it.isNotBlank() }.distinct().size.takeIf { it > 0 } ?: allEvents.map { it.venue }.filter { it.isNotBlank() }.distinct().size
    return SummaryStats(events = eventCount, artists = artists, venues = venues, reminders = reminders.size)
}
private fun List<EventItem>.filterByDiscovery(keyword: String, sources: Set<String>, timeRange: String, priceRange: String): List<EventItem> = filter { event ->
    val keywordOk = keyword.isBlank() || event.title.contains(keyword, true) || event.artist.contains(keyword, true) || event.venue.contains(keyword, true)
    val sourceOk = sources.isEmpty() || sources.contains(event.source)
    keywordOk && sourceOk && event.matchesTimeRange(timeRange) && event.matchesPriceRange(priceRange)
}
private fun EventItem.matchesTimeRange(range: String): Boolean {
    if (range == T.all) return true
    val date = parseDateOrNull(activityTime.take(10)) ?: return true
    val now = Calendar.getInstance()
    val target = Calendar.getInstance().apply { time = date }
    return when (range) {
        T.thisMonth -> target.get(Calendar.YEAR) == now.get(Calendar.YEAR) && target.get(Calendar.MONTH) == now.get(Calendar.MONTH)
        T.nextMonth -> {
            val next = (now.clone() as Calendar).apply { add(Calendar.MONTH, 1) }
            target.get(Calendar.YEAR) == next.get(Calendar.YEAR) && target.get(Calendar.MONTH) == next.get(Calendar.MONTH)
        }
        T.thisYear -> target.get(Calendar.YEAR) == now.get(Calendar.YEAR)
        else -> true
    }
}
private fun EventItem.matchesPriceRange(range: String): Boolean {
    val value = parsePrice(price)
    if (range == T.all || value == 0) return true
    return when (range) {
        T.priceUnder1000 -> value <= 1000
        T.price1000To3000 -> value in 1000..3000
        T.priceOver3000 -> value >= 3000
        else -> true
    }
}
private fun getAnalysisData(tickets: List<WalletTicket>): AnalysisResult {
    val platformStats = tickets.groupingBy { it.platform.ifBlank { T.other } }.eachCount()
    val monthlyStats = tickets.groupBy { ticketMonth(it.date) }.filterKeys { it in 1..12 }.mapValues { entry -> entry.value.sumOf { parsePrice(it.price) } }
    return AnalysisResult(platformStats, monthlyStats)
}
private fun ticketMonth(date: String): Int = date.split("-").getOrNull(1)?.toIntOrNull() ?: 0
private fun parseDateOrNull(value: String) = runCatching { SimpleDateFormat("yyyy-MM-dd", Locale.TAIWAN).parse(value) }.getOrNull()
private fun chartColors() = listOf(Blue, Purple, Color(0xFF0F9F6E), Color(0xFFEAB308), Color(0xFFDB2777), Color(0xFF64748B))
private fun daysUntil(date: String): Int = runCatching { val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.TAIWAN); val target = fmt.parse(date) ?: return@runCatching -1; val today = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }; ((target.time - today.timeInMillis) / (24L * 60L * 60L * 1000L)).toInt() }.getOrDefault(-1)
private fun ticketDateTime(ticket: WalletTicket): String = if (ticket.date.isBlank()) ticket.time else runCatching { val date = SimpleDateFormat("yyyy-MM-dd", Locale.TAIWAN).parse(ticket.date) ?: return@runCatching listOf(ticket.date, ticket.time).filter { it.isNotBlank() }.joinToString(" "); listOf(SimpleDateFormat("MM-dd, yy (E)", Locale.TAIWAN).format(date), ticket.time).filter { it.isNotBlank() }.joinToString(" ") }.getOrDefault(listOf(ticket.date, ticket.time).filter { it.isNotBlank() }.joinToString(" "))
private fun ticketCompactDateTime(ticket: WalletTicket): String = if (ticket.date.isBlank()) ticket.time else runCatching {
    val date = SimpleDateFormat("yyyy-MM-dd", Locale.TAIWAN).parse(ticket.date) ?: return@runCatching listOf(ticket.date, ticket.time).filter { it.isNotBlank() }.joinToString(", ")
    listOf(SimpleDateFormat("MM月-dd", Locale.TAIWAN).format(date), ticket.time).filter { it.isNotBlank() }.joinToString(", ")
}.getOrDefault(listOf(ticket.date, ticket.time).filter { it.isNotBlank() }.joinToString(", "))
private fun formatNumber(value: Int) = NumberFormat.getNumberInstance(Locale.TAIWAN).format(value)
private fun parseDate(value: String) = Calendar.getInstance().apply { runCatching { val p = value.split("-").map { it.toInt() }; if (p.size == 3) set(p[0], p[1] - 1, p[2]) } }
private fun parseTime(value: String): Pair<Int, Int> { val m = Regex("""(\d{1,2})[:\uFF1A](\d{2})""").find(value); return if (m != null) (m.groupValues[1].toIntOrNull()?.coerceIn(0, 23) ?: 18) to (m.groupValues[2].toIntOrNull()?.coerceIn(0, 59) ?: 0) else 18 to 0 }
private fun persistTicketImage(context: Context, sourceUri: Uri): Uri { val dir = File(context.filesDir, "ticket_images").apply { mkdirs() }; val file = File(dir, "ticket_${System.currentTimeMillis()}.jpg"); return runCatching { context.contentResolver.openInputStream(sourceUri).use { input -> FileOutputStream(file).use { out -> input?.copyTo(out) } }; Uri.fromFile(file) }.getOrDefault(sourceUri) }
private fun parseEventTime(value: String): Long = runCatching { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.TAIWAN).parse(value)?.time ?: 0L }.getOrDefault(0L).takeIf { it > 0L } ?: (System.currentTimeMillis() + 60_000L)
private fun artistIntro(artist: String): String = if (artist.isBlank()) T.artistIntroFallback else artist + T.artistIntroSuffix
private fun List<Int>.averageIntOrZero(): Int = if (isEmpty()) 0 else average().toInt()

enum class AppTab(val title: String, val icon: ImageVector) {
    Home(T.home, Icons.Rounded.Home),
    Search(T.search, Icons.Rounded.Search),
    Wallet(T.wallet, Icons.Rounded.ConfirmationNumber),
    Reminders(T.reminders, Icons.Rounded.Notifications),
    More(T.more, Icons.Rounded.MoreHoriz),
    SeatMap(T.seatMap, Icons.Rounded.LocationOn),
    Budget(T.budget, Icons.Rounded.AttachMoney),
    Parking(T.traffic, Icons.Rounded.LocationOn),
    Analysis(T.analysis, Icons.Rounded.Analytics),
    Food(T.food, Icons.Rounded.Restaurant),
    Venues(T.venueGuide, Icons.Rounded.Stadium),
    Account(T.account, Icons.Rounded.AccountCircle)
}

object T {
    const val home = "\u9996\u9801"; const val search = "\u63a2\u7d22"; const val wallet = "\u7968\u5238\u593e"; const val reminders = "\u63d0\u9192"; const val more = "\u66f4\u591a"; const val seatMap = "\u5ea7\u4f4d\u5716"; const val budget = "\u9810\u7b97\u63a8\u85a6"; const val traffic = "\u4ea4\u901a\u61c9\u63f4"; const val analysis = "\u8cc7\u6599\u5206\u6790"; const val food = "\u7f8e\u98df\u63a8\u85a6"; const val account = "\u5e33\u865f"
    const val appTitle = "\u6f14\u5531\u6703\u7968\u52d9\u52a9\u624b"; const val randomHomeSub = "\u9996\u9801\u6bcf\u6b21\u96a8\u6a5f\u63a8\u85a6 10 \u500b\u6d3b\u52d5"; const val recentTickets = "\u8fd1\u671f\u7968\u5238"; const val randomEvents = "\u96a8\u6a5f\u6d3b\u52d5\u63a8\u85a6"; const val refresh = "\u66f4\u65b0"; const val noTickets = "\u5c1a\u672a\u65b0\u589e\u7968\u5238"; const val tapAddTicket = "\u9ede\u9019\u88e1\u524d\u5f80\u7968\u5238\u593e\u65b0\u589e\u7b2c\u4e00\u5f35\u7968\u5238\u3002"
    const val explore = "\u63a2\u7d22\u6d3b\u52d5"; const val searchSub = "\u641c\u5c0b\u6f14\u5531\u6703\u8207\u6d3b\u52d5\u8cc7\u8a0a"; const val keyword = "\u95dc\u9375\u5b57"; const val addReminder = "\u52a0\u5165\u63d0\u9192"; const val eventDetail = "\u6d3b\u52d5\u8a73\u60c5"; const val basicInfo = "\u57fa\u672c\u8cc7\u8a0a"; const val ticketSite = "\u552e\u7968\u7db2\u7ad9"; const val artist = "\u6f14\u51fa\u8005"; const val place = "\u5730\u9ede"; const val saleTime = "\u552e\u7968\u6642\u9593"; const val eventTime = "\u6d3b\u52d5\u6642\u9593"; const val price = "\u7968\u50f9"; const val artistInfo = "\u85dd\u4eba\u8cc7\u8a0a"; const val artistIntro = "\u85dd\u4eba\u4ecb\u7d39"; const val artistNews = "\u85dd\u4eba\u65b0\u805e"; const val artistNewsFallback = "\u76ee\u524d\u6c92\u6709\u5c0d\u61c9\u65b0\u805e\u8cc7\u6599\u3002"; const val officialLink = "\u5b98\u65b9\u9023\u7d50"
    const val reminderSetting = "\u63d0\u9192\u8a2d\u5b9a"; const val chooseReminderTime = "\u9078\u64c7\u63d0\u524d\u63d0\u9192\u6642\u9593"; const val before15Min = "\u63d0\u524d 15 \u5206\u9418"; const val before1Hour = "\u63d0\u524d 1 \u5c0f\u6642"; const val before1Day = "\u63d0\u524d 1 \u5929"; const val beforeCustom = "\u63d0\u524d %d \u5206\u9418"; const val onTimeReminder = "\u6e96\u6642\u63d0\u9192"; const val daysBefore = "\u5929"; const val hoursBefore = "\u5c0f\u6642"; const val minutesBefore = "\u5206\u9418"; const val saveReminder = "\u5132\u5b58\u63d0\u9192"; const val editReminder = "\u7de8\u8f2f\u63d0\u9192"; const val reminderSaved = "\u5df2\u5efa\u7acb\u672c\u5730\u901a\u77e5\u63d0\u9192"; const val notificationDenied = "\u672a\u6388\u6b0a\u901a\u77e5\u6b0a\u9650"; const val saleReminders = "\u552e\u7968\u63d0\u9192"; const val noReminders = "\u5c1a\u672a\u52a0\u5165\u63d0\u9192"; const val noRemindersSub = "\u53ef\u4ee5\u5f9e\u9996\u9801\u6d3b\u52d5\u6216\u8a73\u60c5\u9801\u52a0\u5165\u63d0\u9192\u3002"; const val demoReminder = "QWER \u958b\u8ce3\u63d0\u9192"
    const val walletTitle = "\u6211\u7684\u7968\u5238\u593e"; const val walletSub = "\u7968\u5238\u6536\u85cf\u8207\u884c\u7a0b\u7ba1\u7406"; const val annualTotal = "2026 \u5e74\u5ea6\u8cfc\u7968\u7e3d\u984d"; const val recentEvents = "\u8fd1\u671f\u6d3b\u52d5"; const val walletEmpty = "\u7968\u5238\u593e\u76ee\u524d\u662f\u7a7a\u7684"; const val tapPlusAdd = "\u9ede\u53f3\u4e0b\u89d2 + \u65b0\u589e\u7968\u5238\u3002"; const val addTicket = "\u65b0\u589e\u7968\u5238"; const val scanTicket = "\u6383\u63cf\u7968\u6839"; const val chooseTicketImage = "\u9078\u64c7\u7968\u5238\u5716\u7247"; const val ticketTitle = "\u7968\u5238\u540d\u7a31"; const val date = "\u65e5\u671f"; const val time = "\u6642\u9593"; const val chooseDate = "\u9078\u64c7\u65e5\u671f"; const val chooseTime = "\u9078\u64c7\u6642\u9593"; const val seat = "\u5ea7\u4f4d"; const val notes = "\u5099\u8a3b"; const val enterTitle = "\u8acb\u8f38\u5165\u7968\u5238\u540d\u7a31"; const val saveTicket = "\u5132\u5b58\u7968\u5238"; const val ticket = "\u7968\u5238"; const val close = "\u95dc\u9589"; const val ocrDone = "\u6383\u63cf\u5b8c\u6210\uff0c\u8acb\u78ba\u8a8d\u8cc7\u8a0a"; const val ocrPartial = "\u6383\u63cf\u5b8c\u6210\uff0c\u8acb\u624b\u52d5\u78ba\u8a8d\u90e8\u5206\u8cc7\u8a0a"; const val ocrFailed = "\u7968\u6839\u8fa8\u8b58\u5931\u6557"; const val cameraDenied = "\u672a\u6388\u6b0a\u76f8\u6a5f\u6b0a\u9650"
    const val moreFeatures = "\u66f4\u591a\u529f\u80fd"; const val moreSub = "\u4e0d\u5e38\u7528\u5de5\u5177\u96c6\u4e2d\u6536\u7d0d\u5728\u9019\u88e1"; const val seatMapSub = "\u67e5\u8a62\u5834\u9928\u5ea7\u4f4d\u5716\u8207\u6b4c\u8ff7\u8996\u91ce"; const val trafficSub = "\u505c\u8eca\u8207\u5834\u9928\u4ea4\u901a\u8cc7\u8a0a"; const val budgetSub = "\u7968\u5238\u9810\u7b97\u8207\u63a8\u85a6\u5de5\u5177"; const val analysisSub = "\u5716\u8868\u8207\u7968\u5238\u6d1e\u5bdf"; const val foodSub = "\u4f9d\u5730\u5340\u3001\u50f9\u683c\u8207\u985e\u578b\u62bd\u9078\u9644\u8fd1\u9910\u5ef3"; const val venueGuide = "場館導覽"; const val venueGuideSub = "瀏覽場館地址與介紹"; const val noVenueDescription = "目前尚無場館介紹"; const val accountSettings = "\u5e33\u865f\u8a2d\u5b9a"; const val accountSub = "\u5e33\u865f\u8207\u500b\u4eba\u8a2d\u5b9a"; const val seatMapBrowse = "\u4f9d\u5340\u57df\u700f\u89bd\u5834\u9928\u5ea7\u4f4d\u5716"; const val north = "\u5317\u90e8"; const val central = "\u4e2d\u90e8"; const val south = "\u5357\u90e8"
    const val trafficItem1 = "\u5834\u9928\u505c\u8eca"; const val trafficItem1Sub = "\u6574\u7406\u71b1\u9580\u5834\u9928\u5468\u908a\u505c\u8eca\u8cc7\u8a0a\u3002"; const val trafficItem2 = "\u5927\u773e\u904b\u8f38"; const val trafficItem2Sub = "\u63d0\u4f9b MRT\u3001\u9ad8\u9435\u3001\u53f0\u9435\u8207\u63a5\u99c1\u5efa\u8b70\u3002"; const val trafficItem3 = "\u51fa\u767c\u524d\u6aa2\u67e5"; const val trafficItem3Sub = "\u5efa\u8b70\u958b\u6f14\u524d 90 \u5206\u9418\u62b5\u9054\u5834\u9928\u3002"; const val budgetItem1 = "\u9810\u7b97\u5340\u9593"; const val budgetItem1Sub = "\u6839\u64da\u7968\u50f9\u5340\u9593\u6311\u9078\u512a\u5148\u5834\u6b21\u3002"; const val budgetItem2 = "\u50f9\u503c\u8a55\u5206"; const val budgetItem2Sub = "\u7d50\u5408\u5834\u9928\u3001\u5ea7\u4f4d\u8207\u6d3b\u52d5\u71b1\u5ea6\u9032\u884c\u8a55\u4f30\u3002"; const val budgetItem3 = "\u8cfc\u7968\u8a18\u9304"; const val budgetItem3Sub = "\u8ffd\u8e64\u5df2\u82b1\u8cbb\u8207\u5269\u9918\u9810\u7b97\u3002"; const val eventCount = "\u6d3b\u52d5\u6578\uff1a"; const val ticketCount = "\u7968\u5238\u6578\uff1a"; const val averagePrice = "\u5e73\u5747\u7968\u50f9"; const val hotVenue = "\u71b1\u9580\u5834\u9928"
    const val loading = "\u8cc7\u6599\u8f09\u5165\u4e2d..."; const val noData = "\u5c1a\u7121\u8cc7\u6599"; const val noDataSub = "\u76ee\u524d\u6c92\u6709\u53ef\u986f\u793a\u7684\u5167\u5bb9\uff0c\u8acb\u7a0d\u5f8c\u518d\u8a66\u3002"; const val dataConnectionFailed = "資料連線失敗"; const val savedEventCount = "\u6536\u85cf\u6d3b\u52d5\u6578"; const val estimatedTotal = "\u9810\u4f30\u7e3d\u8cbb\u7528"; const val budgetStatus = "\u9810\u7b97\u72c0\u614b"; const val budgetSafe = "\u5b89\u5168"; const val budgetWarning = "\u504f\u9ad8"; const val budgetUsage = "\u9810\u7b97\u4f54\u6bd4"; const val budgetLimit = "\u9810\u7b97\u4e0a\u9650"; const val saved = "\u5df2\u6536\u85cf"
    const val quickFixPlace = "\u5feb\u901f\u4fee\u6b63\u5730\u9ede"; const val quickFixArtist = "\u5feb\u901f\u4fee\u6b63\u6f14\u51fa\u8005"; const val parkingTitle = "\u9644\u8fd1\u505c\u8eca\u5834\u67e5\u8a62"; const val parkingIntro = "\u53ef\u81ea\u884c\u8f38\u5165\u5730\u5740\uff0c\u6216\u9ede\u9078\u71b1\u9580\u6f14\u51fa\u5834\u9928\u3002\u7d50\u5408\u96f2\u7aef\u8cc7\u6599\u5eab\u54c8\u5f17\u8f9b\u7a7a\u9593\u5e7e\u4f55\u6f14\u7b97\u6cd5\uff0c\u52d5\u614b\u7cbe\u7b97\u5468\u908a\u7a7a\u4f4d\u72c0\u614b\u3002"; const val searchAction = "\u67e5\u8a62"; const val parkingSearchHint = "\u8f38\u5165\u5730\u5740\u6216\u5834\u9928\u95dc\u9375\u5b57"; const val hotVenueQuick = "\u71b1\u9580\u6f14\u51fa\u6703\u5834\u5feb\u9078"; const val smartRadius = "\u667a\u6167\u641c\u5c0b\u534a\u5f91"; const val meterWithin = "\u516c\u5c3a\u5167"; const val findFoodByVenue = "\u4f9d\u5834\u9928\u4f4d\u7f6e\u627e\u7f8e\u98df"; const val parkingResultPrefix = "\u7b26\u5408\u65b9\u5713\u534a\u5f91\u7d50\u679c"; const val countUnit = "\u7b46"; const val parkingNoData = "\u627e\u4e0d\u5230\u9644\u8fd1\u505c\u8eca\u5834\u8cc7\u6599"; const val parkingNoDataSub = "\u5617\u8a66\u66f4\u63db\u8f38\u5165\u5730\u5740\uff0c\u6216\u662f\u5c07\u4e0a\u65b9\u7684\u96f7\u9054\u534a\u5f91\u6ed1\u687f\u5f80\u53f3\u8abf\u5927\u3002"; const val parkingSpaces = "\u7a7a\u4f4d"
    const val monthBudget = "\u672c\u6708\u6d3b\u52d5\u9810\u7b97"; const val usedBudget = "\u5df2\u4f7f\u7528"; const val remainingBudget = "\u5269\u9918"; const val savedList = "\u6536\u85cf\u6e05\u55ae"; const val evaluationCount = "\u53c3\u52a0\u524d\u8a55\u4f30"; const val ticketRecords = "\u5df2\u8cfc\u7968\u7d00\u9304"; const val budgetSettings = "\u9810\u7b97\u8a2d\u5b9a"; const val eventUnit = "\u5834"; const val budgetImpact = "\u9810\u7b97\u5f71\u97ff\u6982\u89bd"; const val budgetImpactSub = "\u4f9d\u5df2\u8cfc\u7968\u8207\u6536\u85cf\u6d3b\u52d5\u4f30\u7b97\u672c\u6708\u9810\u7b97"; const val viewRecommend = "\u67e5\u770b\u63a8\u85a6"; const val concert = "\u6f14\u5531\u6703"; const val exhibition = "\u5c55\u89bd\u6d3b\u52d5"; const val sports = "\u904b\u52d5\u8cfd\u4e8b"; const val other = "\u5176\u4ed6"; const val budgetAllocation = "\u9810\u7b97\u5206\u914d\u5efa\u8b70"; const val budgetAllocationSub = "\u4f9d\u6d3b\u52d5\u985e\u578b\u8207\u8fd1\u671f\u9810\u7b97\u5206\u914d"
    const val all = "\u4e0d\u9650"; const val priceBand = "\u50f9\u683c"; const val foodType = "\u985e\u578b"; const val city = "\u57ce\u5e02"; const val priceLow = "NT$ 100 \u5167"; const val priceMid = "NT$ 100-300"; const val priceHigh = "NT$ 300 \u4ee5\u4e0a"; const val foodCafe = "\u5496\u5561"; const val foodMeal = "\u6b63\u9910"; const val foodDessert = "\u751c\u9ede"; const val taipeiCity = "\u53f0\u5317"; const val newTaipeiCity = "\u65b0\u5317"; const val kaohsiungCity = "\u9ad8\u96c4"; const val currentLocation = "\u76ee\u524d\u4f4d\u7f6e"; const val venueLocation = "\u5834\u9928\u4f4d\u7f6e"; const val foodRandomTitle = "\u6f14\u51fa\u524d\u5403\u4ec0\u9ebc"; const val foodRandomSub = "\u7be9\u9078\u5f8c\u53ef\u4ee5\u4e00\u9375\u62bd\u9078\u9910\u5ef3"; const val randomFood = "\u62bd\u9078\u7f8e\u98df"; const val foodResults = "\u63a8\u85a6\u6e05\u55ae"
    const val officialSeatMap = "\u5b98\u65b9\u5ea7\u4f4d\u5716"; const val fanView = "\u73fe\u5834\u8996\u91ce"; const val officialInfo = "\u5b98\u65b9\u8cc7\u8a0a"; const val taipeiDome = "\u53f0\u5317\u5927\u5de8\u86cb"; const val taipeiArena = "\u53f0\u5317\u5c0f\u5de8\u86cb"; const val taipeiMusicCenter = "\u53f0\u5317\u6d41\u884c\u97f3\u6a02\u4e2d\u5fc3"; const val concertVenue = "\u6f14\u5531\u6703\u5834\u9928"; const val conventionCenter = "\u5927\u6703\u5802"; const val ntt = "\u81fa\u4e2d\u570b\u5bb6\u6b4c\u5287\u9662"; const val centralVenue = "\u81fa\u4e2d\u5834\u9928"; const val kaohsiungArena = "\u9ad8\u96c4\u5de8\u86cb"; const val kmc = "\u9ad8\u96c4\u6d41\u884c\u97f3\u6a02\u4e2d\u5fc3"
    const val daysLeftPrefix = "\u9084\u6709 "; const val daysLeftSuffix = " \u5929"; const val untitledTicket = "\u672a\u547d\u540d\u7968\u5238"; const val noDate = "\u5c1a\u672a\u9078\u64c7\u65e5\u671f"; const val twoWeeks = "\u5169\u9031"; const val month = "\u6574\u6708"; const val monthPattern = "yyyy \u5e74 M \u6708"; const val sun = "\u65e5"; const val mon = "\u4e00"; const val tue = "\u4e8c"; const val wed = "\u4e09"; const val thu = "\u56db"; const val fri = "\u4e94"; const val sat = "\u516d"; const val sortByDateAsc = "排序：日期 (近→遠)"; const val noScheduleDay = "這一天沒有行程"; const val cannotOpenLink = "\u7121\u6cd5\u958b\u555f\u9023\u7d50"; const val artistIntroFallback = "\u76ee\u524d\u6c92\u6709\u85dd\u4eba\u4ecb\u7d39\u3002"; const val artistIntroSuffix = " \u7684\u6f14\u51fa\u98a8\u683c\u548c\u6d3b\u52d5\u8cc7\u8a0a\u5df2\u6536\u9304\u65bc\u7cfb\u7d71\u4e2d\u3002"
    const val homeStatEvents = "\u6d3b\u52d5\u8cc7\u6599"; const val homeStatArtists = "\u85dd\u4eba\u6a94\u6848"; const val homeStatVenues = "\u5408\u4f5c\u5834\u5730"; const val homeStatReminders = "\u6436\u7968\u63d0\u9192"; const val edit = "\u7de8\u8f2f"; const val delete = "\u522a\u9664"; const val editTicket = "\u7de8\u8f2f\u7968\u5238"; const val ticketCountUnit = "\u5f35\u7968\u5238"; const val noTicketsOnDate = "\u7576\u5929\u6c92\u6709\u7968\u5238"; const val noTicketsOnDateSub = "\u8acb\u5728\u65b0\u589e\u7968\u5238\u6642\u9078\u64c7\u6b63\u78ba\u65e5\u671f\u3002"; const val thisMonth = "\u672c\u6708"; const val nextMonth = "\u4e0b\u500b\u6708"; const val thisYear = "\u4eca\u5e74"; const val priceRange = "\u7968\u50f9\u7bc4\u570d"; const val priceUnder1000 = "NT$ 1000 \u4ee5\u4e0b"; const val price1000To3000 = "NT$ 1000-3000"; const val priceOver3000 = "NT$ 3000 \u4ee5\u4e0a"; const val searchResults = "\u641c\u5c0b\u7d50\u679c"; const val platformDistribution = "\u552e\u7968\u5e73\u53f0\u5206\u4f48"; const val monthlySpendTrend = "\u6bcf\u6708\u82b1\u8cbb\u8da8\u52e2"; const val totalSpend = "\u7e3d\u82b1\u8cbb"
    const val googleAttribution = "\u7531 Google \u63d0\u4f9b\u8cc7\u6599"; const val googleServiceUnavailable = "目前無法連線至 Google 服務，改為顯示離線紀錄"; const val navigation = "\u5c0e\u822a"; const val genre = "類型"; const val language = "語言"; const val wikiUrl = "Wiki 連結"; const val share = "分享"; const val sharePreview = "分享預覽"; const val shareToSocial = "分享至社群平台"; const val shareFailed = "分享圖片產生失敗"
    const val loginTitle = "登入帳號"; const val registerTitle = "註冊帳號"; const val loginSub = "登入後即可同步預算、提醒與票券資料。"; const val email = "Email"; const val password = "密碼"; const val login = "登入"; const val register = "註冊"; const val createAccount = "還沒有帳號？註冊"; const val haveAccountLogin = "已有帳號？登入"; const val invalidEmail = "請輸入正確的 Email 格式"; const val invalidPassword = "密碼至少需要 6 個字元"; const val loginSuccess = "登入成功"; const val registerSuccess = "註冊成功"; const val loginFailed = "登入失敗，請稍後再試"; const val invalidCredentials = "Email 或密碼錯誤"; const val emailExists = "此 Email 已註冊"; const val invalidRegisterFields = "請確認 Email 格式與密碼長度"; const val missingLoginFields = "請輸入 Email 與密碼"
    const val logoutAccount = "\u767b\u51fa\u5e33\u865f"; const val logoutDone = "\u5df2\u767b\u51fa\u5e33\u865f"
    const val loggedOutTitle = "\u5df2\u767b\u51fa"; const val loggedOutSub = "\u767b\u5165\u72c0\u614b\u5df2\u6e05\u9664\uff0c\u8acb\u91cd\u65b0\u9032\u5165\u529f\u80fd\u3002"; const val backToHome = "\u56de\u5230\u9996\u9801"
}
