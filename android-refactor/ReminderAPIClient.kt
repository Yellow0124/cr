package com.example.myapplication

import android.content.Context
import android.widget.Toast
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/**
 * ReminderAPIClient - 提醒相關 API 呼叫的具體實現
 * 實現 ReminderServiceV2 介面
 */
class ReminderAPIClient(
    private val context: Context,
    private val apiBaseUrl: String,
    private val client: OkHttpClient,
    private val getAuthToken: () -> String
) : ReminderServiceV2 {

    override fun loadReminders(onResult: (List<ReminderItem>) -> Unit) {
        val request = Request.Builder()
            .url("$apiBaseUrl/api/reminders")
            .get()
            .build()

        executeRequest(request,
            onSuccess = { body ->
                val array = JSONArray(body)
                val result = mutableListOf<ReminderItem>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    result.add(
                        ReminderItem(
                            id = obj.optInt("id"),
                            title = obj.optString("title"),
                            saleAt = obj.optString("saleAt"),
                            offsetsMinutes = obj.optString("offsetsMinutes"),
                            enabled = obj.optInt("enabled", 1) == 1
                        )
                    )
                }
                onResult(result)
            },
            onError = {
                Toast.makeText(context, "提醒資料讀取失敗", Toast.LENGTH_SHORT).show()
                onResult(emptyList())
            }
        )
    }

    override fun addReminder(
        title: String,
        saleAt: String,
        offsetsMinutes: List<Int>,
        onSuccess: () -> Unit,
        onError: () -> Unit
    ) {
        val payload = JSONObject()
            .put("title", title)
            .put("saleAt", saleAt)
            .put("offsetsMinutes", JSONArray(offsetsMinutes))
            .toString()

        val request = Request.Builder()
            .url("$apiBaseUrl/api/reminders")
            .post(okhttp3.RequestBody.Companion.toRequestBody(payload, "application/json; charset=utf-8".toMediaType()))
            .build()

        executeRequest(request,
            onSuccess = { onSuccess() },
            onError = {
                Toast.makeText(context, "新增提醒失敗", Toast.LENGTH_SHORT).show()
                onError()
            }
        )
    }

    override fun deleteReminder(id: Int, onSuccess: () -> Unit, onError: () -> Unit) {
        val request = Request.Builder()
            .url("$apiBaseUrl/api/reminders/$id")
            .delete()
            .build()

        executeRequest(request,
            onSuccess = { onSuccess() },
            onError = {
                Toast.makeText(context, "刪除提醒失敗", Toast.LENGTH_SHORT).show()
                onError()
            }
        )
    }

    private fun executeRequest(
        request: Request,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        val token = getAuthToken()
        val authedRequest = if (token.isNotBlank() && request.header("Authorization") == null) {
            request.newBuilder()
                .addHeader("Authorization", "Bearer $token")
                .build()
        } else {
            request
        }

        client.newCall(authedRequest).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                onError(e.message ?: "network error")
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val body = it.body?.string().orEmpty()
                    if (it.isSuccessful) onSuccess(body) else onError(body)
                }
            }
        })
    }
}

/**
 * 提醒服務 - API 層介面
 */
interface ReminderServiceV2 {
    fun loadReminders(onResult: (List<ReminderItem>) -> Unit)
    fun addReminder(
        title: String,
        saleAt: String,
        offsetsMinutes: List<Int>,
        onSuccess: () -> Unit,
        onError: () -> Unit
    )
    fun deleteReminder(id: Int, onSuccess: () -> Unit, onError: () -> Unit)
}
