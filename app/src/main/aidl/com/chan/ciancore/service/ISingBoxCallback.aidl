package com.chan.ciancore.service;

/**
 * Callback from :singbox process to main process.
 */
interface ISingBoxCallback {
    /**
     * Protect a socket fd from TUN routing (VpnService.protect).
     * Called from :singbox process, executed in main process.
     */
    boolean protect(int fd);
}
