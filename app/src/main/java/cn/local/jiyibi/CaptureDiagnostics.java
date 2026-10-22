package cn.local.jiyibi;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import java.time.LocalDateTime;

/** 记录最近一次手动识别的阶段和本地 OCR 文字；不保存图片、不上传。 */
public final class CaptureDiagnostics {
    private static final String PREFERENCES = "capture_diagnostics";
    private static final String REPORT = "last_report";
    private static final int MAX_REPORT_LENGTH = 6000;

    private CaptureDiagnostics() { }

    public static void begin(Context context) {
        preferences(context).edit().remove("last_ocr_text").putString(REPORT, "开始时间：" + LocalDateTime.now()).apply();
    }

    public static void append(Context context, String stage) {
        String previous = read(context);
        String report = previous + "\n" + stage;
        if (report.length() > MAX_REPORT_LENGTH) {
            report = report.substring(report.length() - MAX_REPORT_LENGTH);
        }
        preferences(context).edit().putString(REPORT, report).apply();
        Log.i("LedgerCapture", stage);
    }

    public static String read(Context context) {
        return preferences(context).getString(REPORT, "还没有手动识别记录");
    }

    /** 最近一次的 OCR 文字只保存在本机私有空间，供用户检查解析失败原因。 */
    public static void recordRecognizedText(Context context, String text) {
        preferences(context).edit().putString("last_ocr_text",
                text.substring(0, Math.min(text.length(), MAX_REPORT_LENGTH))).apply();
    }

    public static String readRecognizedText(Context context) {
        return preferences(context).getString("last_ocr_text", "本次没有 OCR 文字记录");
    }

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
    }
}
