package cn.local.jiyibi;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 完整备份含草稿、原始识别文字与评价；恢复前完整校验，避免部分恢复。 */
public final class BackupCodec {
    private static final int SCHEMA_VERSION = 1;

    private BackupCodec() { }

    public static byte[] encode(List<LedgerEntry> entries) throws JSONException {
        JSONArray rows = new JSONArray();
        for (LedgerEntry entry : entries) {
            JSONObject row = new JSONObject();
            row.put("id", entry.getId());
            row.put("date", entry.getDate());
            row.put("shop", entry.getShop());
            row.put("product", entry.getProduct());
            row.put("category", entry.getCategory());
            row.put("amount", entry.getAmount());
            row.put("note", entry.getNote());
            row.put("sourceText", entry.getSourceText());
            row.put("status", entry.getStatus());
            rows.put(row);
        }
        return new JSONObject().put("format", "jiyibi-backup")
                .put("version", SCHEMA_VERSION).put("entries", rows).toString(2).getBytes(StandardCharsets.UTF_8);
    }

    public static List<LedgerEntry> decode(byte[] data) throws JSONException {
        JSONObject root = new JSONObject(new String(data, StandardCharsets.UTF_8));
        if (!"jiyibi-backup".equals(root.getString("format")) || root.getInt("version") != SCHEMA_VERSION) {
            throw new JSONException("不是兼容的Ellery Ledger备份文件");
        }
        JSONArray rows = root.getJSONArray("entries");
        List<LedgerEntry> entries = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (int index = 0; index < rows.length(); index++) {
            JSONObject row = rows.getJSONObject(index);
            LedgerEntry entry = new LedgerEntry();
            entry.setId(row.getString("id"));
            entry.setDate(row.getString("date"));
            entry.setShop(row.getString("shop"));
            entry.setProduct(row.getString("product"));
            entry.setCategory(row.getString("category"));
            entry.setAmount(row.getString("amount"));
            entry.setNote(row.getString("note"));
            entry.setSourceText(row.getString("sourceText"));
            entry.setStatus(row.getString("status"));
            if (entry.getId().trim().isEmpty() || !ids.add(entry.getId())
                    || !(LedgerEntry.DRAFT.equals(entry.getStatus()) || LedgerEntry.CONFIRMED.equals(entry.getStatus()))
                    || (LedgerEntry.CONFIRMED.equals(entry.getStatus()) && !entry.validationError().isEmpty())) {
                throw new JSONException("备份中包含重复标识或无效账目，第 " + (index + 1) + " 条");
            }
            entries.add(entry);
        }
        return entries;
    }
}
