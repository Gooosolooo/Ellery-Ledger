package cn.local.jiyibi;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** 参考 Focus Buddy 的留白和圆角，以雾霾蓝作为记账应用的主色。 */
final class BlueUi {
    static final int BACKGROUND = 0xFFF2F5F9;
    static final int CARD = 0xFFFCFDFE;
    static final int INK = 0xFF2C4052;
    static final int SUB = 0xFF708598;
    static final int BLUE = 0xFF607D94;
    static final int DEEP_BLUE = 0xFF3E5E79;
    static final int PALE_BLUE = 0xFFE3ECF4;
    static final int BORDER = 0xFFDCE5ED;
    static final int DANGER = 0xFF9B555D;
    static final int DATE_TAG_BACKGROUND = 0xFFE2E9ED;
    static final int DATE_TAG_TEXT = 0xFF4D6371;
    static final int DRAFT_TAG_BACKGROUND = 0xFFF0E5D9;
    static final int DRAFT_TAG_TEXT = 0xFF79624D;
    static final int CARD_RADIUS_DP = 18;
    static final int BUTTON_HEIGHT_DP = 48;

    private static final Object FIXED_TAG_COLORS = new Object();

    private BlueUi() { }

    static boolean hasFixedTagColors(View view) {
        return view.getTag() == FIXED_TAG_COLORS;
    }

    static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    static GradientDrawable round(Context context, int color, int radius) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(color);
        background.setCornerRadius(dp(context, radius));
        return background;
    }

    static TextView text(Context context, String value, int size, int color, boolean bold) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setIncludeFontPadding(false);
        view.setLineSpacing(dp(context, 3), 1f);
        if (bold) {
            view.setTypeface(null, Typeface.BOLD);
        }
        return view;
    }

    /** 创建使用独立配色的账目标签。 */
    static TextView tag(Context context, String value, int backgroundColor, int textColor) {
        TextView view = text(context, value, 12, textColor, true);
        view.setTag(FIXED_TAG_COLORS);
        view.setPadding(dp(context, 10), dp(context, 5), dp(context, 10), dp(context, 5));
        view.setBackground(round(context, backgroundColor, 10));
        return view;
    }

    /** 按类别名称使用固定配色，未知类别使用中性灰。 */
    static TextView categoryTag(Context context, String category) {
        CategoryPalette palette;
        switch (category == null ? "" : category) {
            case "餐饮": palette = CategoryPalette.DINING; break;
            case "享受": palette = CategoryPalette.LEISURE; break;
            case "交通": palette = CategoryPalette.TRANSPORT; break;
            case "提升": palette = CategoryPalette.LEARNING; break;
            case "日用": palette = CategoryPalette.DAILY; break;
            case "社交": palette = CategoryPalette.SOCIAL; break;
            case "穿搭": palette = CategoryPalette.CLOTHING; break;
            case "咪用": palette = CategoryPalette.PET; break;
            case "大额": palette = CategoryPalette.LARGE_EXPENSE; break;
            default: palette = CategoryPalette.NEUTRAL; break;
        }
        String label = category == null || category.isEmpty() ? "类别待选择" : category;
        return tag(context, label, palette.backgroundColor, palette.textColor);
    }

    /** 莫兰迪浅底与深色文字配对，避免依赖类别排序。 */
    private enum CategoryPalette {
        DINING(0xFFF0E1D7, 0xFF795642),
        LEISURE(0xFFE9E3EB, 0xFF69586F),
        TRANSPORT(0xFFE0E8EF, 0xFF4D637B),
        LEARNING(0xFFE3E9DC, 0xFF536747),
        DAILY(0xFFECE7DC, 0xFF6D634E),
        SOCIAL(0xFFF0E0E4, 0xFF805965),
        CLOTHING(0xFFEADFE7, 0xFF76566E),
        PET(0xFFDEEBE8, 0xFF456D65),
        LARGE_EXPENSE(0xFFEEDDD9, 0xFF82534C),
        NEUTRAL(0xFFE6E6E4, 0xFF60615C);

        private final int backgroundColor;
        private final int textColor;

        CategoryPalette(int backgroundColor, int textColor) {
            this.backgroundColor = backgroundColor;
            this.textColor = textColor;
        }
    }

    static LinearLayout column(Context context) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    static LinearLayout row(Context context) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        return layout;
    }

    static LinearLayout card(Context context) {
        LinearLayout layout = column(context);
        layout.setPadding(dp(context, 18), dp(context, 18), dp(context, 18), dp(context, 18));
        layout.setBackground(round(context, CARD, CARD_RADIUS_DP));
        layout.setElevation(dp(context, 1));
        return layout;
    }

    static LinearLayout.LayoutParams below(Context context, int margin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(context, margin);
        return params;
    }

    static Button button(Context context, String label, boolean primary, Runnable action) {
        Button button = new Button(context);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(15);
        button.setTextColor(primary ? 0xFFFFFFFF : DEEP_BLUE);
        button.setTypeface(null, Typeface.BOLD);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setMinHeight(dp(context, BUTTON_HEIGHT_DP));
        button.setMinimumHeight(dp(context, BUTTON_HEIGHT_DP));
        button.setPadding(dp(context, 14), dp(context, 10), dp(context, 14), dp(context, 10));
        button.setStateListAnimator(null);
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(0x22607D94),
                round(context, primary ? BLUE : PALE_BLUE, 20), round(context, 0xFFFFFFFF, 20)));
        button.setOnClickListener(view -> action.run());
        return button;
    }

    static void appendText(LinearLayout parent, String value, int size, int color, boolean bold, int margin) {
        Context context = parent.getContext();
        parent.addView(text(context, value, size, color, bold), below(context, margin));
    }
}
