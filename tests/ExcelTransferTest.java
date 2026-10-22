package cn.local.jiyibi;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Excel 编解码器的主机数据测试，不依赖模拟器或用户真实账目库。 */
public final class ExcelTransferTest {
    public static void main(String[] args) throws Exception {
        LedgerEntry entry = new LedgerEntry();
        entry.setDate("2026-10-22");
        entry.setShop("测试店");
        entry.setProduct("测试商品");
        entry.setCategory("日用");
        entry.setAmount("20.10");
        entry.setNote("评价 <>&\n第二行");
        if (!ExcelTransfer.read(ExcelTransfer.write(List.of(entry))).getEntries().isEmpty()) {
            throw new AssertionError("草稿被错误导出");
        }
        entry.setStatus(LedgerEntry.CONFIRMED);
        byte[] bytes = ExcelTransfer.write(List.of(entry));
        ExcelTransfer.ImportResult result = ExcelTransfer.read(bytes);
        if (result.getEntries().size() != 1 || !result.getSkippedRows().isEmpty()) {
            throw new AssertionError("往返行数错误");
        }
        LedgerEntry copy = result.getEntries().get(0);
        if (!copy.getDate().equals(entry.getDate()) || !copy.getNote().equals(entry.getNote())
                || !copy.item().equals(entry.item()) || !copy.getAmount().equals("20.1")) {
            throw new AssertionError("Excel 往返内容不一致");
        }
        Path output = Path.of("build/core-tests/roundtrip-fixture.xlsx");
        Files.write(output, bytes);
        System.out.println("PASS: Excel typed-value round-trip; drafts excluded");
        if (args.length > 0) {
            ExcelTransfer.ImportResult original = ExcelTransfer.read(Files.readAllBytes(Path.of(args[0])));
            if (original.getEntries().isEmpty()) {
                throw new AssertionError("提供的模板没有导入有效记录");
            }
            List<LedgerEntry> again = ExcelTransfer.read(ExcelTransfer.write(original.getEntries())).getEntries();
            if (again.size() != original.getEntries().size()) {
                throw new AssertionError("原始账目导出后行数不一致");
            }
            for (int index = 0; index < again.size(); index++) {
                LedgerEntry before = original.getEntries().get(index);
                LedgerEntry after = again.get(index);
                if (!before.item().equals(after.item()) || !before.getNote().equals(after.getNote())
                        || !before.getAmount().equals(after.getAmount()) || !before.getDate().equals(after.getDate())) {
                    throw new AssertionError("原始账目往返值不一致");
                }
            }
            System.out.println("PASS: original workbook import and in-memory round-trip; valid="
                    + original.getEntries().size() + "; skipped rows=" + original.getSkippedRows());
        }
    }
}
