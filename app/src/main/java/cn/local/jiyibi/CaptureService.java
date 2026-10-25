package cn.local.jiyibi;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.hardware.HardwareBuffer;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.Display;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.widget.Toast;
import com.google.mlkit.vision.common.InputImage;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/** 只响应手动请求；每次重新获取前台窗口，不缓存页面或识别结果。 */
public final class CaptureService extends AccessibilityService {
    private static final long PANEL_CLOSE_DELAY_MILLIS = 650L;
    private static final int MAX_NODE_COUNT = 1200;
    private static final int MAX_NODE_DEPTH = 40;
    private static volatile CaptureService activeService;
    private final AtomicBoolean capturing = new AtomicBoolean(false);
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onServiceConnected() {
        AccessibilityServiceInfo info = getServiceInfo();
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED | AccessibilityEvent.TYPE_WINDOWS_CHANGED;
        info.flags |= AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
                | AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS;
        setServiceInfo(info);
        if (Build.VERSION.SDK_INT >= 33) {
            setCacheEnabled(false);
        }
        activeService = this;
    }

    public static boolean isAvailable() {
        return activeService != null;
    }

    public static boolean requestCaptureFromTile() {
        CaptureService service = activeService;
        if (service == null) {
            return false;
        }
        service.handler.post(service::captureAfterClosingPanel);
        return true;
    }

