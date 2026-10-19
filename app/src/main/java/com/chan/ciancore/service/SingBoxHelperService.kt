package com.chan.ciancore.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.util.Log
import com.chan.ciancore.core.singbox.SingBoxCore
import com.chan.ciancore.core.singbox.SingBoxPlatformInterface
import libbox.TunOptions

/**
 * Runs in :singbox process (android:process=":singbox").
 * Hosts the sing-box Go runtime isolated from Xray's Go runtime.
 *
 * Main process VpnService creates TUN and passes fd + config via AIDL.
 * openTun() just returns the pre-received fd (dup'd by Go).
 */
class SingBoxHelperService : Service() {

    companion object {
        private const val TAG = "SingBoxHelper"
    }

    @Volatile
    private var tunFd: Int = -1

    @Volatile
    private var core: SingBoxCore? = null

    private val binder = object : ISingBoxService.Stub() {
        override fun start(
            configJson: String?,
            tunFdPfd: ParcelFileDescriptor?,
            callback: ISingBoxCallback?
        ): String? {
            return try {
                if (configJson.isNullOrEmpty()) return "empty config"
                if (tunFdPfd == null) return "null tunFd"
                // Detach fd; Go dups it
                tunFd = tunFdPfd.detachFd()
                Log.i(TAG, "start: got tunFd=$tunFd")

                val platform = SingBoxPlatformInterface(
                    tunBuilder = { _: TunOptions -> tunFd },
                    protectSocket = { fd ->
                        // Cross-process protect via callback to main process VpnService
                        try {
                            callback?.protect(fd) ?: false
                        } catch (t: Throwable) {
                            Log.w(TAG, "protect($fd) via callback failed", t)
                            false
                        }
                    }
                )
                val c = SingBoxCore(applicationContext, platform)
                val result = c.start(configJson)
                val err = result.exceptionOrNull()
                if (err != null) {
                    Log.e(TAG, "sing-box start failed", err)
                    return "start failed: ${err.message} (${err.javaClass.simpleName})"
                }
                core = c
                Log.i(TAG, "sing-box started")
                null // success
            } catch (t: Throwable) {
                Log.e(TAG, "start failed", t)
                "exception: ${t.message} (${t.javaClass.simpleName})"
            }
        }

        override fun stop() {
            try {
                core?.stop()
            } catch (t: Throwable) {
                Log.w(TAG, "stop failed", t)
            } finally {
                core = null
                tunFd = -1
            }
        }

        override fun isRunning(): Boolean = core != null
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "SingBoxHelperService created in :singbox process")
    }

    override fun onDestroy() {
        try {
            core?.stop()
        } catch (_: Throwable) {
        }
        super.onDestroy()
    }
}
