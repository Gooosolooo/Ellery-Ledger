package cn.local.jiyibi;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

/** 将 ⇋ 顺时针旋转 90 度，分别绘制右侧上箭头和左侧下箭头的颜色。 */
final class SortArrowsDrawable extends Drawable {
    private static final String SYMBOL = "⇋";
    private static final int INACTIVE_COLOR = 0xFFA6AFB7;
    private static final float ROTATION_DEGREES = 90f;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Rect symbolBounds = new Rect();
    private final int width;
    private final int height;
    private final int upColor;
    private final int downColor;
    private int alpha = 255;

    SortArrowsDrawable(Context context, boolean ascendingActive, boolean descendingActive) {
        width = BlueUi.dp(context, 24);
        height = BlueUi.dp(context, 28);
        paint.setTextSize(BlueUi.dp(context, 28));
        paint.getTextBounds(SYMBOL, 0, SYMBOL.length(), symbolBounds);
        upColor = ascendingActive ? BlueUi.DEEP_BLUE : INACTIVE_COLOR;
        downColor = descendingActive ? BlueUi.DEEP_BLUE : INACTIVE_COLOR;
    }

    @Override
    public void draw(Canvas canvas) {
        Rect bounds = getBounds();
        int saved = canvas.save();
        canvas.clipRect(bounds);
        canvas.translate(bounds.exactCenterX(), bounds.exactCenterY());
        canvas.rotate(ROTATION_DEGREES);
        // 原符号上半部旋转后位于右侧，指向上；下半部位于左侧，指向下。
        drawHalf(canvas, true, upColor);
        drawHalf(canvas, false, downColor);
        canvas.restoreToCount(saved);
    }

    private void drawHalf(Canvas canvas, boolean upperHalf, int color) {
        float extent = Math.max(width, height);
        int saved = canvas.save();
        canvas.clipRect(-extent, upperHalf ? -extent : 0, extent, upperHalf ? 0 : extent);
        paint.setColor(color);
        paint.setAlpha(alpha);
        canvas.drawText(SYMBOL, -symbolBounds.exactCenterX(), -symbolBounds.exactCenterY(), paint);
        canvas.restoreToCount(saved);
    }

    @Override
    public int getIntrinsicWidth() {
        return width;
    }

    @Override
    public int getIntrinsicHeight() {
        return height;
    }

    @Override
    public void setAlpha(int alpha) {
        this.alpha = alpha;
        invalidateSelf();
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        paint.setColorFilter(colorFilter);
        invalidateSelf();
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
