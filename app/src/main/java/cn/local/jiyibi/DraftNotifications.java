package cn.local.jiyibi;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.widget.Toast;

/** 通知只提供核对入口，所有入账操作均在编辑页由用户明确确认。 */
public final class DraftNotifications {
    private static final String CHANNEL = "review_drafts";

    private DraftNotifications() { }

    public static void show(Context context, LedgerEntry entry) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel(CHANNEL, "待核对账目",
                NotificationManager.IMPORTANCE_HIGH));
        if ((Build.VERSION.SDK_INT >= 33
                && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                || !manager.areNotificationsEnabled()) {
            Toast.makeText(context, "已保存待核对草稿，请打开Ellery Ledger；通知权限尚未开启", Toast.LENGTH_LONG).show();
            return;
        }
        Intent intent = new Intent(context, MainActivity.class)
                .putExtra(MainActivity.EXTRA_ENTRY_ID, entry.getId())
                .setData(android.net.Uri.parse("jiyibi://draft/" + entry.getId()))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent edit = PendingIntent.getActivity(context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        String item = entry.item().isEmpty() ? "店铺／商品待补充" : entry.item();
        String amount = entry.getAmount().isEmpty() ? "金额待补充" : "¥" + entry.getAmount();
        String content = item + " · " + amount + "\n点击核对，补充商品与评价后确认入账";
        Notification notification = new Notification.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_ledger_scan)
                .setContentTitle("待核对，尚未入账")
                .setContentText(item + " · " + amount)
                .setStyle(new Notification.BigTextStyle().bigText(content))
                .setVisibility(Notification.VISIBILITY_PRIVATE)
                .setContentIntent(edit)
                .addAction(new Notification.Action.Builder(null, "核对并填写评价", edit).build())
                .setAutoCancel(true)
                .build();
        manager.notify(entry.getId(), 1, notification);
    }

    public static void cancel(Context context, String id) {
        context.getSystemService(NotificationManager.class).cancel(id, 1);
    }
}
