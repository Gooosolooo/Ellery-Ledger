package cn.local.jiyibi;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.widget.TextView;

/** 普通竖向滑动交给父容器；按住后拖动或横向拖动选文，松手只提交一次。 */
// 本项目使用原生 Activity 和 Theme.Material，不使用 AppCompat 主题或控件体系。
@SuppressLint({"ViewConstructor", "AppCompatCustomView"})
final class SourceSelectionView extends TextView {
    interface Listener {
        void onSelectionStarted();
        void onSelectionFinished(String selected);
    }

    private final SourceSelectionSession selection = new SourceSelectionSession();
    private final Paint highlightPaint = new Paint();
    private final Path highlightPath = new Path();
    private final int touchSlop;
    private final Listener listener;
    private final Runnable longPress = this::startLongPressSelection;
    private float downX;
    private float downY;
    private int anchorOffset;
    private boolean touching;
    private boolean selecting;
    private boolean canSelect;

    SourceSelectionView(Context context, String text, Listener listener) {
        super(context);
        this.listener = listener;
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        highlightPaint.setColor(0x66607D94);
        setText(text);
        setTextSize(16);
        setTextColor(BlueUi.INK);
        setLineSpacing(BlueUi.dp(context, 7), 1f);
        setPadding(BlueUi.dp(context, 4), BlueUi.dp(context, 8), BlueUi.dp(context, 4), BlueUi.dp(context, 16));
        setFocusable(false);
        setClickable(true);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                selection.cancel();
                downX = event.getX();
                downY = event.getY();
                anchorOffset = safeOffset(downX, downY);
                touching = true;
                selecting = false;
                canSelect = true;
                postDelayed(longPress, ViewConfiguration.getLongPressTimeout());
                invalidate();
                return true;
            case MotionEvent.ACTION_MOVE:
                if (!touching) {
                    return true;
                }
                float deltaX = Math.abs(event.getX() - downX);
                float deltaY = Math.abs(event.getY() - downY);
                if (!selecting && (deltaX > touchSlop || deltaY > touchSlop)) {
                    removeCallbacks(longPress);
                    if (canSelect && deltaX > deltaY) {
                        startSelection();
                    } else {
                        canSelect = false;
                    }
                }
                if (selecting) {
                    selection.update(safeOffset(event.getX(), event.getY()));
                    invalidate();
                }
                return true;
            case MotionEvent.ACTION_UP:
                removeCallbacks(longPress);
                touching = false;
                if (selecting) {
                    selecting = false;
                    String selected = selection.finish(getText().toString());
                    getParent().requestDisallowInterceptTouchEvent(false);
                    if (!selected.isBlank()) {
                        listener.onSelectionFinished(selected);
                    }
                }
                performClick();
                return true;
            case MotionEvent.ACTION_POINTER_DOWN:
            case MotionEvent.ACTION_CANCEL:
                cancelGesture();
                return true;
            default:
                return true;
        }
    }

    private void startLongPressSelection() {
        if (!touching || !canSelect || getText().length() == 0) {
            return;
        }
        if (anchorOffset == getText().length()) {
            anchorOffset = Character.offsetByCodePoints(getText(), anchorOffset, -1);
        }
        startSelection();
        selection.update(Character.offsetByCodePoints(getText(), anchorOffset, 1));
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        invalidate();
    }

    private void startSelection() {
        selecting = true;
        selection.begin(anchorOffset, getText().length());
        getParent().requestDisallowInterceptTouchEvent(true);
        listener.onSelectionStarted();
    }

    private int safeOffset(float x, float y) {
        int offset = getOffsetForPosition(x, y);
        if (offset > 0 && offset < getText().length()
                && Character.isLowSurrogate(getText().charAt(offset))
                && Character.isHighSurrogate(getText().charAt(offset - 1))) {
            return offset - 1;
        }
        return offset;
    }

    String selectedText() {
        return selection.selectedText(getText().toString());
    }

    private void cancelGesture() {
        removeCallbacks(longPress);
        touching = false;
        selecting = false;
        canSelect = false;
        selection.cancel();
        if (getParent() != null) {
            getParent().requestDisallowInterceptTouchEvent(false);
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (getLayout() != null && selection.start() >= 0 && selection.end() > selection.start()) {
            highlightPath.reset();
            getLayout().getSelectionPath(selection.start(), selection.end(), highlightPath);
            int checkpoint = canvas.save();
            canvas.translate(getTotalPaddingLeft(), getTotalPaddingTop());
            canvas.drawPath(highlightPath, highlightPaint);
            canvas.restoreToCount(checkpoint);
        }
        super.onDraw(canvas);
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    @Override
    protected void onDetachedFromWindow() {
        cancelGesture();
        super.onDetachedFromWindow();
    }
}
