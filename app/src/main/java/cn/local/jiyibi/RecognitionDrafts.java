package cn.local.jiyibi;

/** 单次识别结果的草稿整理；不借用历史账目，不将识别提示写入用户评价。 */
final class RecognitionDrafts {
    static final String MISSING_AMOUNT_MESSAGE = "未识别到可靠账单金额，请手工补充核对。";

    private RecognitionDrafts() { }

    static LedgerEntry prepare(LedgerEntry entry, String source, String readAt, String warning) {
        String original = entry.getSourceText();
        StringBuilder details = new StringBuilder("识别来源：").append(source)
                .append("\n读取时间：").append(readAt);
        if (!warning.isBlank()) {
            details.append("\n识别提示：").append(warning);
        }
        details.append("\n\n").append(original);
        entry.setSourceText(details.toString());
        entry.setStatus(LedgerEntry.DRAFT);
        return entry;
    }

    /** 截图识别遗漏字段或为空时，保留同一次尝试已读取的页面信息。 */
    static LedgerEntry merge(LedgerEntry page, LedgerEntry screenshot) {
        if (screenshot.getDate().isBlank()) {
            screenshot.setDate(page.getDate());
        }
        if (screenshot.getShop().isBlank()) {
            screenshot.setShop(page.getShop());
        }
        if (screenshot.getProduct().isBlank()) {
            screenshot.setProduct(page.getProduct());
        }
        if (screenshot.getAmount().isBlank()) {
            screenshot.setAmount(page.getAmount());
        }
        String pageText = page.getSourceText();
        String screenshotText = screenshot.getSourceText();
        if (screenshotText.isBlank()) {
            screenshot.setSourceText(pageText);
        } else if (!pageText.isBlank() && !pageText.equals(screenshotText)) {
            screenshot.setSourceText("页面文字：\n" + pageText + "\n\n截图文字：\n" + screenshotText);
        }
        screenshot.setStatus(LedgerEntry.DRAFT);
        return screenshot;
    }
}
