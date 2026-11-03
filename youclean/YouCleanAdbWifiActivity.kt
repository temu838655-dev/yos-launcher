package com.gathin.tvhome

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.*
import io.github.rhythmcache.adb.AdbClient
import io.github.rhythmcache.adb.FileKeyProvider
import kotlinx.coroutines.*
import java.io.File

/** Experimental real ADB pairing screen. Package operations are read-only. */
class YouCleanAdbWifiActivity : Activity() {
    private val job = SupervisorJob()
    private val scope = CoroutineScope(job + Dispatchers.Main)
    private lateinit var host: EditText
    private lateinit var pairingPort: EditText
    private lateinit var pairingCode: EditText
    private lateinit var connectPort: EditText
    private lateinit var status: TextView
    private lateinit var keyProvider: FileKeyProvider

    private fun dp(v: Int) = (v * resources.displayMetrics.density + 0.5f).toInt()
    private fun label(value: String, size: Float = 16f) = TextView(this).apply {
        text = value; textSize = size; setTextColor(Color.WHITE); setPadding(0, dp(5), 0, dp(5))
    }
    private fun field(hintText: String, input: Int = android.text.InputType.TYPE_CLASS_TEXT) = EditText(this).apply {
        hint = hintText; setHintTextColor(Color.LTGRAY); setTextColor(Color.WHITE); textSize = 17f
        inputType = input; setSingleLine(true); setPadding(dp(12), dp(8), dp(12), dp(8))
        setBackgroundColor(Color.rgb(40, 49, 62))
        setOnFocusChangeListener { view, focused ->
            view.setBackgroundColor(if (focused) Color.rgb(58, 92, 130) else Color.rgb(40, 49, 62))
            view.scaleX = if (focused) 1.015f else 1.0f
            view.scaleY = if (focused) 1.035f else 1.0f
        }
    }
    private fun button(caption: String, action: () -> Unit) = Button(this).apply {
        text = caption; isFocusable = true
        setOnFocusChangeListener { view, focused ->
            view.alpha = if (focused) 1.0f else 0.88f
            view.scaleX = if (focused) 1.025f else 1.0f
            view.scaleY = if (focused) 1.025f else 1.0f
        }
        setOnClickListener { action() }
    }
    private fun setStatus(message: String) { status.text = message }
    private fun validPort(value: String): Int? = value.toIntOrNull()?.takeIf { it in 1..65535 }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        val keyDir = File(filesDir, "adb-identity")
        keyDir.mkdirs()
        keyProvider = FileKeyProvider(File(keyDir, "adbkey"))

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
            setPadding(dp(36), dp(22), dp(36), dp(22))
            setBackgroundColor(Color.rgb(13, 17, 24))
        }
        root.addView(label("YouClean · ADB Wi-Fi (thử nghiệm)", 25f))
        root.addView(label("Ghép đôi ADB không dây Android 11+. TV và YouClean phải cùng mạng Wi-Fi. Bật Wireless debugging trên TV.", 15f))
        host = field("Địa chỉ IP TV, ví dụ 192.168.1.20")
        pairingPort = field("Cổng ghép đôi (Pairing port)", android.text.InputType.TYPE_CLASS_NUMBER)
        pairingCode = field("Mã ghép đôi 6 số", android.text.InputType.TYPE_CLASS_NUMBER)
        connectPort = field("Cổng kết nối ADB (Wireless debugging port)", android.text.InputType.TYPE_CLASS_NUMBER)
        host.imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_NEXT
        pairingPort.imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_NEXT
        pairingCode.imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_NEXT
        connectPort.imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_DONE
        host.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_NEXT) { pairingPort.requestFocus(); true } else false
        }
        pairingPort.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_NEXT) { pairingCode.requestFocus(); true } else false
        }
        pairingCode.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_NEXT) { connectPort.requestFocus(); true } else false
        }
        connectPort.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) { pairingCode.clearFocus(); true } else false
        }
        listOf("IP của TV" to host, "Cổng ghép đôi" to pairingPort, "Mã ghép đôi" to pairingCode,
            "Cổng kết nối" to connectPort).forEach { (caption, edit) ->
            root.addView(label(caption, 14f))
            root.addView(edit, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)))
        }
        root.addView(button("1. Ghép đôi bằng mã 6 số") { pairDevice() })
        root.addView(button("2. Kết nối ADB và đọc danh sách ứng dụng") { connectAndList() })
        status = label("Chưa kết nối. Chưa có lệnh nào được gửi tới TV.", 15f)
        root.addView(status)
        root.addView(label("Cổng ghép đôi và cổng kết nối là hai cổng khác nhau. Khóa ADB được lưu trong vùng riêng của YouClean. Bản thử nghiệm chỉ đọc danh sách package; không vô hiệu hóa hay gỡ ứng dụng.", 13f))
        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun pairDevice() {
        val ip = host.text.toString().trim()
        val port = validPort(pairingPort.text.toString().trim())
        val code = pairingCode.text.toString().trim()
        if (ip.isBlank() || port == null || !code.matches(Regex("\\d{6}"))) {
            setStatus("Kiểm tra IP, cổng ghép đôi và mã gồm đúng 6 chữ số.")
            return
        }
        pairingCode.text.clear()
        setStatus("Đang thực hiện ghép đôi TLS…")
        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) { AdbClient.pairTls(ip, port, code, keyProvider) }
                if (result.isSuccess) {
                    setStatus("Ghép đôi thành công. Mở lại Wireless debugging trên TV, nhập cổng kết nối đang hiển thị rồi bấm bước 2.")
                } else {
                    setStatus("Ghép đôi thất bại: " + (result.exceptionOrNull()?.message ?: "TV từ chối hoặc mã đã hết hạn"))
                }
            } catch (e: Exception) {
                setStatus("Lỗi ghép đôi: " + (e.message ?: e.javaClass.simpleName))
            }
        }
    }

    private fun connectAndList() {
        val ip = host.text.toString().trim()
        val port = validPort(connectPort.text.toString().trim())
        if (ip.isBlank() || port == null) {
            setStatus("Nhập IP TV và cổng Wireless debugging hợp lệ.")
            return
        }
        setStatus("Đang kết nối TLS và đọc danh sách ứng dụng…")
        scope.launch {
            try {
                val output = withContext(Dispatchers.IO) {
                    AdbClient.connectTls(ip, port, keyProvider).use { client ->
                        client.shell("pm list packages -3").stdoutText
                    }
                }
                val packages = output.lineSequence().filter { it.startsWith("package:") }.toList()
                setStatus("Đã kết nối ADB. Tìm thấy " + packages.size + " ứng dụng:\n" +
                    packages.take(80).joinToString("\n").ifBlank { "(TV không trả về package nào)" })
            } catch (e: Exception) {
                setStatus("Kết nối thất bại: " + (e.message ?: e.javaClass.simpleName) +
                    ". Kiểm tra lại cổng kết nối và ghép đôi.")
            }
        }
    }

    override fun onDestroy() { job.cancel(); super.onDestroy() }
}
