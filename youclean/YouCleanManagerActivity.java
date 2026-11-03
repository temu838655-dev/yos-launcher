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
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.InputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;

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
    private static final int CREATE_BACKUP_FILE = 7101;
    private static final int OPEN_BACKUP_FILE = 7102;

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

        TextView note = text("Xem ứng dụng, loại ứng dụng và trạng thái bật/tắt. Chọn một ứng dụng để mở trang thông tin hệ thống. "
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
        search.setPadding(dp(12), dp(6), dp(12), dp(6));
        search.setBackgroundColor(Color.rgb(40, 49, 62));
        search.setOnFocusChangeListener((v, focused) ->
                v.setBackgroundColor(focused ? Color.rgb(58, 92, 130) : Color.rgb(40, 49, 62)));
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

        Button refresh = new Button(this);
        refresh.setText("Làm mới danh sách ứng dụng");
        refresh.setTextSize(13);
        refresh.setOnClickListener(v -> {
            summary.setText("Đang cập nhật danh sách ứng dụng...");
            loadApps();
        });
        LinearLayout.LayoutParams refreshLp = new LinearLayout.LayoutParams(-1, dp(44));
        refreshLp.topMargin = dp(5);
        root.addView(refresh, refreshLp);

        Button backup = new Button(this);
        backup.setText("Sao lưu danh sách ứng dụng (JSON)");
        backup.setTextSize(13);
        backup.setOnFocusChangeListener((v, focused) -> {
            v.setAlpha(focused ? 1.0f : 0.88f);
            v.setScaleX(focused ? 1.025f : 1.0f);
            v.setScaleY(focused ? 1.025f : 1.0f);
        });
        backup.setOnClickListener(v -> createBackupFile());
        LinearLayout.LayoutParams backupLp = new LinearLayout.LayoutParams(-1, dp(48));
        backupLp.topMargin = dp(6);
        root.addView(backup, backupLp);

        Button restore = new Button(this);
        restore.setText("Nhập tệp sao lưu JSON · Kiểm tra ứng dụng thiếu");
        restore.setTextSize(13);
        restore.setOnFocusChangeListener((v, focused) -> {
            v.setAlpha(focused ? 1.0f : 0.88f);
            v.setScaleX(focused ? 1.025f : 1.0f);
            v.setScaleY(focused ? 1.025f : 1.0f);
        });
        restore.setOnClickListener(v -> openBackupFile());
        LinearLayout.LayoutParams restoreLp = new LinearLayout.LayoutParams(-1, dp(48));
        restoreLp.topMargin = dp(4);
        root.addView(restore, restoreLp);

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
        button.setOnFocusChangeListener((v, focused) -> {
            v.setAlpha(focused ? 1.0f : 0.88f);
            v.setScaleX(focused ? 1.04f : 1.0f);
            v.setScaleY(focused ? 1.04f : 1.0f);
        });
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
        row.setOnFocusChangeListener((v, focused) -> {
            v.setBackgroundColor(focused ? Color.rgb(58, 92, 130) : Color.rgb(27, 34, 45));
            v.setScaleX(focused ? 1.012f : 1.0f);
            v.setScaleY(focused ? 1.012f : 1.0f);
        });

        TextView name = text(label, 17, Color.WHITE);
        name.setTypeface(null, 1);
        row.addView(name);
        TextView details = text((system ? "Hệ thống" : "Người dùng") + "  ·  " + pkg,
                12, Color.rgb(174, 190, 208));
        LinearLayout.LayoutParams detailLp = new LinearLayout.LayoutParams(-1, -2);
        detailLp.topMargin = dp(3);
        row.addView(details, detailLp);

        String enabledLabel;
        try {
            int state = getPackageManager().getApplicationEnabledSetting(pkg);
            switch (state) {
                case PackageManager.COMPONENT_ENABLED_STATE_DISABLED:
                    enabledLabel = "Đã tắt";
                    break;
                case PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER:
                    enabledLabel = "Đã tắt bởi người dùng";
                    break;
                case PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED:
                    enabledLabel = "Tạm tắt đến khi được mở";
                    break;
                case PackageManager.COMPONENT_ENABLED_STATE_ENABLED:
                    enabledLabel = "Đang bật";
                    break;
                default:
                    enabledLabel = app.enabled ? "Đang bật (mặc định)" : "Đang tắt (mặc định)";
                    break;
            }
        } catch (Exception e) {
            enabledLabel = app.enabled ? "Trạng thái: đang bật" : "Trạng thái: đang tắt";
        }
        TextView stateView = text(enabledLabel, 12, Color.rgb(135, 190, 160));
        LinearLayout.LayoutParams stateLp = new LinearLayout.LayoutParams(-1, -2);
        stateLp.topMargin = dp(2);
        row.addView(stateView, stateLp);
        row.setOnClickListener(v -> openAppInfo(pkg));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(7);
        list.addView(row, lp);
    }

    private void createBackupFile() {
        try {
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/json");
            intent.putExtra(Intent.EXTRA_TITLE, "YouClean-app-list-backup.json");
            startActivityForResult(intent, CREATE_BACKUP_FILE);
        } catch (Exception e) {
            summary.setText("Không thể tạo tệp sao lưu: " + e.getMessage());
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (requestCode == OPEN_BACKUP_FILE) {
            compareBackupFile(uri);
            return;
        }
        if (requestCode != CREATE_BACKUP_FILE) return;
        try {
            JSONObject root = new JSONObject();
            root.put("format", "youclean-app-list");
            root.put("version", 1);
            root.put("createdAt", System.currentTimeMillis());
            JSONArray items = new JSONArray();
            for (ApplicationInfo app : applications) {
                JSONObject item = new JSONObject();
                item.put("packageName", app.packageName == null ? "" : app.packageName);
                item.put("label", String.valueOf(getPackageManager().getApplicationLabel(app)));
                item.put("system", (app.flags & ApplicationInfo.FLAG_SYSTEM) != 0);
                items.put(item);
            }
            root.put("applications", items);
            try (OutputStream out = getContentResolver().openOutputStream(uri);
                 OutputStreamWriter writer = new OutputStreamWriter(out, "UTF-8")) {
                writer.write(root.toString(2));
                writer.flush();
            }
            summary.setText("Đã sao lưu danh sách " + items.length() + " ứng dụng. Tệp chỉ lưu tên và package, không sao lưu dữ liệu riêng tư.");
        } catch (Exception e) {
            summary.setText("Sao lưu thất bại: " + e.getMessage());
        }
    }

    private void openBackupFile() {
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/json");
            startActivityForResult(intent, OPEN_BACKUP_FILE);
        } catch (Exception e) {
            summary.setText("Không thể mở tệp sao lưu: " + e.getMessage());
        }
    }

    private void compareBackupFile(Uri uri) {
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) throw new IllegalArgumentException("Không đọc được tệp đã chọn.");
            StringBuilder content = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, "UTF-8"))) {
                String line;
                while ((line = reader.readLine()) != null) content.append(line);
            }
            JSONObject backup = new JSONObject(content.toString());
            if (!"youclean-app-list".equals(backup.optString("format"))
                    || backup.optInt("version", -1) != 1) {
                throw new IllegalArgumentException("Định dạng hoặc phiên bản tệp sao lưu không được hỗ trợ.");
            }
            JSONArray saved = backup.optJSONArray("applications");
            if (saved == null) throw new IllegalArgumentException("Tệp không có danh sách ứng dụng hợp lệ.");

            java.util.HashSet<String> installed = new java.util.HashSet<>();
            for (ApplicationInfo app : applications) {
                if (app.packageName != null) installed.add(app.packageName);
            }
            java.util.LinkedHashSet<String> missing = new java.util.LinkedHashSet<>();
            for (int i = 0; i < saved.length(); i++) {
                JSONObject item = saved.optJSONObject(i);
                if (item == null) continue;
                String pkg = item.optString("packageName", "").trim();
                if (!pkg.isEmpty() && !installed.contains(pkg)) missing.add(pkg);
            }

            if (missing.isEmpty()) {
                summary.setText("Đã kiểm tra " + saved.length()
                        + " mục trong bản sao lưu: không phát hiện package nào bị thiếu. "
                        + "Không có ứng dụng nào được cài đặt hoặc thay đổi.");
            } else {
                StringBuilder report = new StringBuilder();
                report.append("Bản sao lưu: ").append(saved.length())
                        .append(" mục · Đã cài: ").append(saved.length() - missing.size())
                        .append(" · Còn thiếu: ").append(missing.size()).append("\n");
                int count = 0;
                for (String pkg : missing) {
                    if (count++ == 30) {
                        report.append("… và ").append(missing.size() - 30).append(" package khác");
                        break;
                    }
                    report.append("• ").append(pkg).append("\n");
                }
                report.append("Chỉ đối chiếu danh sách; YouClean không tự cài APK, bật hoặc tắt ứng dụng.");
                summary.setText(report.toString());
            }
        } catch (Exception e) {
            summary.setText("Không thể đọc bản sao lưu: " + e.getMessage());
        }
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
