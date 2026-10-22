package cn.local.jiyibi;

import android.app.Instrumentation;
import android.net.Uri;
import android.os.Bundle;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** 对显式提供的真实账单图片运行离线模型，不写入账目数据库。 */
public final class ReceiptImageInstrumentation {
    private ReceiptImageInstrumentation() { }

    public static void verify(Instrumentation instrumentation, String imagePath) {
        Bundle output = new Bundle();
        TextRecognizer recognizer = TextRecognition.getClient(new ChineseTextRecognizerOptions.Builder().build());
        try {
            CountDownLatch completion = new CountDownLatch(1);
            AtomicReference<Text> recognized = new AtomicReference<>();
            recognizer.process(InputImage.fromFilePath(instrumentation.getTargetContext(), Uri.fromFile(new File(imagePath))))
                    .addOnSuccessListener(recognized::set)
                    .addOnCompleteListener(task -> completion.countDown());
            if (!completion.await(45, TimeUnit.SECONDS) || recognized.get() == null) {
                throw new AssertionError("原图本地 OCR 未完成");
            }
            Text text = recognized.get();
            LedgerEntry entry = LocalOcr.extract(text, false);
            JSONObject report = new JSONObject();
            report.put("amount", entry.getAmount()).put("date", entry.getDate()).put("shop", entry.getShop())
                    .put("product", entry.getProduct()).put("note", entry.getNote()).put("status", entry.getStatus());
            JSONArray lines = new JSONArray();
            for (Text.TextBlock block : text.getTextBlocks()) {
                for (Text.Line line : block.getLines()) {
                    JSONObject row = new JSONObject().put("text", line.getText());
                    if (line.getBoundingBox() != null) {
                        android.graphics.Rect box = line.getBoundingBox();
                        row.put("left", box.left).put("top", box.top).put("right", box.right).put("bottom", box.bottom);
                    }
                    lines.put(row);
                }
            }
            report.put("ocrLines", lines).put("reconstructed", entry.getSourceText());
            Files.write(new File(instrumentation.getTargetContext().getFilesDir(), "sample-ocr-report.json").toPath(),
                    report.toString(2).getBytes(StandardCharsets.UTF_8));
            if (!"192.5".equals(entry.getAmount()) || !"2026-10-22".equals(entry.getDate())
                    || !entry.getShop().contains("中免") || !entry.getProduct().contains("乳液")
                    || !entry.getNote().isEmpty() || !LedgerEntry.DRAFT.equals(entry.getStatus())) {
                throw new AssertionError("原图提取未达到金额、日期、店铺、商品及空备注的预期，请检查报告");
            }
            LedgerEntry amountOnly = LocalOcr.extract(text, true);
            if (!"192.5".equals(amountOnly.getAmount()) || !amountOnly.item().isEmpty()) {
                throw new AssertionError("原图的仅金额模式异常");
            }
            output.putString("stream", "PASS: original image offline OCR and amount-only extraction; no ledger entries created\n");
            instrumentation.finish(-1, output);
        } catch (Exception | AssertionError exception) {
            output.putString("stream", "FAIL: " + exception + "\n");
            instrumentation.finish(0, output);
        } finally {
            recognizer.close();
        }
    }
}
