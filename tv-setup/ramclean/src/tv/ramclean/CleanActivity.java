package tv.ramclean;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * RAM in use, and the apps running in the background with the RAM each one holds.
 * OK on an app closes it; "Close all" closes every one. Closing = kill its background
 * processes, then (with the helper on) Force stop from App info.
 */
public class CleanActivity extends Activity {

    static final String EXTRA_STOPPED = "stopped";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private ActivityManager am;
    private RingView ring;
    private TextView usedText, status, listTitle;
    private LinearLayout list;
    private View closeAll;
    private final List<String> shown = new ArrayList<>();
    // Static: the screen is recreated after the helper's Force stop run.
    private static long usedBefore = -1;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        buildUi();
        readStopped(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        readStopped(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void readStopped(Intent i) {
        int n = i.getIntExtra(EXTRA_STOPPED, -1);
        if (n >= 0) status.setText(n == 1 ? "Force-stopped 1 app" : "Force-stopped " + n + " apps");
    }

    // ---- data -------------------------------------------------------------------------------

    private void refresh() {
        new Thread(() -> {
            long[] u = Mem.usage();
            List<Mem.App> apps = Mem.apps(this);
            handler.post(() -> show(u, apps));
        }).start();
    }

    private void show(long[] u, List<Mem.App> apps) {
        if (u != null) {
            ring.set(u[0], u[1]);
            usedText.setText(u[1] + " MB of " + u[0] + " MB used");
            if (usedBefore > 0 && usedBefore > u[1]) {
                status.setText("Freed " + (usedBefore - u[1]) + " MB");
                usedBefore = -1;
            }
        }
        list.removeAllViews();
        shown.clear();
        listTitle.setText(apps.isEmpty() ? "Nothing left to close" : "Apps that can be closed");
        PackageManager pm = getPackageManager();
        for (Mem.App a : apps) {
            shown.add(a.pkg);
            list.addView(row(pm, a));
        }
        if (!Helper.isOn()) {
            list.addView(note("Force stop is off: turn on Settings → Accessibility → RAM Clean helper. "
                    + "Until then apps are only closed from the background."));
        }
        closeAll.setVisibility(apps.isEmpty() ? View.GONE : View.VISIBLE);
        (apps.isEmpty() ? list : closeAll).requestFocus();
    }

    private void close(List<String> pkgs) {
        if (pkgs.isEmpty()) return;
        long[] u = Mem.usage();
        usedBefore = u == null ? -1 : u[1];
        for (String p : pkgs) am.killBackgroundProcesses(p);
        status.setText("Closing…");
        // With the helper on, it force-stops each app and reopens this screen; otherwise just re-read.
        if (!Helper.forceStop(new ArrayList<>(pkgs))) handler.postDelayed(this::refresh, 1500);
    }

    // ---- views ------------------------------------------------------------------------------

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setBackgroundColor(Color.BLACK);   // Plume's dark ground
        root.setPadding(dp(48), dp(36), dp(48), dp(36));

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        left.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(left, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 4));

        TextView title = text("RAM Clean", 26, Color.WHITE, true);
        left.addView(title);
        ring = new RingView(this);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(dp(190), dp(190));
        rlp.topMargin = dp(20);
        left.addView(ring, rlp);
        usedText = text("", 15, Color.argb(190, 255, 255, 255), false);
        usedText.setPadding(0, dp(16), 0, 0);
        left.addView(usedText);
        status = text("", 17, Color.rgb(52, 211, 153), true);
        status.setPadding(0, dp(8), 0, dp(16));
        left.addView(status);

        TextView all = text("Close all", 17, Color.WHITE, true);
        all.setGravity(Gravity.CENTER);
        all.setPadding(dp(28), dp(12), dp(28), dp(12));
        all.setBackground(selector(dp(26), Color.argb(34, 255, 255, 255), Color.argb(90, 255, 255, 255)));
        all.setFocusable(true);
        all.setOnClickListener(v -> close(new ArrayList<>(shown)));
        closeAll = all;
        left.addView(all);

        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 6);
        rp.leftMargin = dp(36);
        root.addView(right, rp);
        listTitle = text("Reading…", 18, Color.argb(200, 255, 255, 255), true);
        listTitle.setPadding(dp(8), 0, 0, dp(10));
        right.addView(listTitle);
        ScrollView scroll = new ScrollView(this);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list);
        right.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        setContentView(root);
    }

    private View row(PackageManager pm, Mem.App a) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(8), dp(18), dp(8));
        row.setFocusable(true);
        row.setBackground(selector(dp(12), Color.TRANSPARENT, Color.argb(55, 255, 255, 255)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(4);
        row.setLayoutParams(lp);

        CharSequence label = a.pkg;
        Drawable icon = null;
        try {
            ApplicationInfo info = pm.getApplicationInfo(a.pkg, 0);
            label = pm.getApplicationLabel(info);
            icon = pm.getApplicationIcon(info);
        } catch (PackageManager.NameNotFoundException ignored) { }
        View iv = new View(this);
        if (icon != null) iv.setBackground(icon);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(36), dp(36));
        ilp.rightMargin = dp(14);
        row.addView(iv, ilp);

        TextView name = text(label.toString(), 17, Color.WHITE, false);
        row.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        boolean recent = a.lastUsed > 0 && System.currentTimeMillis() - a.lastUsed < 3600_000L;
        row.addView(text(ago(a.lastUsed), 15,
                recent ? Color.rgb(251, 191, 36) : Color.argb(150, 255, 255, 255), recent));
        row.setOnClickListener(v -> close(Collections.singletonList(a.pkg)));
        return row;
    }

    /** "used 5 min ago", "used 3 h ago", "used 2 days ago". */
    private static String ago(long t) {
        if (t <= 0) return "";
        long min = Math.max(0, (System.currentTimeMillis() - t) / 60_000L);
        if (min < 1) return "used just now";
        if (min < 60) return "used " + min + " min ago";
        if (min < 48 * 60) return "used " + (min / 60) + " h ago";
        return "used " + (min / 1440) + " days ago";
    }

    private TextView note(String s) {
        TextView t = text(s, 14, Color.argb(150, 255, 255, 255), false);
        t.setPadding(dp(14), dp(14), dp(14), dp(14));
        return t;
    }

    private TextView text(String s, int sp, int color, boolean medium) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        if (medium) t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return t;
    }

    private static StateListDrawable selector(float radius, int normal, int focused) {
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[] { android.R.attr.state_focused }, rounded(radius, focused));
        s.addState(new int[0], rounded(radius, normal));
        return s;
    }

    private static GradientDrawable rounded(float radius, int color) {
        GradientDrawable d = new GradientDrawable();
        d.setCornerRadius(radius);
        d.setColor(color);
        return d;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    /** Gradient ring showing the share of RAM in use, with the percentage in the middle. */
    private static class RingView extends View {
        private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint big = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private float pct;

        RingView(Context c) {
            super(c);
            track.setStyle(Paint.Style.STROKE);
            track.setColor(Color.argb(45, 255, 255, 255));
            ring.setStyle(Paint.Style.STROKE);
            ring.setStrokeCap(Paint.Cap.ROUND);
            big.setColor(Color.WHITE);
            big.setTextAlign(Paint.Align.CENTER);
            big.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        }

        void set(long total, long used) {
            pct = Math.min(1f, used / (float) total);
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            float s = Math.min(getWidth(), getHeight()), stroke = s * 0.08f, r = s / 2 - stroke;
            float cx = getWidth() / 2f, cy = getHeight() / 2f;
            track.setStrokeWidth(stroke);
            ring.setStrokeWidth(stroke);
            ring.setShader(new LinearGradient(cx - r, cy - r, cx + r, cy + r,
                    Color.WHITE, Color.argb(150, 255, 255, 255), Shader.TileMode.CLAMP));
            rect.set(cx - r, cy - r, cx + r, cy + r);
            c.drawArc(rect, 0, 360, false, track);
            c.drawArc(rect, -90, 360 * pct, false, ring);
            big.setTextSize(s * 0.22f);
            c.drawText(Math.round(pct * 100) + "%", cx, cy + s * 0.08f, big);
        }
    }
}
