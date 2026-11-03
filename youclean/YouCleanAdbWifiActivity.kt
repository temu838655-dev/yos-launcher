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
    private lateinit var pairButton: Button
    private lateinit var connectButton: Button
    private lateinit var keyProvider: FileKeyProvider
    private var operationInProgress = false
    private var activityDestroyed = false

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
    private fun setStatus(message: String) {
        if (!activityDestroyed && ::status.isInitialized) status.text = message
    }

    private fun setBusy(busy: Boolean) {
        operationInProgress = busy
        // A cancelled coroutine may still enter finally{} while the Activity
        // is being destroyed. Never touch Views after onDestroy().
        if (activityDestroyed) return
        if (::pairButton.isInitialized) pairButton.isEnabled = !busy
        if (::connectButton.isInitialized) connectButton.isEnabled = !busy
    }

    private fun validPort(value: String): Int? = value.toIntOrNull()?.takeIf { it in 1..65535 }

    private fun validIp(value: String): Boolean =
        value.isNotBlank() && android.util.Patterns.IP_ADDRESS.matcher(value).matches()

    /** Translate common network/TLS failures into actionable Wi-Fi ADB guidance. */
    private fun adbErrorMessage(action: String, error: Throwable): String {
        val detail = error.message ?: error.javaClass.simpleName
        val lower = detail.lowercase()
        val guidance = when {
            lower.contains("refused") || lower.contains("econnrefused") ->
                "TV đã từ chối cổng. Mở Wireless debugging trên TV và nhập lại đúng cổng hiện tại; cổng ghép đôi và cổng kết nối là hai cổng riêng."
            lower.contains("timeout") || lower.contains("timed out") ||
                lower.contains("unreachable") || lower.contains("no route") ->
                "Không tới được TV. Kiểm tra hai thiết bị cùng Wi-Fi, tắt VPN nếu đang dùng, kiểm tra IP TV và tránh mạng khách có chặn thiết bị nội bộ."
            lower.contains("auth") || lower.contains("unauthorized") ||
                lower.contains("pair") || lower.contains("tls") ->
                "Xác nhận TV đã ghép đôi với khóa YouClean này. Nếu mã hết hạn hoặc TV đã quên thiết bị, hãy ghép đôi lại bằng mã mới."
            else ->
                "Kiểm tra Wireless debugging còn bật, IP TV chưa đổi và cổng đang hiển thị trên TV."
        }
        return "$action thất bại: $detail\\n$guidance"
    }

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
        // Restore the last successfully used TV address and ports. No discovery scan is started.
        val saved = getSharedPreferences("youclean_adb", MODE_PRIVATE)
        host.setText(saved.getString("remote_ip", saved.getString("last_ip", "")) ?: "")
        pairingPort.setText(saved.getString("last_pairing_port", "") ?: "")
        connectPort.setText(saved.getString("last_connect_port", "") ?: "")
        val lastSavedAt = saved.getLong("remote_saved_at", 0L)
        val lastIp = saved.getString("remote_ip", null)
        val rememberedHint = if (!lastIp.isNullOrBlank() && lastSavedAt > 0L) {
            "TV đã đọc gần nhất: " + lastIp + " · " + android.text.format.DateFormat.format("dd/MM/yyyy HH:mm", lastSavedAt) + ". Kiểm tra TV còn cùng IP/cổng trước khi kết nối lại."
        } else {
            "Chưa lưu TV nào. Nhập IP và cổng hiển thị trong Wireless debugging."
        }

        listOf("IP của TV" to host, "Cổng ghép đôi" to pairingPort, "Mã ghép đôi" to pairingCode,
            "Cổng kết nối" to connectPort).forEach { (caption, edit) ->
            root.addView(label(caption, 14f))
            root.addView(edit, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)))
        }
        pairButton = button("1. Ghép đôi bằng mã 6 số") { pairDevice() }
        connectButton = button("2. Kết nối ADB và đọc toàn bộ ứng dụng") { connectAndList() }
        root.addView(pairButton)
        root.addView(connectButton)
        status = label(rememberedHint + "\nChưa có phiên ADB đang hoạt động.", 15f)
        root.addView(status)
        root.addView(label("Cổng ghép đôi và cổng kết nối là hai cổng khác nhau. Khóa ADB được lưu trong vùng riêng của YouClean. Bản thử nghiệm chỉ đọc danh sách package; không vô hiệu hóa hay gỡ ứng dụng.", 13f))
        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun pairDevice() {
        val ip = host.text.toString().trim()
        val port = validPort(pairingPort.text.toString().trim())
        val code = pairingCode.text.toString().trim()
        if (!validIp(ip) || port == null || !code.matches(Regex("\\d{6}"))) {
            setStatus("Kiểm tra địa chỉ IP, cổng ghép đôi (1–65535) và mã gồm đúng 6 chữ số.")
            return
        }
        if (operationInProgress) {
            setStatus("Một thao tác ADB khác đang chạy. Vui lòng đợi hoàn tất.")
            return
        }
        pairingCode.text.clear()
        setBusy(true)
        setStatus("Đang thực hiện ghép đôi TLS…")
        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) { AdbClient.pairTls(ip, port, code, keyProvider) }
                if (result.isSuccess) {
                    getSharedPreferences("youclean_adb", MODE_PRIVATE).edit()
                        .putString("last_ip", ip)
                        .putString("last_pairing_port", port.toString())
                        .apply()
                    setStatus("Ghép đôi thành công với " + ip + ". Mở lại Wireless debugging trên TV, nhập cổng kết nối đang hiển thị rồi bấm bước 2.")
                } else {
                    setStatus(adbErrorMessage("Ghép đôi", result.exceptionOrNull() ?: IllegalStateException("TV từ chối hoặc mã đã hết hạn")))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                setStatus(adbErrorMessage("Ghép đôi", e))
            } finally {
                setBusy(false)
            }
        }
    }

    private fun connectAndList() {
        val ip = host.text.toString().trim()
        val port = validPort(connectPort.text.toString().trim())
        if (!validIp(ip) || port == null) {
            setStatus("Nhập địa chỉ IP TV hợp lệ và cổng Wireless debugging (1–65535).")
            return
        }
        if (operationInProgress) {
            setStatus("Một thao tác ADB khác đang chạy. Vui lòng đợi hoàn tất.")
            return
        }
        setBusy(true)
        setStatus("Đang kết nối TLS và đọc toàn bộ package (ứng dụng người dùng + hệ thống)…")
        scope.launch {
            try {
                val output = withContext(Dispatchers.IO) {
                    AdbClient.connectTls(ip, port, keyProvider).use { client ->
                        client.shell("pm list packages").stdoutText
                    }
                }
                val packages = output.lineSequence()
                    .filter { it.startsWith("package:") }
                    .map { it.removePrefix("package:").trim() }
                    .filter { it.isNotEmpty() }
                    .distinct()
                    .toList()
                getSharedPreferences("youclean_adb", MODE_PRIVATE).edit()
                    .putString("remote_ip", ip)
                    .putString("last_ip", ip)
                    .putString("last_connect_port", port.toString())
                    .putStringSet("remote_packages", HashSet(packages))
                    .putLong("remote_saved_at", System.currentTimeMillis())
                    .apply()
                setStatus("Đã đọc thành công " + packages.size + " package từ TV " + ip + ".\n" +
                    "Danh sách đầy đủ đã lưu trong YouClean. Phiên ADB đã đóng sau khi đọc; đây không phải kết nối trực tiếp đang duy trì.\n\n" +
                    packages.take(120).map { "package:" + it }.joinToString("\n").ifBlank { "(TV không trả về package nào)" } +
                    if (packages.size > 120) "\n… Còn " + (packages.size - 120) + " package đã lưu." else "")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                setStatus(adbErrorMessage("Kết nối ADB", e))
            } finally {
                setBusy(false)
            }
        }
    }

    override fun onDestroy() {
        activityDestroyed = true
        job.cancel()
        super.onDestroy()
    }
}
