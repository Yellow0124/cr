package com.example.myapplication.ui // 🛠️ 關鍵修正：將 Package 空間完全對齊 ui 資料夾

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.Mail
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun AccountScreen(
    session: AuthSession, // 這裡就能完美抓到同套件下的 AuthSession 了！
    onLogout: () -> Unit,
    onDeleteAccount: (String) -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    var confirmPassword by remember { mutableStateOf("") }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = {
                showDeleteDialog = false
                confirmPassword = ""
            },
            title = { Text("註銷帳號") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("註銷後，這個帳號的提醒資料會一起刪除。")
                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it },
                        label = { Text("請輸入密碼確認") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Cobalt,
                            unfocusedBorderColor = Mist,
                            cursorColor = Cobalt,
                            focusedLabelColor = Cobalt,
                            focusedContainerColor = Porcelain,
                            unfocusedContainerColor = Porcelain
                        ),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteAccount(confirmPassword)
                    showDeleteDialog = false
                    confirmPassword = ""
                }) {
                    Text("確認註銷", color = Rose)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    confirmPassword = ""
                }) {
                    Text("取消")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        PageTitle(title = "帳號", subtitle = "管理登入狀態與帳號安全")
        
        Card(
            colors = CardDefaults.cardColors(containerColor = SurfaceIvory),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, FineLine.copy(alpha = 0.6f))
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                InfoLine(Icons.Rounded.Mail, "Email", session.email)
                InfoLine(Icons.Rounded.Lock, "狀態", "已登入")
            }
        }
        
        Button(
            onClick = onLogout,
            colors = ButtonDefaults.buttonColors(containerColor = Ink),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Rounded.Logout, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("登出")
        }
        
        OutlinedButton(
            onClick = { showDeleteDialog = true },
            border = BorderStroke(1.dp, Rose),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Rounded.DeleteForever, contentDescription = null, tint = Rose)
            Spacer(modifier = Modifier.width(8.dp))
            Text("註銷帳號", color = Rose)
        }
    }
}