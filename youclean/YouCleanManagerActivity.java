package com.gathin.tvhome;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * YouClean's first built-in app-management screen.
 * This screen is intentionally read-only: it never disables or removes system packages.
 */
public final class YouCleanManagerActivity extends Activity {
    private static final int ALL = 0, USER = 1, SYSTEM = 2;
    private int selectedFilter = ALL;
    private String query = "";
    private LinearLayout list;
    private TextView summary;
    private List<ApplicationInfo> applications = new ArrayList<>();

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        buildUi();
        loadApps();
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private TextView text(String value, float size, int color) {
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextSize(size);
        v.setTextColor(color);
        return v;
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(34), dp(22), dp(34), dp(18));
        root.setBackgroundColor(Color.rgb(13, 17, 24));

        TextView title = text("YouClean · Quản lý ứng dụng", 25, Color.WHITE);
        title.setTypeface(null, 1);
        root.addView(title);

        TextView note = text("Xem ứng dụng trên TV. Chọn một ứng dụng để mở trang thông tin hệ thống. "
                + "Bản này chưa tự vô hiệu hóa hoặc gỡ bất kỳ gói nào.", 14,
                Color.rgb(190, 202, 218));
        LinearLayout.LayoutParams noteLp = new LinearLayout.LayoutParams(-1, -2);
        noteLp.topMargin = dp(7);
        root.addView(note, noteLp);

        EditText search = new EditText(this);
        search.setSingleLine(true);
        search.setTextColor(Color.WHITE);
        search.setHintTextColor(Color.GRAY);
        search.setHint("Tìm theo tên hoặc package...");
        search.setTextSize(16);
        LinearLayout.LayoutParams searchLp = new LinearLayout.LayoutParams(-1, dp(48));
        searchLp.topMargin = dp(14);
        root.addView(search, searchLp);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int st, int before, int count) {
                query = s.toString().toLowerCase(Locale.ROOT);
                renderApps();
            }
            @Override public void afterTextChanged(Editable e) {}
        });

        LinearLayout filters = new LinearLayout(this);
        filters.setOrientation(LinearLayout.HORIZONTAL);
        addFilter(filters, "Tất cả", ALL);
        addFilter(filters, "Ứng dụng người dùng", USER);
        addFilter(filters, "Ứng dụng hệ thống", SYSTEM);
        LinearLayout.LayoutParams filterLp = new LinearLayout.LayoutParams(-1, -2);
        filterLp.topMargin = dp(8);
        root.addView(filters, filterLp);

        summary = text("Đang tải danh sách...", 13, Color.LTGRAY);
        LinearLayout.LayoutParams summaryLp = new LinearLayout.LayoutParams(-1, -2);
        summaryLp.topMargin = dp(10);
        root.addView(summary, summaryLp);

        ScrollView scroll = new ScrollView(this);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list);
        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(-1, 0, 1f);
        scrollLp.topMargin = dp(8);
        root.addView(scroll, scrollLp);

        setContentView(root);
    }

    private void addFilter(LinearLayout parent, String label, int filter) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(12);
        button.setOnClickListener(v -> {
            selectedFilter = filter;
            renderApps();
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(46), 1f);
        lp.setMargins(dp(3), 0, dp(3), 0);
        parent.addView(button, lp);
    }

    private void loadApps() {
        try {
            applications = getPackageManager().getInstalledApplications(PackageManager.GET_META_DATA);
            Collections.sort(applications, Comparator.comparing(
                    a -> String.valueOf(getPackageManager().getApplicationLabel(a)),
                    String.CASE_INSENSITIVE_ORDER));
            renderApps();
        } catch (Exception e) {
            summary.setText("Không thể đọc danh sách ứng dụng: " + e.getMessage());
        }
    }

    private void renderApps() {
        if (list == null || summary == null) return;
        list.removeAllViews();
        int total = 0;
        int shown = 0;
        for (ApplicationInfo app : applications) {
            boolean system = (app.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
            if (selectedFilter == USER && system) continue;
            if (selectedFilter == SYSTEM && !system) continue;
            total++;
            String label = String.valueOf(getPackageManager().getApplicationLabel(app));
            String pkg = app.packageName == null ? "" : app.packageName;
            if (!query.isEmpty() && !label.toLowerCase(Locale.ROOT).contains(query)
                    && !pkg.toLowerCase(Locale.ROOT).contains(query)) continue;
            shown++;
            addAppRow(app, label, pkg, system);
        }
        summary.setText("Hiển thị " + shown + " / " + total + " ứng dụng trong bộ lọc");
        if (shown == 0) {
            TextView empty = text("Không tìm thấy ứng dụng phù hợp.", 16, Color.LTGRAY);
            empty.setPadding(dp(12), dp(20), dp(12), dp(20));
            list.addView(empty);
        }
    }

    private void addAppRow(ApplicationInfo app, String label, String pkg, boolean system) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setFocusable(true);
        row.setClickable(true);
        row.setBackgroundColor(Color.rgb(27, 34, 45));
        row.setPadding(dp(14), dp(10), dp(14), dp(10));

        TextView name = text(label, 17, Color.WHITE);
        name.setTypeface(null, 1);
        row.addView(name);
        TextView details = text((system ? "Hệ thống" : "Người dùng") + "  ·  " + pkg,
                12, Color.rgb(174, 190, 208));
        LinearLayout.LayoutParams detailLp = new LinearLayout.LayoutParams(-1, -2);
        detailLp.topMargin = dp(3);
        row.addView(details, detailLp);
        row.setOnClickListener(v -> openAppInfo(pkg));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(7);
        list.addView(row, lp);
    }

    private void openAppInfo(String packageName) {
        try {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + packageName));
            startActivity(intent);
        } catch (Exception e) {
            // Some TV firmware does not expose the standard app-details screen.
        }
    }
}
