package com.rinse.app.data

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.rinse.app.model.LaundryMachine
import com.rinse.app.model.LaundryResult
import com.rinse.app.model.LaundryStatus
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.json.JSONArray
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

object LaundryRepository {

    val defaultMachines = listOf(
        LaundryMachine(
            name = "辅2地下1号洗衣机",
            type = "洗衣机",
            url = "https://h5.qiekj.com/skip?Company=qiekj&CommunicateType=1&Ver=4&NQT=13fb133a-bbfe-4334-a045-b638e84672f3"
        ),
        LaundryMachine(
            name = "辅2地下2号洗衣机",
            type = "洗衣机",
            url = "https://h5.qiekj.com/skip?Company=qiekj&CommunicateType=1&Ver=4&NQT=1533fb07-dcd9-457b-a442-16e849c09395"
        ),
        LaundryMachine(
            name = "辅2地下3号洗衣机",
            type = "洗衣机",
            url = "https://h5.qiekj.com/skip?Company=qiekj&CommunicateType=1&Ver=4&NQT=8ad6a118-e7ff-49fa-af92-3d1c20ddf63a"
        ),
        LaundryMachine(
            name = "辅2地下洗鞋机",
            type = "洗鞋机",
            url = "https://h5.qiekj.com/skip?NQT=84ab7acb-ae5f-42c2-bd85-96ac87f56cf3"
        ),
        LaundryMachine(
            name = "辅2地下烘干机",
            type = "烘干机",
            url = "https://h5.qiekj.com/skip?Company=qiekj&CommunicateType=1&Ver=4&NQT=21f785df-d301-4bf0-9dbc-475265df0b7a"
        )
    )

    private val busyKeywords = listOf(
        "设备工作中",
        "工作中，请更换设备使用",
        "工作中"
    )

    private val unavailableKeywords = listOf(
        "无法使用",
        "暂停使用",
        "暂停服务",
        "维修中",
        "已停用",
        "设备离线"
    )

    suspend fun checkAll(context: Context, machines: List<LaundryMachine>): List<LaundryResult> = coroutineScope {
        machines.map { machine ->
            async {
                LaundryResult(
                    machine = machine,
                    status = checkMachine(context, machine)
                )
            }
        }.awaitAll()
    }

    private suspend fun checkMachine(
        context: Context,
        machine: LaundryMachine
    ): LaundryStatus = suspendCancellableCoroutine { continuation ->

        val handler = Handler(Looper.getMainLooper())
        var webView: WebView? = null
        var completed = false
        var evaluateRunnable: Runnable? = null

        fun cleanup() {
            evaluateRunnable?.let(handler::removeCallbacks)
            evaluateRunnable = null

            webView?.let { view ->
                try {
                    view.stopLoading()
                    view.loadUrl("about:blank")
                    view.clearHistory()
                    view.removeAllViews()
                    view.destroy()
                } catch (_: Exception) {
                }
            }
            webView = null
        }

        fun finish(status: LaundryStatus) {
            if (completed) return
            completed = true
            handler.removeCallbacksAndMessages(null)
            cleanup()

            if (continuation.isActive) {
                continuation.resume(status)
            }
        }

        val timeoutRunnable = Runnable {
            finish(LaundryStatus.UNKNOWN)
        }

        continuation.invokeOnCancellation {
            handler.post {
                if (!completed) {
                    completed = true
                    handler.removeCallbacksAndMessages(null)
                    cleanup()
                }
            }
        }

        handler.post {
            if (!continuation.isActive) return@post

            try {
                val view = WebView(context)
                webView = view

                with(view.settings) {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    databaseEnabled = true
                    cacheMode = WebSettings.LOAD_DEFAULT
                    mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                }

                view.webViewClient = object : WebViewClient() {

                    override fun onPageStarted(
                        view: WebView?,
                        url: String?,
                        favicon: Bitmap?
                    ) {
                        super.onPageStarted(view, url, favicon)
                        evaluateRunnable?.let(handler::removeCallbacks)
                        evaluateRunnable = null
                    }

                    override fun onPageFinished(view: WebView, url: String) {
                        super.onPageFinished(view, url)

                        // 页面是 JS H5，给状态跳转和 DOM 渲染留一点时间。
                        val task = Runnable {
                            if (completed) return@Runnable

                            val js = """
                                (function() {
                                    try {
                                        return document.body ? document.body.innerText : "";
                                    } catch (e) {
                                        return "";
                                    }
                                })();
                            """.trimIndent()

                            view.evaluateJavascript(js) { rawValue ->
                                if (completed) return@evaluateJavascript

                                val bodyText = decodeJavascriptString(rawValue)
                                val finalUrl = view.url ?: url
                                finish(classify(finalUrl, bodyText))
                            }
                        }

                        evaluateRunnable?.let(handler::removeCallbacks)
                        evaluateRunnable = task
                        handler.postDelayed(task, 2200L)
                    }

                    override fun onReceivedError(
                        view: WebView?,
                        request: WebResourceRequest?,
                        error: WebResourceError?
                    ) {
                        super.onReceivedError(view, request, error)
                        if (request?.isForMainFrame == true) {
                            finish(LaundryStatus.UNKNOWN)
                        }
                    }
                }

                handler.postDelayed(timeoutRunnable, 20_000L)
                view.loadUrl(machine.url)

            } catch (_: Exception) {
                finish(LaundryStatus.UNKNOWN)
            }
        }
    }

    private fun decodeJavascriptString(rawValue: String?): String {
        if (rawValue.isNullOrBlank() || rawValue == "null") return ""

        return try {
            JSONArray("[$rawValue]").getString(0)
        } catch (_: Exception) {
            rawValue
                .removePrefix("\"")
                .removeSuffix("\"")
                .replace("\\n", "\n")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\")
        }
    }

    private fun extractUrlMessage(url: String): String {
        return try {
            val uri = Uri.parse(url)
            buildList {
                uri.getQueryParameters("text").forEach(::add)
                uri.getQueryParameters("status").forEach(::add)
            }.joinToString(" ")
        } catch (_: Exception) {
            ""
        }
    }

    private fun classify(finalUrl: String, bodyText: String): LaundryStatus {
        val urlMessage = extractUrlMessage(finalUrl)
        val combined = "$urlMessage\n$bodyText"

        if (busyKeywords.any { combined.contains(it) }) {
            return LaundryStatus.BUSY
        }

        if (unavailableKeywords.any { combined.contains(it) }) {
            return LaundryStatus.UNAVAILABLE
        }

        // 与原 Python 逻辑一致：
        // 正常进入胖乖生活页面，且没有命中“使用中”或明确停用信息 -> 可用。
        if (finalUrl.contains("h5.qiekj.com", ignoreCase = true)) {
            return LaundryStatus.AVAILABLE
        }

        return LaundryStatus.UNKNOWN
    }
}
