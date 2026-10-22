package cn.local.jiyibi;

import android.util.Xml;
import org.xmlpull.v1.XmlPullParser;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/** 只导入“1.明细”的五列数据。导出新工作簿，不改动用户原文件与查询结构。 */
public final class ExcelTransfer {
    public static final int MAX_FILE_BYTES = 20 * 1024 * 1024;
    private static final String[] HEADERS = {"日期", "事项", "类别", "金额", "备注"};
    private static final String MAIN_NS = "http://schemas.openxmlformats.org/spreadsheetml/2006/main";

    private ExcelTransfer() { }

    public static byte[] readLimited(InputStream input, int limit) throws IOException {
        if (input == null) {
            throw new IOException("无法打开所选文件");
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) != -1) {
            if (output.size() + count > limit) {
                throw new IOException("文件过大，超过安全读取上限");
            }
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }

    public static ImportResult read(byte[] bytes) throws Exception {
        Map<String, byte[]> parts = new HashMap<>();
        int expanded = 0;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry part;
            while ((part = zip.getNextEntry()) != null) {
                if (parts.size() > 1000) {
                    throw new IOException("工作簿文件项过多");
                }
                byte[] data = readLimited(zip, MAX_FILE_BYTES - expanded);
                expanded += data.length;
                if (!part.isDirectory()) {
                    parts.put(part.getName(), data);
                }
            }
        }
        XmlPullParser workbook = parser(parts.get("xl/workbook.xml"));
        String relationshipId = null;
        while (workbook.next() != XmlPullParser.END_DOCUMENT) {
            if (workbook.getEventType() != XmlPullParser.START_TAG) {
                continue;
            }
            if ("workbookPr".equals(workbook.getName()) &&
                    ("1".equals(workbook.getAttributeValue(null, "date1904"))
                    || "true".equals(workbook.getAttributeValue(null, "date1904")))) {
                throw new IOException("暂不支持 1904 日期系统，请转换后导入");
            }
            if ("sheet".equals(workbook.getName()) && "1.明细".equals(workbook.getAttributeValue(null, "name"))) {
                relationshipId = workbook.getAttributeValue(
                        "http://schemas.openxmlformats.org/officeDocument/2006/relationships", "id");
            }
        }
        if (relationshipId == null) {
            throw new IOException("找不到“1.明细”工作表");
        }
        XmlPullParser relationships = parser(parts.get("xl/_rels/workbook.xml.rels"));
        String sheetPath = null;
        while (relationships.next() != XmlPullParser.END_DOCUMENT) {
            if (relationships.getEventType() == XmlPullParser.START_TAG
                    && "Relationship".equals(relationships.getName())
                    && relationshipId.equals(relationships.getAttributeValue(null, "Id"))) {
                String target = relationships.getAttributeValue(null, "Target");
                sheetPath = target.startsWith("/") ? target.substring(1) : "xl/" + target;
            }
        }
        List<String> shared = new ArrayList<>();
        if (parts.containsKey("xl/sharedStrings.xml")) {
            XmlPullParser strings = parser(parts.get("xl/sharedStrings.xml"));
            StringBuilder item = new StringBuilder();
            while (strings.next() != XmlPullParser.END_DOCUMENT) {
                if (strings.getEventType() == XmlPullParser.START_TAG && "si".equals(strings.getName())) {
                    item.setLength(0);
                } else if (strings.getEventType() == XmlPullParser.START_TAG && "t".equals(strings.getName())) {
                    item.append(strings.nextText());
                } else if (strings.getEventType() == XmlPullParser.END_TAG && "si".equals(strings.getName())) {
                    shared.add(item.toString());
                }
            }
        }
        XmlPullParser sheet = parser(parts.get(sheetPath));
        ImportResult result = new ImportResult();
        Map<String, String> row = new HashMap<>();
        String column = "";
        String type = "";
        String rowNumber = "";
        StringBuilder cell = new StringBuilder();
        boolean headerFound = false;
        while (sheet.next() != XmlPullParser.END_DOCUMENT) {
            String name = sheet.getName();
            int event = sheet.getEventType();
            if (event == XmlPullParser.START_TAG && "row".equals(name)) {
                row.clear();
                rowNumber = sheet.getAttributeValue(null, "r");
            } else if (event == XmlPullParser.START_TAG && "c".equals(name)) {
                column = sheet.getAttributeValue(null, "r").replaceAll("[0-9]", "");
                type = sheet.getAttributeValue(null, "t");
                cell.setLength(0);
            } else if (event == XmlPullParser.START_TAG && ("v".equals(name) || "t".equals(name))) {
                cell.append(sheet.nextText());
            } else if (event == XmlPullParser.END_TAG && "c".equals(name)) {
                String value = cell.toString();
                if ("s".equals(type) && !value.isEmpty()) {
                    value = shared.get(Integer.parseInt(value));
                }
                row.put(column, value);
            } else if (event == XmlPullParser.END_TAG && "row".equals(name)) {
                if (!headerFound) {
                    for (int index = 0; index < HEADERS.length; index++) {
                        if (!HEADERS[index].equals(row.get(String.valueOf((char) ('A' + index))))) {
                            throw new IOException("表头应为：日期、事项、类别、金额、备注");
                        }
                    }
                    headerFound = true;
                } else if (!row.getOrDefault("B", "").trim().isEmpty()) {
                    LedgerEntry entry = new LedgerEntry();
                    String date = row.getOrDefault("A", "");
                    try {
                        if (date.matches("[0-9]+(?:\\.[0-9]+)?")) {
                            long serial = new BigDecimal(date).longValue();
                            date = LocalDate.of(1899, 12, 30).plusDays(serial).toString();
                        }
                        entry.setDate(date);
                        // 旧事项无法可靠拆分店铺和商品，整体保留在商品字段，不猜测。
                        entry.setProduct(row.get("B"));
                        entry.setCategory(row.getOrDefault("C", ""));
                        BigDecimal importedAmount = new BigDecimal(row.getOrDefault("D", ""));
                        BigDecimal roundedAmount = importedAmount.setScale(2, java.math.RoundingMode.HALF_UP);
                        // Excel 浮点缓存可能产生 20.100000000000001，容许极小存储误差。
                        if (importedAmount.subtract(roundedAmount).abs().compareTo(new BigDecimal("0.00000001")) > 0) {
                            throw new IllegalArgumentException("原始金额超过两位小数");
                        }
                        entry.setAmount(roundedAmount.stripTrailingZeros().toPlainString());
                        entry.setNote(row.getOrDefault("E", ""));
                        if (!entry.validationError().isEmpty()) {
                            throw new IllegalArgumentException(entry.validationError());
                        }
                        String key = entry.getDate() + "\u0000" + entry.item() + "\u0000" + entry.getCategory()
                                + "\u0000" + entry.getAmount() + "\u0000" + entry.getNote() + "\u0000" + rowNumber;
                        entry.setId(UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)).toString());
                        entry.setStatus(LedgerEntry.CONFIRMED);
                        result.entries.add(entry);
                    } catch (RuntimeException exception) {
                        result.skippedRows.add(rowNumber);
                    }
                }
            }
        }
        return result;
    }

    public static byte[] write(List<LedgerEntry> entries) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            put(zip, "[Content_Types].xml", "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                    + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
                    + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
                    + "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>"
                    + "<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>"
                    + "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/></Types>");
            put(zip, "_rels/.rels", "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                    + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>");
            put(zip, "xl/workbook.xml", "<workbook xmlns=\"" + MAIN_NS + "\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">"
                    + "<sheets><sheet name=\"1.明细\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>");
            put(zip, "xl/_rels/workbook.xml.rels", "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                    + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/>"
                    + "<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/></Relationships>");
            put(zip, "xl/styles.xml", "<styleSheet xmlns=\"" + MAIN_NS + "\">"
                    + "<numFmts count=\"1\"><numFmt numFmtId=\"164\" formatCode=\"yyyy-mm-dd\"/></numFmts>"
                    + "<fonts count=\"1\"><font><sz val=\"11\"/><name val=\"Calibri\"/></font></fonts>"
                    + "<fills count=\"2\"><fill><patternFill patternType=\"none\"/></fill><fill><patternFill patternType=\"gray125\"/></fill></fills>"
                    + "<borders count=\"1\"><border><left/><right/><top/><bottom/><diagonal/></border></borders>"
                    + "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>"
                    + "<cellXfs count=\"3\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>"
                    + "<xf numFmtId=\"164\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\"/>"
                    + "<xf numFmtId=\"2\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\"/></cellXfs>"
                    + "<cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles></styleSheet>");
            StringBuilder sheet = new StringBuilder("<worksheet xmlns=\"" + MAIN_NS + "\"><cols>"
                    + "<col min=\"1\" max=\"1\" width=\"14\" customWidth=\"1\"/><col min=\"2\" max=\"2\" width=\"42\" customWidth=\"1\"/>"
                    + "<col min=\"3\" max=\"4\" width=\"14\" customWidth=\"1\"/><col min=\"5\" max=\"5\" width=\"48\" customWidth=\"1\"/></cols><sheetData>");
            appendRow(sheet, 1, HEADERS, false);
            int row = 2;
            for (LedgerEntry entry : entries) {
                if (LedgerEntry.CONFIRMED.equals(entry.getStatus())) {
                    appendRow(sheet, row++, new String[]{entry.getDate(), entry.item(), entry.getCategory(),
                            entry.getAmount(), entry.getNote()}, true);
                }
            }
            sheet.append("</sheetData><autoFilter ref=\"A1:E").append(row - 1).append("\"/></worksheet>");
            put(zip, "xl/worksheets/sheet1.xml", sheet.toString());
        }
        return bytes.toByteArray();
    }

    private static void appendRow(StringBuilder xml, int row, String[] values, boolean numericAmount) {
        xml.append("<row r=\"").append(row).append("\">");
        for (int index = 0; index < values.length; index++) {
            xml.append("<c r=\"").append((char) ('A' + index)).append(row).append("\"");
            if (numericAmount && index == 0) {
                long serial = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.of(1899, 12, 30), LocalDate.parse(values[index]));
                xml.append(" s=\"1\"><v>").append(serial).append("</v></c>");
            } else if (numericAmount && index == 3) {
                xml.append(" s=\"2\"><v>").append(new BigDecimal(values[index]).toPlainString()).append("</v></c>");
            } else {
                xml.append(" t=\"inlineStr\"><is><t xml:space=\"preserve\">")
                        .append(escape(values[index])).append("</t></is></c>");
            }
        }
        xml.append("</row>");
    }

    private static String escape(String text) {
        StringBuilder clean = new StringBuilder();
        text.codePoints().filter(value -> value == 9 || value == 10 || value == 13
                || (value >= 32 && value <= 0xD7FF) || (value >= 0xE000 && value <= 0xFFFD)
                || (value >= 0x10000 && value <= 0x10FFFF)).forEach(clean::appendCodePoint);
        return clean.toString().replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static XmlPullParser parser(byte[] bytes) throws Exception {
        if (bytes == null) {
            throw new IOException("工作簿缺少必要的文件项");
        }
        XmlPullParser parser = Xml.newPullParser();
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true);
        parser.setInput(new ByteArrayInputStream(bytes), "UTF-8");
        return parser;
    }

    private static void put(ZipOutputStream zip, String name, String text) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(("<?xml version=\"1.0\" encoding=\"UTF-8\"?>" + text).getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    public static final class ImportResult {
        private final List<LedgerEntry> entries = new ArrayList<>();
        private final List<String> skippedRows = new ArrayList<>();
        public List<LedgerEntry> getEntries() { return entries; }
        public List<String> getSkippedRows() { return skippedRows; }
    }
}
