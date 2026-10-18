package com.chan.ciancore.data

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Batch import: subscription URLs and multi-link text.
 *
 * Subscription formats supported:
 * - Base64-encoded multi-link list (standard v2ray subscription)
 * - Plain text, one share link per line
 */
object SubscriptionManager {

    /** Result of a batch import: how many nodes were added and per-line errors. */
    data class ImportResult(
        val added: Int,
        val failed: Int,
        val errors: List<String> = emptyList()
    )

    /**
     * Fetch a subscription URL and extract raw share links.
     * Returns the list of link strings (not yet parsed into Nodes).
     */
    suspend fun fetchLinks(subUrl: String): Result<List<String>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val url = URL(subUrl.trim())
                require(url.protocol == "http" || url.protocol == "https") {
                    "订阅链接必须以 http:// 或 https:// 开头"
                }
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15000
                    readTimeout = 15000
                    setRequestProperty(
                        "User-Agent",
                        "CianCore/1.0"
                    )
                }
                try {
                    val code = conn.responseCode
                    require(code in 200..299) { "订阅请求失败：HTTP $code" }
                    val body = conn.inputStream.bufferedReader().readText()
                    require(body.isNotBlank()) { "订阅内容为空" }
                    extractLinks(body)
                } finally {
                    conn.disconnect()
                }
            }
        }

    /**
     * Extract share links from subscription body.
     * Tries base64 first, falls back to plain text lines.
     */
    fun extractLinks(body: String): List<String> {
        val trimmed = body.trim()
        // Try base64 decode (standard subscription format).
        val decoded = try {
            val cleaned = trimmed.replace("\\s".toRegex(), "")
            String(Base64.decode(cleaned, Base64.DEFAULT), Charsets.UTF_8)
        } catch (_: Throwable) {
            null
        }
        val text = if (decoded != null && decoded.contains("://")) decoded else trimmed
        return text.lineSequence()
            .map { it.trim() }
            .filter { it.contains("://") }
            .toList()
    }

    /**
     * Parse raw link text (one link per line, or a single link)
     * into Nodes. Returns parsed nodes + per-line error messages.
     */
    fun parseBatch(rawText: String): Pair<List<Node>, List<String>> {
        val links = rawText.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toList()
        val nodes = mutableListOf<Node>()
        val errors = mutableListOf<String>()
        // Also handle the case where the whole text is a base64 subscription.
        val effectiveLinks = if (links.size == 1 && !links[0].contains("://")) {
            extractLinks(links[0])
        } else {
            links.filter { it.contains("://") }
        }
        if (effectiveLinks.isEmpty() && links.isNotEmpty()) {
            errors.add("没有找到有效的节点链接")
            return emptyList<Node>() to errors
        }
        for (link in effectiveLinks) {
            NodeLinkParser.parse(link).fold(
                onSuccess = { nodes.add(it) },
                onFailure = { t ->
                    val preview = link.take(24)
                    errors.add("$preview…：${t.message ?: "解析失败"}")
                }
            )
        }
        return nodes to errors
    }
}
