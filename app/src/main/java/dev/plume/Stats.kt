package dev.plume

import android.content.Context
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.TrafficStats
import android.os.SystemClock
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import java.io.File
import java.util.Locale

/**
 * The top-bar line "● RAM 52%  ↓ 2.4 MB/s  VPN off". RAM is counted out of what apps can use: the TV's
 * picture chip reserves a CMA block (~650 MB on a 2 GB set) that Android can never free, so it is left out.
 * Selecting the line opens RAM Clean (tv.ramclean), which lists and force-stops background apps.
 */
internal class Stats(private val context: Context) {
    private var lastRx = 0L
    private var lastAt = 0L

    /** Speed is measured from the next reading on, not averaged over time spent away from Home. */
    fun reset() {
        lastRx = 0L
    }

    fun line(): CharSequence {
        val s = SpannableStringBuilder()
        ramPercent()?.let { pct ->
            val dot = if (pct < 70) Color.rgb(52, 211, 153) else if (pct < 85) Color.rgb(251, 191, 36) else Color.rgb(248, 113, 113)
            s.append("●", ForegroundColorSpan(dot), 0).append("  RAM $pct%")
        }
        speed()?.let { s.append("     ↓ ").append(it) }
        s.append("     ").append(if (vpnOn()) "VPN on" else "VPN off")
        return s
    }

    /** Used share of MemTotal - CmaTotal; null if /proc/meminfo can't be read. */
    private fun ramPercent(): Int? = runCatching {
        val m = File("/proc/meminfo").readLines().associate { l ->
            l.substringBefore(':') to (l.substringAfter(':').trim().substringBefore(' ').toLongOrNull() ?: 0L)
        }
        val usable = m.getValue("MemTotal") - (m["CmaTotal"] ?: 0L)
        val free = (m.getValue("MemAvailable") - (m["CmaFree"] ?: 0L)).coerceIn(0L, usable)
        ((usable - free) * 100 / usable).toInt()
    }.getOrNull()

    private fun speed(): String? {
        val rx = TrafficStats.getTotalRxBytes()
        val now = SystemClock.elapsedRealtime()
        val out = if (rx == TrafficStats.UNSUPPORTED.toLong() || lastRx <= 0 || now <= lastAt) null else {
            val perSec = (rx - lastRx) * 1000.0 / (now - lastAt)
            if (perSec >= 1_000_000) String.format(Locale.US, "%.1f MB/s", perSec / 1_000_000)
            else "${Math.round(perSec / 1000)} KB/s"
        }
        lastRx = rx
        lastAt = now
        return out
    }

    private fun vpnOn(): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        @Suppress("DEPRECATION")
        return cm.allNetworks.any { cm.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true }
    }

    companion object {
        const val RAM_CLEAN = "tv.ramclean"
        const val TICK_MS = 3000L
    }
}
