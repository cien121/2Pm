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

    const val GEOIP_URL =
        "https://github.com/Loyalsoldier/v2ray-rules-dat/releases/latest/download/geoip.dat"
    const val GEOSITE_URL =
        "https://github.com/Loyalsoldier/v2ray-rules-dat/releases/latest/download/geosite.dat"

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
     * Returns null on success, or an error message.
     */
    suspend fun update(context: Context): String? = withContext(Dispatchers.IO) {
        downloadTo(GEOIP_URL, geoipFile(context)) ?:
        downloadTo(GEOSITE_URL, geositeFile(context))
    }

    private fun downloadTo(url: String, dest: File): String? {
        return try {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 20000
                readTimeout = 60000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "CianCore/1.0")
            }
            try {
                val code = conn.responseCode
                if (code !in 200..299) return "下载失败：HTTP $code"
                val tmp = File(dest.parent, dest.name + ".tmp")
                conn.inputStream.use { input ->
                    tmp.outputStream().use { output -> input.copyTo(output) }
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
