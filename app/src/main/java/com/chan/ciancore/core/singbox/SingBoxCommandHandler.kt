package com.chan.ciancore.core.singbox

import android.util.Log
import libbox.CommandServerHandler

/**
 * Minimal CommandServerHandler implementation.
 * 7 methods per sing-box v1.14.3 source.
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

    override fun connectSSHAgent(): libbox.SSHAgent? = null

    companion object {
        private const val TAG = "SingBoxHandler"
    }
}
