package tv.ramclean;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayDeque;
import java.util.List;

/**
 * Accessibility helper that force-stops apps: it opens each one's App info and presses
 * Force stop, then OK. The RAM figures on the Home screen are drawn by Plume itself.
 */
public class Helper extends AccessibilityService {

    private static final String SETTINGS_PKG = "com.android.tv.settings";
    private static final long STEP_TIMEOUT_MS = 4000;
    private static final int FIND_STOP = 0, FIND_OK = 1, WAIT_NEXT = 2;

    private static Helper instance;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private ArrayDeque<String> queue;
    private int state, stopped;
    private final Runnable stepTimeout = this::next;

    static boolean isOn() {
        return instance != null;
    }

    /** Force-stops each package in turn; returns false if the helper is switched off. */
    static boolean forceStop(List<String> pkgs) {
        if (instance == null || pkgs.isEmpty()) return false;
        instance.start(pkgs);
        return true;
    }

    @Override
    protected void onServiceConnected() {
        instance = this;
    }

    @Override
    public void onDestroy() {
        instance = null;
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    @Override
    public void onInterrupt() { }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent e) {
        CharSequence pkg = e.getPackageName();
        if (queue != null && pkg != null && SETTINGS_PKG.contentEquals(pkg)) step();
    }

    private void start(List<String> pkgs) {
        queue = new ArrayDeque<>(pkgs);
        stopped = 0;
        next();
    }

    private void next() {
        handler.removeCallbacks(stepTimeout);
        if (queue == null) return;
        String pkg = queue.poll();
        if (pkg == null) {
            finishRun();
            return;
        }
        state = FIND_STOP;
        startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + pkg))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION));
        handler.postDelayed(stepTimeout, STEP_TIMEOUT_MS);
    }

    private void step() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;
        if (state == FIND_STOP) {
            AccessibilityNodeInfo title = find(root, "android:id/title", "Force stop");
            AccessibilityNodeInfo target = clickable(title);
            if (target == null) return;
            if (!title.isEnabled() || !target.isEnabled()) {   // greyed out: already stopped
                state = WAIT_NEXT;
                handler.post(this::next);
                return;
            }
            target.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            state = FIND_OK;
            handler.removeCallbacks(stepTimeout);
            handler.postDelayed(stepTimeout, STEP_TIMEOUT_MS);
        } else if (state == FIND_OK) {
            AccessibilityNodeInfo ok = clickable(find(root, SETTINGS_PKG + ":id/guidedactions_item_title", "OK"));
            if (ok == null) return;
            ok.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            stopped++;
            state = WAIT_NEXT;
            handler.removeCallbacks(stepTimeout);
            handler.postDelayed(this::next, 300);
        }
    }

    private void finishRun() {
        queue = null;
        startActivity(new Intent(this, CleanActivity.class)
                .putExtra(CleanActivity.EXTRA_STOPPED, stopped)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION));
    }

    private static AccessibilityNodeInfo find(AccessibilityNodeInfo root, String id, String text) {
        List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByViewId(id);
        for (AccessibilityNodeInfo n : nodes) {
            if (n.getText() != null && text.contentEquals(n.getText())) return n;
        }
        return null;
    }

    private static AccessibilityNodeInfo clickable(AccessibilityNodeInfo n) {
        while (n != null && !n.isClickable()) n = n.getParent();
        return n;
    }
}
