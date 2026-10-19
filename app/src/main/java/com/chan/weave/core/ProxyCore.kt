package com.chan.weave.core

import android.content.Context

/**
 * Up/down traffic counters in bytes.
 */
data class TrafficStats(
    val txBytes: Long = 0,
    val rxBytes: Long = 0
)

/**
 * Pluggable proxy core abstraction.
 *
 * Each engine (Xray, sing-box, …) implements this interface against its
 * own native library. Callers never touch engine-specific APIs directly —
 * everything goes through here, so adding a third core means writing one
 * new class and registering it, nothing else changes.
 *
 * `configJson` is engine-native: Xray JSON for [CoreType.XRAY],
 * sing-box JSON for [CoreType.SING_BOX]. Config translation from a
 * [com.chan.weave.data.Node] lives in the per-core config builders
 * (next iteration).
 */
interface ProxyCore {

    val type: CoreType

    /** Human-readable engine version, e.g. "v26.10.10". */
    val version: String

    /** One-time init. Safe to call repeatedly. */
    fun initialize(context: Context)

    /**
     * Start the engine with an engine-native JSON config.
     * @return failure with the engine's error message on failure.
     */
    fun start(configJson: String): Result<Unit>

    /** Stop the engine. Idempotent. */
    fun stop()

    val isRunning: Boolean

    /**
     * Measure outbound delay for a single config without disturbing
     * the running instance. Returns ms, or -1 on failure.
     */
    suspend fun measureLatency(configJson: String): Long

    /** Current session traffic counters. */
    fun queryTrafficStats(): TrafficStats
}
