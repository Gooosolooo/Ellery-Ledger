package cn.local.jiyibi;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** 本地账目；草稿与已确认账目使用同一标识，避免重复确认产生重复记录。 */
public final class LedgerEntry {
    public static final String DRAFT = "draft";
    public static final String CONFIRMED = "confirmed";
    public static final String[] CATEGORIES = {"", "餐饮", "享受", "交通", "提升", "日用", "社交", "穿搭", "咪用", "大额"};

    private String id = UUID.randomUUID().toString();
    private String date = "";
    private String shop = "";
    private String product = "";
    private String category = "";
    private String amount = "";
    private String note = "";
    private String sourceText = "";
    private String status = DRAFT;

    public String item() {
        return (shop.trim() + " " + product.trim()).trim();
    }

    /** 金额保存为十进制文本，计算时使用 BigDecimal，避免二进制浮点误差。 */
    public String validationError() {
        try {
            LocalDate.parse(date);
        } catch (RuntimeException exception) {
            return "请填写有效日期，例如 2026-10-22";
        }
        if (item().isEmpty()) {
            return "请补充店铺／品牌或商品名称";
        }
        if (category.trim().isEmpty()) {
            return "请选择类别";
        }
        try {
            BigDecimal value = new BigDecimal(amount);
            if (value.signum() <= 0 || value.scale() > 2 || value.precision() > 14) {
                return "请输入大于零、最多两位小数的支出金额";
            }
        } catch (NumberFormatException exception) {
            return "请补充有效金额";
        }
        return "";
    }

    public String getId() { return id; }
    public void setId(String value) { id = value; }
    public String getDate() { return date; }
    public void setDate(String value) { date = value; }
    public String getShop() { return shop; }
    public void setShop(String value) { shop = value; }
    public String getProduct() { return product; }
    public void setProduct(String value) { product = value; }
    public String getCategory() { return category; }
    public void setCategory(String value) { category = value; }
    public String getAmount() { return amount; }
    public void setAmount(String value) { amount = value; }
    public String getNote() { return note; }
    public void setNote(String value) { note = value; }
    public String getSourceText() { return sourceText; }
    public void setSourceText(String value) { sourceText = value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { status = value; }
}
