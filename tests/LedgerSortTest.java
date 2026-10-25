package cn.local.jiyibi;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** 已筛选账目的排序、精确金额比较与互斥排序状态回归测试。 */
public final class LedgerSortTest {
    private static int checks;

    private LedgerSortTest() { }

    public static void main(String[] args) {
        LedgerEntry recent = entry("2026-10-25", "9", "餐饮", "咖啡");
        LedgerEntry previous = entry("2026-09-20", "9.00", "餐饮", "咖啡");
        LedgerEntry transit = entry("2026-10-23", "10", "交通", "车票");
        LedgerEntry large = entry("2026-10-22", "100", "日用", "商品");
        LedgerEntry sameDay = entry("2026-10-25", "9.0", "餐饮", "咖啡");
        LedgerEntry draft = entry("2026-10-26", "无法解析的金额", "餐饮", "咖啡");
        draft.setStatus(LedgerEntry.DRAFT);
        List<LedgerEntry> entries = new ArrayList<>(List.of(large, previous, recent, transit, sameDay, draft));
        List<LedgerEntry> original = new ArrayList<>(entries);
        LedgerFilter defaults = new LedgerFilter("", null, "");
        equal(LedgerFilter.SortOrder.DATE_DESCENDING, defaults.getSortOrder());
        equal(List.of(recent, sameDay, transit, large, previous), defaults.apply(entries).getEntries());

        LedgerFilter dateAscending = defaults.toggleDateSort();
        equal(LedgerFilter.SortOrder.DATE_ASCENDING, dateAscending.getSortOrder());
        equal(List.of(previous, large, transit, recent, sameDay), dateAscending.apply(entries).getEntries());
        equal(defaults.apply(entries).getEntries(), dateAscending.toggleDateSort().apply(entries).getEntries());

        LedgerFilter priceAscending = defaults.togglePriceSort();
        equal(LedgerFilter.SortOrder.PRICE_ASCENDING, priceAscending.getSortOrder());
        equal(List.of(recent, sameDay, previous, transit, large), priceAscending.apply(entries).getEntries());
        LedgerFilter priceDescending = priceAscending.togglePriceSort();
        equal(LedgerFilter.SortOrder.PRICE_DESCENDING, priceDescending.getSortOrder());
        equal(List.of(large, transit, recent, sameDay, previous), priceDescending.apply(entries).getEntries());
        equal(LedgerFilter.SortOrder.DATE_ASCENDING, priceDescending.toggleDateSort().getSortOrder());
        equal(LedgerFilter.SortOrder.PRICE_ASCENDING, dateAscending.togglePriceSort().getSortOrder());
        equal(LedgerFilter.SortOrder.DATE_DESCENDING, defaults.getSortOrder());
        equal(original, entries);

        for (LedgerFilter.SortOrder order : LedgerFilter.SortOrder.values()) {
            LedgerFilter filter = new LedgerFilter("", null, "", order);
            equal(0, new BigDecimal("137").compareTo(filter.apply(entries).getExpense()));
            equal(5, filter.apply(entries).getEntries().size());
            equal(false, filter.apply(entries).getEntries().contains(draft));
            equal(order, LedgerFilter.SortOrder.fromSavedValue(order.name()));
        }

        LedgerFilter combined = priceDescending.withDatePrefix("2026-10").withCategory("餐饮").withKeyword("咖啡");
        equal(LedgerFilter.SortOrder.PRICE_DESCENDING, combined.getSortOrder());
        equal(List.of(recent, sameDay), combined.apply(entries).getEntries());
        equal(0, new BigDecimal("18.00").compareTo(combined.apply(entries).getExpense()));
        LedgerFilter september = combined.withDatePrefix("2026-09");
        equal(LedgerFilter.SortOrder.PRICE_DESCENDING, september.getSortOrder());
        equal(List.of(previous), september.apply(entries).getEntries());
        LedgerFilter changedCategory = combined.withCategory("交通").withKeyword("车票");
        equal(LedgerFilter.SortOrder.PRICE_DESCENDING, changedCategory.getSortOrder());
        equal(List.of(transit), changedCategory.apply(entries).getEntries());
        equal("2026-10", combined.toggleDateSort().getDatePrefix());
        equal("餐饮", combined.toggleDateSort().getCategory());
        equal("咖啡", combined.toggleDateSort().getKeyword());
        equal(List.of(), combined.withKeyword("不存在").apply(entries).getEntries());
        equal(List.of(), priceAscending.apply(List.of()).getEntries());
        equal(List.of(), priceDescending.apply(List.of(draft)).getEntries());

        LedgerEntry fraction = entry("2026-10-25", "0.2", "日用", "商品");
        LedgerEntry lowerFraction = entry("2026-10-25", "0.11", "日用", "商品");
        LedgerEntry maximum = entry("2026-10-25", "999999999999.99", "日用", "商品");
        equal(List.of(lowerFraction, fraction, maximum),
                priceAscending.apply(List.of(maximum, fraction, lowerFraction)).getEntries());
        equal(List.of(maximum, fraction, lowerFraction),
                priceDescending.apply(List.of(lowerFraction, maximum, fraction)).getEntries());
        equal(LedgerFilter.SortOrder.DATE_DESCENDING, LedgerFilter.SortOrder.fromSavedValue(null));
        equal(LedgerFilter.SortOrder.DATE_DESCENDING, LedgerFilter.SortOrder.fromSavedValue("unknown"));
        equal(LedgerFilter.SortOrder.DATE_DESCENDING, LedgerFilter.SortOrder.fromSavedValue(""));
        equal(true, dateAscending.getSortOrder().isDateSort());
        equal(true, dateAscending.getSortOrder().isAscending());
        equal(false, priceDescending.getSortOrder().isDateSort());
        equal(false, priceDescending.getSortOrder().isAscending());
        System.out.println("PASS: " + checks + " ledger sort checks");
    }

    private static LedgerEntry entry(String date, String amount, String category, String product) {
        LedgerEntry entry = new LedgerEntry();
        entry.setDate(date);
        entry.setAmount(amount);
        entry.setCategory(category);
        entry.setProduct(product);
        entry.setStatus(LedgerEntry.CONFIRMED);
        return entry;
    }

    private static void equal(Object expected, Object actual) {
        checks++;
        if (!expected.equals(actual)) {
            throw new AssertionError("Expected " + expected + ", got " + actual);
        }
    }
}