    private void captureAfterClosingPanel() {
        if (!capturing.compareAndSet(false, true)) {
            Toast.makeText(this, "正在识别上一笔，请稍候", Toast.LENGTH_SHORT).show();
            return;
        }
        CaptureDiagnostics.begin(this);
        int capabilities = getServiceInfo().getCapabilities();
        CaptureDiagnostics.append(this, "系统能力=" + capabilities);
        if ((capabilities & AccessibilityServiceInfo.CAPABILITY_CAN_RETRIEVE_WINDOW_CONTENT) == 0) {
            failCapture("系统未授予读取窗口内容能力，请关闭再开启 Ellery Ledger 辅助功能服务");
            return;
        }
        if (Build.VERSION.SDK_INT < 31
                || !performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)) {
            failCapture("无法收起快捷面板，请将截图分享给 Ellery Ledger 识别");
            return;
        }
        handler.postDelayed(() -> readCurrentPage(0), PANEL_CLOSE_DELAY_MILLIS);
    }

    private void readCurrentPage(int retry) {
        if (activeService != this) {
            failCapture("识别服务已断开，请重新开启服务");
            return;
        }
        AccessibilityNodeInfo root = getRootInActiveWindow();
        boolean missingRoot = root == null;
        if (root == null || "com.android.systemui".contentEquals(
                root.getPackageName() == null ? "" : root.getPackageName())) {
            if (root != null) {
                root.recycle();
            }
            if (retry < 4) {
                handler.postDelayed(() -> readCurrentPage(retry + 1), 200L);
            } else {
                CaptureDiagnostics.append(this, missingRoot ? "页面根节点为空，尝试按前台窗口截图" : "快捷面板仍在前台");
                if (missingRoot && Build.VERSION.SDK_INT >= 34) {
                    AccessibilityWindowInfo window = findFocusedApplicationWindow();
                    if (window != null) {
                        try {
                            captureWindow("", window.getId(), LocalDateTime.now().toString());
                        } finally {
                            window.recycle();
                        }
                        return;
                    }
                }
                failCapture("无法取得前台账单窗口，未进入 OCR；详情见 App 设置中的识别诊断");
            }
            return;
        }
        String packageName = root.getPackageName() == null ? "" : root.getPackageName().toString();
        int windowId = root.getWindowId();
        CaptureDiagnostics.append(this, "前台应用=" + packageName + "，窗口=" + windowId);
        if (!CaptureSourcePolicy.allows(packageName, getPackageName())) {
            root.recycle();
            failCapture("请先切换到账单详情页；不会识别 Ellery Ledger 中的旧账目");
            return;
        }
        List<ReceiptLine> lines = new ArrayList<>();
        try {
            readVisibleText(root, lines, new HashSet<>(), new int[]{0}, 0);
        } catch (RuntimeException exception) {
            lines.clear();
        } finally {
            root.recycle();
        }
        CaptureDiagnostics.append(this, "读取到文字区域数=" + lines.size());
        String capturedAt = LocalDateTime.now().toString();
        LedgerEntry entry = ReceiptLayoutParser.parse(lines);
        if (CaptureSourcePolicy.hasReceiptAmount(entry)) {
            publishCurrentDraft(entry, packageName, windowId, capturedAt, "页面文字");
        } else {
            captureWindow(packageName, windowId, capturedAt, entry);
        }
    }

    private void readVisibleText(AccessibilityNodeInfo node, List<ReceiptLine> lines,
            Set<String> seen, int[] count, int depth) {
        if (++count[0] > MAX_NODE_COUNT || depth > MAX_NODE_DEPTH) {
            return;
        }
        if (node.isVisibleToUser() && !node.isPassword()) {
            CharSequence value = node.getText();
            if (value == null || value.toString().isBlank()) {
                value = node.getContentDescription();
            }
            Rect bounds = new Rect();
            node.getBoundsInScreen(bounds);
            if (value != null && !value.toString().isBlank() && !bounds.isEmpty()) {
                String text = value.toString();
                if (seen.add(text + "@" + bounds.toShortString())) {
                    lines.add(new ReceiptLine(text, bounds.left, bounds.top, bounds.right, bounds.bottom));
                }
            }
        }
        for (int index = 0; index < node.getChildCount() && count[0] < MAX_NODE_COUNT; index++) {
            AccessibilityNodeInfo child = node.getChild(index);
            if (child != null) {
                try {
                    readVisibleText(child, lines, seen, count, depth + 1);
                } finally {
                    child.recycle();
                }
            }
        }
    }

    /** 只取实际获得焦点的应用窗口，不选择后台窗口。调用者释放返回值。 */
    private AccessibilityWindowInfo findFocusedApplicationWindow() {
        List<AccessibilityWindowInfo> windows = getWindows();
        AccessibilityWindowInfo selected = null;
        try {
            for (AccessibilityWindowInfo window : windows) {
                if (!window.isFocused()) {
                    continue;
                }
                if (window.getType() != AccessibilityWindowInfo.TYPE_APPLICATION) {
                    if (selected != null) {
                        selected.recycle();
                    }
                    selected = null;
                    break;
                }
                CharSequence title = window.getTitle();
                if (title != null && title.toString().contains("Ellery Ledger")) {
                    break;
                }
                AccessibilityNodeInfo root = window.getRoot();
                boolean allowed = true;
                if (root != null) {
                    try {
                        allowed = CaptureSourcePolicy.allows(root.getPackageName() == null
                                ? "" : root.getPackageName().toString(), getPackageName());
                    } finally {
                        root.recycle();
                    }
                }
                if (allowed) {
                    selected = AccessibilityWindowInfo.obtain(window);
                }
                break;
            }
        } finally {
            for (AccessibilityWindowInfo window : windows) {
                window.recycle();
            }
        }
        return selected;
    }

    private boolean isCurrentSource(String packageName, int windowId) {
        if (activeService != this) {
            return false;
        }
        AccessibilityNodeInfo current = getRootInActiveWindow();
        if (current != null) {
            try {
                String currentPackage = current.getPackageName() == null ? "" : current.getPackageName().toString();
                return current.getWindowId() == windowId
                        && CaptureSourcePolicy.allows(currentPackage, getPackageName())
                        && (packageName.isEmpty() || packageName.equals(currentPackage));
            } finally {
                current.recycle();
            }
        }
        AccessibilityWindowInfo currentWindow = findFocusedApplicationWindow();
        if (currentWindow == null) {
            return false;
        }
        try {
            return currentWindow.getId() == windowId;
        } finally {
            currentWindow.recycle();
        }
    }

    private void captureWindow(String packageName, int windowId, String capturedAt) {
        captureWindow(packageName, windowId, capturedAt, new LedgerEntry());
    }

    private void captureWindow(String packageName, int windowId, String capturedAt, LedgerEntry pageEntry) {
        if (!isCurrentSource(packageName, windowId)) {
            failCapture("页面已切换，本次识别已取消，请在账单页重新点击");
            return;
        }
        if ((getServiceInfo().getCapabilities() & AccessibilityServiceInfo.CAPABILITY_CAN_TAKE_SCREENSHOT) == 0) {
            publishIncompleteDraft(pageEntry, packageName, windowId, capturedAt,
                    "系统未授予截图能力，请手工补充或重新开启辅助功能服务后重试。");
            return;
        }
        CaptureDiagnostics.append(this, "开始截图，窗口=" + windowId);
        TakeScreenshotCallback callback = new TakeScreenshotCallback() {
            @Override
            public void onSuccess(ScreenshotResult screenshot) {
                Bitmap softwareBitmap;
                try (HardwareBuffer buffer = screenshot.getHardwareBuffer()) {
                    Bitmap hardwareBitmap = Bitmap.wrapHardwareBuffer(buffer, screenshot.getColorSpace());
                    if (hardwareBitmap == null) {
                        publishIncompleteDraft(pageEntry, packageName, windowId, capturedAt,
                                "当前窗口无法截图，请手工补充。");
                        return;
                    }
                    try {
                        softwareBitmap = hardwareBitmap.copy(Bitmap.Config.ARGB_8888, false);
                    } finally {
                        hardwareBitmap.recycle();
                    }
                } catch (RuntimeException exception) {
                    publishIncompleteDraft(pageEntry, packageName, windowId, capturedAt,
                            "当前窗口无法截图，请手工补充。");
                    return;
                }
                if (softwareBitmap == null) {
                    publishIncompleteDraft(pageEntry, packageName, windowId, capturedAt,
                            "当前窗口无法截图，请手工补充。");
                    return;
                }
                if (!isCurrentSource(packageName, windowId)) {
                    softwareBitmap.recycle();
                    failCapture("页面已切换，本次识别已取消");
                    return;
                }
                CaptureDiagnostics.append(CaptureService.this, "截图成功 " + softwareBitmap.getWidth() + "×" + softwareBitmap.getHeight() + "，开始本地 OCR");
                LocalOcr.recognizeCurrentPage(CaptureService.this,
                        InputImage.fromBitmap(softwareBitmap, 0),
                        entry -> publishCurrentDraft(RecognitionDrafts.merge(pageEntry, entry),
                                packageName, windowId, capturedAt, "当前窗口截图"),
                        () -> publishIncompleteDraft(pageEntry, packageName, windowId, capturedAt,
                                "本地识别失败，请手工补充或重新识别。"), softwareBitmap::recycle);
            }

            @Override
            public void onFailure(int errorCode) {
                publishIncompleteDraft(pageEntry, packageName, windowId, capturedAt,
                        "当前页面不允许截图或授权已变化，请手工补充（" + errorCode + "）。");
            }
        };
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                takeScreenshotOfWindow(windowId, getMainExecutor(), callback);
            } else {
                takeScreenshot(Display.DEFAULT_DISPLAY, getMainExecutor(), callback);
            }
        } catch (RuntimeException exception) {
            publishIncompleteDraft(pageEntry, packageName, windowId, capturedAt,
                    "无法截取当前账单，请检查服务授权或手工补充。");
        }
    }

    private void publishCurrentDraft(LedgerEntry entry, String packageName, int windowId,
            String capturedAt, String method) {
        String warning = CaptureSourcePolicy.hasReceiptAmount(entry)
                ? "" : RecognitionDrafts.MISSING_AMOUNT_MESSAGE;
        publishDraft(entry, packageName, windowId, capturedAt, method, warning);
    }

    private void publishIncompleteDraft(LedgerEntry entry, String packageName, int windowId,
            String capturedAt, String warning) {
        publishDraft(entry, packageName, windowId, capturedAt, "页面文字／截图识别未完成", warning);
    }

    private void publishDraft(LedgerEntry entry, String packageName, int windowId,
            String capturedAt, String method, String warning) {
        if (!isCurrentSource(packageName, windowId)) {
            failCapture("页面已切换，本次识别已取消，请重新点击");
            return;
        }
        CaptureDiagnostics.append(this, method + "完成，原文字数=" + entry.getSourceText().length()
                + "，有金额=" + !entry.getAmount().isBlank());
        if (entry.getSourceText().contains("待核对") && entry.getSourceText().contains("最近账目")) {
            failCapture("识别到本 App 页面内容，本次已取消，请切换到账单详情页重试");
            return;
        }
        String source = method + "；来源应用：" + (packageName.isEmpty() ? "当前前台应用窗口" : packageName);
        LocalOcr.persistDraft(this, RecognitionDrafts.prepare(entry, source, capturedAt, warning));
        CaptureDiagnostics.append(this, "新草稿已生成，标识=" + entry.getId());
        capturing.set(false);
        String message = warning.isBlank() ? "本次识别金额：" + entry.getAmount() + "，已生成新的待核对草稿"
                : "识别信息不完整，已保存待核对草稿，请打开 App 手工补充";
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private void failCapture(String message) {
        capturing.set(false);
        CaptureDiagnostics.append(this, "失败：" + message);
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // 不处理页面变化事件，只有手动点击才读取页面。
    }

    @Override
    public void onInterrupt() { }

    @Override
    public boolean onUnbind(Intent intent) {
        if (activeService == this) {
            activeService = null;
        }
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        if (activeService == this) {
            activeService = null;
        }
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
