package com.example.myapplication.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.ConfirmationNumber
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.myapplication.EventItem
import com.example.myapplication.data.WalletTicket

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

@Composable
fun PageTitle(title: String, subtitle: String) {
    Column {
        Text(title, color = Ink, fontSize = 28.sp, fontWeight = FontWeight.Black, lineHeight = 34.sp)
        Spacer(modifier = Modifier.height(5.dp))
        Text(subtitle, color = Muted, fontSize = 14.sp, lineHeight = 21.sp)
    }
}

@Composable
fun FeatureHeader(
    eyebrow: String,
    title: String,
    subtitle: String,
    icon: ImageVector,
    accent: Color,
    action: String? = null,
    actionIcon: ImageVector? = null,
    onAction: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SurfaceIvory),
        shape = RoundedCornerShape(28.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(1.dp, FineLine.copy(alpha = 0.72f))
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .background(accent.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
                        .padding(11.dp)
                ) {
                    Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(23.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(eyebrow, color = accent, fontSize = 12.sp, fontWeight = FontWeight.Black)
                    Text(title, color = Ink, fontSize = 22.sp, fontWeight = FontWeight.Black, lineHeight = 28.sp)
                }
                if (action != null && onAction != null) {
                    OutlinedButton(
                        onClick = onAction,
                        shape = RoundedCornerShape(99.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Ink)
                    ) {
                        if (actionIcon != null) {
                            Icon(actionIcon, contentDescription = null, modifier = Modifier.size(17.dp))
                            Spacer(modifier = Modifier.width(5.dp))
                        }
                        Text(action, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(subtitle, color = Muted, fontSize = 14.sp, lineHeight = 21.sp)
        }
    }
}

@Composable
fun InfoLine(icon: ImageVector, label: String, value: String) {
    Row(
        modifier = Modifier.padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(icon, contentDescription = null, tint = Color(0xFF9CA3AF), modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(label, modifier = Modifier.width(58.dp), color = Color(0xFF9CA3AF), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Text(
            value,
            modifier = Modifier.weight(1f),
            color = Color(0xFF374151),
            fontSize = 13.sp,
            lineHeight = 19.sp,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun EmptyStateCard(title: String, message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SurfaceIvory),
        shape = RoundedCornerShape(26.dp),
        border = BorderStroke(1.dp, FineLine.copy(alpha = 0.65f))
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .background(CobaltSoft, RoundedCornerShape(18.dp))
                    .padding(13.dp)
            ) {
                Icon(Icons.Rounded.Search, contentDescription = null, tint = Cobalt, modifier = Modifier.size(28.dp))
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(title, fontWeight = FontWeight.Black, fontSize = 18.sp, color = Ink)
            Spacer(modifier = Modifier.height(6.dp))
            Text(message, color = Muted, fontSize = 14.sp, lineHeight = 21.sp)
        }
    }
}

@Composable
fun LoadingCard(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SurfaceIvory),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, FineLine.copy(alpha = 0.65f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(22.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(modifier = Modifier.size(28.dp), color = Cobalt)
            Spacer(modifier = Modifier.width(14.dp))
            Text(message, color = Muted)
        }
    }
}

@Composable
fun TicketInputField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Cobalt,
            unfocusedBorderColor = Mist,
            cursorColor = Cobalt,
            focusedLabelColor = Cobalt,
            focusedContainerColor = Porcelain,
            unfocusedContainerColor = Porcelain
        )
    )
}

@Composable
fun authTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Cobalt,
    unfocusedBorderColor = Mist,
    cursorColor = Cobalt,
    focusedLabelColor = Cobalt,
    focusedContainerColor = Porcelain,
    unfocusedContainerColor = Porcelain
)

