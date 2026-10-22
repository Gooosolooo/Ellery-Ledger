package cn.local.jiyibi;

/** 一次选文手势的范围与提交状态；提交后继续收到事件也不会重复取出文字。 */
final class SourceSelectionSession {
    private int anchor = -1;
    private int extent = -1;
    private int textLength;
    private boolean active;

    void begin(int offset, int length) {
        textLength = Math.max(0, length);
        anchor = Math.max(0, Math.min(offset, textLength));
        extent = anchor;
        active = true;
    }

    void update(int offset) {
        if (active) {
            extent = Math.max(0, Math.min(offset, textLength));
        }
    }

    int start() {
        return Math.min(anchor, extent);
    }

    int end() {
        return Math.max(anchor, extent);
    }

    String finish(String text) {
        if (!active) {
            return "";
        }
        active = false;
        if (text.length() != textLength || start() == end()) {
            return "";
        }
        return selectedText(text);
    }

    String selectedText(String text) {
        if (start() < 0 || end() > text.length()) {
            return "";
        }
        return text.substring(start(), end());
    }

    void cancel() {
        active = false;
        anchor = -1;
        extent = -1;
    }
}
