package cn.local.jiyibi;

/** OCR 文字行及其图像坐标，独立于 Android，便于验证分栏布局。 */
public final class ReceiptLine {
    private final String text;
    private final int left;
    private final int top;
    private final int right;
    private final int bottom;

    public ReceiptLine(String text, int left, int top, int right, int bottom) {
        this.text = text; this.left = left; this.top = top; this.right = right; this.bottom = bottom;
    }
    public String getText() { return text; }
    public int getLeft() { return left; }
    public int getTop() { return top; }
    public int getRight() { return right; }
    public int getBottom() { return bottom; }
    public int height() { return Math.max(1, bottom - top); }
    public int centerY() { return (top + bottom) / 2; }
}
