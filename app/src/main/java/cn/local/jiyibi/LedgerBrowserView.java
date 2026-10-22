package cn.local.jiyibi;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.Spinner;
import android.widget.TextView;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** 全部账目筛选界面；仅程序化创建，构造时注入账目快照与回调，不用于 XML 布局。 */
@SuppressLint("ViewConstructor")
final class LedgerBrowserView extends LinearLayout {
    private static final String ALL_DATES = "全部日期";
    private static final String BY_MONTH = "按月";
    private static final String BY_DAY = "按日";
    private static final int MONTH_PREFIX_LENGTH = 7;
    private final List<LedgerEntry> entries;
    private final BiConsumer<LedgerEntry, LinearLayout> cardRenderer;
    private final Consumer<LedgerFilter> onFilterChanged;
    private final List<String> categories = new ArrayList<>();
    private final LinearLayout results;
    private final TextView expense;
    private final TextView counts;
    private final Button dateButton;
    private LedgerFilter filter;
    private boolean updatingControls;

    LedgerBrowserView(Context context, List<LedgerEntry> entries, LedgerFilter initialFilter,
            BiConsumer<LedgerEntry, LinearLayout> cardRenderer, Consumer<LedgerFilter> onFilterChanged) {
        super(context);
        this.entries = entries;
        this.filter = initialFilter;
        this.cardRenderer = cardRenderer;
        this.onFilterChanged = onFilterChanged;
        setOrientation(VERTICAL);

        EditText search = new EditText(context);
        search.setHint("搜索店铺、商品或评价");
        search.setContentDescription("模糊查询店铺、商品或评价");
        search.setTextSize(16);
        search.setTextColor(BlueUi.INK);
        search.setHintTextColor(BlueUi.SUB);
        search.setSingleLine(true);
        search.setInputType(InputType.TYPE_CLASS_TEXT);
        search.setMinHeight(dp(48));
        search.setPadding(dp(12), dp(10), dp(12), dp(10));
        search.setBackground(BlueUi.round(context, BlueUi.CARD, 12));
        search.setText(filter.getKeyword());
        addView(search, BlueUi.below(context, 12));

        LinearLayout dates = BlueUi.row(context);
        dateButton = BlueUi.button(context, dateLabel(filter.getDatePrefix()), false, this::showDatePicker);
        dates.addView(dateButton, new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Button reset = BlueUi.button(context, "重置", false, () -> { });
        LayoutParams resetParams = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        resetParams.setMarginStart(dp(8));
        dates.addView(reset, resetParams);
        addView(dates, BlueUi.below(context, 10));

        Spinner category = new Spinner(context);
        List<String> labels = categoryLabels();
        category.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_spinner_dropdown_item, labels));
        category.setContentDescription("按类别筛选账目");
        category.setMinimumHeight(dp(48));
        category.setBackground(BlueUi.round(context, BlueUi.PALE_BLUE, 12));
        category.setSelection(categoryIndex(filter.getCategory()));
        addView(category, BlueUi.below(context, 10));

