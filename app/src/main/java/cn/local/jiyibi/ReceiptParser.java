package cn.local.jiyibi;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 支持字段别名、无冒号标签和轻微 OCR 标签误差；金额歧义留待人工核对。 */
public final class ReceiptParser {
    private static final String[] AMOUNT_KEYS = {"合计实付", "实付总额", "实付金额", "实际付款", "实际支付", "付款金额", "支付金额", "实付款", "已付款", "实付"};
    private static final String[] SHOP_KEYS = {"店铺名称", "商家名称", "商户名称", "商户简称", "商户全称", "商家全称", "对方名称", "收款方", "店铺", "商家", "品牌"};
    private static final String[] PRODUCT_KEYS = {"商品名称", "商品说明", "交易说明", "消费说明", "产品名称", "订单商品", "商品"};
    private static final String[] PAYMENT_DATE_KEYS = {"支付时间", "付款时间", "交易时间", "付款时问", "支付时问"};
    private static final String[] OTHER_KEYS = {"当前状态", "交易状态", "支付方式", "付款方式", "收单机构", "交易单号", "订单编号", "订单号", "商户单号", "商家订单号", "创建时间", "下单时间", "成交时间", "订单信息", "商品总价", "商品金额", "运费", "优惠", "订单备注", "备注", "账单分类"};
    private static final Pattern DATE = Pattern.compile("(?<!\\d)(20\\d{2})\\s*[年/.-]\\s*(\\d{1,2})\\s*[月/.-]\\s*(\\d{1,2})\\s*日?(?!\\d)");
    private static final Pattern MONEY = Pattern.compile("^[-]?\\s*[¥￥]?\\s*(\\d[\\d,]{0,12})(?:\\s*[.．]\\s*(\\d{1,2}))?\\s*元?$");
    private static final Pattern STANDALONE_MONEY = Pattern.compile("^[-]?\\s*(?:[¥￥]\\s*\\d[\\d,]{0,12}(?:\\s*[.．]\\s*\\d{1,2})?|\\d[\\d,]{0,12}\\s*[.．]\\s*\\d{2})\\s*元?$");
    // 中文 OCR 可能将独立金额前的负号读成“一”；只纠正完整的两位小数金额行。
    private static final Pattern OCR_MINUS_AMOUNT = Pattern.compile("^一\\s*(?=[¥￥]?\\s*\\d[\\d,]{0,12}\\s*\\.\\s*\\d{2}\\s*元?$)");
    private static final Pattern STORE = Pattern.compile(".*(?:旗舰店|专卖店|专营店|官方店|企业店|自营店|食品店|零食店|家居店|数码店|百货店)$");

    private ReceiptParser() { }

    public static LedgerEntry parse(String text) {
        String normalized = normalize(text);
        List<String> lines = new ArrayList<>();
        for (String line : normalized.split("\\n")) {
            if (!line.trim().isEmpty()) { lines.add(line.trim()); }
        }
        LedgerEntry entry = new LedgerEntry();
        entry.setSourceText(text);
        Set<String> amounts = new LinkedHashSet<>();
        for (String value : values(lines, AMOUNT_KEYS)) {
            String amount = money(value, false);
            if (!amount.isEmpty()) { amounts.add(amount); }
        }
        if (amounts.isEmpty() && !hasAmountLabel(normalized)) {
            for (String line : lines) {
                String amount = money(line, true);
                if (!amount.isEmpty()) { amounts.add(amount); }
            }
        }
        if (amounts.size() == 1 && !isUnpaidOrRefund(normalized)) {
            entry.setAmount(amounts.iterator().next());
        }
        Set<String> paymentDates = dates(String.join("\n", values(lines, PAYMENT_DATE_KEYS)));
        Set<String> candidates = paymentDates.isEmpty() ? dates(normalized) : paymentDates;
        if (candidates.size() == 1) { entry.setDate(candidates.iterator().next()); }
        Set<String> shops = new LinkedHashSet<>(values(lines, SHOP_KEYS));
        if (shops.size() == 1) { entry.setShop(shops.iterator().next()); }
        List<String> products = values(lines, PRODUCT_KEYS);
        if (!products.isEmpty()) { entry.setProduct(String.join("；", new LinkedHashSet<>(products))); }
        extractStoreCard(lines, entry);
        return entry;
    }

    static String normalize(String text) {
        return text.replace('\u00a0', ' ').replace("\r", "").replace('−', '-').replace('–', '-')
                .replace('—', '-').replace('﹣', '-').replace('－', '-').replace('．', '.');
    }

    static boolean isUnpaidOrRefund(String text) {
        return text.contains("退款成功") || text.contains("交易关闭") || text.contains("等待付款")
                || text.contains("等待买家付款") || text.contains("待付款") || text.contains("退款详情");
    }

    static boolean hasAmountLabel(String text) {
        for (String line : text.split("\\n")) {
            for (String key : AMOUNT_KEYS) {
                if (splitField(line, key) != null) { return true; }
            }
        }
        return false;
    }

