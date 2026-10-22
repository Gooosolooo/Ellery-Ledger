package cn.local.jiyibi;

/** 选文范围、反向拖选、取消与一次提交的回归检查。 */
public final class SourceSelectionSessionTest {
    private static int checks;

    public static void main(String[] args) {
        String text = "品牌：甲店\n商品：牛奶";
        SourceSelectionSession selection = new SourceSelectionSession();
        selection.begin(3, text.length());
        selection.update(4);
        selection.update(5);
        equal("甲店", selection.finish(text));
        equal("", selection.finish(text));
        // 结束后的迟到移动和抬手事件，不能导致第二次提交。
        selection.update(8);
        equal("", selection.finish(text));
        equal("甲店", selection.selectedText(text));
        selection.begin(5, text.length());
        selection.update(3);
        equal("甲店", selection.finish(text));
        // 纵向滚动和被父容器取消的手势不会写入。
        selection.begin(3, text.length());
        selection.update(5);
        selection.cancel();
        equal("", selection.finish(text));
        equal("", selection.selectedText(text));
        selection.begin(2, text.length());
        equal("", selection.finish(text));
        selection.begin(3, text.length());
        selection.update(5);
        equal("", selection.finish("原文发生变化"));
        selection.begin(0, 0);
        equal("", selection.finish(""));
        selection.begin(-1, text.length());
        selection.update(text.length() + 10);
        equal(text, selection.finish(text));
        selection.begin(3, text.length());
        selection.update(5);
        equal("甲店", selection.finish(text));
        selection.begin(9, text.length());
        selection.update(text.length());
        equal("牛奶", selection.finish(text));
        String emoji = "店🍵铺";
        selection.begin(1, emoji.length());
        selection.update(3);
        equal("🍵", selection.finish(emoji));
        System.out.println("PASS: " + checks + " source selection checks");
    }

    private static void equal(String expected, String actual) {
        checks++;
        if (!expected.equals(actual)) {
            throw new AssertionError("expected [" + expected + "] but got [" + actual + "]");
        }
    }
}
