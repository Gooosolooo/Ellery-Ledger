package cn.local.jiyibi;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.provider.Settings;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

/** 单次动作按钮：已授权时读取当前页面，仅在用户选择设置时才启动设置页面。 */
public final class LedgerTileService extends TileService {
    @Override
    public void onStartListening() {
        super.onStartListening();
        Tile tile = getQsTile();
        if (tile != null) {
            tile.setIcon(Icon.createWithResource(this, R.drawable.ic_ledger_scan));
            tile.setLabel("识别当前账单");
            tile.setSubtitle(CaptureService.isAvailable() ? "点一次，识别一次" : "需先开启识别服务");
            tile.setState(Tile.STATE_INACTIVE);
            tile.updateTile();
        }
    }

    @Override
    public void onClick() {
        super.onClick();
        if (isLocked()) {
            unlockAndRun(this::captureCurrentScreen);
        } else {
            captureCurrentScreen();
        }
    }

    private void captureCurrentScreen() {
        if (!CaptureService.requestCaptureFromTile()) {
            AlertDialog dialog = new AlertDialog.Builder(this)
                    .setTitle("页面识别服务尚未启用")
                    .setMessage("请启用 Ellery Ledger 的辅助功能页面识别服务，再返回账单页面点击此按钮。")
                    .setPositiveButton("去开启", (view, which) -> openAccessibilitySettings())
                    .setNegativeButton("取消", null)
                    .create();
            showDialog(dialog);
        }
    }

    @SuppressWarnings("deprecation")
    @SuppressLint("StartActivityAndCollapseDeprecated") // 旧接口仅在 API 30–33 的用户明确选择“去开启”后调用。
    private void openAccessibilitySettings() {
        Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        } else {
            startActivityAndCollapse(intent);
        }
    }
}
