package com.gathin.tvhome;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Safe on-device guide for enabling Android Wireless debugging. Does not claim an ADB session exists. */
public final class YouCleanAdbGuideActivity extends Activity {
    private int dp(float v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private TextView text(String value, int size, int color) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setPadding(0, dp(5), 0, dp(5));
        return t;
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(38), dp(24), dp(38), dp(20));
        root.setBackgroundColor(Color.rgb(13, 17, 24));

        TextView title = text("YouClean · ADB Wi-Fi", 26, Color.WHITE);
        title.setTypeface(null, 1);
        root.addView(title);

        root.addView(text("Kết nối không dây cần bật và xác nhận trên TV. YouClean chưa tự ghép đôi hoặc chạy lệnh ADB trong màn hình hướng dẫn này.", 15, Color.rgb(190, 202, 218)));

        if (Build.VERSION.SDK_INT >= 30) {
            root.addView(text("Android 11 trở lên", 20, Color.WHITE));
            root.addView(text("1. Kết nối TV và điện thoại/máy tính cùng mạng Wi-Fi.\n"
                    + "2. Mở Tùy chọn nhà phát triển trên TV.\n"
                    + "3. Bật Gỡ lỗi không dây (Wireless debugging).\n"
                    + "4. Chọn Ghép nối thiết bị bằng mã ghép nối (Pair device with pairing code).\n"
                    + "5. Android hiển thị địa chỉ IP, cổng ghép đôi và mã 6 số. Cổng ghép đôi khác cổng kết nối ADB.\n"
                    + "6. Chỉ nhập mã trên thiết bị mà bạn tin cậy; mã có thời hạn.", 15, Color.WHITE));
        } else {
            root.addView(text("Android 8–10", 20, Color.WHITE));
            root.addView(text("1. Kết nối TV và thiết bị điều khiển cùng mạng Wi-Fi.\n"
                    + "2. Bật Tùy chọn nhà phát triển và Gỡ lỗi USB.\n"
                    + "3. Một số firmware có mục Gỡ lỗi ADB qua mạng hoặc Network debugging.\n"
                    + "4. Chỉ dùng địa chỉ IP và cổng mà chính TV hiển thị; không đoán cổng mặc định.", 15, Color.WHITE));
        }

        Button settings = new Button(this);
        settings.setText("Mở cài đặt nhà phát triển");
        settings.setAllCaps(false);
        settings.setTextSize(16);
        settings.setFocusable(true);
        settings.setFocusableInTouchMode(true);
        settings.setOnFocusChangeListener((v, focused) -> {
            v.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                    focused ? Color.rgb(42, 105, 170) : Color.rgb(48, 57, 70)));
            v.setScaleX(focused ? 1.025f : 1.0f);
            v.setScaleY(focused ? 1.025f : 1.0f);
            v.setElevation(focused ? dp(8) : dp(2));
        });
        settings.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS));
            } catch (Exception e) {
                try { startActivity(new Intent(Settings.ACTION_SETTINGS)); }
                catch (Exception ignored) {}
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(56));
        lp.topMargin = dp(16);
        root.addView(settings, lp);

        root.addView(text("Lưu ý an toàn: tắt Gỡ lỗi không dây khi không sử dụng. Không chia sẻ mã ghép đôi hoặc cho người lạ truy cập mạng TV.", 14, Color.rgb(255, 210, 140)));
        setContentView(root);
        settings.requestFocus();
    }
}