@Composable
fun DateTimePickerField(value: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Porcelain),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Mist)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.CalendarMonth, contentDescription = null, tint = Cobalt, modifier = Modifier.size(21.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("日期時間", color = Cobalt, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(2.dp))
                Text(value, color = Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
            Text("選擇", color = Muted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun SourceBadge(source: String) {
    Box(
        modifier = Modifier
            .background(GoldSoft, RoundedCornerShape(99.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(source.take(12), color = Color(0xFF8A5A00), fontSize = 12.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
fun TicketPricePanel(price: String, ticketType: String) {
    Spacer(modifier = Modifier.height(10.dp))
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CobaltSoft.copy(alpha = 0.72f)),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.75f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text("票價資訊", color = Cobalt, fontSize = 12.sp, fontWeight = FontWeight.Black)
            Spacer(modifier = Modifier.height(4.dp))
            Text(price, color = Ink, fontSize = 15.sp, fontWeight = FontWeight.Bold, lineHeight = 21.sp, maxLines = 3)
            if (ticketType != "尚未公布") {
                Spacer(modifier = Modifier.height(3.dp))
                Text(ticketType, color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
fun EventCard(event: EventItem, onOpenUrl: () -> Unit, onAddReminder: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SurfaceIvory),
        shape = RoundedCornerShape(28.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        border = BorderStroke(1.dp, FineLine.copy(alpha = 0.72f))
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SourceBadge(displayValue(event.source))
                Spacer(modifier = Modifier.width(9.dp))
                Text(displayValue(event.venue), color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(event.title, fontWeight = FontWeight.Black, fontSize = 20.sp, color = Ink, lineHeight = 26.sp, maxLines = 3)
            if (event.artist.isNotBlank() && event.artist != event.title) {
                Spacer(modifier = Modifier.height(5.dp))
                Text(event.artist, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(modifier = Modifier.height(14.dp))
            InfoLine(Icons.Rounded.CalendarMonth, "活動", displayValue(event.activityTime))
            InfoLine(Icons.Rounded.Refresh, "開賣", displayValue(event.saleTime))
            InfoLine(Icons.Rounded.Search, "場地", displayValue(event.venue))
            TicketPricePanel(price = displayValue(event.price), ticketType = displayValue(event.ticketType))
            Spacer(modifier = Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onAddReminder,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = StageBlack)
                ) {
                    Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(19.dp))
                    Spacer(modifier = Modifier.width(7.dp))
                    Text("加入提醒", fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = onOpenUrl,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp),
                    enabled = event.url.startsWith("http"),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Ink)
                ) {
                    Icon(Icons.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(7.dp))
                    Text(if (event.url.startsWith("http")) "購票連結" else "尚無連結", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun MetricCard(title: String, value: String, note: String, icon: ImageVector, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = SurfaceIvory),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(1.dp, FineLine.copy(alpha = 0.68f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .background(CobaltSoft, RoundedCornerShape(12.dp))
                        .padding(8.dp)
                ) {
                    Icon(icon, contentDescription = null, tint = Cobalt, modifier = Modifier.size(18.dp))
                }
                Spacer(modifier = Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(title, color = Muted, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, fontWeight = FontWeight.Black, fontSize = 26.sp, color = Ink, maxLines = 1)
            Spacer(modifier = Modifier.height(3.dp))
            Text(note, color = Color(0xFF9CA3AF), fontSize = 12.sp)
        }
    }
}

@Composable
fun SectionHeader(
    title: String,
    subtitle: String,
    action: String,
    actionIcon: ImageVector,
    onAction: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Ink, fontSize = 22.sp, fontWeight = FontWeight.Black)
            Spacer(modifier = Modifier.height(4.dp))
            Text(subtitle, color = Muted, fontSize = 13.sp, lineHeight = 19.sp)
        }
        OutlinedButton(
            onClick = onAction,
            shape = RoundedCornerShape(99.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Ink)
        ) {
            Icon(actionIcon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(action, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun ProgressRow(label: String, value: Int, max: Int, color: Color) {
    val ratio = if (max <= 0) 0f else value.toFloat() / max.toFloat()
    val animatedRatio by animateFloatAsState(
        targetValue = ratio.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 650),
        label = "progress"
    )
    Column(modifier = Modifier.padding(vertical = 7.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f), color = Ink, fontSize = 14.sp)
            Text(formatNumber(value), fontWeight = FontWeight.Black, color = Ink)
        }
        Spacer(modifier = Modifier.height(7.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .background(Mist, RoundedCornerShape(99.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedRatio)
                    .height(8.dp)
                    .background(color, RoundedCornerShape(99.dp))
            )
        }
    }
}

@Composable
fun RankBadge(rank: Int) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .background(StageBlack, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(rank.toString(), color = Gold, fontSize = 13.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
fun WalletTicketCard(
    ticket: WalletTicket,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clickable { onEdit() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(110.dp).clip(RoundedCornerShape(12.dp)).background(Color.LightGray)
            ) {
                if (ticket.imagePath.isNotEmpty()) {
                    AsyncImage(
                        model = ticket.imagePath,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(Icons.Rounded.ConfirmationNumber, null, modifier = Modifier.align(Alignment.Center), tint = Color.Gray)
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(text = ticket.title, fontSize = 17.sp, fontWeight = FontWeight.Black, color = Color.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(text = ticket.date, fontSize = 13.sp, color = Color.DarkGray, fontWeight = FontWeight.Bold)
                
                Text("Cast: ${ticket.cast}", fontSize = 11.sp, color = Color.Gray)
                Text("Location: ${ticket.location}", fontSize = 11.sp, color = Color.Gray)
                Text("Seat: ${ticket.seat}", fontSize = 11.sp, color = Color.Gray)
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(onClick = onShare) {
                    Icon(Icons.Rounded.Refresh, null, tint = Color(0xFF6750A4), modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Rounded.Search, null, tint = Color.Red.copy(alpha = 0.5f), modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
fun HeroPill(text: String) {
    Box(
        modifier = Modifier
            .background(Color.White.copy(alpha = 0.15f), RoundedCornerShape(99.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

fun displayValue(value: String): String {
    val cleaned = value.trim()
    return if (cleaned.isEmpty() || cleaned == "?" || cleaned == "null") "尚未公布" else cleaned
}

fun formatNumber(value: Int): String = String.format(Locale.TAIWAN, "%,d", value.coerceAtLeast(0))

fun formatCurrency(value: Int): String {
    return if (value <= 0) "未提供" else "NT$ ${formatNumber(value)}"
}

fun formatReminderOffsets(value: String): String {
    val minutes = Regex("""\d+""").findAll(value).map { it.value }.distinct().toList()
    return if (minutes.isEmpty()) "尚未設定" else "提前 ${minutes.joinToString("、")} 分鐘"
}

import java.util.Locale
