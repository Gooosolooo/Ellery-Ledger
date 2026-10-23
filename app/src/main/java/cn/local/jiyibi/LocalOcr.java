package cn.local.jiyibi;

import android.content.Context;
import android.graphics.Rect;
import java.util.ArrayList;
import java.util.List;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions;

/** 中文模型内置，识别后只创建草稿，不存在自动入账分支。 */
public final class LocalOcr {
    private LocalOcr() { }

    public static void recognize(Context context, InputImage image, Runnable release) {
        recognize(context, image, "分享或选择的图片", release);
    }

    public static void recognize(Context context, InputImage image, String source, Runnable release) {
        String readAt = java.time.LocalDateTime.now().toString();
        Context application = context.getApplicationContext();
        boolean amountOnly = RecognitionPreferences.isAmountOnly(application);
        TextRecognizer recognizer = TextRecognition.getClient(new ChineseTextRecognizerOptions.Builder().build());
        recognizer.process(image).addOnSuccessListener(result -> {
            LedgerEntry entry = extract(result, amountOnly);
            String warning = entry.getAmount().isBlank() ? RecognitionDrafts.MISSING_AMOUNT_MESSAGE : "";
            persistDraft(application, RecognitionDrafts.prepare(entry, source, readAt, warning));
        }).addOnFailureListener(exception -> {
            LedgerEntry entry = new LedgerEntry();
            persistDraft(application, RecognitionDrafts.prepare(entry, source, readAt,
                    "本地识别失败，请手工填写或重新分享截图。"));
        }).addOnCompleteListener(task -> {
            recognizer.close();
            release.run();
        });
    }

    /** 当前页面识别结果由调用方校验来源后保存；失败时保留本次页面信息生成待核对草稿。 */
    public static void recognizeCurrentPage(Context context, InputImage image,
            java.util.function.Consumer<LedgerEntry> success, Runnable failure, Runnable release) {
        boolean amountOnly = RecognitionPreferences.isAmountOnly(context);
        TextRecognizer recognizer = TextRecognition.getClient(new ChineseTextRecognizerOptions.Builder().build());
        recognizer.process(image).addOnSuccessListener(result -> {
                    CaptureDiagnostics.recordRecognizedText(context, result.getText());
                    success.accept(extract(result, amountOnly));
                })
                .addOnFailureListener(exception -> failure.run())
                .addOnCompleteListener(task -> {
                    recognizer.close();
                    release.run();
                });
    }

    public static LedgerEntry extract(Text result, boolean amountOnly) {
        List<ReceiptLine> lines = new ArrayList<>();
        for (Text.TextBlock block : result.getTextBlocks()) {
            for (Text.Line line : block.getLines()) {
                Rect box = line.getBoundingBox();
                if (box != null) {
                    lines.add(new ReceiptLine(line.getText(), box.left, box.top, box.right, box.bottom));
                }
            }
        }
        LedgerEntry entry = lines.isEmpty() ? ReceiptParser.parse(result.getText(), amountOnly)
                : ReceiptLayoutParser.parse(lines, amountOnly);
        return entry;
    }

    static void persistDraft(Context context, LedgerEntry entry) {
        try (LedgerDatabase database = new LedgerDatabase(context)) {
            database.save(entry);
        }
        DraftNotifications.show(context, entry);
    }
}
