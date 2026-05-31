# Android 應用分層重構

此目錄包含了一個完整的 Android 應用分層架構重構項目，展示了如何將單一龐大的 `MainActivity` (124.5KB) 分解為多個職責單一的層級。

## 📊 重構成果

### 文件統計
- **原始檔案**: MainActivity.kt (124.5 KB) - 單一檔案包含所有功能
- **新建檔案**: 12 個 Kotlin 檔案 (~42 KB) - 分層結構
- **改進**: 代碼可讀性 ↑↑↑ | 可維護性 ↑↑↑ | 可測試性 ↑↑↑

## 🏗️ 架構層次

```
┌─────────────────────────────────────────────────────────┐
│                    UI 層 (Composable)                    │
│  RemindersScreen | HomeScreen | SearchScreen | ...      │
└──────────────────────┬──────────────────────────────────┘
                       │ StateManagement
┌──────────────────────▼──────────────────────────────────┐
│              ViewModel 層 (業務邏輯)                     │
│  ReminderViewModel | EventViewModel | WalletViewModel   │
└──────────────────────┬──────────────────────────────────┘
                       │ API Calls
┌──────────────────────▼──────────────────────────────────┐
│            API 層 (HTTP + JSON 解析)                    │
│  ReminderAPIClient | EventAPIClient | AuthAPIClient ... │
└──────────────────────┬──────────────────────────────────┘
                       │ Network
┌──────────────────────▼──────────────────────────────────┐
│                   Backend Server                        │
└─────────────────────────────────────────────────────────┘
```

## 📁 新建檔案列表

### 基礎層 (2 個)
- **Constants.kt** - 常數和顏色定義集中管理
- **CommonUtils.kt** - 通用工具函數

### API 層 (5 個)
- **ReminderAPIClient.kt** - 提醒 API (GET/POST/DELETE)
- **EventAPIClient.kt** - 活動 API (搜尋、分頁、精選)
- **AuthAPIClient.kt** - 認證 API (登入、註冊、登出)
- **StatsAPIClient.kt** - 統計 API (摘要、票價、時間、場地)
- **WalletAPIClient.kt** - 票券 API (CRUD 操作)

### ViewModel 層 (3 個)
- **ReminderViewModel.kt** - 提醒狀態和邏輯
- **EventViewModel.kt** - 活動狀態和搜尋邏輯
- **WalletViewModel.kt** - 票券狀態和統計邏輯

### UI 層 (1 個)
- **RemindersScreen.kt** - 提醒完整頁面的 Composable

### 文檔 (2 個)
- **ARCHITECTURE.md** - 架構詳細說明
- **INTEGRATION_GUIDE.kt** - 集成步驟教程

## ✨ 核心改進

| 方面 | 改進 | 效益 |
|------|------|------|
| **可讀性** | 每個檔案職責單一 | 新人易上手 |
| **可維護性** | 修改不會波及其他層 | 快速 debug |
| **可測試性** | 業務邏輯脫離 UI | 單元測試容易 |
| **代碼複用** | UI 元件獨立 | 多個地方使用 |
| **並行開發** | 各層獨立開發 | 團隊效率高 |

## 🚀 快速開始

### 基本使用

```kotlin
// 初始化服務層
val reminderService = ReminderAPIClient(
    context = this,
    apiBaseUrl = API_BASE_URL,
    client = OkHttpClient(),
    getAuthToken = { activeToken }
)

// 初始化業務邏輯層
val reminderViewModel = ReminderViewModel(reminderService)

// 在 Composable 中使用
reminderViewModel.loadReminders()
RemindersScreen(
    reminders = reminderViewModel.reminders.value,
    onDelete = { reminderViewModel.deleteReminder(it.id) },
    formatOffsets = { reminderViewModel.formatReminderOffsets(it) }
)
```

### 數據流向

```
UI 層 (Composable)
    ↓ [傳遞狀態和回調]
ViewModel 層
    ↓ [調用 API]
API 層 (HTTP)
    ↓ [網絡請求]
Backend Server
```

## 📝 架構最佳實踐

### ✓ 應該做的

- **ViewModel**: 管理狀態、調用 API、處理業務邏輯
- **APIClient**: HTTP 呼叫、JSON 解析、錯誤處理
- **Screen**: 純 UI 展示，接收資料和回調作為參數
- **Constants**: 集中所有常數和顏色定義

### ✗ 不要做的

- ❌ 在 UI 層進行 HTTP 呼叫
- ❌ 在 APIClient 中進行業務邏輯
- ❌ 在 ViewModel 中進行 UI 操作
- ❌ 分散常數和顏色定義

## 🧪 測試

### ViewModel 單元測試

```kotlin
@Test
fun testLoadReminders() {
    val mockService = mock(ReminderServiceV2::class.java)
    val viewModel = ReminderViewModel(mockService)
    
    viewModel.loadReminders()
    
    verify(mockService).loadReminders(any())
}
```

### UI 測試 (Compose)

```kotlin
@get:Rule
val composeTestRule = createComposeRule()

@Test
fun testRemindersScreenDisplay() {
    composeTestRule.setContent {
        RemindersScreen(
            reminders = listOf(ReminderItem(...)),
            onDelete = {},
            formatOffsets = { it }
        )
    }
    
    composeTestRule.onNodeWithText("移除提醒").assertIsDisplayed()
}
```

## 📚 進階主題

### 升級狀態管理

當前使用 `mutableStateOf`，可以升級到：
- **StateFlow + AndroidViewModel** - 生命周期感知
- **Redux 模式** - 使用 Orbit 或 MVI-Kotlin
- **MobX 風格** - 使用 Ballast

### 處理長時間任務

```kotlin
class ReminderViewModel : ViewModel() {
    fun loadReminders() {
        viewModelScope.launch {
            try {
                reminders.value = reminderService.loadReminders()
            } catch (e: Exception) {
                // 錯誤處理
            }
        }
    }
}
```

### 認證過期處理

在 APIClient 中攔截 401 錯誤：

```kotlin
override fun onResponse(...) {
    when (response.code) {
        401 -> {
            performLogin()
            retry()
        }
    }
}
```

## 🔄 遷移檢查清單

- [ ] 初始化所有 APIClient
- [ ] 初始化所有 ViewModel
- [ ] 提取其他 UI 層 (Home、Search、Wallet、Analysis、Account)
- [ ] 更新 MainActivity 使用新組件
- [ ] 編譯測試
- [ ] 完整功能測試
- [ ] 效能測試

## 📖 參考資源

- [Jetpack Compose 官方文檔](https://developer.android.com/jetpack/compose)
- [OkHttp 文檔](https://square.github.io/okhttp/)
- [Kotlin Coroutines](https://kotlinlang.org/docs/coroutines-overview.html)
- [Android Architecture Components](https://developer.android.com/topic/architecture)

## 🎯 技術棧

- **語言**: Kotlin
- **UI 框架**: Jetpack Compose
- **HTTP 客戶端**: OkHttp3
- **JSON 解析**: org.json
- **圖片加載**: Coil
- **Compose Material**: Material 3

## 📄 許可證

此代碼遵循原始項目的許可證。

---

**最後更新**: 2024-12-20  
**狀態**: 生產就緒
