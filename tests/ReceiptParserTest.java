package cn.local.jiyibi;

/** 不依赖 Android 的业务回归测试；使用 JDK 17 直接执行。 */
public final class ReceiptParserTest {
    private static int checks;

    public static void main(String[] args) {
        LedgerEntry receipt = ReceiptParser.parse("支付成功\n商户名称：瑞幸咖啡\n商品名称：生椰拿铁\n实付金额：￥18.90\n支付时间 2026年10月22日 12:01\n评价：很好喝");
        equal("瑞幸咖啡 生椰拿铁", receipt.item());
        equal("18.9", receipt.getAmount());
        equal("2026-10-22", receipt.getDate());
        equal("", receipt.getNote());
        equal("", receipt.getCategory());
        equal(LedgerEntry.DRAFT, receipt.getStatus());
        equal("", ReceiptParser.parse("订单金额：100\n优惠：20\n订单号：202610220001").getAmount());
        equal("80", ReceiptParser.parse("订单金额：100\n优惠：20\n实付款\n￥80.00").getAmount());
        equal("", ReceiptParser.parse("实付金额：25\n付款金额：35").getAmount());
        equal("25", ReceiptParser.parse("实付金额：25.00\n付款金额：25").getAmount());
        equal("", ReceiptParser.parse("2026-02-30").getDate());
        equal("", ReceiptParser.parse("下单 2026-10-21\n支付 2026-10-22").getDate());
        equal("", ReceiptParser.parse("商户名称：甲店\n商户名称：乙店").getShop());
        equal("商品甲；商品乙", ReceiptParser.parse("商品：商品甲\n商品：商品乙").getProduct());
        equal("", ReceiptParser.parse("实付款：12.345").getAmount());
        equal("1234.56", ReceiptParser.parse("实际支付：¥1,234.56").getAmount());
        equal("18.9", ReceiptParser.parse("支付成功\n-18.90\n微信支付").getAmount());
        equal("28", ReceiptParser.parse("支付成功\n￥28\n微信支付").getAmount());
        equal("", ReceiptParser.parse("原价\n100.00\n优惠\n20.00").getAmount());
        equal("", ReceiptParser.parse("支付成功\n+18.90").getAmount());
        LedgerEntry complete = ReceiptParser.parse("商户名称：甲店\n商品名称：甲商品\n实付金额：18.90\n2026-10-22");
        equal("18.9", complete.getAmount());
        equal("甲店", complete.getShop());
        equal("甲商品", complete.getProduct());
        equal("2026-10-22", complete.getDate());
        equal("", complete.getNote());
        equal(LedgerEntry.DRAFT, complete.getStatus());
        receipt.setCategory("餐饮");
        equal("", receipt.validationError());
        receipt.setAmount("0");
        nonempty(receipt.validationError());
        receipt.setAmount("-1");
        nonempty(receipt.validationError());
        receipt.setAmount("18.999");
        nonempty(receipt.validationError());
        receipt.setAmount("18.9");
        receipt.setDate("2026-02-30");
        nonempty(receipt.validationError());
        verifyCommonLayouts();
        equal("31.71", ReceiptParser.parse("支付成功\n一31.71\n商户筒称 淘宝特价版平台商户").getAmount());
        equal("淘宝特价版平台商户", ReceiptParser.parse("商户筒称 淘宝特价版平台商户").getShop());
        equal("58.2", ReceiptParser.parse("支付成功\n一58.20").getAmount());
        equal("", ReceiptParser.parse("商品 一瓶31.71元").getAmount());
        equal("", ReceiptParser.parse("支付成功\n一31.71件").getAmount());
        equal("", ReceiptParser.parse("支付成功\n一31.71\n一58.20").getAmount());
        equal("", ReceiptParser.parse("退款成功\n一31.71").getAmount());
        equal("31.71", ReceiptParser.parse("支付成功\n一31.71").getAmount());
        verifyFreshPageResults();
        System.out.println("PASS: " + checks + " business checks");
    }


    private static void verifyFreshPageResults() {
        equal("false", String.valueOf(CaptureSourcePolicy.allows("cn.local.jiyibi", "cn.local.jiyibi")));
        equal("false", String.valueOf(CaptureSourcePolicy.allows("com.android.systemui", "cn.local.jiyibi")));
        equal("false", String.valueOf(CaptureSourcePolicy.allows("", "cn.local.jiyibi")));
        equal("false", String.valueOf(CaptureSourcePolicy.allows("com.sec.android.app.launcher", "cn.local.jiyibi")));
        equal("true", String.valueOf(CaptureSourcePolicy.allows("com.tencent.mm", "cn.local.jiyibi")));
        equal("true", String.valueOf(CaptureSourcePolicy.allows("com.eg.android.AlipayGphone", "cn.local.jiyibi")));
        LedgerEntry previous = null;
        for (String amount : new String[]{"192.5", "18.9", "52.3", "99"}) {
            LedgerEntry current = ReceiptLayoutParser.parse(java.util.List.of(
                    new ReceiptLine("支付成功", 0, 0, 300, 40),
                    new ReceiptLine("-" + new java.math.BigDecimal(amount).setScale(2).toPlainString(), 40, 100, 300, 180)));
            equal(amount, current.getAmount());
            equal("true", String.valueOf(CaptureSourcePolicy.hasReceiptAmount(current)));
            if (previous != null) {
                equal("false", String.valueOf(previous.getId().equals(current.getId())));
            }
            previous = current;
        }
        LedgerEntry empty = ReceiptLayoutParser.parse(java.util.List.of());
        equal("", empty.getAmount());
        equal("false", String.valueOf(CaptureSourcePolicy.hasReceiptAmount(empty)));
        equal("99", previous.getAmount());
        LedgerEntry unrelated = ReceiptParser.parse("实付金额：28");
        unrelated.setSourceText("商品推广价格 28");
        unrelated.setAmount("");
        equal("false", String.valueOf(CaptureSourcePolicy.hasReceiptAmount(unrelated)));
    }

