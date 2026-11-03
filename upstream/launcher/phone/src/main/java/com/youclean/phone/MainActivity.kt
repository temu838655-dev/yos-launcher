package com.youclean.phone

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
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
    private lateinit var packageSearch: EditText
    private lateinit var status: TextView
    private lateinit var packagesView: TextView
    private lateinit var keyProvider: FileKeyProvider
    private var packageRows: List<String> = emptyList()
    private var systemPackages: Set<String> = emptySet()
    private val protectedPackages = setOf(
        "android",
        "com.android.systemui",
        "com.android.settings",
        "com.android.packageinstaller",
        "com.google.android.gms",
        "com.google.android.gsf",
        "com.google.android.permissioncontroller",
        "com.android.permissioncontroller"
    )
    private var showingAllPackages = false
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
        restoreConnectionFields()
        listOf("2. Nhập thông tin TV" to ip, "Cổng ghép đôi" to pairPort, "Mã 6 số trên TV" to pairCode, "Cổng kết nối hiển thị trên TV" to connectPort).forEach { (caption, edit) ->
            root.addView(label(caption, 13f, Color.LTGRAY).apply { setPadding(0, dp(10), 0, dp(4)) })
            root.addView(edit, LinearLayout.LayoutParams(-1, dp(52)))
        }
        root.addView(action("Ghép đôi với TV") { pair() }, LinearLayout.LayoutParams(-1, dp(50)).apply { topMargin = dp(12) })
        root.addView(action("Kết nối và quét ứng dụng người dùng") { connectAndList(false) }, LinearLayout.LayoutParams(-1, dp(50)).apply { topMargin = dp(6) })
        root.addView(action("Quét tất cả ứng dụng (kể cả hệ thống)") { connectAndList(true) }, LinearLayout.LayoutParams(-1, dp(50)).apply { topMargin = dp(6) })
        status = label("Chưa kết nối TV.", 14f, Color.rgb(255, 210, 140))
        root.addView(status.apply { setPadding(0, dp(12), 0, dp(8)) })
        root.addView(label("Danh sách ứng dụng", 18f).apply { setTypeface(null, 1); setPadding(0, dp(8), 0, dp(6)) })
        packageSearch = field("Tìm theo tên package…")
        packageSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { renderPackages() }
            override fun afterTextChanged(s: Editable?) {}
        })
        root.addView(packageSearch, LinearLayout.LayoutParams(-1, dp(48)))
        packagesView = label("Chưa có dữ liệu. Bản thử nghiệm chỉ đọc package. Chưa package nào được xác nhận an toàn để tắt; không tự ý vô hiệu hóa ứng dụng.", 13f, Color.rgb(205, 214, 226))
        packagesView.setPadding(0, dp(10), 0, dp(4))
        packagesView.setTextIsSelectable(true)
        root.addView(packagesView)
        root.addView(action("Sao chép danh sách đang hiển thị") { copyPackageList() }, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(8) })
        root.addView(action("Xóa thông tin nhập") {
            ip.text.clear(); pairPort.text.clear(); pairCode.text.clear(); connectPort.text.clear()
            getSharedPreferences("youclean_connection", MODE_PRIVATE).edit().clear().apply()
            packageSearch.text.clear(); packageRows = emptyList(); systemPackages = emptySet(); showingAllPackages = false
            packagesView.text = "Chưa có dữ liệu."; status("Đã xóa các trường nhập.")
        }, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(14) })
        setContentView(ScrollView(this).apply { isFillViewport = true; addView(root) })
    }

    private fun copyPackageList() {
        val content = packagesView.text?.toString().orEmpty()
        if (content.isBlank() || content.startsWith("Chưa có dữ liệu")) {
            Toast.makeText(this, "Chưa có danh sách để sao chép.", Toast.LENGTH_SHORT).show()
            return
        }
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("YouClean package list", content))
        Toast.makeText(this, "Đã sao chép danh sách package.", Toast.LENGTH_SHORT).show()
    }

    private fun restoreConnectionFields() {
        val prefs = getSharedPreferences("youclean_connection", MODE_PRIVATE)
        ip.setText(prefs.getString("ip", ""))
        pairPort.setText(prefs.getString("pair_port", ""))
        connectPort.setText(prefs.getString("connect_port", ""))
    }

    private fun saveConnectionFields() {
        getSharedPreferences("youclean_connection", MODE_PRIVATE).edit()
            .putString("ip", ip.text.toString().trim())
            .putString("pair_port", pairPort.text.toString().trim())
            .putString("connect_port", connectPort.text.toString().trim())
            .apply()
    }

    private fun renderPackages() {
        val query = packageSearch.text.toString().trim()
        val filtered = packageRows.filter { it.contains(query, ignoreCase = true) }
        packagesView.text = when {
            packageRows.isEmpty() -> "Chưa có dữ liệu. Bản thử nghiệm chỉ đọc package; chưa có thao tác tắt hoặc gỡ ứng dụng."
            filtered.isEmpty() -> "Không tìm thấy package phù hợp."
            else -> {
                val userCount = packageRows.count { it !in systemPackages }
                val systemCount = packageRows.size - userCount
                val protectedCount = packageRows.count { it in protectedPackages }
                val unreviewedCount = packageRows.size - protectedCount
                "Tổng: ${packageRows.size} | Người dùng: $userCount | Hệ thống: $systemCount\n" +
                    "Cốt lõi cần giữ: $protectedCount | Chưa đánh giá: $unreviewedCount\n" +
                    "Phân loại chỉ mang tính nhận diện, KHÔNG có nghĩa package chưa đánh giá là an toàn để tắt.\n" +
                    filtered.mapIndexed { index, name ->
                        val type = when {
                            name in protectedPackages -> "[CỐT LÕI - GIỮ LẠI]"
                            name in systemPackages -> "[HỆ THỐNG - CHƯA ĐÁNH GIÁ]"
                            else -> "[CÀI THÊM - CHƯA ĐÁNH GIÁ]"
                        }
                        "${index + 1}. $type $name"
                    }.joinToString("\n")
            }
        }
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
        saveConnectionFields()
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

    private fun connectAndList(includeSystem: Boolean) {
        val host = ip.text.toString().trim()
        val p = port(connectPort.text.toString())
        if (host.isBlank() || p == null) { status("Nhập IP TV và cổng Wireless debugging hợp lệ."); return }
        showingAllPackages = includeSystem
        saveConnectionFields()
        setBusy(true, if (includeSystem) "Đang phân loại package hệ thống và ứng dụng người dùng…" else "Đang đọc ứng dụng người dùng…")
        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    AdbClient.connectTls(host, p, keyProvider).use { client ->
                        val userOutput = client.shell("pm list packages -3").stdoutText
                        val users = userOutput.lineSequence().filter { it.startsWith("package:") }
                            .map { it.removePrefix("package:").trim() }.filter { it.isNotEmpty() }.toSet()
                        val systems = if (includeSystem) {
                            client.shell("pm list packages -s").stdoutText.lineSequence()
                                .filter { it.startsWith("package:") }
                                .map { it.removePrefix("package:").trim() }.filter { it.isNotEmpty() }.toSet()
                        } else emptySet()
                        Pair(if (includeSystem) (users + systems).sorted() else users.sorted(), systems)
                    }
                }
                packageRows = result.first
                systemPackages = result.second
                renderPackages()
                val userCount = packageRows.count { it !in systemPackages }
                val systemCount = packageRows.size - userCount
                status("Đã kết nối. $userCount ứng dụng người dùng, $systemCount package hệ thống. Chỉ đọc dữ liệu, chưa thay đổi ứng dụng trên TV.")
            } catch (e: Exception) { status("Kết nối thất bại: " + (e.message ?: e.javaClass.simpleName) + ". Kiểm tra lại IP, cổng và trạng thái ghép đôi.") }
            finally { busy = false }
        }
    }

    override fun onDestroy() { job.cancel(); super.onDestroy() }
}
