package cn.local.jiyibi;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** 日期、组合查询与草稿隔离的业务回归测试，不依赖 Android 或真实账目。 */
public final class LedgerFilterTest {
    private static int checks;

    private LedgerFilterTest() { }

    public static void main(String[] args) {
        LedgerEntry coffee = entry("2026-10-23", "甲咖啡店", "拿铁", "餐饮", "28.10", "同事聚会", false);
        LedgerEntry beans = entry("2026-10-22", "乙店", "Coffee 豆", "日用", "89.20", "家用", false);
        LedgerEntry subway = entry("2026-10-23", "地铁", "通勤", "交通", "8.00", "公司往返", false);
        LedgerEntry previous = entry("2026-09-30", "甲咖啡店", "美式", "餐饮", "22.00", "", false);
        LedgerEntry draft = entry("2026-10-23", "甲咖啡店", "待核对", "餐饮", "不是金额", "草稿评价", true);
        LedgerEntry undated = entry("", "商店", "未分类商品", "", "", "", true);
        LedgerEntry custom = entry("2026-10-01", "书店", "书", "自定义类别", "0.10", "100%_折扣'", false);
        List<LedgerEntry> source = new ArrayList<>(Arrays.asList(previous, beans, coffee, subway, draft, undated, custom));

        LedgerFilter.Result all = select(source, "", null, "");
        equal(7, all.getEntries().size());
        equal(5, all.getConfirmedCount());
        equal(2, all.getDraftCount());
        amount("147.40", all);
        equal(coffee, all.getEntries().get(0));
        equal(subway, all.getEntries().get(1));
        equal(draft, all.getEntries().get(2));
        equal(undated, all.getEntries().get(6));
        equal(previous, source.get(0));

        LedgerFilter.Result month = select(source, "2026-10", null, "");
        equal(5, month.getEntries().size());
        amount("125.40", month);
        LedgerFilter.Result day = select(source, "2026-10-23", null, "");
        equal(3, day.getEntries().size());
        amount("36.10", day);
        equal(1, day.getDraftCount());
        LedgerFilter.Result combined = select(source, "2026-10-23", "餐饮", "甲咖啡");
        equal(2, combined.getEntries().size());
        equal(1, combined.getConfirmedCount());
        amount("28.10", combined);
        equal(1, select(source, "", null, "拿铁").getEntries().size());
        equal(1, select(source, "", null, "  COFFEE  ").getEntries().size());
        equal(1, select(source, "", null, "同事聚").getEntries().size());
        equal(7, select(source, "", null, "  ").getEntries().size());
        equal(1, select(source, "", "", "").getEntries().size());
        equal(1, select(source, "", "自定义类别", "").getEntries().size());
        equal(1, select(source, "", null, "100%_折扣'").getEntries().size());
        LedgerFilter.Result draftOnly = select(source, "", null, "草稿评价");
        amount("0", draftOnly);
        equal(0, draftOnly.getConfirmedCount());
        equal(1, draftOnly.getDraftCount());
        LedgerFilter.Result empty = select(source, "2026-11", null, "");
        equal(0, empty.getEntries().size());
        amount("0", empty);
        equal(0, select(source, "2026-10", "交通", "咖啡").getEntries().size());
        equal(0, select(List.of(), "", null, "").getEntries().size());

        LedgerEntry leap = entry("2024-02-29", "店", "商品", "日用", "0.20", "", false);
        LedgerEntry invalidDay = entry("2026-02-29", "店", "商品", "日用", "", "", true);
        LedgerEntry malformed = entry("2026-10-230", "店", "商品", "日用", "", "", true);
        LedgerEntry incomplete = entry("2026-10", "店", "商品", "日用", "", "", true);
        List<LedgerEntry> boundary = List.of(leap, invalidDay, malformed, incomplete);
        equal(1, select(boundary, "2024-02", null, "").getEntries().size());
        equal(1, select(boundary, "2024-02-29", null, "").getEntries().size());
        equal(0, select(boundary, "2026-02", null, "").getEntries().size());
        equal(0, select(boundary, "2026-10-23", null, "").getEntries().size());
        equal(0, select(boundary, "2026-10", null, "").getEntries().size());
        amount("0.30", select(List.of(leap, custom), "", null, ""));
        equal(7, select(source, "", null, "").getEntries().size());
        System.out.println("PASS: " + checks + " ledger filter checks");
    }

    private static LedgerFilter.Result select(List<LedgerEntry> entries, String date, String category, String keyword) {
        return new LedgerFilter(date, category, keyword).apply(entries);
    }

    private static LedgerEntry entry(String date, String shop, String product, String category,
            String amount, String note, boolean draft) {
        LedgerEntry entry = new LedgerEntry();
        entry.setDate(date);
        entry.setShop(shop);
        entry.setProduct(product);
        entry.setCategory(category);
        entry.setAmount(amount);
        entry.setNote(note);
        entry.setStatus(draft ? LedgerEntry.DRAFT : LedgerEntry.CONFIRMED);
        return entry;
    }

    private static void amount(String expected, LedgerFilter.Result actual) {
        checks++;
        if (new BigDecimal(expected).compareTo(actual.getExpense()) != 0) {
            throw new AssertionError("Expected expense " + expected + ", got " + actual.getExpense());
        }
    }

    private static void equal(Object expected, Object actual) {
        checks++;
        if (!expected.equals(actual)) {
            throw new AssertionError("Expected " + expected + ", got " + actual);
        }
    }
}
