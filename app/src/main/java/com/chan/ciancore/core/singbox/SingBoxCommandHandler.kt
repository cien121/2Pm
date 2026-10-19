package com.chan.ciancore.core.singbox

import android.util.Log
import libbox.CommandServerHandler

/**
 * Minimal CommandServerHandler implementation.
 * Signatures verified against sing-box v1.14.3 command_server.go.
 */
class SingBoxCommandHandler : CommandServerHandler {

    override fun serviceStop() {
        Log.i(TAG, "serviceStop")
    }

    override fun serviceReload() {
        Log.i(TAG, "serviceReload")
    }

    override fun getSystemProxyStatus(): libbox.SystemProxyStatus? = null

    override fun setSystemProxyEnabled(enabled: Boolean) {
        Log.i(TAG, "setSystemProxyEnabled=$enabled")
    }

    override fun triggerNativeCrash() {
        Log.w(TAG, "triggerNativeCrash requested")
    }

    override fun writeDebugMessage(message: String) {
        Log.d(TAG, "libbox: $message")
    }

    override fun connectSSHAgent(): Int = -1

    companion object {
        private const val TAG = "SingBoxHandler"
    }
}
