package tv.ramclean;

import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** RAM totals from /proc/meminfo, and the apps that can be closed. */
final class Mem {

    // Never listed or closed: system, Google services, voice search, keyboard, live TV and
    // inputs, Chromecast, the Home screen and this app.
    private static final String[] KEEP = {
        "android", "com.android.", "com.google.android.gms", "com.google.android.gsf",
        "com.google.android.katniss", "com.google.android.inputmethod",
        "com.google.android.apps.mediashell", "com.google.android.tvrecommendations",
        "com.sony.", "com.realtek.", "dev.plume", "tv.ramclean",
    };

    private Mem() { }

    static final class App {
        final String pkg;
        final long lastUsed;   // ms since epoch, 0 if unknown
        App(String pkg, long lastUsed) { this.pkg = pkg; this.lastUsed = lastUsed; }
    }

    /**
     * {totalMb, usedMb} for the RAM apps can actually use. The TV's picture chip reserves a CMA
     * block (~650 MB here) for video that Android cannot free, so it is left out of both numbers:
     * total = MemTotal - CmaTotal, used = total - (MemAvailable - CmaFree). Null if unreadable.
     */
    static long[] usage() {
        long total = -1, avail = -1, cmaTotal = 0, cmaFree = 0;
        try (BufferedReader r = new BufferedReader(new FileReader("/proc/meminfo"))) {
            String line;
            while ((line = r.readLine()) != null) {
                if (line.startsWith("MemTotal:")) total = kb(line);
                else if (line.startsWith("MemAvailable:")) avail = kb(line);
                else if (line.startsWith("CmaTotal:")) cmaTotal = kb(line);
                else if (line.startsWith("CmaFree:")) cmaFree = kb(line);
            }
        } catch (Exception e) {
            return null;
        }
        if (total <= 0 || avail < 0) return null;
        long usable = total - cmaTotal;
        long free = Math.max(0, Math.min(usable, avail - cmaFree));
        return new long[] { usable / 1024, (usable - free) / 1024 };
    }

    private static long kb(String line) {
        return Long.parseLong(line.replaceAll("[^0-9]", ""));
    }

    static boolean keep(String pkg) {
        for (String k : KEEP) {
            if (k.endsWith(".") ? pkg.startsWith(k) : pkg.equals(k)) return true;
        }
        return false;
    }

    /**
     * Apps that have run since they were last force-stopped (Android clears FLAG_STOPPED when an
     * app starts and sets it again on Force stop), most recently used first. Android 14 does not
     * let one app read another's RAM, so the list is ordered by last use instead.
     */
    static List<App> apps(Context c) {
        PackageManager pm = c.getPackageManager();
        Map<String, UsageStats> usage = null;
        try {
            UsageStatsManager usm = (UsageStatsManager) c.getSystemService(Context.USAGE_STATS_SERVICE);
            long now = System.currentTimeMillis();
            usage = usm.queryAndAggregateUsageStats(now - 7L * 24 * 3600 * 1000, now);
        } catch (Exception ignored) { }

        List<App> out = new ArrayList<>();
        for (ApplicationInfo info : pm.getInstalledApplications(0)) {
            if (!info.enabled || (info.flags & ApplicationInfo.FLAG_STOPPED) != 0 || keep(info.packageName)) continue;
            // Only apps a person would recognise: ones with a TV or phone launcher entry.
            if (pm.getLeanbackLaunchIntentForPackage(info.packageName) == null
                    && pm.getLaunchIntentForPackage(info.packageName) == null) continue;
            UsageStats u = usage == null ? null : usage.get(info.packageName);
            out.add(new App(info.packageName, u == null ? 0 : u.getLastTimeUsed()));
        }
        Collections.sort(out, (x, y) -> Long.compare(y.lastUsed, x.lastUsed));
        return out;
    }
}
