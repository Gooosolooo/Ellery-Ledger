package cn.local.jiyibi;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/** 执行生产导入导出方法，验证六列 OOXML、兼容旧格式和非空商品约束。 */
public final class ExcelTransferTest {
    private static int checks;

    public static void main(String[] args) throws Exception {
        LedgerEntry full = entry("Coffee & Co", "Tea <milk>\nlarge");
        full.setNote("taste <good> & useful\nsecond line");
        LedgerEntry onlyShop = entry("Only Brand", "");
        LedgerEntry onlyProduct = entry("", "Only Product");
        LedgerEntry draft = new LedgerEntry();
        byte[] workbook = ExcelTransfer.write(List.of(full, onlyShop, onlyProduct, draft));
        String sheet = sheetXml(workbook);
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        Document document = factory.newDocumentBuilder().parse(new ByteArrayInputStream(sheet.getBytes(StandardCharsets.UTF_8)));
        NodeList rows = document.getElementsByTagNameNS("*", "row");
        equal("4", String.valueOf(rows.getLength()));
        List<String> headers = new ArrayList<>();
        NodeList headerCells = ((Element) rows.item(0)).getElementsByTagNameNS("*", "c");
        for (int index = 0; index < headerCells.getLength(); index++) {
            headers.add(headerCells.item(index).getTextContent());
        }
        equal("日期|店铺/品牌|商品名称|类别|金额|备注", String.join("|", headers));
        equal("A1:F4", ((Element) document.getElementsByTagNameNS("*", "autoFilter").item(0)).getAttribute("ref"));
        Element amountCell = (Element) ((Element) rows.item(1)).getElementsByTagNameNS("*", "c").item(4);
        equal("E2", amountCell.getAttribute("r"));
        equal("2", amountCell.getAttribute("s"));
        equal("", amountCell.getAttribute("t"));
        equal("28.00", amountCell.getTextContent());
        ExcelTransfer.ImportResult parsed = ExcelTransfer.read(workbook);
        equal("3", String.valueOf(parsed.getEntries().size()));
        equal("0", String.valueOf(parsed.getSkippedRows().size()));
        equal(full.getShop(), parsed.getEntries().get(0).getShop());
        equal(full.getProduct(), parsed.getEntries().get(0).getProduct());
        equal(full.getNote(), parsed.getEntries().get(0).getNote());
        equal(full.getDate(), parsed.getEntries().get(0).getDate());
        equal("日用", parsed.getEntries().get(0).getCategory());
        equal("28", parsed.getEntries().get(0).getAmount());
        equal("", parsed.getEntries().get(1).getShop());
        equal("Only Brand", parsed.getEntries().get(1).getProduct());
        equal("", parsed.getEntries().get(2).getShop());
        equal("Only Product", parsed.getEntries().get(2).getProduct());
        equal("Only Brand", onlyShop.getShop());
        equal("", onlyShop.getProduct());
        equal("", onlyShop.validationError());
        equal("", onlyProduct.validationError());
        LedgerEntry missing = entry("   ", " ");
        truth(!missing.validationError().isEmpty());
        try {
            ExcelTransfer.write(List.of(missing));
            throw new AssertionError("empty confirmed product must not be exported");
        } catch (IOException expected) {
            truth(expected.getMessage().contains("商品名称"));
        }
        String legacy = "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>"
                + row(1, new String[]{"日期", "事项", "类别", "金额", "备注"})
                + row(2, new String[]{"2026-10-23", "Legacy combined item", "日用", "12.30", "old note"})
                + "</sheetData></worksheet>";
        List<LedgerEntry> old = ExcelTransfer.read(replaceSheet(workbook, legacy)).getEntries();
        equal("1", String.valueOf(old.size()));
        equal("", old.get(0).getShop());
        equal("Legacy combined item", old.get(0).getProduct());
        equal("12.3", old.get(0).getAmount());
        equal("old note", old.get(0).getNote());
        String split = "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>"
                + row(1, new String[]{"日期", "店铺/品牌", "商品名称", "类别", "金额", "备注"})
                + row(2, new String[]{"2026-10-23", "Only shop on import", "", "日用", "15.00", ""})
                + row(3, new String[]{"2026-10-23", "", "", "日用", "15.00", ""})
                + "</sheetData></worksheet>";
        ExcelTransfer.ImportResult splitResult = ExcelTransfer.read(replaceSheet(workbook, split));
        equal("1", String.valueOf(splitResult.getEntries().size()));
        equal("", splitResult.getEntries().get(0).getShop());
        equal("Only shop on import", splitResult.getEntries().get(0).getProduct());
        equal("3", splitResult.getSkippedRows().get(0));
        System.out.println("PASS: " + checks + " Excel format and item validation checks");
    }

    private static LedgerEntry entry(String shop, String product) {
        LedgerEntry entry = new LedgerEntry();
        entry.setShop(shop);
        entry.setProduct(product);
        entry.setDate("2026-10-23");
        entry.setCategory("日用");
        entry.setAmount("28.00");
        entry.setStatus(LedgerEntry.CONFIRMED);
        return entry;
    }

    private static String row(int row, String[] values) {
        StringBuilder xml = new StringBuilder("<row r=\"").append(row).append("\">");
        for (int index = 0; index < values.length; index++) {
            xml.append("<c r=\"").append((char) ('A' + index)).append(row)
                    .append("\" t=\"inlineStr\"><is><t>").append(values[index]).append("</t></is></c>");
        }
        return xml.append("</row>").toString();
    }

    private static String sheetXml(byte[] workbook) throws IOException {
        try (ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(workbook))) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                if ("xl/worksheets/sheet1.xml".equals(entry.getName())) {
                    return new String(input.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
        }
        throw new IOException("sheet missing");
    }

    private static byte[] replaceSheet(byte[] workbook, String sheet) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(workbook));
                ZipOutputStream output = new ZipOutputStream(bytes)) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                output.putNextEntry(new ZipEntry(entry.getName()));
                output.write("xl/worksheets/sheet1.xml".equals(entry.getName())
                        ? sheet.getBytes(StandardCharsets.UTF_8) : input.readAllBytes());
                output.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    private static void equal(String expected, String actual) {
        checks++;
        if (!expected.equals(actual)) {
            throw new AssertionError("expected [" + expected + "] got [" + actual + "]");
        }
    }

    private static void truth(boolean condition) {
        checks++;
        if (!condition) {
            throw new AssertionError("condition failed");
        }
    }
}
