package cn.local.jiyibi;

import android.app.Instrumentation;
import android.os.Bundle;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** 在 Android 上验证文件互通、确认状态和恢复幂等性，不读写真实用户账目。 */
public final class LedgerInstrumentation extends Instrumentation {
    private String sampleImagePath;
    @Override
    public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        sampleImagePath = arguments.getString("image_path");
        start();
    }

    @Override
    public void onStart() {
        if (sampleImagePath != null) {
            ReceiptImageInstrumentation.verify(this, sampleImagePath);
            return;
        }
        Bundle result = new Bundle();
        try {
            LedgerEntry draft = ReceiptParser.parse("店铺：测试店\n商品：测试商品\n实付金额：18.90\n2026-10-22");
            draft.setCategory("餐饮");
            draft.setNote("口味清淡，下次回购。\n保留换行和 <>& 符号");
            if (!LedgerEntry.DRAFT.equals(draft.getStatus())) {
                throw new AssertionError("识别不应自动确认");
            }
            byte[] draftOnly = ExcelTransfer.write(List.of(draft));
            if (!ExcelTransfer.read(draftOnly).getEntries().isEmpty()) {
                throw new AssertionError("Excel 不应包含待核对草稿");
            }
            draft.setStatus(LedgerEntry.CONFIRMED);
            List<LedgerEntry> restored = BackupCodec.decode(BackupCodec.encode(List.of(draft)));
            equal(draft.getNote(), restored.get(0).getNote());
            equal(draft.getId(), restored.get(0).getId());
            equal(draft.getSourceText(), restored.get(0).getSourceText());
            byte[] workbook = ExcelTransfer.write(List.of(draft));
            ExcelTransfer.ImportResult imported = ExcelTransfer.read(workbook);
            if (imported.getEntries().size() != 1 || !imported.getSkippedRows().isEmpty()) {
                throw new AssertionError("Excel 往返记录数不一致");
            }
            LedgerEntry copy = imported.getEntries().get(0);
            equal(draft.item(), copy.item());
            equal(draft.getShop(), copy.getShop());
            equal(draft.getProduct(), copy.getProduct());
            equal(draft.getDate(), copy.getDate());
            equal(draft.getNote(), copy.getNote());
            if (new BigDecimal(draft.getAmount()).compareTo(new BigDecimal(copy.getAmount())) != 0) {
                throw new AssertionError("金额往返不一致");
            }
            // 使用独立命名的测试数据库，不读写 App 实际账目库。
            try (LedgerDatabase database = new LedgerDatabase(getTargetContext(), "instrumentation-ledger.db")) {
                database.delete(draft.getId());
                draft.setStatus(LedgerEntry.DRAFT);
                database.save(draft);
                equal(LedgerEntry.DRAFT, database.find(draft.getId()).getStatus());
                draft.setStatus(LedgerEntry.CONFIRMED);
                database.save(draft);
                database.save(draft);
                equal(LedgerEntry.CONFIRMED, database.find(draft.getId()).getStatus());
                if (database.merge(restored) != 0) {
                    throw new AssertionError("重复恢复不应新增");
                }
                database.delete(draft.getId());
            }
            getTargetContext().deleteDatabase("instrumentation-ledger.db");
            verifyClearConfirmed();
            verifyOfflineOcr();
            result.putString("stream", "PASS: Excel round-trip, JSON backup, draft confirmation, idempotent restore and offline Chinese OCR\n");
            finish(ActivityResult.SUCCESS, result);
        } catch (Exception | AssertionError exception) {
            result.putString("stream", "FAIL: " + exception + "\n");
            finish(ActivityResult.FAILURE, result);
        }
    }

    private static void equal(String expected, String actual) {
        if (!expected.equals(actual)) {
            throw new AssertionError("往返数据不一致");
        }
    }

    private void verifyClearConfirmed() throws Exception {
        String databaseName = "clear-confirmed-" + java.util.UUID.randomUUID() + ".db";
        try (LedgerDatabase database = new LedgerDatabase(getTargetContext(), databaseName)) {
            if (database.countConfirmed() != 0 || database.clearConfirmed() != 0) {
                throw new AssertionError("空数据库清空应为零条");
            }
            LedgerEntry pending = ReceiptParser.parse("商户名称：保留店铺\n商品：保留商品\n实付金额：28.00");
            pending.setNote("保留用户评价和换行\n第二行");
            database.save(pending);
            byte[] before = BackupCodec.encode(database.list());
            for (int index = 0; index < 2; index++) {
                LedgerEntry confirmed = new LedgerEntry();
                confirmed.setAmount("31.71");
                confirmed.setStatus(LedgerEntry.CONFIRMED);
                database.save(confirmed);
            }
            if (database.countConfirmed() != 2 || database.clearConfirmed() != 2
                    || database.countConfirmed() != 0) {
                throw new AssertionError("应只删除全部已确认账单");
            }
            if (!java.util.Arrays.equals(before, BackupCodec.encode(database.list()))) {
                throw new AssertionError("待核对草稿的标识、字段与原文必须完整保留");
            }
            if (database.clearConfirmed() != 0 || database.list().size() != 1) {
                throw new AssertionError("重复清空不能删除草稿");
            }
            database.save(new LedgerEntry());
            if (database.clearConfirmed() != 0 || database.list().size() != 2) {
                throw new AssertionError("新生成的待核对草稿也必须保留");
            }
        } finally {
            getTargetContext().deleteDatabase(databaseName);
        }
    }

    private void verifyOfflineOcr() throws Exception {
        Bitmap bitmap = Bitmap.createBitmap(1200, 800, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(Color.WHITE);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.BLACK);
        paint.setTextSize(48);
        canvas.drawText("商户名称：测试咖啡店", 50, 100, paint);
        canvas.drawText("商品名称：生椰拿铁", 50, 200, paint);
        canvas.drawText("实付金额：18.90", 50, 300, paint);
        canvas.drawText("支付时间：2026-10-22", 50, 400, paint);
        CountDownLatch completion = new CountDownLatch(1);
        AtomicReference<String> recognized = new AtomicReference<>();
        TextRecognizer recognizer = TextRecognition.getClient(new ChineseTextRecognizerOptions.Builder().build());
        try {
            recognizer.process(InputImage.fromBitmap(bitmap, 0))
                    .addOnSuccessListener(result -> recognized.set(result.getText()))
                    .addOnCompleteListener(task -> completion.countDown());
            if (!completion.await(40, TimeUnit.SECONDS) || recognized.get() == null
                    || !recognized.get().contains("拿铁") || !recognized.get().contains("18.90")) {
                throw new AssertionError("内置中文 OCR 未完成预期识别");
            }
        } finally {
            recognizer.close();
            bitmap.recycle();
        }
    }

    private static final class ActivityResult {
        private static final int SUCCESS = -1;
        private static final int FAILURE = 0;
    }
}