    private static void verifyCommonLayouts() {
        LedgerEntry wechat = ReceiptParser.parse("cdf中免\n–192.50\n当前状态 支付成功\n支付时间 2026 年 10 月 22 日 15:46:15\n商品 雪肌精谧雅臻活乳液 盈润型 140ml\n商户全称 中免（海南）数字科技有限公司\n支付方式 招商银行储蓄卡(0352)");
        equal("192.5", wechat.getAmount());
        equal("2026-10-22", wechat.getDate());
        equal("雪肌精谧雅臻活乳液 盈润型 140ml", wechat.getProduct());
        equal("", wechat.getNote());
        LedgerEntry alipay = ReceiptParser.parse("账单详情\n-69.00\n交易成功\n商品说明\n软件终身会员\n商家名称\n测试软件有限公司\n创建时间 2026-10-21 15:00:00\n付款时间 2026-10-22 16:00:00");
        equal("69", alipay.getAmount());
        equal("软件终身会员", alipay.getProduct());
        equal("测试软件有限公司", alipay.getShop());
        equal("2026-10-22", alipay.getDate());
        LedgerEntry taobao = ReceiptParser.parse("订单详情\n交易成功\n测试家居旗舰店\n迷你手动蒜泥器家用压蒜器\n白色 标准款\n￥29.00\n×1\n商品总价 ￥29.00\n优惠 ￥3.00\n实付款 ￥26.00\n付款时间 2026-10-22 12:00:00");
        equal("26", taobao.getAmount());
        equal("测试家居旗舰店", taobao.getShop());
        equal("迷你手动蒜泥器家用压蒜器 白色 标准款", taobao.getProduct());
        LedgerEntry pdd = ReceiptParser.parse("拼多多\n待发货\n测试数码专营店\nApple 35W 双USB-C 电源适配器\n￥325.00\n商品总价 ￥325.00\n平台优惠 ￥60.00\n支付优惠 ￥5.00\n实付金额 ￥260.00\n支付时间 2026-10-22 12:00:00");
        equal("260", pdd.getAmount());
        equal("测试数码专营店", pdd.getShop());
        equal("Apple 35W 双USB-C 电源适配器", pdd.getProduct());
        equal("", ReceiptParser.parse("退款成功\n￥26.00\n退款金额 26.00").getAmount());
        equal("", ReceiptParser.parse("等待买家付款\n实付款 ￥26.00").getAmount());
        equal("雪肌精乳液", ReceiptParser.parse("商品名陈 雪肌精乳液").getProduct());
        java.util.List<ReceiptLine> columns = java.util.List.of(
                new ReceiptLine("cdf中免", 460, 450, 610, 490),
                new ReceiptLine("–192.50", 400, 570, 680, 640),
                new ReceiptLine("当前状态", 85, 850, 230, 885),
                new ReceiptLine("支付时间", 85, 925, 230, 960),
                new ReceiptLine("商品", 85, 1000, 150, 1035),
                new ReceiptLine("商户全称", 85, 1075, 230, 1110),
                new ReceiptLine("支付成功", 310, 850, 460, 885),
                new ReceiptLine("2026年10月22日 15:46:15", 310, 925, 760, 960),
                new ReceiptLine("雪肌精乳液 盈润型 140ml", 310, 1000, 900, 1035),
                new ReceiptLine("中免（海南）数字科技有限公司", 310, 1075, 900, 1110));
        LedgerEntry spatial = ReceiptLayoutParser.parse(columns);
        equal("cdf中免", spatial.getShop());
        equal("192.5", spatial.getAmount());
        equal("雪肌精乳液 盈润型 140ml", spatial.getProduct());
        equal("2026-10-22", spatial.getDate());
        equal("", spatial.getNote());
        equal("26", ReceiptParser.parse("实付款（含运费） ￥26.00").getAmount());
        equal("26", ReceiptParser.parse("实付款￥26.00").getAmount());
        equal("260", ReceiptParser.parse("实付金颜 ￥260.00").getAmount());
        equal("家用压蒜器 白色款", ReceiptParser.parse("测试家居旗舰店\n家用压蒜器 白色款 ￥29.00\n×1\n实付款 ￥26.00").getProduct());
        equal(LedgerEntry.DRAFT, spatial.getStatus());
    }

    private static void equal(String expected, String actual) {
        checks++;
        if (!expected.equals(actual)) {
            throw new AssertionError("expected [" + expected + "] but got [" + actual + "]");
        }
    }

    private static void nonempty(String value) {
        checks++;
        if (value.isEmpty()) {
            throw new AssertionError("invalid data accepted");
        }
    }
}
