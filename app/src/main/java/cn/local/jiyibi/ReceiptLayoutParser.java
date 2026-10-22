package cn.local.jiyibi;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** 按坐标重建左右分栏；对钱包账单的大字金额和上方商户标题提供布局回退。 */
public final class ReceiptLayoutParser {
    private ReceiptLayoutParser() { }

    public static LedgerEntry parse(List<ReceiptLine> input, boolean amountOnly) {
        List<ReceiptLine> lines = new ArrayList<>(input);
        lines.sort(Comparator.comparingInt(ReceiptLine::getTop).thenComparingInt(ReceiptLine::getLeft));
        List<List<ReceiptLine>> rows = new ArrayList<>();
        for (ReceiptLine line : lines) {
            List<ReceiptLine> matching = null;
            for (int index = rows.size() - 1; index >= 0; index--) {
                ReceiptLine anchor = rows.get(index).get(0);
                int overlap = Math.min(anchor.getBottom(), line.getBottom()) - Math.max(anchor.getTop(), line.getTop());
                if (overlap >= Math.min(anchor.height(), line.height()) * 0.5
                        && Math.abs(anchor.centerY() - line.centerY()) <= Math.max(anchor.height(), line.height()) * 0.6) {
                    matching = rows.get(index); break;
                }
            }
            if (matching == null) { matching = new ArrayList<>(); rows.add(matching); }
            matching.add(line);
        }
        StringBuilder reconstructed = new StringBuilder();
        for (List<ReceiptLine> row : rows) {
            row.sort(Comparator.comparingInt(ReceiptLine::getLeft));
            for (ReceiptLine cell : row) {
                if (reconstructed.length() > 0 && reconstructed.charAt(reconstructed.length() - 1) != '\n') { reconstructed.append(' '); }
                reconstructed.append(cell.getText());
            }
            reconstructed.append('\n');
        }
        String text = reconstructed.toString();
        LedgerEntry entry = ReceiptParser.parse(text, amountOnly);
        List<ReceiptLine> amounts = new ArrayList<>();
        for (ReceiptLine line : lines) {
            if (!ReceiptParser.money(line.getText(), true).isEmpty()) { amounts.add(line); }
        }
        amounts.sort(Comparator.comparingInt(ReceiptLine::height).reversed());
        ReceiptLine prominent = amounts.isEmpty() ? null : amounts.get(0);
        if (entry.getAmount().isEmpty() && prominent != null && !ReceiptParser.hasAmountLabel(text)
                && !ReceiptParser.isUnpaidOrRefund(text)
                && (amounts.size() == 1 || prominent.height() >= amounts.get(1).height() * 1.5)) {
            entry.setAmount(ReceiptParser.money(prominent.getText(), true));
        }
        if (!amountOnly && prominent != null && !entry.getAmount().isEmpty()
                && entry.getAmount().equals(ReceiptParser.money(prominent.getText(), true))) {
            for (int index = lines.size() - 1; index >= 0; index--) {
                ReceiptLine header = lines.get(index);
                String candidate = header.getText().trim();
                if (header.getBottom() >= prominent.getTop() || prominent.getTop() - header.getBottom() > prominent.height() * 4) { continue; }
                if (candidate.length() >= 2 && candidate.length() <= 50 && candidate.matches(".*[\\p{IsHan}A-Za-z].*")
                        && !ReceiptParser.isField(candidate)
                        && !candidate.matches(".*(?:账单|详情|支付|付款|订单|交易|成功|退款|返回|余额|全部|扫一扫|支付宝|微信).*")) {
                    entry.setShop(candidate); break;
                }
            }
        }
        return entry;
    }
}