    static String money(String value, boolean standalone) {
        String clean = OCR_MINUS_AMOUNT.matcher(normalize(value).trim()).replaceFirst("-");
        if (standalone && !STANDALONE_MONEY.matcher(clean).matches()) { return ""; }
        Matcher matcher = MONEY.matcher(clean);
        if (!matcher.matches()) { return ""; }
        try {
            String digits = matcher.group(1).replace(",", "");
            String decimal = matcher.group(2);
            BigDecimal amount = new BigDecimal(digits + (decimal == null ? "" : "." + decimal));
            return amount.signum() > 0 && amount.precision() <= 14
                    ? amount.stripTrailingZeros().toPlainString() : "";
        } catch (NumberFormatException exception) { return ""; }
    }

    private static Set<String> dates(String text) {
        Set<String> result = new LinkedHashSet<>();
        Matcher matcher = DATE.matcher(text);
        while (matcher.find()) {
            try {
                result.add(LocalDate.of(Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)),
                        Integer.parseInt(matcher.group(3))).toString());
            } catch (RuntimeException exception) { /* 无效日期留空，不猜测日期。 */ }
        }
        return result;
    }

    private static List<String> values(List<String> lines, String[] keys) {
        List<String> result = new ArrayList<>();
        for (int index = 0; index < lines.size(); index++) {
            for (String key : keys) {
                String value = splitField(lines.get(index), key);
                if (value == null) { continue; }
                if (value.isEmpty() && index + 1 < lines.size() && !isField(lines.get(index + 1))) {
                    value = lines.get(index + 1);
                }
                if (!value.isEmpty()) { result.add(value); }
                break;
            }
        }
        return result;
    }

    private static String splitField(String line, String key) {
        String compact = line.trim();
        String spacedKey = String.join("\\s*", key.split(""));
        Matcher matcher = Pattern.compile("^" + spacedKey + "(?:\\s*[（(][^）)]{0,20}[）)])?(?:\\s*[:：]\\s*|\\s+|(?=[¥￥0-9-])|$)(.*)$").matcher(compact);
        if (matcher.matches()) { return matcher.group(1).trim(); }
        // 只对四字以上的字段标签容许一次编辑误差，不改写金额或商品名称。
        String[] tokens = compact.split("[\\s:：]+", 2);
        if (key.length() >= 4 && tokens[0].length() >= 4 && tokens[0].substring(0, 2).equals(key.substring(0, 2)) && editDistance(tokens[0], key) == 1) {
            return tokens.length == 1 ? "" : tokens[1].trim();
        }
        return null;
    }

    private static int editDistance(String left, String right) {
        int[] previous = new int[right.length() + 1];
        for (int index = 0; index < previous.length; index++) { previous[index] = index; }
        for (int row = 1; row <= left.length(); row++) {
            int[] current = new int[right.length() + 1];
            current[0] = row;
            for (int column = 1; column <= right.length(); column++) {
                current[column] = Math.min(Math.min(current[column - 1] + 1, previous[column] + 1),
                        previous[column - 1] + (left.charAt(row - 1) == right.charAt(column - 1) ? 0 : 1));
            }
            previous = current;
        }
        return previous[right.length()];
    }

    static boolean isField(String line) {
        for (String[] group : Arrays.asList(AMOUNT_KEYS, SHOP_KEYS, PRODUCT_KEYS, PAYMENT_DATE_KEYS, OTHER_KEYS)) {
            for (String key : group) { if (splitField(line, key) != null) { return true; } }
        }
        return false;
    }

    private static void extractStoreCard(List<String> lines, LedgerEntry entry) {
        List<Integer> storeIndices = new ArrayList<>();
        for (int index = 0; index < lines.size(); index++) {
            String store = lines.get(index).replaceAll("[ >›》]+$", "").trim();
            if (STORE.matcher(store).matches() && !isField(store)) { storeIndices.add(index); }
        }
        if (storeIndices.size() != 1) { return; }
        int storeIndex = storeIndices.get(0);
        if (entry.getShop().isEmpty()) { entry.setShop(lines.get(storeIndex).replaceAll("[ >›》]+$", "").trim()); }
        if (!entry.getProduct().isEmpty()) { return; }
        List<String> productLines = new ArrayList<>();
        for (int index = storeIndex + 1; index < lines.size() && index <= storeIndex + 7; index++) {
            String line = lines.get(index);
            if (isField(line) || line.matches(".*(?:联系卖家|联系商家|订单信息|商品总价|查看物流|申请售后|退款|运费|优惠|保险|交易成功).*")) { break; }
            if (!money(line, true).isEmpty() || line.matches("[x×X]\\s*\\d+")) { continue; }
            String title = line.replaceAll("\\s+[¥￥]\\s*\\d[\\d,.]*(?:\\s*[x×X]\\s*\\d+)?$", "")
                    .replaceAll("\\s+[x×X]\\s*\\d+$", "").trim();
            if (title.length() >= 4 && title.matches(".*[\\p{IsHan}A-Za-z].*")) { productLines.add(title); }
        }
        if (!productLines.isEmpty()) { entry.setProduct(String.join(" ", productLines)); }
    }
}
