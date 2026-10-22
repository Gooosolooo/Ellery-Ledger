package cn.local.jiyibi;

import android.content.Context;

/** 本地识别模式；切换只影响后续识别，不修改已有草稿或账目。 */
public final class RecognitionPreferences {
    private static final String FILE_NAME = "recognition_preferences";
    private static final String AMOUNT_ONLY = "amount_only";

    private RecognitionPreferences() { }

    public static boolean isAmountOnly(Context context) {
        return context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE).getBoolean(AMOUNT_ONLY, false);
    }

    public static void setAmountOnly(Context context, boolean enabled) {
        context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE).edit().putBoolean(AMOUNT_ONLY, enabled).apply();
    }
}
