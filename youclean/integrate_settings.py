from pathlib import Path

source = Path("upstream/launcher/app/src/main/java/com/gathin/tvhome/LauncherSettingsActivity.java")
text = source.read_text(encoding="utf-8")

anchor = "        refresh();\n        if (config.launcherSettingsButton) reorderChoice.requestFocus();"
if "YouClean · Quản lý ứng dụng và sao lưu" not in text:
    if anchor not in text:
        raise SystemExit("Could not locate the launcher settings insertion point")

    block = """        android.widget.LinearLayout settingsRoot = (android.widget.LinearLayout)
                ((android.widget.ScrollView) ((android.view.ViewGroup)
                findViewById(android.R.id.content)).getChildAt(0)).getChildAt(0);

        android.widget.TextView managerEntry = new android.widget.TextView(this);
        managerEntry.setText("YouClean · Quản lý ứng dụng và sao lưu");
        managerEntry.setTextColor(android.graphics.Color.WHITE);
        managerEntry.setTextSize(18);
        managerEntry.setFocusable(true);
        managerEntry.setClickable(true);
        managerEntry.setPadding(24, 22, 24, 22);
        managerEntry.setBackgroundColor(android.graphics.Color.rgb(35, 45, 58));
        android.widget.LinearLayout.LayoutParams managerParams =
                new android.widget.LinearLayout.LayoutParams(-1, -2);
        managerParams.topMargin = 20;
        settingsRoot.addView(managerEntry, managerParams);
        managerEntry.setOnClickListener(v ->
                open(new Intent(this, YouCleanManagerActivity.class)));

        android.widget.TextView adbEntry = new android.widget.TextView(this);
        adbEntry.setText("YouClean · ADB Wi-Fi (hướng dẫn / kết nối thử nghiệm)");
        adbEntry.setTextColor(android.graphics.Color.WHITE);
        adbEntry.setTextSize(18);
        adbEntry.setFocusable(true);
        adbEntry.setClickable(true);
        adbEntry.setPadding(24, 22, 24, 22);
        adbEntry.setBackgroundColor(android.graphics.Color.rgb(35, 45, 58));
        android.widget.LinearLayout.LayoutParams adbParams =
                new android.widget.LinearLayout.LayoutParams(-1, -2);
        adbParams.topMargin = 10;
        settingsRoot.addView(adbEntry, adbParams);
        adbEntry.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("YouClean · ADB Wi-Fi")
                .setMessage("Chọn hướng dẫn ghép đôi hoặc mở kết nối thử nghiệm. Công cụ hiện chỉ đọc danh sách ứng dụng; không tắt hoặc gỡ package.")
                .setPositiveButton("Hướng dẫn", (dialog, which) ->
                        open(new Intent(this, YouCleanAdbGuideActivity.class)))
                .setNeutralButton("Kết nối thử nghiệm", (dialog, which) ->
                        open(new Intent(this, YouCleanAdbWifiActivity.class)))
                .setNegativeButton(android.R.string.cancel, null)
                .show());

"""
    text = text.replace(anchor, block + anchor, 1)
    source.write_text(text, encoding="utf-8")
    print("Integrated YouClean manager and ADB entry points into launcher Settings.")
else:
    print("YouClean settings entries already present; no duplicate injection.")
