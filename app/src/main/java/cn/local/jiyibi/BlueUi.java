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
    static final int CATEGORY_TAG_BACKGROUND = 0xFFE9E3EB;
    static final int CATEGORY_TAG_TEXT = 0xFF69586F;
    static final int DRAFT_TAG_BACKGROUND = 0xFFF0E5D9;
    static final int DRAFT_TAG_TEXT = 0xFF79624D;
    static final int CARD_RADIUS_DP = 18;
    static final int BUTTON_HEIGHT_DP = 48;

    private BlueUi() { }

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
        view.setPadding(dp(context, 10), dp(context, 5), dp(context, 10), dp(context, 5));
        view.setBackground(round(context, backgroundColor, 10));
        return view;
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
