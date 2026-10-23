package cn.local.jiyibi;

/** 空识别、异常草稿和同次页面信息回退测试，不读写真实账目或图片。 */
public final class RecognitionDraftsTest {
    private static int checks;

    private RecognitionDraftsTest() { }

    public static void main(String[] args) {
        String source = "当前窗口截图；来源应用：com.tencent.mm";
        String readAt = "2026-10-23T10:00:00";
        LedgerEntry empty = ReceiptParser.parse("");
        RecognitionDrafts.prepare(empty, source, readAt, RecognitionDrafts.MISSING_AMOUNT_MESSAGE);
        equal(LedgerEntry.DRAFT, empty.getStatus());
        equal("", empty.getDate());
        equal("", empty.getAmount());
        equal("", empty.getShop());
        equal("", empty.getProduct());
        equal("", empty.getNote());
        contains(source, empty.getSourceText());
        contains(readAt, empty.getSourceText());
        contains(RecognitionDrafts.MISSING_AMOUNT_MESSAGE, empty.getSourceText());

        LedgerEntry partial = ReceiptParser.parse("商户名称：星巴克");
        String partialId = partial.getId();
        RecognitionDrafts.prepare(partial, "选择的图片", readAt, RecognitionDrafts.MISSING_AMOUNT_MESSAGE);
        equal(partialId, partial.getId());
        equal("星巴克", partial.getShop());
        equal("", partial.getAmount());
        equal("", partial.getNote());
        contains("商户名称：星巴克", partial.getSourceText());

        LedgerEntry failure = RecognitionDrafts.prepare(new LedgerEntry(), source, readAt, "本地识别失败");
        equal(LedgerEntry.DRAFT, failure.getStatus());
        equal("", failure.getAmount());
        equal("", failure.getNote());
        contains("识别提示：本地识别失败", failure.getSourceText());
        equal(false, failure.getId().equals(empty.getId()));
        equal(false, failure.getId().equals(partial.getId()));

        LedgerEntry page = ReceiptParser.parse("商户名称：甲店\n商品：拿铁\n支付时间：2026-10-23");
        String pageText = page.getSourceText();
        LedgerEntry image = ReceiptParser.parse("支付成功\n实付金额：28.00");
        String imageId = image.getId();
        LedgerEntry merged = RecognitionDrafts.merge(page, image);
        equal(imageId, merged.getId());
        equal("甲店", merged.getShop());
        equal("拿铁", merged.getProduct());
        equal("2026-10-23", merged.getDate());
        equal("28", merged.getAmount());
        equal(LedgerEntry.DRAFT, merged.getStatus());
        equal("", merged.getNote());
        contains(pageText, merged.getSourceText());
        contains("实付金额：28.00", merged.getSourceText());
        equal(pageText, page.getSourceText());
        equal("", page.getAmount());

        LedgerEntry blankImage = RecognitionDrafts.merge(page, ReceiptParser.parse(""));
        equal(pageText, blankImage.getSourceText());
        equal("甲店", blankImage.getShop());
        equal("拿铁", blankImage.getProduct());
        equal("", blankImage.getAmount());
        equal(false, imageId.equals(blankImage.getId()));

        LedgerEntry preferred = RecognitionDrafts.merge(page,
                ReceiptParser.parse("商户名称：乙店\n商品：咖啡豆\n支付时间：2026-10-22\n实付金额：89.00"));
        equal("乙店", preferred.getShop());
        equal("咖啡豆", preferred.getProduct());
        equal("2026-10-22", preferred.getDate());
        equal("89", preferred.getAmount());
        RecognitionDrafts.prepare(preferred, source, readAt, "");
        equal(false, preferred.getSourceText().contains("识别提示："));
        equal("", preferred.getNote());

        LedgerEntry afterConfirmation = new LedgerEntry();
        afterConfirmation.setStatus(LedgerEntry.CONFIRMED);
        RecognitionDrafts.prepare(afterConfirmation, source, readAt, "本地识别失败");
        equal(LedgerEntry.DRAFT, afterConfirmation.getStatus());
        System.out.println("PASS: " + checks + " recognition draft checks");
    }

    private static void contains(String expected, String actual) {
        checks++;
        if (!actual.contains(expected)) {
            throw new AssertionError("Expected text " + expected + " in " + actual);
        }
    }

    private static void equal(Object expected, Object actual) {
        checks++;
        if (!expected.equals(actual)) {
            throw new AssertionError("Expected " + expected + ", got " + actual);
        }
    }
}