        LinearLayout summary = BlueUi.card(context);
        BlueUi.appendText(summary, "筛选结果 · 支出总额", 13, BlueUi.SUB, false, 0);
        expense = BlueUi.text(context, "", 28, BlueUi.DEEP_BLUE, true);
        expense.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        summary.addView(expense, BlueUi.below(context, 10));
        counts = BlueUi.text(context, "", 13, BlueUi.SUB, false);
        counts.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        summary.addView(counts, BlueUi.below(context, 8));
        BlueUi.appendText(summary, "仅已确认账目计入支出，草稿不计入", 13, BlueUi.SUB, false, 8);
        addView(summary, BlueUi.below(context, 14));
        BlueUi.appendText(this, "账目明细 · 按日期倒序", 14, BlueUi.SUB, false, 18);
        results = BlueUi.column(context);
        addView(results, BlueUi.below(context, 0));

        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence text, int start, int count, int after) { }

            @Override
            public void onTextChanged(CharSequence text, int start, int before, int count) {
                if (!updatingControls) {
                    update(new LedgerFilter(filter.getDatePrefix(), filter.getCategory(), text.toString()));
                }
            }

            @Override
            public void afterTextChanged(Editable text) { }
        });
        category.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selected = position == 0 ? null : categories.get(position - 1);
                if (!updatingControls && !java.util.Objects.equals(selected, filter.getCategory())) {
                    update(new LedgerFilter(filter.getDatePrefix(), selected, filter.getKeyword()));
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) { }
        });
        reset.setOnClickListener(view -> {
            updatingControls = true;
            filter = new LedgerFilter("", null, "");
            search.setText("");
            category.setSelection(0);
            updatingControls = false;
            update(filter);
        });
        render();
    }

    private List<String> categoryLabels() {
        categories.addAll(Arrays.asList(LedgerEntry.CATEGORIES));
        for (LedgerEntry entry : entries) {
            if (!categories.contains(entry.getCategory())) {
                categories.add(entry.getCategory());
            }
        }
        if (filter.getCategory() != null && !categories.contains(filter.getCategory())) {
            categories.add(filter.getCategory());
        }
        List<String> labels = new ArrayList<>();
        labels.add("全部类别");
        for (String category : categories) {
            labels.add(category.isEmpty() ? "未分类" : category);
        }
        return labels;
    }

    private int categoryIndex(String category) {
        return category == null ? 0 : categories.indexOf(category) + 1;
    }

    private void update(LedgerFilter nextFilter) {
        filter = nextFilter;
        onFilterChanged.accept(filter);
        dateButton.setText(dateLabel(filter.getDatePrefix()));
        render();
    }

    private void render() {
        LedgerFilter.Result result = filter.apply(entries);
        expense.setText(getContext().getString(R.string.ledger_filter_expense,
                result.getExpense().setScale(2).toPlainString()));
        counts.setText(getContext().getString(R.string.ledger_filter_counts, result.getEntries().size(),
                result.getConfirmedCount(), result.getDraftCount()));
        results.removeAllViews();
        for (LedgerEntry entry : result.getEntries()) {
            cardRenderer.accept(entry, results);
        }
        if (result.getEntries().isEmpty()) {
            LinearLayout empty = BlueUi.card(getContext());
            BlueUi.appendText(empty, "没有符合条件的账目", 18, BlueUi.DEEP_BLUE, true, 0);
            BlueUi.appendText(empty, "试试更换日期、类别或关键词，或点击重置查看全部账目。",
                    14, BlueUi.SUB, false, 10);
            results.addView(empty, BlueUi.below(getContext(), 12));
        }
    }

    private void showDatePicker() {
        Context context = getContext();
        LinearLayout panel = BlueUi.column(context);
        panel.setPadding(dp(20), dp(20), dp(20), dp(24));
        panel.setBackground(BlueUi.round(context, BlueUi.CARD, 20));
        BlueUi.appendText(panel, "选择日期", 20, BlueUi.DEEP_BLUE, true, 0);

        LinearLayout modes = BlueUi.row(context);
        LinearLayout wheels = BlueUi.row(context);
        LocalDate initialDate = selectedDate();
        int[] yearRange = yearRange(initialDate);
        NumberPicker year = picker(yearRange[0], yearRange[1], initialDate.getYear(), "年");
        NumberPicker month = picker(1, 12, initialDate.getMonthValue(), "月");
        NumberPicker day = picker(1, initialDate.lengthOfMonth(), initialDate.getDayOfMonth(), "日");
        wheels.addView(year, new LayoutParams(0, dp(180), 1f));
        wheels.addView(month, new LayoutParams(0, dp(180), 1f));
        wheels.addView(day, new LayoutParams(0, dp(180), 1f));

        String[] labels = {ALL_DATES, BY_MONTH, BY_DAY};
        int[] selectedMode = {filter.getDatePrefix().isEmpty() ? 0
                : filter.getDatePrefix().length() == MONTH_PREFIX_LENGTH ? 1 : 2};
        List<Button> modeButtons = new ArrayList<>();
        TextView selection = BlueUi.text(context, "", 14, BlueUi.SUB, false);
        selection.setGravity(Gravity.CENTER);
        selection.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        Runnable updatePreview = () -> {
            wheels.setVisibility(selectedMode[0] == 0 ? View.GONE : View.VISIBLE);
            day.setVisibility(selectedMode[0] == 2 ? View.VISIBLE : View.GONE);
            selection.setText(selectedMode[0] == 0 ? "查看全部日期的账目"
                    : year.getValue() + "年" + month.getValue() + "月"
                    + (selectedMode[0] == 2 ? day.getValue() + "日" : ""));
            for (int index = 0; index < modeButtons.size(); index++) {
                Button button = modeButtons.get(index);
                boolean selected = index == selectedMode[0];
                button.setSelected(selected);
                button.setTextColor(selected ? BlueUi.CARD : BlueUi.DEEP_BLUE);
                button.setBackground(BlueUi.round(context, selected ? BlueUi.BLUE : BlueUi.PALE_BLUE, 12));
                button.setContentDescription(labels[index] + (selected ? "，已选择" : ""));
            }
        };
        for (int index = 0; index < labels.length; index++) {
            final int mode = index;
            Button button = BlueUi.button(context, labels[index], false, () -> {
                selectedMode[0] = mode;
                updatePreview.run();
            });
            button.setTextSize(13);
            button.setPadding(dp(4), dp(8), dp(4), dp(8));
            LayoutParams params = new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            params.setMargins(dp(3), 0, dp(3), 0);
            modes.addView(button, params);
            modeButtons.add(button);
        }
        panel.addView(modes, BlueUi.below(context, 16));
        panel.addView(wheels, BlueUi.below(context, 12));
        panel.addView(selection, BlueUi.below(context, 12));
        NumberPicker.OnValueChangeListener clampDay = (picker, oldValue, newValue) -> {
            int maximumDay = YearMonth.of(year.getValue(), month.getValue()).lengthOfMonth();
            int selectedDay = Math.min(day.getValue(), maximumDay);
            day.setMaxValue(maximumDay);
            day.setValue(selectedDay);
            updatePreview.run();
        };
        year.setOnValueChangedListener(clampDay);
        month.setOnValueChangedListener(clampDay);
        day.setOnValueChangedListener((picker, oldValue, newValue) -> updatePreview.run());

        AlertDialog dialog = new AlertDialog.Builder(context).setView(panel).create();
        LinearLayout actions = BlueUi.row(context);
        actions.addView(BlueUi.button(context, "取消", false, dialog::dismiss),
                new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Button confirm = BlueUi.button(context, "确定", true, () -> {
            String prefix = "";
            if (selectedMode[0] == 1) {
                prefix = YearMonth.of(year.getValue(), month.getValue()).toString();
            } else if (selectedMode[0] == 2) {
                prefix = LocalDate.of(year.getValue(), month.getValue(), day.getValue()).toString();
            }
            update(new LedgerFilter(prefix, filter.getCategory(), filter.getKeyword()));
            dialog.dismiss();
        });
        LayoutParams confirmParams = new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        confirmParams.setMarginStart(dp(10));
        actions.addView(confirm, confirmParams);
        panel.addView(actions, BlueUi.below(context, 18));
        updatePreview.run();
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.setGravity(Gravity.BOTTOM);
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        }
    }

    private NumberPicker picker(int minimum, int maximum, int value, String unit) {
        NumberPicker picker = new NumberPicker(getContext());
        picker.setMinValue(minimum);
        picker.setMaxValue(maximum);
        picker.setValue(value);
        picker.setWrapSelectorWheel(false);
        picker.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        picker.setFormatter(number -> number + unit);
        picker.setContentDescription(unit + "滚轮，上下滑动选择");
        return picker;
    }

    private LocalDate selectedDate() {
        String prefix = filter.getDatePrefix();
        if (prefix.isEmpty()) {
            return LocalDate.now();
        }
        return prefix.length() == MONTH_PREFIX_LENGTH ? YearMonth.parse(prefix).atDay(1)
                : LocalDate.parse(prefix);
    }

    private int[] yearRange(LocalDate initialDate) {
        int minimum = Math.min(LocalDate.now().getYear() - 100, initialDate.getYear());
        int maximum = Math.max(LocalDate.now().getYear() + 100, initialDate.getYear());
        for (LedgerEntry entry : entries) {
            try {
                int year = LocalDate.parse(entry.getDate()).getYear();
                minimum = Math.min(minimum, year);
                maximum = Math.max(maximum, year);
            } catch (DateTimeParseException exception) {
                // 草稿允许尚未填写有效日期，不作为滚轮年份范围依据。
            }
        }
        return new int[]{Math.max(1, minimum), Math.min(9999, maximum)};
    }

    private static String dateLabel(String prefix) {
        if (prefix.isEmpty()) {
            return ALL_DATES + " ▾";
        }
        YearMonth month = YearMonth.parse(prefix.substring(0, MONTH_PREFIX_LENGTH));
        return month.getYear() + "年" + month.getMonthValue() + "月"
                + (prefix.length() == MONTH_PREFIX_LENGTH ? "" : LocalDate.parse(prefix).getDayOfMonth() + "日") + " ▾";
    }

    private int dp(int value) {
        return BlueUi.dp(getContext(), value);
    }
}
