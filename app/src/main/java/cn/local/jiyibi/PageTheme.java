package cn.local.jiyibi;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

/** 四个主页的莫兰迪配色；类别、日期和状态标签保留独立语义颜色。 */
enum PageTheme {
    OVERVIEW(0xFFF6F3F8, 0xFF82708F, 0xFF655174, 0xFFECE4F1,
            0xFF44394D, 0xFF7B6D85, 0xFFE4DBE9),
    DRAFTS(0xFFF2F6F0, 0xFF6C8162, 0xFF4F6745, 0xFFE2EBDC,
            0xFF394A33, 0xFF718269, 0xFFDAE4D4),
    LEDGER(BlueUi.BACKGROUND, BlueUi.BLUE, BlueUi.DEEP_BLUE, BlueUi.PALE_BLUE,
            BlueUi.INK, BlueUi.SUB, BlueUi.BORDER),
    SETTINGS(0xFFF3F4F3, 0xFF747B78, 0xFF505A55, 0xFFE4E8E5,
            0xFF3C4540, 0xFF727C76, 0xFFDCE1DD);

    final int backgroundColor;
    private final int primaryColor;
    private final int deepColor;
    private final int paleColor;
    private final int inkColor;
    private final int subColor;
    private final int borderColor;

    PageTheme(int backgroundColor, int primaryColor, int deepColor, int paleColor,
            int inkColor, int subColor, int borderColor) {
        this.backgroundColor = backgroundColor;
        this.primaryColor = primaryColor;
        this.deepColor = deepColor;
        this.paleColor = paleColor;
        this.inkColor = inkColor;
        this.subColor = subColor;
        this.borderColor = borderColor;
    }

    static PageTheme forTab(int position) {
        switch (position) {
            case 0:
                return OVERVIEW;
            case 1:
                return DRAFTS;
            case 3:
                return SETTINGS;
            default:
                return LEDGER;
        }
    }

    /** 只替换页面主题色，错误提示和固定标签颜色不参与替换。 */
    void apply(View view) {
        if (BlueUi.hasFixedTagColors(view)) {
            return;
        }
        if (view instanceof Button) {
            Button button = (Button) view;
            int textColor = button.getCurrentTextColor();
            styleButton(button, textColor == Color.WHITE);
            if (textColor == BlueUi.DANGER) {
                button.setTextColor(BlueUi.DANGER);
            }
        } else {
            if (view instanceof TextView) {
                TextView text = (TextView) view;
                text.setTextColor(resolve(text.getCurrentTextColor()));
            }
            Drawable background = view.getBackground();
            if (background instanceof GradientDrawable) {
                GradientDrawable shape = (GradientDrawable) background.mutate();
                ColorStateList colors = shape.getColor();
                if (colors != null) {
                    shape.setColor(resolve(colors.getDefaultColor()));
                }
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                apply(group.getChildAt(index));
            }
        }
    }

    void styleButton(Button button, boolean selected) {
        button.setTextColor(selected ? Color.WHITE : deepColor);
        int rippleColor = (primaryColor & 0x00FFFFFF) | 0x22000000;
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(rippleColor),
                BlueUi.round(button.getContext(), selected ? primaryColor : paleColor, 20),
                BlueUi.round(button.getContext(), Color.WHITE, 20)));
    }

    // 顶栏在切页时会反复换色，因此同时识别四套主题的来源颜色。
    private int resolve(int color) {
        for (PageTheme source : values()) {
            if (color == source.backgroundColor) {
                return backgroundColor;
            }
            if (color == source.primaryColor) {
                return primaryColor;
            }
            if (color == source.deepColor) {
                return deepColor;
            }
            if (color == source.paleColor) {
                return paleColor;
            }
            if (color == source.inkColor) {
                return inkColor;
            }
            if (color == source.subColor) {
                return subColor;
            }
            if (color == source.borderColor) {
                return borderColor;
            }
        }
        return color;
    }
}
