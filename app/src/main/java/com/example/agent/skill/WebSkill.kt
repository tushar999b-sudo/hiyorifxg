package com.example.agent.skill

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class WebSkill(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val _inAppWebViewUrl = MutableStateFlow<String?>(null)
    val inAppWebViewUrl: StateFlow<String?> = _inAppWebViewUrl.asStateFlow()

    fun openInAppWebView(url: String) {
        val normalized = normalizeUrl(url)
        _inAppWebViewUrl.value = normalized
    }

    fun closeInAppWebView() {
        _inAppWebViewUrl.value = null
    }

    fun openInExternalBrowser(url: String): Result<String> = runCatching {
        val normalized = normalizeUrl(url)
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(normalized)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        "Opened $normalized in browser"
    }

    suspend fun searchWeb(query: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val encoded = URLEncoder.encode(query.trim(), "UTF-8")
            val apiUrl = "https://api.duckduckgo.com/?q=$encoded&format=json&no_html=1&skip_disambig=1"
            val req = Request.Builder()
                .url(apiUrl)
                .header("User-Agent", "HiyoriAndroidAgent/1.0")
                .build()

            val summaryBuilder = StringBuilder()
            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val json = JSONObject(resp.body?.string().orEmpty())
                    val abstractText = json.optString("AbstractText")
                    val abstractUrl = json.optString("AbstractURL")
                    if (abstractText.isNotBlank()) {
                        summaryBuilder.append("Summary: $abstractText\nSource: $abstractUrl\n\n")
                    }
                    val related = json.optJSONArray("RelatedTopics")
                    if (related != null) {
                        for (i in 0 until related.length().coerceAtMost(5)) {
                            val item = related.optJSONObject(i) ?: continue
                            val text = item.optString("Text")
                            val firstUrl = item.optString("FirstURL")
                            if (text.isNotBlank()) {
                                summaryBuilder.append("• $text ($firstUrl)\n")
                            }
                        }
                    }
                }
            }

            if (summaryBuilder.isBlank()) {
                val htmlUrl = "https://html.duckduckgo.com/html/?q=$encoded"
                val htmlReq = Request.Builder()
                    .url(htmlUrl)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
                    .build()
                client.newCall(htmlReq).execute().use { resp ->
                    val html = resp.body?.string().orEmpty()
                    val snippetRegex = Regex("class=\"result__snippet\"[^>]*>(.*?)</a>", RegexOption.DOT_MATCHES_ALL)
                    val matches = snippetRegex.findAll(html).take(5).toList()
                    if (matches.isNotEmpty()) {
                        summaryBuilder.append("Search results for '$query':\n")
                        matches.forEachIndexed { idx, m ->
                            val clean = m.groupValues[1].replace(Regex("<[^>]+>"), "").trim()
                            summaryBuilder.append("${idx + 1}. $clean\n")
                        }
                    }
                }
            }

            summaryBuilder.toString().ifBlank { "No instant web results found for '$query'." }
        }
    }

    suspend fun fetchUrlContent(url: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val normalized = normalizeUrl(url)
            val req = Request.Builder()
                .url(normalized)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) error("HTTP ${resp.code} fetching $normalized")
                val rawHtml = resp.body?.string().orEmpty()
                val noScripts = rawHtml
                    .replace(Regex("<script[^>]*>.*?</script>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), " ")
                    .replace(Regex("<style[^>]*>.*?</style>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), " ")
                val plainText = noScripts
                    .replace(Regex("<[^>]+>"), " ")
                    .replace(Regex("\\s+"), " ")
                    .trim()
                "URL: $normalized\n\n${plainText.take(3500)}"
            }
        }
    }

    private fun normalizeUrl(url: String): String {
        val trimmed = url.trim()
        return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else {
            "https://$trimmed"
        }
    }
}
