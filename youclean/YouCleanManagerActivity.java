package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.res.ColorStateList;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
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
import android.widget.Toast;

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
    private ScrollView appScroll;
    private TextView summary;
    private List<ApplicationInfo> applications = new ArrayList<>();
    // Separate remote inventory: never mix TV package names with this device's PackageManager list.
    private List<String> remotePackages = new ArrayList<>();
    private boolean showingRemotePackages = false;
    private String remoteInventoryInfo = "";
    private boolean resumedOnce = false;
    private int savedListScrollY = 0;
    private String focusedPackageBeforeRefresh = null;
    private final List<Button> filterButtons = new ArrayList<>();
    private static final int CREATE_BACKUP_FILE = 7101;
    private static final int OPEN_BACKUP_FILE = 7102;
    private static final int EXPORT_REMOTE_PACKAGES_FILE = 7103;
    private static final int EXPORT_REMOTE_PACKAGES_JSON_FILE = 7104;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        buildUi();
        loadApps();
    }

    @Override protected void onResume() {
        super.onResume();
        // The first list load happens in onCreate. Refresh only after returning
        // from Android's App info screen or another external activity.
        if (resumedOnce) loadApps();
        resumedOnce = true;
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
                + "Nhãn an toàn chỉ là cảnh báo sơ bộ; package chưa xác minh không được coi là an toàn để tắt. "
                + "Bản này chưa tự vô hiệu hóa hoặc gỡ bất kỳ gói nào.", 14,
                Color.rgb(190, 202, 218));
        LinearLayout.LayoutParams noteLp = new LinearLayout.LayoutParams(-1, -2);
        noteLp.topMargin = dp(7);
        root.addView(note, noteLp);

        TextView deviceInfo = text(deviceIdentityText(), 13, Color.rgb(190, 202, 218));
        deviceInfo.setBackgroundColor(Color.rgb(27, 34, 45));
        deviceInfo.setPadding(dp(12), dp(10), dp(12), dp(10));
        LinearLayout.LayoutParams deviceInfoLp = new LinearLayout.LayoutParams(-1, -2);
        deviceInfoLp.topMargin = dp(10);
        root.addView(deviceInfo, deviceInfoLp);

        TextView matchingNotice = text(
                "Đối chiếu model: chưa xác minh danh sách riêng cho thiết bị này. "
                + "Không áp dụng danh sách của TV khác nếu chưa kiểm tra firmware.",
                12, Color.rgb(235, 194, 130));
        LinearLayout.LayoutParams matchingNoticeLp = new LinearLayout.LayoutParams(-1, -2);
        matchingNoticeLp.topMargin = dp(5);
        root.addView(matchingNotice, matchingNoticeLp);

        Button modelLookup = new Button(this);
        modelLookup.setText("Tra cứu hồ sơ TV từ CleanYourTV");
        modelLookup.setTextSize(13);
        applyTvFocusFeedback(modelLookup);
        modelLookup.setOnClickListener(v -> showModelMatches());
        LinearLayout.LayoutParams modelLookupLp = new LinearLayout.LayoutParams(-1, dp(44));
        modelLookupLp.topMargin = dp(5);
        root.addView(modelLookup, modelLookupLp);

        EditText search = new EditText(this);
        search.setSingleLine(true);
        search.setFocusable(true);
        search.setFocusableInTouchMode(true);
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

        Button adbConnect = new Button(this);
        adbConnect.setText("Kết nối TV khác qua ADB Wi-Fi");
        adbConnect.setTextSize(13);
        applyTvFocusFeedback(adbConnect);
        adbConnect.setOnClickListener(v ->
                startActivity(new Intent(this, YouCleanAdbWifiActivity.class)));
        LinearLayout.LayoutParams adbConnectLp = new LinearLayout.LayoutParams(-1, dp(44));
        adbConnectLp.topMargin = dp(8);
        root.addView(adbConnect, adbConnectLp);

        Button remotePackagesButton = new Button(this);
        remotePackagesButton.setText("Xem package TV đã đọc qua ADB");
        remotePackagesButton.setTextSize(13);
        applyTvFocusFeedback(remotePackagesButton);
        remotePackagesButton.setOnClickListener(v -> loadRemotePackages());
        LinearLayout.LayoutParams remotePackagesLp = new LinearLayout.LayoutParams(-1, dp(44));
        remotePackagesLp.topMargin = dp(4);
        root.addView(remotePackagesButton, remotePackagesLp);

        Button copyRemotePackages = new Button(this);
        copyRemotePackages.setText("Sao chép toàn bộ package TV đã lưu");
        copyRemotePackages.setTextSize(13);
        applyTvFocusFeedback(copyRemotePackages);
        copyRemotePackages.setOnClickListener(v -> copyAllRemotePackages());
        LinearLayout.LayoutParams copyRemoteLp = new LinearLayout.LayoutParams(-1, dp(42));
        copyRemoteLp.topMargin = dp(4);
        root.addView(copyRemotePackages, copyRemoteLp);

        Button exportRemotePackages = new Button(this);
        exportRemotePackages.setText("Xuất danh sách package TV đã lưu thành TXT");
        exportRemotePackages.setTextSize(13);
        applyTvFocusFeedback(exportRemotePackages);
        exportRemotePackages.setOnClickListener(v -> createRemotePackagesTextFile());
        LinearLayout.LayoutParams exportRemoteLp = new LinearLayout.LayoutParams(-1, dp(42));
        exportRemoteLp.topMargin = dp(4);
        root.addView(exportRemotePackages, exportRemoteLp);

        Button exportRemotePackagesJson = new Button(this);
        exportRemotePackagesJson.setText("Xuất bản sao package TV thành JSON");
        exportRemotePackagesJson.setTextSize(13);
        applyTvFocusFeedback(exportRemotePackagesJson);
        exportRemotePackagesJson.setOnClickListener(v -> createRemotePackagesJsonFile());
        LinearLayout.LayoutParams exportRemoteJsonLp = new LinearLayout.LayoutParams(-1, dp(42));
        exportRemoteJsonLp.topMargin = dp(4);
        root.addView(exportRemotePackagesJson, exportRemoteJsonLp);

        Button refresh = new Button(this);
        refresh.setText("Làm mới danh sách ứng dụng");
        refresh.setTextSize(13);
        applyTvFocusFeedback(refresh);
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

        appScroll = new ScrollView(this);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        appScroll.addView(list);
        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(-1, 0, 1f);
        scrollLp.topMargin = dp(8);
        root.addView(appScroll, scrollLp);

        setContentView(root);
    }

    /** Consistent visible focus feedback for TV remotes and keyboards. */
    private void applyTvFocusFeedback(View view) {
        view.setFocusable(true);
        view.setClickable(true);
        view.setOnFocusChangeListener((v, focused) -> {
            v.setAlpha(focused ? 1.0f : 0.92f);
            v.setScaleX(focused ? 1.025f : 1.0f);
            v.setScaleY(focused ? 1.025f : 1.0f);
        });
    }

    private void addFilter(LinearLayout parent, String label, int filter) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(12);
        button.setFocusable(true);
        filterButtons.add(button);
        button.setOnFocusChangeListener((v, focused) -> {
            v.setAlpha(focused ? 1.0f : 0.92f);
            v.setScaleX(focused ? 1.04f : 1.0f);
            v.setScaleY(focused ? 1.04f : 1.0f);
            updateFilterButtonStyle(button, filter, focused);
        });
        button.setOnClickListener(v -> {
            showingRemotePackages = false;
            selectedFilter = filter;
            updateAllFilterButtonStyles();
            renderApps();
        });
        updateFilterButtonStyle(button, filter, false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(46), 1f);
        lp.setMargins(dp(3), 0, dp(3), 0);
        parent.addView(button, lp);
    }

    private void updateFilterButtonStyle(Button button, int filter, boolean focused) {
        boolean selected = selectedFilter == filter;
        int background = selected
                ? (focused ? Color.rgb(48, 112, 166) : Color.rgb(35, 83, 125))
                : (focused ? Color.rgb(65, 78, 96) : Color.rgb(40, 49, 62));
        button.setBackgroundTintList(ColorStateList.valueOf(background));
        button.setTextColor(selected ? Color.WHITE : Color.rgb(205, 215, 228));
    }

    private void updateAllFilterButtonStyles() {
        for (int i = 0; i < filterButtons.size(); i++) {
            updateFilterButtonStyle(filterButtons.get(i), i == 0 ? ALL : (i == 1 ? USER : SYSTEM),
                    filterButtons.get(i).hasFocus());
        }
    }

    private String deviceIdentityText() {
        String manufacturer = safeBuildValue(Build.MANUFACTURER);
        String model = safeBuildValue(Build.MODEL);
        String device = safeBuildValue(Build.DEVICE);
        String product = safeBuildValue(Build.PRODUCT);
        String board = safeBuildValue(Build.BOARD);
        String release = safeBuildValue(Build.VERSION.RELEASE);
        return "THÔNG TIN THIẾT BỊ\n"
                + "Hãng: " + manufacturer + "\n"
                + "Model: " + model + "\n"
                + "Device: " + device + "\n"
                + "Product: " + product + "\n"
                + "Board: " + board + "\n"
                + "Android: " + release + " (API " + Build.VERSION.SDK_INT + ")";
    }

    private String safeBuildValue(String value) {
        if (value == null || value.trim().isEmpty()) return "không cung cấp";
        return value.trim();
    }

    private void showModelMatches() {
        try {
            StringBuilder jsonText = new StringBuilder();
            try (InputStream in = getAssets().open("youclean/model_catalog.json");
                 BufferedReader reader = new BufferedReader(new InputStreamReader(in, "UTF-8"))) {
                String line;
                while ((line = reader.readLine()) != null) jsonText.append(line);
            }
            JSONObject catalog = new JSONObject(jsonText.toString());
            JSONArray devices = catalog.optJSONArray("devices");
            if (devices == null) throw new IllegalStateException("Danh mục model không hợp lệ.");

            String currentFingerprint = normalizeFingerprint(Build.FINGERPRINT);
            String currentBuild = safeBuildValue(Build.DISPLAY);
            String currentIncremental = safeBuildValue(Build.VERSION.INCREMENTAL);
            String[] deviceValues = {
                    Build.MANUFACTURER, Build.MODEL, Build.DEVICE,
                    Build.PRODUCT, Build.BOARD
            };
            java.util.LinkedHashSet<String> terms = new java.util.LinkedHashSet<>();
            for (String value : deviceValues) {
                if (value == null) continue;
                String normalized = value.toLowerCase(Locale.ROOT)
                        .replaceAll("[^a-z0-9]+", " ").trim();
                for (String part : normalized.split("\\s+")) {
                    if (part.length() >= 4 && !isGenericModelTerm(part)) terms.add(part);
                }
            }

            java.util.ArrayList<JSONObject> matches = new java.util.ArrayList<>();
            java.util.ArrayList<Integer> scores = new java.util.ArrayList<>();
            for (int i = 0; i < devices.length(); i++) {
                JSONObject item = devices.optJSONObject(i);
                if (item == null) continue;
                String itemFingerprint = normalizeFingerprint(item.optString("fingerprint", ""));
                boolean exactFingerprint = !currentFingerprint.isEmpty()
                        && currentFingerprint.equals(itemFingerprint);
                String haystack = (item.optString("brand") + " " + item.optString("tv")
                        + " " + item.optString("id")).toLowerCase(Locale.ROOT);
                int score = exactFingerprint ? 100000 : 0;
                for (String term : terms) {
                    if (haystack.contains(term)) score += term.length() >= 6 ? 10 : 4;
                }
                if (score > 0) {
                    int position = 0;
                    while (position < scores.size() && scores.get(position) >= score) position++;
                    matches.add(position, item);
                    scores.add(position, score);
                }
            }

            StringBuilder report = new StringBuilder();
            report.append("Danh mục tham khảo: ").append(devices.length())
                    .append(" hồ sơ TV. Thiết bị hiện tại: ")
                    .append(Build.MANUFACTURER).append(" / ").append(Build.MODEL)
                    .append(" / ").append(Build.DEVICE)
                    .append("\nBuild hiện tại: ").append(currentBuild)
                    .append(" · Incremental: ").append(currentIncremental)
                    .append("\nFingerprint hiện tại: ")
                    .append(currentFingerprint.isEmpty() ? "không cung cấp" : currentFingerprint)
                    .append("\n\n");
            if (matches.isEmpty()) {
                report.append("Chưa tìm thấy hồ sơ có từ khóa model/hãng trùng. "
                        + "Danh mục upstream có thể không chứa đúng thiết bị này.");
            } else {
                int limit = Math.min(matches.size(), 8);
                report.append("Hồ sơ được xếp theo mức độ trùng. Chỉ fingerprint trùng hoàn toàn "
                        + "mới được đánh dấu trùng fingerprint; các kết quả khác chỉ là gợi ý model/hãng.\n\n");
                for (int i = 0; i < limit; i++) {
                    JSONObject item = matches.get(i);
                    String itemFingerprint = normalizeFingerprint(item.optString("fingerprint", ""));
                    boolean exactFingerprint = !currentFingerprint.isEmpty()
                            && currentFingerprint.equals(itemFingerprint);
                    report.append(i + 1).append(". ")
                            .append(exactFingerprint ? "[FINGERPRINT TRÙNG]" : "[GỢI Ý MODEL/HÃNG]")
                            .append(" ").append(item.optString("tv"))
                            .append("\n   Hệ điều hành: ").append(item.optString("os", "không rõ"))
                            .append(" · ").append(item.optInt("debloatPackageCount"))
                            .append(" package trong danh sách mẫu")
                            .append("\n   Build mẫu: ").append(item.optString("build", "không có"))
                            .append("\n   Fingerprint mẫu: ")
                            .append(itemFingerprint.isEmpty() ? "không có" : itemFingerprint);
                    String notes = item.optString("notes", "");
                    if (!notes.isEmpty()) {
                        if (notes.length() > 420) notes = notes.substring(0, 420) + "…";
                        report.append("\n   Ghi chú: ").append(notes);
                    }
                    report.append("\n   Nguồn: ").append(item.optString("source")).append("\n\n");
                }
            }
            report.append("LƯU Ý AN TOÀN: fingerprint trùng là tín hiệu nhận dạng mạnh nhưng vẫn cần "
                    + "kiểm tra phiên bản firmware và phần cứng. Gợi ý theo model/hãng không chứng minh "
                    + "tương thích. YouClean chỉ đọc danh mục; không áp dụng danh sách và không tắt package nào.");
            TextView body = text(report.toString(), 13, Color.WHITE);
            body.setPadding(dp(18), dp(12), dp(18), dp(12));
            ScrollView scroll = new ScrollView(this);
            scroll.addView(body);
            new AlertDialog.Builder(this)
                    .setTitle("Đối chiếu hồ sơ TV · chỉ tham khảo")
                    .setView(scroll)
                    .setPositiveButton("Đóng", null)
                    .show();
        } catch (Exception e) {
            new AlertDialog.Builder(this)
                    .setTitle("Không đọc được danh mục TV")
                    .setMessage("Danh mục sẽ được tạo trong lần build tiếp theo. Chi tiết: " + e.getMessage())
                    .setPositiveButton("Đóng", null)
                    .show();
        }
    }

    private String normalizeFingerprint(String value) {
        if (value == null) return "";
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private boolean isGenericModelTerm(String term) {
        return "android".equals(term) || "google".equals(term) || "smart".equals(term)
                || "device".equals(term) || "board".equals(term) || "build".equals(term)
                || "generic".equals(term) || "unknown".equals(term) || "model".equals(term);
    }

    private void loadRemotePackages() {
        android.content.SharedPreferences prefs =
                getSharedPreferences("youclean_adb", MODE_PRIVATE);
        java.util.Set<String> saved = prefs.getStringSet(
                "remote_packages", java.util.Collections.emptySet());
        remotePackages = new ArrayList<>(saved == null
                ? java.util.Collections.emptySet() : saved);
        Collections.sort(remotePackages, String.CASE_INSENSITIVE_ORDER);
        String ip = prefs.getString("remote_ip", "");
        long savedAt = prefs.getLong("remote_saved_at", 0L);
        String time = savedAt > 0L
                ? android.text.format.DateFormat.format("dd/MM/yyyy HH:mm", savedAt).toString()
                : "không rõ thời điểm";
        remoteInventoryInfo = (ip == null || ip.trim().isEmpty()
                ? "Không rõ IP TV" : "TV " + ip)
                + " · đọc lúc " + time;
        showingRemotePackages = true;
        selectedFilter = ALL;
        renderApps();
        if (remotePackages.isEmpty()) {
            summary.setText("Chưa có package TV đã lưu. Hãy kết nối TV bằng ADB Wi-Fi trước.");
        }
    }

    /** Copy the last saved remote inventory; this does not reconnect to the TV. */
    private void copyAllRemotePackages() {
        android.content.SharedPreferences prefs =
                getSharedPreferences("youclean_adb", MODE_PRIVATE);
        java.util.Set<String> saved = prefs.getStringSet(
                "remote_packages", java.util.Collections.emptySet());
        if (saved == null || saved.isEmpty()) {
            Toast.makeText(this, "Chưa có danh sách package TV đã lưu.", Toast.LENGTH_SHORT).show();
            return;
        }
        ArrayList<String> sorted = new ArrayList<>(saved);
        Collections.sort(sorted, String.CASE_INSENSITIVE_ORDER);
        StringBuilder content = new StringBuilder();
        for (String pkg : sorted) {
            if (content.length() > 0) content.append('\n');
            content.append(pkg);
        }
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clipboard == null) {
            Toast.makeText(this, "Không thể truy cập bộ nhớ tạm.", Toast.LENGTH_SHORT).show();
            return;
        }
        clipboard.setPrimaryClip(ClipData.newPlainText("YouClean · TV package inventory", content.toString()));
        Toast.makeText(this, "Đã sao chép " + sorted.size()
                + " package từ danh sách đã lưu (không phải kết nối trực tiếp).",
                Toast.LENGTH_LONG).show();
    }

    private void addRemotePackageRow(String pkg) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setTag(pkg);
        row.setFocusable(true);
        row.setClickable(true);
        row.setBackgroundColor(Color.rgb(27, 34, 45));
        row.setPadding(dp(14), dp(10), dp(14), dp(10));
        row.setOnFocusChangeListener((v, focused) -> {
            v.setBackgroundColor(focused ? Color.rgb(58, 92, 130) : Color.rgb(27, 34, 45));
            v.setScaleX(focused ? 1.012f : 1.0f);
            v.setScaleY(focused ? 1.012f : 1.0f);
        });
        TextView name = text(pkg, 16, Color.WHITE);
        name.setTypeface(null, 1);
        row.addView(name);
        TextView source = text("Nhấn OK để sao chép package · dữ liệu ADB đã lưu", 12,
                Color.rgb(174, 190, 208));
        LinearLayout.LayoutParams sourceLp = new LinearLayout.LayoutParams(-1, -2);
        sourceLp.topMargin = dp(3);
        row.addView(source, sourceLp);
        String safety = safetyLabel(pkg, true);
        int safetyColor = safety.startsWith("BẢO VỆ")
                ? Color.rgb(255, 160, 120) : Color.rgb(220, 195, 125);
        TextView safetyView = text(safety, 12, safetyColor);
        LinearLayout.LayoutParams safetyLp = new LinearLayout.LayoutParams(-1, -2);
        safetyLp.topMargin = dp(2);
        row.addView(safetyView, safetyLp);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(7);
        row.setOnClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (clipboard != null) {
                clipboard.setPrimaryClip(ClipData.newPlainText("YouClean package", pkg));
                Toast.makeText(this, "Đã sao chép: " + pkg, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Không thể truy cập bộ nhớ tạm.", Toast.LENGTH_SHORT).show();
            }
        });
        list.addView(row, lp);
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
        // Keep the current remote position when a filter/search/refresh rebuilds rows.
        if (appScroll != null) savedListScrollY = appScroll.getScrollY();
        focusedPackageBeforeRefresh = null;
        if (list != null) {
            for (int i = 0; i < list.getChildCount(); i++) {
                View child = list.getChildAt(i);
                if (child.hasFocus() && child.getTag() instanceof String) {
                    focusedPackageBeforeRefresh = (String) child.getTag();
                    break;
                }
            }
        }
        list.removeAllViews();
        int total = 0;
        int shown = 0;
        if (showingRemotePackages) {
            for (String pkg : remotePackages) {
                total++;
                if (!query.isEmpty() && !pkg.toLowerCase(Locale.ROOT).contains(query)) continue;
                shown++;
                addRemotePackageRow(pkg);
            }
            summary.setText("DỮ LIỆU ĐÃ LƯU · " + remoteInventoryInfo
                    + "\nHiển thị " + shown + " / " + total
                    + " package trong lần đọc ADB gần nhất. Đây không phải trạng thái kết nối trực tiếp; chỉ xem, không thao tác package.");
        } else {
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
            summary.setText("Thiết bị đang chạy YouClean · Hiển thị " + shown + " / " + total
                    + " ứng dụng trong bộ lọc");
        }
        if (shown == 0) {
            TextView empty = text("Không tìm thấy ứng dụng phù hợp.", 16, Color.LTGRAY);
            empty.setPadding(dp(12), dp(20), dp(12), dp(20));
            list.addView(empty);
        }
        if (appScroll != null) {
            final String restorePackage = focusedPackageBeforeRefresh;
            final int restoreScrollY = savedListScrollY;
            appScroll.post(() -> {
                appScroll.scrollTo(0, restoreScrollY);
                if (restorePackage != null && list != null) {
                    for (int i = 0; i < list.getChildCount(); i++) {
                        View child = list.getChildAt(i);
                        if (restorePackage.equals(child.getTag())) {
                            child.requestFocus();
                            break;
                        }
                    }
                }
            });
        }
    }

    private void addAppRow(ApplicationInfo app, String label, String pkg, boolean system) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setTag(pkg);
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

        String safety = safetyLabel(pkg, system);
        int safetyColor = safety.startsWith("BẢO VỆ")
                ? Color.rgb(255, 160, 120)
                : Color.rgb(220, 195, 125);
        TextView safetyView = text(safety, 12, safetyColor);
        LinearLayout.LayoutParams safetyLp = new LinearLayout.LayoutParams(-1, -2);
        safetyLp.topMargin = dp(2);
        row.addView(safetyView, safetyLp);
        row.setOnClickListener(v -> openAppInfo(pkg));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(7);
        list.addView(row, lp);
    }

    /**
     * Conservative, read-only guard labels adapted from upstream CleanYourTV safety checks.
     * Unknown packages are never presented as safe to disable. Model-specific example lists
     * must be matched to the actual TV before they can be considered.
     */
    private String safetyLabel(String pkg, boolean system) {
        if (pkg == null || pkg.isEmpty()) return "CHƯA XÁC MINH · thiếu package";
        // Google TV SetupWraith can own saved Wi-Fi credentials; explain this
        // specific risk before the generic protected-package classification.
        if ("com.google.android.tungsten.setupwraith".equals(pkg)) {
            return "BẢO VỆ · thành phần thiết lập Google TV, có thể quản lý Wi-Fi và Cast";
        }
        if ("com.mediatek.tvinput".equals(pkg)) {
            return "BẢO VỆ · dịch vụ đầu vào TV, có thể điều khiển HDMI/AV";
        }
        String[] protectedExact = {
                "android",
                "android.ext.services",
                "android.ext.shared",
                "com.android.systemui",
                "com.android.shell",
                "com.android.phone",
                "com.android.settings",
                "com.android.tv.settings",
                "com.android.permissioncontroller",
                "com.google.android.permissioncontroller",
                "com.android.packageinstaller",
                "com.google.android.packageinstaller",
                "com.android.ext.services",
                "com.android.ext.shared",
                "com.google.android.ext.services",
                "com.google.android.ext.shared",
                "com.android.modulemetadata",
                "com.google.android.modulemetadata",
                "com.android.sdksandbox",
                "com.android.location.fused",
                "com.android.providers.settings",
                "com.android.providers.tv",
                "com.android.providers.media",
                "com.android.providers.downloads",
                "com.google.android.overlay.modules.ext.services",
                "com.google.android.overlay.modules.permissioncontroller",
                "com.google.android.tungsten.setupwraith",
                "com.cltv.mal",
                "com.mediatek.tis",
                "com.mediatek.tv.service",
                "com.mediatek.tv.agent"
        };
        for (String protectedPkg : protectedExact) {
            if (protectedPkg.equals(pkg)) {
                return "BẢO VỆ · thành phần Android/TV quan trọng";
            }
        }
        String[] protectedPrefixes = {
                "com.mediatek.tv.",
                "com.mediatek.tis."
        };
        for (String prefix : protectedPrefixes) {
            if (pkg.startsWith(prefix)) {
                return "BẢO VỆ · nhóm package hệ thống";
            }
        }
        if (pkg.contains("launcher") || pkg.contains("tvhome")
                || pkg.contains("homekey") || pkg.contains("home_key")) {
            return "CẦN GIỮ · có thể liên quan màn hình Home";
        }
        if (pkg.contains("input") || pkg.contains("remote")
                || pkg.contains("cec") || pkg.contains("hdmi")) {
            return "CẦN KIỂM TRA · có thể liên quan điều khiển/HDMI";
        }
        return "CHƯA XÁC MINH · chưa đối chiếu đúng model TV";
    }

    private void createRemotePackagesTextFile() {
        java.util.Set<String> saved = getSharedPreferences("youclean_adb", MODE_PRIVATE)
                .getStringSet("remote_packages", java.util.Collections.emptySet());
        if (saved == null || saved.isEmpty()) {
            Toast.makeText(this, "Chưa có danh sách package TV đã lưu. Hãy đọc TV qua ADB trước.",
                    Toast.LENGTH_LONG).show();
            return;
        }
        try {
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_TITLE, "YouClean-TV-packages.txt");
            startActivityForResult(intent, EXPORT_REMOTE_PACKAGES_FILE);
        } catch (Exception e) {
            summary.setText("Không thể tạo tệp package TV: " + e.getMessage());
        }
    }

    private void createRemotePackagesJsonFile() {
        java.util.Set<String> saved = getSharedPreferences("youclean_adb", MODE_PRIVATE)
                .getStringSet("remote_packages", java.util.Collections.emptySet());
        if (saved == null || saved.isEmpty()) {
            Toast.makeText(this, "Chưa có danh sách package TV đã lưu. Hãy đọc TV qua ADB trước.",
                    Toast.LENGTH_LONG).show();
            return;
        }
        try {
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/json");
            intent.putExtra(Intent.EXTRA_TITLE, "YouClean-TV-packages.json");
            startActivityForResult(intent, EXPORT_REMOTE_PACKAGES_JSON_FILE);
        } catch (Exception e) {
            summary.setText("Không thể tạo tệp JSON package TV: " + e.getMessage());
        }
    }

    private void exportRemotePackagesToJson(Uri uri) {
        java.util.Set<String> saved = getSharedPreferences("youclean_adb", MODE_PRIVATE)
                .getStringSet("remote_packages", java.util.Collections.emptySet());
        ArrayList<String> sorted = new ArrayList<>(saved == null
                ? java.util.Collections.emptySet() : saved);
        Collections.sort(sorted, String.CASE_INSENSITIVE_ORDER);
        if (sorted.isEmpty()) {
            summary.setText("Không có package TV đã lưu để xuất.");
            return;
        }
        android.content.SharedPreferences prefs = getSharedPreferences("youclean_adb", MODE_PRIVATE);
        JSONObject root = new JSONObject();
        try {
            root.put("format", "youclean-tv-package-inventory");
            root.put("version", 1);
            root.put("deviceIp", prefs.getString("remote_ip", ""));
            root.put("savedAt", prefs.getLong("remote_saved_at", 0L));
            root.put("exportedAt", System.currentTimeMillis());
            root.put("packageCount", sorted.size());
            root.put("readOnlySnapshot", true);
            JSONArray packages = new JSONArray();
            for (String pkg : sorted) packages.put(pkg);
            root.put("packages", packages);
            OutputStream out = getContentResolver().openOutputStream(uri);
            if (out == null) throw new IllegalStateException("Không mở được tệp đã chọn.");
            try (OutputStream stream = out;
                 OutputStreamWriter writer = new OutputStreamWriter(stream, "UTF-8")) {
                writer.write(root.toString(2));
                writer.flush();
            }
            summary.setText("Đã xuất bản sao JSON gồm " + sorted.size()
                    + " package TV. Tệp chỉ chứa danh sách đã lưu, không chứa dữ liệu ứng dụng.");
        } catch (Exception e) {
            summary.setText("Xuất JSON package TV thất bại: " + e.getMessage());
        }
    }

    private void exportRemotePackagesToText(Uri uri) {
        java.util.Set<String> saved = getSharedPreferences("youclean_adb", MODE_PRIVATE)
                .getStringSet("remote_packages", java.util.Collections.emptySet());
        ArrayList<String> sorted = new ArrayList<>(saved == null
                ? java.util.Collections.emptySet() : saved);
        Collections.sort(sorted, String.CASE_INSENSITIVE_ORDER);
        if (sorted.isEmpty()) {
            summary.setText("Không có package TV đã lưu để xuất.");
            return;
        }
        android.content.SharedPreferences prefs = getSharedPreferences("youclean_adb", MODE_PRIVATE);
        String ip = prefs.getString("remote_ip", "không rõ");
        long savedAt = prefs.getLong("remote_saved_at", 0L);
        String time = savedAt > 0L
                ? android.text.format.DateFormat.format("dd/MM/yyyy HH:mm", savedAt).toString()
                : "không rõ thời điểm";
        try {
            OutputStream out = getContentResolver().openOutputStream(uri);
            if (out == null) throw new IllegalStateException("Không mở được tệp đã chọn.");
            try (OutputStream stream = out;
                 OutputStreamWriter writer = new OutputStreamWriter(stream, "UTF-8")) {
                writer.write("# YouClean - danh sách package TV đã đọc qua ADB\n");
                writer.write("# TV: " + ip + "\n");
                writer.write("# Thời điểm đọc: " + time + "\n");
                writer.write("# Tổng package: " + sorted.size() + "\n");
                writer.write("# Đây là dữ liệu đã lưu, không phải kết nối TV trực tiếp.\n\n");
                for (String pkg : sorted) {
                    writer.write(pkg);
                    writer.write("\n");
                }
                writer.flush();
                summary.setText("Đã xuất " + sorted.size()
                        + " package TV thành tệp TXT. Dữ liệu lấy từ bản lưu ADB gần nhất.");
            }
        } catch (Exception e) {
            summary.setText("Xuất danh sách package thất bại: " + e.getMessage());
        }
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
        if (requestCode == EXPORT_REMOTE_PACKAGES_FILE) {
            exportRemotePackagesToText(uri);
            return;
        }
        if (requestCode == EXPORT_REMOTE_PACKAGES_JSON_FILE) {
            exportRemotePackagesToJson(uri);
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
            OutputStream out = getContentResolver().openOutputStream(uri);
            if (out == null) throw new IllegalStateException("Không mở được tệp sao lưu đã chọn.");
            try (OutputStream stream = out;
                 OutputStreamWriter writer = new OutputStreamWriter(stream, "UTF-8")) {
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
            // Enforce the limit on actual UTF-8 bytes before decoding/parsing.
            // Reading line-by-line could allocate an unbounded single line first.
            java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int totalBytes = 0;
            int bytesRead;
            while ((bytesRead = in.read(chunk)) != -1) {
                totalBytes += bytesRead;
                if (totalBytes > 5 * 1024 * 1024) {
                    throw new IllegalArgumentException("Tệp sao lưu quá lớn (giới hạn 5 MB).");
                }
                buffer.write(chunk, 0, bytesRead);
            }
            String content = new String(buffer.toByteArray(), "UTF-8");
            JSONObject backup = new JSONObject(content);
            Object formatValue = backup.opt("format");
            Object versionValue = backup.opt("version");
            if (!(formatValue instanceof String)
                    || !"youclean-app-list".equals(formatValue)
                    || !(versionValue instanceof Number)
                    || ((Number) versionValue).doubleValue() != 1.0d) {
                throw new IllegalArgumentException("Định dạng hoặc phiên bản tệp sao lưu không được hỗ trợ.");
            }
            JSONArray saved = backup.optJSONArray("applications");
            if (saved == null) throw new IllegalArgumentException("Tệp không có danh sách ứng dụng hợp lệ.");
            if (saved.length() > 10000) {
                throw new IllegalArgumentException("Danh sách sao lưu vượt quá 10.000 mục. "
                        + "YouClean từ chối xử lý để tránh tiêu tốn quá nhiều bộ nhớ.");
            }

            java.util.HashSet<String> installed = new java.util.HashSet<>();
            for (ApplicationInfo app : applications) {
                if (app.packageName != null) installed.add(app.packageName);
            }
            java.util.LinkedHashSet<String> uniqueSaved = new java.util.LinkedHashSet<>();
            java.util.LinkedHashSet<String> missing = new java.util.LinkedHashSet<>();
            int invalidEntries = 0;
            int duplicateEntries = 0;
            for (int i = 0; i < saved.length(); i++) {
                JSONObject item = saved.optJSONObject(i);
                if (item == null) {
                    invalidEntries++;
                    continue;
                }
                Object packageValue = item.opt("packageName");
                if (!(packageValue instanceof String)) {
                    invalidEntries++;
                    continue;
                }
                String pkg = ((String) packageValue).trim();
                if (!isPlausiblePackageName(pkg)) {
                    invalidEntries++;
                    continue;
                }
                if (!uniqueSaved.add(pkg)) {
                    duplicateEntries++;
                    continue;
                }
                if (!installed.contains(pkg)) missing.add(pkg);
            }

            if (uniqueSaved.isEmpty()) {
                summary.setText("Tệp không chứa package hợp lệ để đối chiếu. "
                        + "Mục không hợp lệ: " + invalidEntries
                        + " · Mục trùng lặp: " + duplicateEntries
                        + ". Hãy chọn tệp sao lưu YouClean khác.");
                return;
            }
            int installedCount = uniqueSaved.size() - missing.size();

            String backupTime = backupCreatedAtLabel(backup);
            if (missing.isEmpty()) {
                summary.setText(backupTime + "\nĐã đối chiếu " + uniqueSaved.size()
                        + " package hợp lệ: không phát hiện package nào bị thiếu. "
                        + (invalidEntries > 0 ? "Mục lỗi: " + invalidEntries + ". " : "")
                        + (duplicateEntries > 0 ? "Mục trùng lặp: " + duplicateEntries + ". " : "")
                        + "Không có ứng dụng nào được cài đặt hoặc thay đổi.");
            } else {
                StringBuilder report = new StringBuilder();
                report.append(backupTime).append("\n");
                report.append("Bản sao lưu: ").append(uniqueSaved.size())
                        .append(" package hợp lệ · Đã cài: ").append(installedCount)
                        .append(" · Còn thiếu: ").append(missing.size());
                if (invalidEntries > 0) {
                    report.append(" · Mục lỗi: ").append(invalidEntries);
                }
                if (duplicateEntries > 0) {
                    report.append(" · Mục trùng lặp: ").append(duplicateEntries);
                }
                report.append("\n");
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

    /** Show the optional creation timestamp so users can identify an imported backup. */
    private String backupCreatedAtLabel(JSONObject backup) {
        Object value = backup.opt("createdAt");
        if (!(value instanceof Number)) return "Thời điểm sao lưu: không rõ";
        long timestamp = ((Number) value).longValue();
        if (timestamp <= 0L) return "Thời điểm sao lưu: không rõ";
        return "Thời điểm sao lưu: "
                + android.text.format.DateFormat.format("dd/MM/yyyy HH:mm", timestamp);
    }

    /** Reject malformed package identifiers from imported backup files. */
    private boolean isPlausiblePackageName(String packageName) {
        if (packageName == null || packageName.length() > 255) return false;
        String[] parts = packageName.split("\\.", -1);
        if (parts.length < 2) return false;
        for (String part : parts) {
            if (part.isEmpty() || !isPackageIdentifierStart(part.charAt(0))) return false;
            for (int i = 1; i < part.length(); i++) {
                char ch = part.charAt(i);
                if (!isPackageIdentifierStart(ch) && (ch < '0' || ch > '9')) return false;
            }
        }
        return true;
    }

    private boolean isPackageIdentifierStart(char ch) {
        return (ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z') || ch == '_';
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
