package com.chan.ciancore.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Geo resource files (geoip.dat / geosite.dat) for routing rules.
 *
 * Bundled in APK assets, copied to the Xray asset dir on first launch.
 * Can be updated from the official release URL.
 */
object GeoManager {

    // jsdelivr is fast inside mainland China; GitHub releases as fallback.
    private val GEOIP_URLS = listOf(
        "https://cdn.jsdelivr.net/gh/Loyalsoldier/v2ray-rules-dat@release/geoip.dat",
        "https://github.com/Loyalsoldier/v2ray-rules-dat/releases/latest/download/geoip.dat"
    )
    private val GEOSITE_URLS = listOf(
        "https://cdn.jsdelivr.net/gh/Loyalsoldier/v2ray-rules-dat@release/geosite.dat",
        "https://github.com/Loyalsoldier/v2ray-rules-dat/releases/latest/download/geosite.dat"
    )

    fun assetDir(context: Context): File =
        File(context.filesDir, "xray").apply { mkdirs() }

    fun geoipFile(context: Context): File = File(assetDir(context), "geoip.dat")
    fun geositeFile(context: Context): File = File(assetDir(context), "geosite.dat")

    fun isReady(context: Context): Boolean =
        geoipFile(context).exists() && geositeFile(context).exists()

    /** Copy bundled assets into the Xray dir if missing. */
    suspend fun ensureBundled(context: Context): Unit = withContext(Dispatchers.IO) {
        copyAsset(context, "geoip.dat", geoipFile(context))
        copyAsset(context, "geosite.dat", geositeFile(context))
    }

    /**
     * Ensure geo files are available. Downloads them if missing.
     * Called on app startup; runs in background.
     */
    suspend fun ensureAvailable(context: Context): Unit = withContext(Dispatchers.IO) {
        if (isReady(context)) return@withContext
        // Try bundled assets first (if present)
        ensureBundled(context)
        if (isReady(context)) return@withContext
        // Download from mirrors
        update(context)
    }

    private fun copyAsset(context: Context, assetName: String, dest: File) {
        if (dest.exists() && dest.length() > 0) return
        try {
            context.assets.open(assetName).use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }
        } catch (_: Throwable) {
            // Asset may be missing in dev builds; update can fetch it later.
        }
    }

    /**
     * Download fresh geo files, replacing the local copies.
     * Tries each mirror in order. Returns null on success, or an error message.
     * @param onProgress called with (fileLabel, downloadedBytes, totalBytes or -1).
     */
    suspend fun update(
        context: Context,
        onProgress: (String, Long, Long) -> Unit = { _, _, _ -> }
    ): String? = withContext(Dispatchers.IO) {
        downloadTo(GEOIP_URLS, "geoip.dat", geoipFile(context), onProgress)
            ?: downloadTo(GEOSITE_URLS, "geosite.dat", geositeFile(context), onProgress)
    }

    private fun downloadTo(
        urls: List<String>,
        label: String,
        dest: File,
        onProgress: (String, Long, Long) -> Unit
    ): String? {
        var lastError: String? = null
        for (url in urls) {
            lastError = tryDownload(url, label, dest, onProgress)
            if (lastError == null) return null
        }
        return lastError
    }

    private fun tryDownload(
        url: String,
        label: String,
        dest: File,
        onProgress: (String, Long, Long) -> Unit
    ): String? {
        return try {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15000
                readTimeout = 30000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "CianCore/1.0")
            }
            try {
                val code = conn.responseCode
                if (code !in 200..299) return "下载失败：HTTP $code"
                val total = conn.contentLengthLong
                val tmp = File(dest.parent, dest.name + ".tmp")
                var downloaded = 0L
                conn.inputStream.use { input ->
                    tmp.outputStream().use { output ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            output.write(buf, 0, n)
                            downloaded += n
                            onProgress(label, downloaded, total)
                        }
                    }
                }
                if (tmp.length() < 1024 * 100) {
                    tmp.delete()
                    return "下载的文件异常（过小）"
                }
                tmp.renameTo(dest)
                null
            } finally {
                conn.disconnect()
            }
        } catch (t: Throwable) {
            "下载失败：${t.message ?: t.javaClass.simpleName}"
        }
    }
}
