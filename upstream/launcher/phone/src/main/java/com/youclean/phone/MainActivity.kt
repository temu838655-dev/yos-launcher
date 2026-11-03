package com.youclean.phone

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.widget.*
import io.github.rhythmcache.adb.AdbClient
import io.github.rhythmcache.adb.FileKeyProvider
import kotlinx.coroutines.*
import java.io.File

/** Phone companion for managing an Android TV over Wireless debugging. Read-only package listing. */
class MainActivity : Activity() {
    private val job = SupervisorJob()
    private val scope = CoroutineScope(job + Dispatchers.Main)
    private lateinit var ip: EditText
    private lateinit var pairPort: EditText
    private lateinit var pairCode: EditText
    private lateinit var connectPort: EditText
    private lateinit var status: TextView
    private lateinit var packagesView: TextView
    private lateinit var keyProvider: FileKeyProvider
    private var busy = false

    private fun dp(v: Int) = (v * resources.displayMetrics.density + .5f).toInt()
    private fun label(value: String, size: Float = 14f, color: Int = Color.WHITE) =
        TextView(this).apply { text = value; textSize = size; setTextColor(color) }
    private fun field(hintText: String, numeric: Boolean = false) = EditText(this).apply {
        hint = hintText; setSingleLine(true); textSize = 16f
        setTextColor(Color.WHITE); setHintTextColor(Color.LTGRAY)
        inputType = if (numeric) android.text.InputType.TYPE_CLASS_NUMBER else android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        setPadding(dp(14), dp(10), dp(14), dp(10)); setBackgroundColor(Color.rgb(38, 48, 63))
    }
    private fun action(title: String, onTap: () -> Unit) = Button(this).apply {
        text = title; isAllCaps = false; textSize = 15f
        setOnClickListener { if (!busy) onTap() }
    }
    private fun status(message: String) { status.text = message }
    private fun port(value: String) = value.trim().toIntOrNull()?.takeIf { it in 1..65535 }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(10, 14, 21)
        window.navigationBarColor = Color.rgb(10, 14, 21)
        val keyDir = File(filesDir, "adb-identity").apply { mkdirs() }
        keyProvider = FileKeyProvider(File(keyDir, "adbkey"))
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(18), dp(20), dp(20))
            setBackgroundColor(Color.rgb(13, 17, 24))
        }
        root.addView(label("YouClean", 28f).apply { setTypeface(null, 1) })
        root.addView(label("Điện thoại điều khiển Android TV / Google TV", 14f, Color.LTGRAY))
        root.addView(label("1. Chuẩn bị trên TV", 18f).apply { setPadding(0, dp(18), 0, dp(5)) })
        root.addView(label("Bật Tùy chọn nhà phát triển → Gỡ lỗi không dây. Chọn “Ghép nối thiết bị bằng mã ghép nối”. Điện thoại và TV cần cùng mạng Wi-Fi.", 14f, Color.rgb(205, 214, 226)))
        ip = field("Địa chỉ IP TV, ví dụ 192.168.1.20")
        pairPort = field("Cổng ghép đôi (Pairing port)", true)
        pairCode = field("Mã ghép đôi 6 chữ số", true)
        connectPort = field("Cổng Wireless debugging (Connect port)", true)
        listOf("2. Nhập thông tin TV" to ip, "Cổng ghép đôi" to pairPort, "Mã 6 số trên TV" to pairCode, "Cổng kết nối hiển thị trên TV" to connectPort).forEach { (caption, edit) ->
            root.addView(label(caption, 13f, Color.LTGRAY).apply { setPadding(0, dp(10), 0, dp(4)) })
            root.addView(edit, LinearLayout.LayoutParams(-1, dp(52)))
        }
        root.addView(action("Ghép đôi với TV") { pair() }, LinearLayout.LayoutParams(-1, dp(50)).apply { topMargin = dp(12) })
        root.addView(action("Kết nối và quét ứng dụng trên TV") { connectAndList() }, LinearLayout.LayoutParams(-1, dp(50)).apply { topMargin = dp(6) })
        status = label("Chưa kết nối TV.", 14f, Color.rgb(255, 210, 140))
        root.addView(status.apply { setPadding(0, dp(12), 0, dp(8)) })
        root.addView(label("Danh sách ứng dụng", 18f).apply { setTypeface(null, 1); setPadding(0, dp(8), 0, dp(6)) })
        packagesView = label("Chưa có dữ liệu. Bản thử nghiệm chỉ đọc package, chưa tắt hoặc gỡ ứng dụng.", 13f, Color.rgb(205, 214, 226))
        root.addView(packagesView)
        root.addView(action("Xóa thông tin nhập") {
            ip.text.clear(); pairPort.text.clear(); pairCode.text.clear(); connectPort.text.clear()
            packagesView.text = "Chưa có dữ liệu."; status("Đã xóa các trường nhập.")
        }, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(14) })
        setContentView(ScrollView(this).apply { isFillViewport = true; addView(root) })
    }

    private fun setBusy(value: Boolean, message: String) { busy = value; status(message) }

    private fun pair() {
        val host = ip.text.toString().trim()
        val p = port(pairPort.text.toString())
        val code = pairCode.text.toString().trim()
        if (host.isBlank() || p == null || !code.matches(Regex("\\d{6}"))) {
            status("Vui lòng kiểm tra IP, cổng ghép đôi và mã đúng 6 chữ số."); return
        }
        pairCode.text.clear()
        setBusy(true, "Đang ghép đôi bảo mật với TV…")
        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) { AdbClient.pairTls(host, p, code, keyProvider) }
                if (result.isSuccess) status("Ghép đôi thành công. Trên TV hãy quay lại Wireless debugging, nhập cổng kết nối rồi bấm “Kết nối và quét ứng dụng”.")
                else status("Ghép đôi thất bại: " + (result.exceptionOrNull()?.message ?: "TV từ chối hoặc mã đã hết hạn."))
            } catch (e: Exception) { status("Lỗi ghép đôi: " + (e.message ?: e.javaClass.simpleName)) }
            finally { busy = false }
        }
    }

    private fun connectAndList() {
        val host = ip.text.toString().trim()
        val p = port(connectPort.text.toString())
        if (host.isBlank() || p == null) { status("Nhập IP TV và cổng Wireless debugging hợp lệ."); return }
        setBusy(true, "Đang kết nối và đọc danh sách package…")
        scope.launch {
            try {
                val output = withContext(Dispatchers.IO) {
                    AdbClient.connectTls(host, p, keyProvider).use { it.shell("pm list packages -3").stdoutText }
                }
                val rows = output.lineSequence().filter { it.startsWith("package:") }.map { it.removePrefix("package:").trim() }.filter { it.isNotEmpty() }.toList()
                packagesView.text = if (rows.isEmpty()) "TV không trả về ứng dụng người dùng." else rows.mapIndexed { index, name -> "${index + 1}. $name" }.joinToString("\\n")
                status("Đã kết nối. Tìm thấy ${rows.size} ứng dụng người dùng.")
            } catch (e: Exception) { status("Kết nối thất bại: " + (e.message ?: e.javaClass.simpleName) + ". Kiểm tra lại IP, cổng và trạng thái ghép đôi.") }
            finally { busy = false }
        }
    }

    override fun onDestroy() { job.cancel(); super.onDestroy() }
}
