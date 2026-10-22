package cn.local.jiyibi;

/** 手动读取的来源限制，不保留上一笔识别结果。 */
public final class CaptureSourcePolicy {
    private CaptureSourcePolicy() { }

    public static boolean allows(String packageName, String ownPackage) {
        return packageName != null && !packageName.isBlank()
                && !packageName.equals(ownPackage) && !packageName.startsWith(ownPackage + ".")
                && !packageName.equals("com.android.systemui")
                && !packageName.equals("com.android.settings")
                && !packageName.contains("launcher") && !packageName.contains("permissioncontroller");
    }

    public static boolean hasReceiptAmount(LedgerEntry entry) {
        String text = entry.getSourceText();
        return !entry.getAmount().isBlank() && text != null
                && (text.contains("支付") || text.contains("付款") || text.contains("实付")
                || text.contains("交易") || text.contains("订单") || text.contains("商品")
                || text.contains("商户") || text.contains("收款"));
    }
}
