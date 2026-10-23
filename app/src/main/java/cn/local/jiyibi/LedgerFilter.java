package cn.local.jiyibi;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** 全部账目的组合筛选；仅已确认记录可进入列表、查询结果与支出统计。 */
final class LedgerFilter {
    private final String datePrefix;
    private final String category;
    private final String keyword;

    /** 类别为 null 表示全部，空字符串表示尚未分类。 */
    LedgerFilter(String datePrefix, String category, String keyword) {
        if (!datePrefix.isEmpty()) {
            if (datePrefix.length() == 7) {
                YearMonth.parse(datePrefix);
            } else {
                LocalDate.parse(datePrefix);
            }
        }
        this.datePrefix = datePrefix;
        this.category = category;
        this.keyword = keyword;
    }

    String getDatePrefix() {
        return datePrefix;
    }

    String getCategory() {
        return category;
    }

    String getKeyword() {
        return keyword;
    }

    Result apply(List<LedgerEntry> entries) {
        List<LedgerEntry> matches = new ArrayList<>();
        BigDecimal expense = BigDecimal.ZERO;
        String search = keyword.trim().toLowerCase(Locale.ROOT);
        for (LedgerEntry entry : entries) {
            if (!LedgerEntry.CONFIRMED.equals(entry.getStatus())) {
                continue;
            }
            if (!matchesDate(entry.getDate())) {
                continue;
            }
            if (category != null && !category.equals(entry.getCategory())) {
                continue;
            }
            if (!search.isEmpty() && !contains(entry.getShop(), search)
                    && !contains(entry.getProduct(), search) && !contains(entry.getNote(), search)) {
                continue;
            }
            matches.add(entry);
            expense = expense.add(new BigDecimal(entry.getAmount()));
        }
        // 稳定排序保留同日记录的原顺序。
        matches.sort(Comparator.comparing(LedgerEntry::getDate).reversed());
        return new Result(matches, expense);
    }

    private static boolean contains(String text, String keyword) {
        return text.toLowerCase(Locale.ROOT).contains(keyword);
    }

    private boolean matchesDate(String date) {
        if (datePrefix.isEmpty()) {
            return true;
        }
        try {
            LocalDate.parse(date);
            return datePrefix.length() == 7 ? date.startsWith(datePrefix + "-") : datePrefix.equals(date);
        } catch (DateTimeParseException exception) {
            return false;
        }
    }

    static final class Result {
        private final List<LedgerEntry> entries;
        private final BigDecimal expense;

        private Result(List<LedgerEntry> entries, BigDecimal expense) {
            this.entries = List.copyOf(entries);
            this.expense = expense;
        }

        List<LedgerEntry> getEntries() {
            return entries;
        }

        BigDecimal getExpense() {
            return expense;
        }

    }
}
