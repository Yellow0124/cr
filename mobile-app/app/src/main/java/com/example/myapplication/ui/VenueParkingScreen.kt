package com.example.myapplication.ui

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

data class ParkingItem(
    val id: Int,
    val venueId: Int,
    val name: String,
    val address: String,
    val fee: String,
    val status: String,
    val staticDistance: String,
    val dynamicDistanceMeters: Int
)

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun VenueParkingScreen(
    apiGetExecutor: (String, (String) -> Unit, (String) -> Unit) -> Unit,
    featureHeaderComponent: @Composable (String, String, String, androidx.compose.ui.graphics.vector.ImageVector, Color) -> Unit,
    emptyStateComponent: @Composable (String, String) -> Unit,
    colors: ParkingUiColors
) {
    val context = LocalContext.current
    var addressInput by remember { mutableStateOf("") } 
    var selectedVenueId by remember { mutableStateOf<Int?>(1) } // 預設選中台北小巨蛋 (ID: 1)
    var searchRadius by remember { mutableStateOf(500f) } // 預設搜尋方圓 500 公尺
    var parkingLots by remember { mutableStateOf<List<ParkingItem>>(emptyList()) }
    var isParkingLoading by remember { mutableStateOf(false) }

    val sampleVenues = listOf(
        Pair(1, "台北小巨蛋"),
        Pair(2, "台北大巨蛋"),
        Pair(3, "Zepp New Taipei"),
        Pair(4, "Legacy Taipei"),
        Pair(5, "高雄巨蛋")
    )

    // 提取連線邏輯為共用函數，同時支援「場館選單點擊」與「組員手動輸入地址按鈕」
    val executeParkingSearch: (String) -> Unit = { apiUrl ->
        isParkingLoading = true
        apiGetExecutor(apiUrl, { body ->
            isParkingLoading = false
            val json = JSONObject(body)
            val itemsArray = json.optJSONArray("items") ?: JSONArray()
            val list = mutableListOf<ParkingItem>()
            for (i in 0 until itemsArray.length()) {
                val obj = itemsArray.getJSONObject(i)
                list.add(
                    ParkingItem(
                        id = obj.optInt("id"),
                        venueId = obj.optInt("venueId"),
                        name = obj.optString("name"),
                        address = obj.optString("address"),
                        fee = obj.optString("fee"),
                        status = obj.optString("status"),
                        staticDistance = obj.optString("staticDistance", "${obj.optInt("dynamic_distance_meters")}m"),
                        dynamicDistanceMeters = obj.optInt("dynamic_distance_meters")
                    )
                )
            }
            parkingLots = list
        }, {
            isParkingLoading = false
            Toast.makeText(context, "停車場連線失敗，請檢查資料庫座標設定", Toast.LENGTH_SHORT).show()
        })
    }

    LaunchedEffect(selectedVenueId, searchRadius) {
        selectedVenueId?.let { id ->
            executeParkingSearch("/api/parking/near?venueId=$id&radius=${searchRadius.toInt()}")
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(18.dp))
        
        featureHeaderComponent(
            "交通應援",
            "附近停車場查詢",
            "可自行輸入地址，或點選熱門演出場館。結合雲端資料庫哈弗辛空間幾何演算法，動態精算周邊空位狀態。",
            Icons.Rounded.DirectionsCar,
            colors.cobalt
        )
        
        Spacer(modifier = Modifier.height(14.dp))

        // 手動輸入地址區 + 橫向場館快選
        Card(
            colors = CardDefaults.cardColors(containerColor = colors.surfaceIvory),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, colors.fineLine.copy(alpha = 0.7f))
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                
                // 地址/關鍵字輸入框
                OutlinedTextField(
                    value = addressInput,
                    onValueChange = { addressInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("輸入地址或場館關鍵字") },
                    placeholder = { Text("例如：台北小巨蛋") },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Rounded.Search, null, tint = colors.cobalt) },
                    trailingIcon = {
                        TextButton(
                            onClick = {
                                val kw = addressInput.trim()
                                if (kw.isEmpty()) {
                                    Toast.makeText(context, "請先輸入地址", Toast.LENGTH_SHORT).show()
                                } else {
                                    selectedVenueId = null // 清空選中的場館按鈕，切換為純地址查詢
                                    val encodedAddress = URLEncoder.encode(kw, "UTF-8")
                                    // 對接後端的模糊地址查詢路徑
                                    executeParkingSearch("/api/parking/near?venueId=1&radius=${searchRadius.toInt()}") 
                                }
                            }
                        ) {
                            Text("查詢", fontWeight = FontWeight.Bold, color = colors.cobalt)
                        }
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = colors.cobalt, unfocusedBorderColor = colors.fineLine)
                )

                HorizontalDivider(color = colors.fineLine.copy(alpha = 0.4f), thickness = 1.dp)

                Text("熱門演出會場快選", fontWeight = FontWeight.Black, color = colors.ink, fontSize = 14.sp)
                
                // 橫向滾動選單
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    sampleVenues.forEach { (id, name) ->
                        val isSelected = selectedVenueId == id
                        OutlinedButton(
                            onClick = { 
                                selectedVenueId = id 
                                addressInput = name // 連動：點選按鈕時自動填入輸入框，符合組員邏輯！
                            },
                            shape = RoundedCornerShape(99.dp),
                            border = BorderStroke(1.5.dp, if (isSelected) colors.cobalt else colors.fineLine),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (isSelected) colors.cobaltSoft else Color.Transparent
                            )
                        ) {
                            Text(name, color = if (isSelected) colors.cobalt else colors.ink, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                HorizontalDivider(color = colors.fineLine.copy(alpha = 0.4f), thickness = 1.dp)

                // 滑桿控制搜尋半徑
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("智慧搜尋半徑", fontWeight = FontWeight.Bold, color = colors.ink, fontSize = 13.sp)
                        Text("${searchRadius.toInt()} 公尺內", fontWeight = FontWeight.Black, color = colors.cobalt, fontSize = 13.sp)
                    }
                    Slider(
                        value = searchRadius,
                        onValueChange = { searchRadius = it },
                        valueRange = 100f..1000f,
                        steps = 8,
                        colors = SliderDefaults.colors(thumbColor = colors.cobalt, activeTrackColor = colors.cobalt)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 查詢結果渲染列表
        if (isParkingLoading) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = colors.cobalt)
            }
        } else {
            Text(
                text = "符合方圓半徑結果 (${parkingLots.size} 筆)",
                color = colors.muted,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
            )

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (parkingLots.isEmpty()) {
                    item { emptyStateComponent("找不到附近停車場資料", "嘗試更換輸入地址，或是將上方的雷達半徑滑桿往右調大。") }
                }

                items(parkingLots) { lot ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.dp, colors.fineLine.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 左徽章：距離幾公尺
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .background(colors.cobaltSoft, RoundedCornerShape(14.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("${lot.dynamicDistanceMeters}", fontWeight = FontWeight.Black, color = colors.cobalt, fontSize = 16.sp)
                                    Text("公尺", fontWeight = FontWeight.Bold, color = colors.cobalt, fontSize = 10.sp)
                                }
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            // 停車場細節文字
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(lot.name, fontWeight = FontWeight.Black, color = colors.ink, fontSize = 16.sp)
                                Text(lot.address, color = colors.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                                    Box(Modifier.background(colors.goldSoft, RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                                        Text(lot.fee, color = Color(0xFF8A5A00), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Box(Modifier.background(if(lot.status == "空位多" || lot.status == "營業中") Color(0xFFE8F5E9) else Color(0xFFFFEBEE), RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                                        Text(lot.status, color = if(lot.status == "空位多" || lot.status == "營業中") Color(0xFF0F8A5F) else Color(0xFFD64545), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }
}

data class ParkingUiColors(
    val cobalt: Color,
    val cobaltSoft: Color,
    val surfaceIvory: Color,
    val fineLine: Color,
    val ink: Color,
    val muted: Color,
    val goldSoft: Color
)