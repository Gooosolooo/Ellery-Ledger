package cn.local.jiyibi;

import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** 页面内的底部原文面板，不创建模态窗口，上方输入框始终可以点击。 */
@SuppressLint("ViewConstructor")
final class SourceTextPanel extends LinearLayout {
    private final TextView targetHint;

    SourceTextPanel(Context context, String source, SourceSelectionView.Listener listener, Runnable close) {
        super(context);
        setOrientation(VERTICAL);
        setPadding(BlueUi.dp(context, 16), BlueUi.dp(context, 8), BlueUi.dp(context, 16), BlueUi.dp(context, 8));
        setBackground(BlueUi.round(context, BlueUi.CARD, BlueUi.CARD_RADIUS_DP));
        setElevation(BlueUi.dp(context, 10));
        View handle = new View(context);
        handle.setBackground(BlueUi.round(context, BlueUi.BORDER, 3));
        LayoutParams handleParams = new LayoutParams(BlueUi.dp(context, 34), BlueUi.dp(context, 4));
        handleParams.gravity = Gravity.CENTER_HORIZONTAL;
        addView(handle, handleParams);
        LinearLayout header = BlueUi.row(context);
        header.addView(BlueUi.text(context, "本地识别原文", 18, BlueUi.DEEP_BLUE, true),
                new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        header.addView(BlueUi.button(context, "收起", false, close),
                new LayoutParams(BlueUi.dp(context, 64), BlueUi.dp(context, 42)));
        addView(header);
        targetHint = BlueUi.text(context, "先点击上方输入框定位光标，再按住原文拖选；松手填入", 12, BlueUi.SUB, false);
        addView(targetHint, BlueUi.below(context, 4));
        ScrollView scroll = new ScrollView(context);
        SourceSelectionView text = new SourceSelectionView(context, source, listener);
        scroll.addView(text);
        addView(scroll, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        LinearLayout actions = BlueUi.row(context);
        actions.addView(BlueUi.button(context, "复制选中", false, () -> copy(text.selectedText())),
                new LayoutParams(0, BlueUi.dp(context, 42), 1f));
        actions.addView(BlueUi.button(context, "复制全部", false, () -> copy(source)),
                new LayoutParams(0, BlueUi.dp(context, 42), 1f));
        addView(actions);
    }

    void setTargetHint(String label) {
        targetHint.setText(label);
    }

    private void copy(String text) {
        if (text.isEmpty()) {
            Toast.makeText(getContext(), "请先选取文字", Toast.LENGTH_SHORT).show();
            return;
        }
        ClipboardManager clipboard = getContext().getSystemService(ClipboardManager.class);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("账单识别原文", text));
            if (Build.VERSION.SDK_INT < 33) {
                Toast.makeText(getContext(), "已复制", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
