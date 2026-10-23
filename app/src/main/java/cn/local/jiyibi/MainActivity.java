package cn.local.jiyibi;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.StatusBarManager;
import android.content.ComponentName;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.graphics.drawable.Icon;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.drawable.GradientDrawable;
import android.content.res.ColorStateList;
import android.view.Gravity;
import android.view.ViewGroup;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.View;
import android.view.WindowInsets;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;
import com.google.mlkit.vision.common.InputImage;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 待核对列表、人工确认编辑页与本地备份入口。 */
public final class MainActivity extends Activity {
    public static final String EXTRA_ENTRY_ID = "entry_id";
    public static final String EXTRA_SHOW_CAPTURE_SETUP = "show_capture_setup";
    private static final int PICK_IMAGE = 10;
    private static final int EXPORT_BACKUP = 11;
    private static final int IMPORT_BACKUP = 12;
    private static final int EXPORT_EXCEL = 13;
    private static final int IMPORT_EXCEL = 14;
    private static final String[] HOME_TAB_LABELS = {"总览", "待核对", "全部账目", "设置"};
    private final ExecutorService fileExecutor = Executors.newSingleThreadExecutor();
    private LedgerDatabase database;
    private LinearLayout body;
    private LinearLayout pageRoot;
    private LinearLayout homeToolbar;
    private ScrollView pageScroll;
    private SourceTextPanel sourcePanel;
    private boolean sourceKeyboardRequested;
    private boolean sourceKeyboardVisible;
    private EditText sourceTarget;
    private EditText gestureTarget;
    private String sourceTargetLabel;
    private int insertionOffset;
    private LedgerEntry editing;
    private int activeTab;
    private EditText dateField;
    private EditText shopField;
    private EditText productField;
    private EditText amountField;
    private EditText noteField;
    private Spinner categoryField;
    private final List<String> categoryOptions = new ArrayList<>();
    private final List<Button> homeTabButtons = new ArrayList<>();
    private final List<Switch> recognitionSwitches = new ArrayList<>();
    private ViewPager homePager;
    private LedgerFilter ledgerFilter = new LedgerFilter("", null, "");

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        database = new LedgerDatabase(this);
        activeTab = state == null ? 0 : state.getInt("active_tab", 0);
        if (state != null) {
            ledgerFilter = new LedgerFilter(state.getString("ledger_filter_date", ""),
                    state.getString("ledger_filter_category"), state.getString("ledger_filter_keyword", ""));
        }
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
        if (state != null && state.getString(EXTRA_ENTRY_ID) != null) {
            LedgerEntry entry = database.find(state.getString(EXTRA_ENTRY_ID));
            if (entry != null) {
                showEditor(entry);
                return;
            }
        }
        acceptIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        persistEditing();
        setIntent(intent);
        acceptIntent(intent);
    }

    private void acceptIntent(Intent intent) {
        String id = intent.getStringExtra(EXTRA_ENTRY_ID);
        if (id != null) {
            LedgerEntry entry = database.find(id);
            if (entry != null) {
                showEditor(entry);
                return;
            }
        }
        showHome();
        if (intent.getBooleanExtra(EXTRA_SHOW_CAPTURE_SETUP, false)) {
            intent.removeExtra(EXTRA_SHOW_CAPTURE_SETUP);
            showCaptureSetup();
        }
        if (Intent.ACTION_SEND.equals(intent.getAction()) && intent.getType() != null
                && intent.getType().startsWith("image/")) {
            Uri uri = Build.VERSION.SDK_INT >= 33
                    ? intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri.class)
                    : intent.getParcelableExtra(Intent.EXTRA_STREAM);
            if (uri != null) {
                recognizeImage(uri);
            }
            intent.setAction(null);
        }
    }

    private void resetPage(String title) {
        homePager = null;
        homeTabButtons.clear();
        recognitionSwitches.clear();
        sourcePanel = null;
        sourceTarget = null;
        gestureTarget = null;
        LinearLayout root = BlueUi.column(this);
        pageRoot = root;
        root.addOnLayoutChangeListener((view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> resizeSourcePanel());
        root.setBackgroundColor(BlueUi.BACKGROUND);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.ime());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            boolean keyboardVisible = insets.isVisible(WindowInsets.Type.ime());
            if (sourcePanel != null) {
                if (sourceKeyboardVisible && !keyboardVisible) {
                    sourceKeyboardRequested = false;
                    setEditorSoftInputAllowed(false);
                }
                sourceKeyboardVisible = keyboardVisible;
                sourcePanel.setKeyboardVisible(keyboardVisible);
            }
            view.post(this::resizeSourcePanel);
            return insets;
        });
        LinearLayout toolbar = BlueUi.row(this);
        homeToolbar = toolbar;
        toolbar.setPadding(dp(20), dp(12), dp(16), dp(10));
        if (editing != null) {
            Button back = BlueUi.button(this, "返回", false, () -> {
                persistEditing();
                showHome();
            });
            toolbar.addView(back, new LinearLayout.LayoutParams(dp(62), dp(48)));
        }
        TextView heading = BlueUi.text(this, title, editing == null ? 24 : 22, BlueUi.INK, true);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        titleParams.setMarginStart(editing == null ? 0 : dp(12));
        toolbar.addView(heading, titleParams);
        if (editing == null) {
            Button guide = BlueUi.button(this, "教程", false, this::showCaptureSetup);
            toolbar.addView(guide, new LinearLayout.LayoutParams(dp(62), dp(48)));
        }
        root.addView(toolbar);
        if (editing == null) {
            addTabs(root);
        }
        ScrollView scroll = new ScrollView(this);
        pageScroll = scroll;
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setVerticalScrollBarEnabled(false);
        body = BlueUi.column(this);
        body.setPadding(dp(16), dp(8), dp(16), dp(28));
        scroll.addView(body);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
    }

    private void addTabs(LinearLayout root) {
        LinearLayout tabs = BlueUi.row(this);
        tabs.setPadding(dp(12), 0, dp(12), dp(10));
        String[] labels = HOME_TAB_LABELS;
        for (int index = 0; index < labels.length; index++) {
            final int selectedTab = index;
            Button tab = BlueUi.button(this, labels[index], activeTab == index, () -> {
                if (homePager != null) {
                    homePager.setCurrentItem(selectedTab, true);
                }
            });
            homeTabButtons.add(tab);
            tab.setSelected(activeTab == index);
            tab.setTextSize(13);
            tab.setPadding(dp(5), dp(8), dp(5), dp(8));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(44), 1f);
            params.setMargins(dp(4), 0, dp(4), 0);
            tabs.addView(tab, params);
        }
        root.addView(tabs);
    }

    private void showHome() {
        editing = null;
        resetPage("Ellery Ledger");
        List<LedgerEntry> entries = database.list();
        List<ScrollView> pages = new ArrayList<>();
        pageRoot.removeView(pageScroll);
        for (int index = 0; index < HOME_TAB_LABELS.length; index++) {
            ScrollView scroll = new ScrollView(this);
            scroll.setFillViewport(true);
            scroll.setClipToPadding(false);
            scroll.setVerticalScrollBarEnabled(false);
            scroll.setContentDescription(HOME_TAB_LABELS[index]);
            body = BlueUi.column(this);
            body.setPadding(dp(16), dp(8), dp(16), dp(28));
            scroll.addView(body);
            pages.add(scroll);
            renderHomePage(index, entries);
            PageTheme theme = PageTheme.forTab(index);
            theme.apply(body);
            scroll.setBackgroundColor(theme.backgroundColor);
        }
        homePager = new ViewPager(this);
        homePager.setId(View.generateViewId());
        // 保留页面实例，使纵向滚动、筛选输入和标签滚动状态不随切页重置。
        homePager.setOffscreenPageLimit(HOME_TAB_LABELS.length - 1);
        homePager.setAdapter(new PagerAdapter() {
            @Override
            public int getCount() {
                return pages.size();
            }

            @Override
            public boolean isViewFromObject(View view, Object object) {
                return view == object;
            }

            @Override
            public Object instantiateItem(ViewGroup container, int position) {
                ScrollView page = pages.get(position);
                container.addView(page);
                return page;
            }

            @Override
            public void destroyItem(ViewGroup container, int position, Object object) {
                container.removeView((View) object);
            }

            @Override
            public CharSequence getPageTitle(int position) {
                return HOME_TAB_LABELS[position];
            }
        });
        ViewPager currentPager = homePager;
        homePager.addOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override
            public void onPageSelected(int position) {
                if (editing != null || homePager != currentPager) {
                    return;
                }
                activeTab = position;
                pageScroll = pages.get(position);
                body = (LinearLayout) pageScroll.getChildAt(0);
                updateHomeTabs();
                InputMethodManager inputMethod = getSystemService(InputMethodManager.class);
                if (inputMethod != null) {
                    inputMethod.hideSoftInputFromWindow(homePager.getWindowToken(), 0);
                }
            }
        });
        pageRoot.addView(homePager, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        activeTab = Math.max(0, Math.min(activeTab, HOME_TAB_LABELS.length - 1));
        homePager.setCurrentItem(activeTab, false);
        pageScroll = pages.get(activeTab);
        body = (LinearLayout) pageScroll.getChildAt(0);
        updateHomeTabs();
    }

    private void updateHomeTabs() {
        PageTheme currentTheme = PageTheme.forTab(activeTab);
        pageRoot.setBackgroundColor(currentTheme.backgroundColor);
        currentTheme.apply(homeToolbar);
        for (int index = 0; index < homeTabButtons.size(); index++) {
            Button tab = homeTabButtons.get(index);
            boolean selected = index == activeTab;
            tab.setSelected(selected);
            PageTheme.forTab(index).styleButton(tab, selected);
        }
    }

    private void renderHomePage(int tabIndex, List<LedgerEntry> entries) {
        if (tabIndex == 3) {
            showSettings();
            return;
        }
        if (tabIndex == 2) {
            text("全部账目", 20);
            body.addView(new LedgerBrowserView(this, entries, ledgerFilter, this::addEntryCard,
                    filter -> ledgerFilter = filter), BlueUi.below(this, 0));
            return;
        }
        if (tabIndex == 0) {
            showOverview(entries);
        } else {
            text(tabIndex == 1 ? "待核对账目" : "全部账目", 20);
            text(tabIndex == 1 ? "补充商品和评价，确认后入账" : "点开一笔账目，查看或编辑记录", 14);
        }
        int displayed = 0;
        for (LedgerEntry entry : entries) {
            if (tabIndex == 1 && !LedgerEntry.DRAFT.equals(entry.getStatus())) {
                continue;
            }
            if (tabIndex == 0 && displayed >= 5) {
                break;
            }
            addEntryCard(entry);
            displayed++;
        }
        if (displayed == 0) {
            LinearLayout empty = BlueUi.card(this);
            BlueUi.appendText(empty, tabIndex == 1 ? "暂时没有待核对账目" : "从第一笔开始", 18, BlueUi.DEEP_BLUE, true, 0);
            BlueUi.appendText(empty, "选一张支付截图，或手工记一笔。识别结果会先保存在待核对列表。", 14, BlueUi.SUB, false, 10);
            body.addView(empty, BlueUi.below(this, 12));
        }
    }

    private void showOverview(List<LedgerEntry> entries) {
        BigDecimal total = BigDecimal.ZERO;
        int drafts = 0;
        for (LedgerEntry entry : entries) {
            if (LedgerEntry.DRAFT.equals(entry.getStatus())) {
                drafts++;
            } else if (entry.getDate().startsWith(YearMonth.now().toString())) {
                total = total.add(new BigDecimal(entry.getAmount()));
            }
        }
        LinearLayout summary = BlueUi.card(this);
        BlueUi.appendText(summary, YearMonth.now().getMonthValue() + " 月支出", 14, BlueUi.SUB, false, 0);
        BlueUi.appendText(summary, "¥ " + total.setScale(2).toPlainString(), 36, BlueUi.DEEP_BLUE, true, 12);
        BlueUi.appendText(summary, "待核对 " + drafts + " 笔  ·  确认后计入支出", 13, BlueUi.SUB, false, 12);
        body.addView(summary, BlueUi.below(this, 4));
        body.addView(BlueUi.button(this, "选截图识别", true, () -> pick("image/*", PICK_IMAGE)), BlueUi.below(this, 16));
        body.addView(BlueUi.button(this, "从剪贴板识别图片", false, this::recognizeClipboardImage), BlueUi.below(this, 10));
        LinearLayout actions = BlueUi.row(this);
        Button manual = BlueUi.button(this, "手工记账", false, this::newManualEntry);
        Button tutorial = BlueUi.button(this, "识别教程", false, this::showCaptureSetup);
        LinearLayout.LayoutParams left = new LinearLayout.LayoutParams(0, dp(48), 1f);
        left.setMarginEnd(dp(6));
        LinearLayout.LayoutParams right = new LinearLayout.LayoutParams(0, dp(48), 1f);
        right.setMarginStart(dp(6));
        actions.addView(manual, left);
        actions.addView(tutorial, right);
        body.addView(actions, BlueUi.below(this, 10));
        addRecognitionMode();
        text("最近账目", 20);
    }

    private void newManualEntry() {
        LedgerEntry entry = new LedgerEntry();
        entry.setDate(LocalDate.now().toString());
        database.save(entry);
        showEditor(entry);
    }

    private void addRecognitionMode() {
        LinearLayout mode = BlueUi.card(this);
        Switch amountOnlySwitch = new Switch(this);
        amountOnlySwitch.setText("仅识别金额");
        amountOnlySwitch.setTextSize(15);
        amountOnlySwitch.setTextColor(BlueUi.INK);
        amountOnlySwitch.setMinHeight(dp(48));
        amountOnlySwitch.setChecked(RecognitionPreferences.isAmountOnly(this));
        amountOnlySwitch.setThumbTintList(ColorStateList.valueOf(BlueUi.CARD));
        amountOnlySwitch.setTrackTintList(new ColorStateList(
                new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
                new int[]{BlueUi.BLUE, BlueUi.BORDER}));
        recognitionSwitches.add(amountOnlySwitch);
        amountOnlySwitch.setOnCheckedChangeListener((view, enabled) -> {
            RecognitionPreferences.setAmountOnly(this, enabled);
            for (Switch modeSwitch : recognitionSwitches) {
                if (modeSwitch.isChecked() != enabled) {
                    modeSwitch.setChecked(enabled);
                }
            }
        });
        mode.addView(amountOnlySwitch);
        BlueUi.appendText(mode, "关闭时同时尝试识别店铺、商品和日期。评价由你填写。", 13, BlueUi.SUB, false, 4);
        body.addView(mode, BlueUi.below(this, 16));
    }

    private void addEntryCard(LedgerEntry entry) {
        addEntryCard(entry, body);
    }

    private void addEntryCard(LedgerEntry entry, LinearLayout container) {
        LinearLayout card = BlueUi.card(this);
        LinearLayout heading = BlueUi.row(this);
        String date = entry.getDate().isEmpty() ? "日期待补充" : entry.getDate();
        String category = entry.getCategory().isEmpty() ? "类别待选择" : entry.getCategory();
        LinearLayout metadata = BlueUi.column(this);
        LinearLayout tags = BlueUi.row(this);
        tags.addView(BlueUi.tag(this, date, BlueUi.DATE_TAG_BACKGROUND, BlueUi.DATE_TAG_TEXT));
        LinearLayout.LayoutParams categoryParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        categoryParams.setMarginStart(dp(6));
        tags.addView(BlueUi.categoryTag(this, entry.getCategory()), categoryParams);
        // 空间不足时整行标签可水平浏览，避免挤压右侧金额。
        android.widget.HorizontalScrollView tagScroll = new android.widget.HorizontalScrollView(this);
        tagScroll.setHorizontalScrollBarEnabled(false);
        tagScroll.addView(tags);
        metadata.addView(tagScroll);
        if (LedgerEntry.DRAFT.equals(entry.getStatus())) {
            LinearLayout.LayoutParams draftParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            draftParams.topMargin = dp(6);
            metadata.addView(BlueUi.tag(this, "待核对", BlueUi.DRAFT_TAG_BACKGROUND,
                    BlueUi.DRAFT_TAG_TEXT), draftParams);
        }
        heading.addView(metadata, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView amount = BlueUi.text(this, moneyLabel(entry.getAmount()), 20, BlueUi.DEEP_BLUE, true);
        amount.setGravity(Gravity.END);
        amount.setPadding(dp(12), 0, 0, 0);
        heading.addView(amount);
        card.addView(heading);
        // 仅格式化列表展示，商品原文继续保留换行，供编辑和导出使用。
        String product = entry.getProduct().trim().replaceAll("(?:\\s*[\\r\\n]+\\s*)+", " + ");
        String shop = entry.getShop().trim();
        String title;
        if (shop.isEmpty()) {
            title = product.isEmpty() ? "店铺／商品待补充" : product;
        } else {
            title = product.isEmpty() ? shop : shop + ": " + product;
        }
        BlueUi.appendText(card, title, 17, BlueUi.INK, true, 12);
        if (!entry.getNote().trim().isEmpty()) {
            BlueUi.appendText(card, "评价：" + entry.getNote(), 14, BlueUi.SUB, false, 8);
        }
        card.setContentDescription("查看账目：" + title + "，" + date + "，" + category
                + "，" + moneyLabel(entry.getAmount()));
        card.setOnClickListener(view -> showEditor(entry));
        card.setFocusable(true);
        container.addView(card, BlueUi.below(this, 12));
    }

    private static String moneyLabel(String value) {
        if (value.isEmpty()) {
            return "金额待补充";
        }
        try {
            return "¥ " + new BigDecimal(value).setScale(2).toPlainString();
        } catch (ArithmeticException | NumberFormatException exception) {
            return "金额待核对";
        }
    }

    private void showSettings() {
        text("识别与权限", 20);
        addRecognitionMode();
        button("页面识别权限与使用教程", this::showCaptureSetup);
        button("最近一次识别诊断", () -> new AlertDialog.Builder(this)
                .setTitle("最近一次识别诊断").setMessage(CaptureDiagnostics.read(this))
                .setNeutralButton("本次 OCR 原文", (dialog, which) -> new AlertDialog.Builder(this)
                        .setTitle("仅本机保存的最近一次 OCR 文字")
                        .setMessage(CaptureDiagnostics.readRecognizedText(this))
                        .setPositiveButton("关闭", null).show())
                .setPositiveButton("关闭", null).show());
        button("添加“识别当前账单”到快捷面板", this::requestQuickSettingsTile);
        text("备份与导入", 20);
        text("Excel 用于查看，完整备份可恢复草稿、商品和评价。", 14);
        button("导出完整备份", () -> create("application/json", "ellery-ledger-backup-" + LocalDate.now() + ".json", EXPORT_BACKUP));
        button("恢复完整备份", () -> pick("application/json", IMPORT_BACKUP));
        button("导入 Excel 明细", () -> pick("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", IMPORT_EXCEL));
        button("导出 Excel 明细", () -> create("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "ellery-ledger-" + LocalDate.now() + ".xlsx", EXPORT_EXCEL));
        text("数据管理", 20);
        button("清空已确认账单", this::showClearConfirmedDialog);
        text("Ellery Ledger · 本地识别版", 13);
    }

    private void showClearConfirmedDialog() {
        long count = database.countConfirmed();
        if (count == 0) {
            message("没有可清空的已确认账单，待核对草稿已保留");
            return;
        }
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("清空已确认账单？")
                .setMessage("将删除全部 " + count + " 笔已确认账单，删除后无法撤销，建议先导出备份。\n\n"
                        + "待核对草稿、App 设置和已导出的备份文件都会保留。")
                .setNegativeButton("取消", null)
                .setPositiveButton("确认清空", (view, which) -> {
                    try {
                        int deleted = database.clearConfirmed();
                        showHome();
                        message("已清空 " + deleted + " 笔已确认账单，待核对草稿已保留");
                    } catch (android.database.SQLException exception) {
                        message("清空失败，请稍后重试");
                    }
                }).create();
        dialog.setOnShowListener(view -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(BlueUi.DANGER);
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).requestFocus();
        });
        dialog.show();
    }

    private void requestQuickSettingsTile() {
        if (Build.VERSION.SDK_INT < 33) {
            message("请在快捷面板的编辑页面添加“识别当前账单”");
            return;
        }
        StatusBarManager manager = getSystemService(StatusBarManager.class);
        manager.requestAddTileService(new ComponentName(this, LedgerTileService.class), "识别当前账单",
                Icon.createWithResource(this, R.drawable.ic_ledger_scan), getMainExecutor(), result -> {
                    if (result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED) {
                        message("已添加“识别当前账单”到快捷面板");
                    } else if (result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED) {
                        message("按钮已在面板中，可通过面板编辑调整到前面");
                    } else if (result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_NOT_ADDED) {
                        message("本次未添加，可稍后重试");
                    } else {
                        message("系统暂时无法添加，请使用快捷面板的编辑功能");
                    }
                });
    }

    private void showCaptureSetup() {
        String state = CaptureService.isAvailable() ? "页面识别服务：已启用。" : "页面识别服务：尚未启用，请先完成第 1 步。";
        new AlertDialog.Builder(this).setTitle("怎样识别当前账单")
                .setMessage(state + "\n\n"
                        + "1. 打开系统辅助功能 → 已安装的应用程序 → Ellery Ledger：允许手动读取页面及截图，启用服务。更新此版本后，请关闭再开启一次服务。\n\n"
                        + "2. 返回支付成功或账单详情页面。下拉手机快捷面板，点“识别当前账单”。每点一次识别一笔。\n\n"
                        + "3. 等待“待核对”通知，点开补充商品与评价，再确认入账。\n\n"
                        + "也可复制截图后打开本 App，点首页的“从剪贴板识别图片”。只读取系统提供的当前图片，不另存截图；若系统不提供图片，可从截图预览分享给 Ellery Ledger。\n\n"
                        + "快捷面板找不到按钮时，点编辑并添加“识别当前账单”。也可以直接选截图识别。")
                .setPositiveButton("打开无障碍设置", (dialog, which) -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)))
                .setNeutralButton("通知设置", (dialog, which) -> startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName())))
                .setNegativeButton("关闭", null).show();
    }

    private void showEditor(LedgerEntry entry) {
        editing = entry;
        resetPage(LedgerEntry.DRAFT.equals(entry.getStatus()) ? "核对这笔账" : "编辑账目");
        text("核对识别结果，补充商品和你的使用评价。", 14);
        LinearLayout page = body;
        LinearLayout form = BlueUi.card(this);
        page.addView(form, BlueUi.below(this, 12));
        body = form;
        dateField = field("日期（YYYY-MM-DD）", entry.getDate(), false);
        shopField = field("店铺／品牌（未识别可手工补充）", entry.getShop(), false);
        productField = field("商品名称（多件可逐行填写）", entry.getProduct(), true);
        amountField = field("实付金额（人民币支出）", entry.getAmount(), false);
        amountField.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        text("类别", 16);
        categoryOptions.clear();
        categoryOptions.addAll(Arrays.asList(LedgerEntry.CATEGORIES));
        if (!categoryOptions.contains(entry.getCategory())) {
            categoryOptions.add(entry.getCategory());
        }
        List<String> display = new ArrayList<>(categoryOptions);
        display.set(0, "请选择类别");
        categoryField = new Spinner(this);
        categoryField.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, display));
        categoryField.setSelection(categoryOptions.indexOf(entry.getCategory()));
        categoryField.setMinimumHeight(dp(48));
        categoryField.setBackground(BlueUi.round(this, BlueUi.PALE_BLUE, 12));
        body.addView(categoryField, BlueUi.below(this, 6));
        noteField = field("备注／评价（选填，例如口味、体验、是否回购）", entry.getNote(), true);
        body = page;
        text("事项按店铺／品牌与商品组合保存，评价可留空。", 13);
        if (!entry.getSourceText().isEmpty()) {
            button("查看本地识别原文", () -> showSourcePanel(entry.getSourceText()));
        }
        button("确认并保存账目", () -> {
            readFields();
            String error = entry.validationError();
            if (!error.isEmpty()) {
                message(error);
                return;
            }
            confirmEditing();
        });
        button(LedgerEntry.DRAFT.equals(entry.getStatus()) ? "保存草稿并返回" : "返回（不保存修改）", () -> {
            persistEditing();
            showHome();
        });
        button(LedgerEntry.DRAFT.equals(entry.getStatus()) ? "丢弃这笔草稿" : "删除这笔账目", () ->
                new AlertDialog.Builder(this).setTitle("确认删除？").setMessage("删除后仅能从你之前导出的备份恢复。")
                        .setPositiveButton("删除", (dialog, which) -> {
                            database.delete(entry.getId());
                            DraftNotifications.cancel(this, entry.getId());
                            editing = null;
                            showHome();
                        }).setNegativeButton("取消", null).show());
    }

    private void showSourcePanel(String source) {
        if (sourcePanel != null) {
            return;
        }
        sourceKeyboardRequested = false;
        sourceKeyboardVisible = false;
        setEditorSoftInputAllowed(false);
        InputMethodManager inputMethod = getSystemService(InputMethodManager.class);
        if (inputMethod != null) {
            inputMethod.hideSoftInputFromWindow(pageRoot.getWindowToken(), 0);
        }
        sourcePanel = new SourceTextPanel(this, source, new SourceSelectionView.Listener() {
            @Override
            public void onSelectionStarted() {
                // 原文视图不取得输入焦点，保留用户上一次点击字段的光标位置。
                gestureTarget = sourceTarget;
                insertionOffset = gestureTarget == null ? 0 : Math.max(0, gestureTarget.getSelectionEnd());
            }

            @Override
            public void onSelectionFinished(String selected) {
                insertSelectedSource(selected);
            }
        }, this::closeSourcePanel, this::toggleSourceKeyboard);
        pageRoot.addView(sourcePanel, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(300)));
        if (sourceTarget != null) {
            sourcePanel.setTargetHint("当前填入：" + sourceTargetLabel + " · 按住原文拖选，松手插入光标处");
        }
        resizeSourcePanel();
        SourceTextPanel openedPanel = sourcePanel;
        openedPanel.setTranslationY(dp(300));
        openedPanel.animate().translationY(0).setDuration(220L).start();
        pageScroll.post(() -> {
            if (sourceTarget == null) {
                pageScroll.smoothScrollTo(0, 0);
            } else {
                android.graphics.Rect bounds = new android.graphics.Rect();
                sourceTarget.getDrawingRect(bounds);
                sourceTarget.requestRectangleOnScreen(bounds, false);
            }
        });
    }

    private void resizeSourcePanel() {
        if (sourcePanel == null || pageRoot.getHeight() == 0) {
            return;
        }
        int usableHeight = pageRoot.getHeight() - pageRoot.getPaddingTop() - pageRoot.getPaddingBottom();
        int height = Math.min(dp(320), Math.max(dp(180), usableHeight * 45 / 100));
        if (sourcePanel.getLayoutParams().height != height) {
            sourcePanel.getLayoutParams().height = height;
            sourcePanel.requestLayout();
        }
    }

    private void closeSourcePanel() {
        if (sourcePanel != null) {
            sourcePanel.animate().cancel();
            pageRoot.removeView(sourcePanel);
            sourcePanel = null;
            gestureTarget = null;
            sourceKeyboardRequested = false;
            sourceKeyboardVisible = false;
            setEditorSoftInputAllowed(true);
        }
    }

    private void setEditorSoftInputAllowed(boolean allowed) {
        EditText[] fields = {dateField, shopField, productField, amountField, noteField};
        for (EditText input : fields) {
            if (input != null) {
                input.setShowSoftInputOnFocus(allowed);
            }
        }
    }

    private void toggleSourceKeyboard() {
        if (sourcePanel == null) {
            return;
        }
        InputMethodManager inputMethod = getSystemService(InputMethodManager.class);
        if (inputMethod == null) {
            return;
        }
        if (sourceKeyboardVisible || sourceKeyboardRequested) {
            sourceKeyboardRequested = false;
            setEditorSoftInputAllowed(false);
            inputMethod.hideSoftInputFromWindow(pageRoot.getWindowToken(), 0);
            return;
        }
        EditText target = sourceTarget;
        if (target == null || !target.isAttachedToWindow()) {
            message("请先点击要手动输入的字段");
            return;
        }
        sourceKeyboardRequested = true;
        setEditorSoftInputAllowed(true);
        target.requestFocus();
        target.post(() -> {
            if (sourcePanel != null && sourceKeyboardRequested && target.isAttachedToWindow()) {
                inputMethod.showSoftInput(target, InputMethodManager.SHOW_IMPLICIT);
            }
        });
    }

    private void selectSourceTarget(EditText input, String label) {
        sourceTarget = input;
        sourceTargetLabel = label;
        if (sourcePanel != null && !sourceKeyboardRequested) {
            input.setShowSoftInputOnFocus(false);
        }
        if (sourcePanel != null) {
            sourcePanel.setTargetHint("当前填入：" + label + " · 按住原文拖选，松手插入光标处");
        }
    }

    private void insertSelectedSource(String selected) {
        EditText target = gestureTarget;
        gestureTarget = null;
        if (sourcePanel == null || target == null || !target.isAttachedToWindow()) {
            message("请先点击上方要填写的输入框，再从原文选取文字");
            return;
        }
        android.text.Editable text = target.getText();
        int offset = Math.min(insertionOffset, text.length());
        int oldLength = text.length();
        // 只插入，不替换已有文字或自动确认账目；输入框原有字符限制继续生效。
        text.insert(offset, selected);
        int insertedLength = text.length() - oldLength;
        target.setSelection(Math.min(offset + insertedLength, text.length()));
        if (insertedLength == 0) {
            sourcePanel.setTargetHint("选中文字不符合当前字段格式，请换一个字段或重新选取");
        } else {
            sourcePanel.setTargetHint("已填入「" + sourceTargetLabel + "」；可继续选取，或点其他字段切换");
        }
    }

    private void confirmEditing() {
        if (editing == null) {
            return;
        }
        editing.setStatus(LedgerEntry.CONFIRMED);
        database.save(editing);
        DraftNotifications.cancel(this, editing.getId());
        editing = null;
        showHome();
        message("已入账");
    }

    private void readFields() {
        editing.setDate(dateField.getText().toString().trim());
        editing.setShop(shopField.getText().toString().trim());
        editing.setProduct(productField.getText().toString().trim());
        editing.setAmount(amountField.getText().toString().trim());
        editing.setNote(noteField.getText().toString());
        editing.setCategory(categoryOptions.get(categoryField.getSelectedItemPosition()));
    }

    private void persistEditing() {
        if (editing != null) {
            readFields();
            // 已确认账目只允许通过确认按钮更改；自动保存仅用于未入账草稿。
            if (LedgerEntry.DRAFT.equals(editing.getStatus())) {
                database.save(editing);
            }
        }
    }

    @Override
    protected void onPause() {
        persistEditing();
        super.onPause();
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        persistEditing();
        if (editing != null) {
            state.putString(EXTRA_ENTRY_ID, editing.getId());
        }
        state.putInt("active_tab", activeTab);
        state.putString("ledger_filter_date", ledgerFilter.getDatePrefix());
        state.putString("ledger_filter_category", ledgerFilter.getCategory());
        state.putString("ledger_filter_keyword", ledgerFilter.getKeyword());
        super.onSaveInstanceState(state);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (sourcePanel != null) {
            closeSourcePanel();
            return;
        }
        if (editing != null) {
            persistEditing();
            showHome();
        } else {
            super.onBackPressed();
        }
    }

    private void pick(String mimeType, int request) {
        startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType(mimeType)
                .addCategory(Intent.CATEGORY_OPENABLE), request);
    }

    private void create(String mimeType, String name, int request) {
        startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).setType(mimeType)
                .addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE, name), request);
    }

    @Override
    protected void onActivityResult(int request, int result, Intent intent) {
        super.onActivityResult(request, result, intent);
        if (result != RESULT_OK || intent == null || intent.getData() == null) {
            return;
        }
        Uri uri = intent.getData();
        if (request == PICK_IMAGE) {
            recognizeImage(uri);
            return;
        }
        List<LedgerEntry> snapshot = database.list();
        fileExecutor.execute(() -> {
            try {
                if (request == EXPORT_BACKUP || request == EXPORT_EXCEL) {
                    byte[] bytes = request == EXPORT_BACKUP ? BackupCodec.encode(snapshot) : ExcelTransfer.write(snapshot);
                    try (OutputStream output = getContentResolver().openOutputStream(uri, "wt")) {
                        if (output == null) {
                            throw new java.io.IOException("无法写入所选位置");
                        }
                        output.write(bytes);
                        output.flush();
                    }
                    runOnUiThread(() -> message("导出完成，请将备份另存到手机以外的位置"));
                } else {
                    byte[] bytes;
                    try (InputStream input = getContentResolver().openInputStream(uri)) {
                        bytes = ExcelTransfer.readLimited(input, ExcelTransfer.MAX_FILE_BYTES);
                    }
                    if (request == IMPORT_BACKUP) {
                        List<LedgerEntry> entries = BackupCodec.decode(bytes);
                        runOnUiThread(() -> previewImport(entries, "完整备份；含已确认账目与待核对草稿。"));
                    } else if (request == IMPORT_EXCEL) {
                        ExcelTransfer.ImportResult parsed = ExcelTransfer.read(bytes);
                        runOnUiThread(() -> previewImport(parsed.getEntries(),
                                "支持新六列及旧五列明细；旧事项保留为商品名称。无效或缺失金额、日期等的行已跳过："
                                        + (parsed.getSkippedRows().isEmpty() ? "无" : parsed.getSkippedRows().toString())));
                    }
                }
            } catch (Exception exception) {
                runOnUiThread(() -> new AlertDialog.Builder(this).setTitle("操作未完成")
                        .setMessage("账目库保持不变。请检查文件、保存位置或可用空间。\n" + exception.getMessage())
                        .setPositiveButton("关闭", null).show());
            }
        });
    }

    private void previewImport(List<LedgerEntry> entries, String explanation) {
        StringBuilder preview = new StringBuilder(explanation).append("\n\n共 ").append(entries.size()).append(" 条候选记录。\n");
        for (int index = 0; index < Math.min(entries.size(), 5); index++) {
            LedgerEntry entry = entries.get(index);
            preview.append(entry.getDate()).append(" ").append(entry.item()).append(" ¥").append(entry.getAmount()).append("\n");
        }
        preview.append("\n相同记录标识将跳过，已有账目不会被覆盖。Excel 导入按内容和原行号识别重复；重新排序的文件需人工检查重复。");
        new AlertDialog.Builder(this).setTitle("核对导入内容").setMessage(preview.toString())
                .setPositiveButton("确认导入", (dialog, which) -> {
                    int inserted = database.merge(entries);
                    showHome();
                    message("新增 " + inserted + " 条，跳过已有标识 " + (entries.size() - inserted) + " 条");
                }).setNegativeButton("取消", null).show();
    }

    /** 仅在用户点击按钮时读取一次剪贴板，不订阅剪贴板变化。 */
    private void recognizeClipboardImage() {
        ClipboardManager clipboard = getSystemService(ClipboardManager.class);
        ClipData clip;
        try {
            clip = clipboard == null ? null : clipboard.getPrimaryClip();
        } catch (SecurityException exception) {
            message("系统未允许读取剪贴板，请保持 App 在前台后重试");
            return;
        }
        if (clip == null || clip.getItemCount() == 0) {
            message("剪贴板为空或系统没有提供图片，请先复制截图再点击此按钮");
            return;
        }
        List<Uri> candidates = new ArrayList<>();
        for (int index = 0; index < clip.getItemCount(); index++) {
            Uri uri = clip.getItemAt(index).getUri();
            if (uri != null && "content".equals(uri.getScheme())) {
                candidates.add(uri);
            }
        }
        if (candidates.isEmpty()) {
            message("剪贴板没有可读取的图片。若三星剪贴板中能看到图片，请用截图预览的分享功能发送给 Ellery Ledger");
            return;
        }
        boolean imageAdvertised = clip.getDescription().hasMimeType("image/*");
        fileExecutor.execute(() -> {
            for (Uri candidate : candidates) {
                try {
                    String mimeType = getContentResolver().getType(candidate);
                    if ((mimeType != null && mimeType.startsWith("image/"))
                            || (mimeType == null && imageAdvertised)) {
                        runOnUiThread(() -> {
                            if (!isFinishing() && !isDestroyed()) {
                                recognizeImage(candidate, "剪贴板图片");
                            }
                        });
                        return;
                    }
                } catch (SecurityException exception) {
                    // 当前图片可能已过期；继续检查本次剪贴板中的其他图片。
                }
            }
            runOnUiThread(() -> message("剪贴板图片无法读取或已失效，请重新复制，或从截图预览分享给 Ellery Ledger"));
        });
    }

    private void recognizeImage(Uri uri) {
        recognizeImage(uri, "选择或分享的图片");
    }

    private void recognizeImage(Uri uri, String source) {
        activeTab = 1;
        String readAt = java.time.LocalDateTime.now().toString();
        message("正在本地识别，完成后会产生待核对通知");
        fileExecutor.execute(() -> {
            try {
                InputImage image = InputImage.fromFilePath(getApplicationContext(), uri);
                runOnUiThread(() -> LocalOcr.recognize(this, image, source, () -> {
                    if (!isFinishing() && !isDestroyed() && editing == null) {
                        showHome();
                    }
                }));
            } catch (Exception exception) {
                runOnUiThread(() -> {
                    LocalOcr.persistDraft(getApplicationContext(), RecognitionDrafts.prepare(new LedgerEntry(),
                            source, readAt, "图片无法打开或读取权限已失效，请手工补充或重新分享截图。"));
                    if (!isFinishing() && !isDestroyed() && editing == null) {
                        showHome();
                    }
                    message("图片无法读取，已保存待核对草稿，请手工补充或重新分享截图");
                });
            }
        });
    }

    private EditText field(String label, String value, boolean multiline) {
        text(label, 13);
        EditText input = new EditText(this);
        input.setText(value);
        String targetLabel = label.contains("（") ? label.substring(0, label.indexOf('（')) : label;
        input.setOnClickListener(view -> selectSourceTarget(input, targetLabel));
        input.setOnFocusChangeListener((view, focused) -> {
            if (focused && sourceTarget != null) {
                selectSourceTarget(input, targetLabel);
            }
        });
        input.setHint("待补充");
        input.setTextSize(16);
        input.setTextColor(BlueUi.INK);
        input.setHintTextColor(BlueUi.SUB);
        input.setSingleLine(!multiline);
        input.setMinHeight(dp(48));
        input.setPadding(dp(12), dp(12), dp(12), dp(12));
        GradientDrawable background = BlueUi.round(this, BlueUi.CARD, 12);
        background.setStroke(dp(1), BlueUi.BORDER);
        input.setBackground(background);
        input.setBackgroundTintList(null);
        if (multiline) {
            input.setMinLines(2);
            input.setGravity(Gravity.TOP | Gravity.START);
            input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        }
        body.addView(input, BlueUi.below(this, 4));
        return input;
    }

    private void text(String value, int size) {
        TextView label = BlueUi.text(this, value, size, size >= 18 ? BlueUi.DEEP_BLUE : BlueUi.SUB, size >= 18);
        body.addView(label, BlueUi.below(this, size >= 18 ? 22 : 12));
    }

    private void button(String label, Runnable action) {
        Button button = BlueUi.button(this, label, "确认并保存账目".equals(label), action);
        if (label.contains("删除") || label.contains("丢弃") || label.contains("清空")) {
            button.setTextColor(BlueUi.DANGER);
        }
        body.addView(button, BlueUi.below(this, 12));
    }

    private void message(String text) {
        Toast.makeText(this, text, Toast.LENGTH_LONG).show();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        fileExecutor.shutdown();
        // 文件任务只负责读写文件；数据库连接仅在 Activity 主线程使用。
        database.close();
        super.onDestroy();
    }
}
