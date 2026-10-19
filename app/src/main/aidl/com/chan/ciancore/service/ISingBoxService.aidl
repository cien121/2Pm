package com.chan.ciancore.service;

import android.os.ParcelFileDescriptor;

/**
 * AIDL for sing-box running in :singbox process.
 * Main process (VpnService) creates TUN and passes fd + config here.
 */
interface ISingBoxService {
    /**
     * Start sing-box with given config. tunFd is the TUN ParcelFileDescriptor
     * (fd detached and dup'd by Go side). callback for protect() etc.
     * Returns 0 on success, non-zero error code otherwise.
     */
    int start(in String configJson, in ParcelFileDescriptor tunFd, in ISingBoxCallback callback);

    /** Stop sing-box. */
    void stop();

    /** Returns true if sing-box is running. */
    boolean isRunning();
}
