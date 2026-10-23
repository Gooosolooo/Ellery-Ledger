package cn.local.jiyibi;

import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Build;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.widget.ImageButton;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** 页面内的底部原文面板，不创建模态窗口，上方输入框始终可以点击。 */
@SuppressLint("ViewConstructor")
final class SourceTextPanel extends LinearLayout {
    private static final long HANDLE_ANIMATION_DURATION_MILLIS = 180L;
    private static final int MIN_DISMISS_DISTANCE_DP = 56;
    private static final float DISMISS_HEIGHT_FRACTION = 0.2f;
    private final TextView targetHint;
    private final ImageButton keyboardButton;
    private float handleDownX;
    private float handleDownY;
    private boolean draggingHandle;
    private boolean trackingHandle;

    SourceTextPanel(Context context, String source, SourceSelectionView.Listener listener, Runnable close, Runnable toggleKeyboard) {
        super(context);
        setOrientation(VERTICAL);
        setPadding(BlueUi.dp(context, 16), BlueUi.dp(context, 8), BlueUi.dp(context, 16), BlueUi.dp(context, 8));
        setBackground(BlueUi.round(context, BlueUi.CARD, BlueUi.CARD_RADIUS_DP));
        setElevation(BlueUi.dp(context, 10));
        LinearLayout handleArea = BlueUi.column(context);
        handleArea.setGravity(Gravity.CENTER);
        handleArea.setContentDescription("向下拖动收起原文");
        handleArea.setClickable(true);
        View handle = new View(context);
        handle.setBackground(BlueUi.round(context, BlueUi.BORDER, 3));
        handleArea.addView(handle, new LayoutParams(BlueUi.dp(context, 34), BlueUi.dp(context, 4)));
        handleArea.setOnTouchListener((view, event) -> onHandleTouch(view, event, close));
        addView(handleArea, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, BlueUi.dp(context, 28)));
        LinearLayout header = BlueUi.row(context);
        header.addView(BlueUi.text(context, "本地识别原文", 18, BlueUi.DEEP_BLUE, true),
                new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        keyboardButton = new ImageButton(context);
        keyboardButton.setImageResource(R.drawable.ic_keyboard);
        keyboardButton.setPadding(BlueUi.dp(context, 10), BlueUi.dp(context, 10),
                BlueUi.dp(context, 10), BlueUi.dp(context, 10));
        keyboardButton.setBackground(BlueUi.round(context, BlueUi.PALE_BLUE, 16));
        keyboardButton.setOnClickListener(view -> toggleKeyboard.run());
        setKeyboardVisible(false);
        LayoutParams keyboardParams = new LayoutParams(BlueUi.dp(context, 48), BlueUi.dp(context, 44));
        keyboardParams.setMarginEnd(BlueUi.dp(context, 8));
        header.addView(keyboardButton, keyboardParams);
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

    void setKeyboardVisible(boolean visible) {
        keyboardButton.setSelected(visible);
        keyboardButton.setImageTintList(android.content.res.ColorStateList.valueOf(
                visible ? android.graphics.Color.WHITE : BlueUi.DEEP_BLUE));
        keyboardButton.setBackground(BlueUi.round(getContext(), visible ? BlueUi.BLUE : BlueUi.PALE_BLUE, 16));
        String description = visible ? "隐藏键盘" : "显示键盘";
        keyboardButton.setContentDescription(description);
        keyboardButton.setTooltipText(description);
    }

    @SuppressLint("ClickableViewAccessibility") // 抬手时调用 performClick；独立的“收起”按钮提供无障碍操作。
    private boolean onHandleTouch(View handle, MotionEvent event, Runnable close) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                animate().cancel();
                handleDownX = event.getRawX();
                handleDownY = event.getRawY();
                draggingHandle = false;
                trackingHandle = true;
                return true;
            case MotionEvent.ACTION_MOVE:
                if (!trackingHandle) {
                    return true;
                }
                float deltaX = Math.abs(event.getRawX() - handleDownX);
                float deltaY = event.getRawY() - handleDownY;
                if (deltaY > ViewConfiguration.get(getContext()).getScaledTouchSlop() && deltaY > deltaX) {
                    draggingHandle = true;
                    handle.getParent().requestDisallowInterceptTouchEvent(true);
                }
                if (draggingHandle) {
                    setTranslationY(Math.max(0, Math.min(getHeight(), deltaY)));
                }
                return true;
            case MotionEvent.ACTION_UP:
                if (!trackingHandle) {
                    return true;
                }
                trackingHandle = false;
                handle.performClick();
                if (draggingHandle && getTranslationY() >= Math.max(BlueUi.dp(getContext(), MIN_DISMISS_DISTANCE_DP), getHeight() * DISMISS_HEIGHT_FRACTION)) {
                    animate().translationY(getHeight()).setDuration(HANDLE_ANIMATION_DURATION_MILLIS).withEndAction(close).start();
                } else {
                    animate().translationY(0).setDuration(HANDLE_ANIMATION_DURATION_MILLIS).start();
                }
                draggingHandle = false;
                handle.getParent().requestDisallowInterceptTouchEvent(false);
                return true;
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_POINTER_DOWN:
                trackingHandle = false;
                draggingHandle = false;
                animate().translationY(0).setDuration(HANDLE_ANIMATION_DURATION_MILLIS).start();
                handle.getParent().requestDisallowInterceptTouchEvent(false);
                return true;
            default:
                return true;
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        animate().cancel();
        trackingHandle = false;
        super.onDetachedFromWindow();
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
