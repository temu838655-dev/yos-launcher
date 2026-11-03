package com.youclean.phone

import android.app.Activity
import android.content.ClipData
import android.content.Intent
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
    private lateinit var packageFilter: Spinner
    private lateinit var status: TextView
    private lateinit var deviceInfo: TextView
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
        listOf(ip, pairPort, connectPort).forEach { input ->
            input.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    saveConnectionFields()
                }
                override fun afterTextChanged(s: Editable?) {}
            })
        }
        listOf("2. Nhập thông tin TV" to ip, "Cổng ghép đôi" to pairPort, "Mã 6 số trên TV" to pairCode, "Cổng kết nối hiển thị trên TV" to connectPort).forEach { (caption, edit) ->
            root.addView(label(caption, 13f, Color.LTGRAY).apply { setPadding(0, dp(10), 0, dp(4)) })
            root.addView(edit, LinearLayout.LayoutParams(-1, dp(52)))
        }
        root.addView(action("Ghép đôi với TV") { pair() }, LinearLayout.LayoutParams(-1, dp(50)).apply { topMargin = dp(12) })
        root.addView(action("Kết nối và quét ứng dụng người dùng") { connectAndList(false) }, LinearLayout.LayoutParams(-1, dp(50)).apply { topMargin = dp(6) })
        root.addView(action("Quét tất cả ứng dụng (kể cả hệ thống)") { connectAndList(true) }, LinearLayout.LayoutParams(-1, dp(50)).apply { topMargin = dp(6) })
        status = label("Chưa kết nối TV.", 14f, Color.rgb(255, 210, 140))
        root.addView(status.apply { setPadding(0, dp(12), 0, dp(8)) })
        deviceInfo = label("Thông tin thiết bị: chưa đọc.", 13f, Color.rgb(180, 200, 220))
        root.addView(deviceInfo.apply { setPadding(0, dp(4), 0, dp(8)) })
        root.addView(action("Sao chép thông tin thiết bị") { copyDeviceInfo() },
            LinearLayout.LayoutParams(-1, dp(44)).apply { bottomMargin = dp(6) })
        root.addView(label("Danh sách ứng dụng", 18f).apply { setTypeface(null, 1); setPadding(0, dp(8), 0, dp(6)) })
        packageSearch = field("Tìm theo tên package…")
        packageSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { renderPackages() }
            override fun afterTextChanged(s: Editable?) {}
        })
        root.addView(packageSearch, LinearLayout.LayoutParams(-1, dp(48)))
        packageFilter = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item,
                listOf("Tất cả package", "Ứng dụng người dùng", "Package hệ thống", "Package đã nhận diện cần giữ", "Package chưa đánh giá"))
            onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
                override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) { renderPackages() }
            }
        }
        root.addView(packageFilter, LinearLayout.LayoutParams(-1, dp(48)))
        packagesView = label("Chưa có dữ liệu. Bản thử nghiệm chỉ đọc package. Chưa package nào được xác nhận an toàn để tắt; không tự ý vô hiệu hóa ứng dụng.", 13f, Color.rgb(205, 214, 226))
        packagesView.setPadding(0, dp(10), 0, dp(4))
        packagesView.setTextIsSelectable(true)
        packagesView.setOnClickListener { openPackagePicker() }
        root.addView(packagesView)
        root.addView(label("Chạm vào khu vực danh sách để xem chi tiết từng package.", 12f, Color.LTGRAY).apply {
            setPadding(0, dp(2), 0, dp(4))
        })
        root.addView(action("Sao chép danh sách đang hiển thị") { copyPackageList() }, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(8) })
        root.addView(action("Chia sẻ danh sách package") { sharePackageList() }, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(6) })
        root.addView(action("Xuất báo cáo thiết bị và package") { shareDiagnosticReport() },
            LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(6) })
        root.addView(action("Xóa thông tin nhập") {
            ip.text.clear(); pairPort.text.clear(); pairCode.text.clear(); connectPort.text.clear()
            getSharedPreferences("youclean_connection", MODE_PRIVATE).edit().clear().apply()
            packageSearch.text.clear(); packageFilter.setSelection(0); packageRows = emptyList(); systemPackages = emptySet(); showingAllPackages = false
            packagesView.text = "Chưa có dữ liệu."; deviceInfo.text = "Thông tin thiết bị: chưa đọc."; status("Đã xóa các trường nhập.")
        }, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(14) })
        setContentView(ScrollView(this).apply { isFillViewport = true; addView(root) })
    }

    private fun copyDeviceInfo() {
        val content = deviceInfo.text?.toString().orEmpty()
        if (content.isBlank() || content.contains("chưa đọc", ignoreCase = true)) {
            Toast.makeText(this, "Chưa có thông tin thiết bị để sao chép.", Toast.LENGTH_SHORT).show()
            return
        }
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("YouClean device info", content))
        Toast.makeText(this, "Đã sao chép thông tin thiết bị.", Toast.LENGTH_SHORT).show()
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

    private fun sharePackageList() {
        val content = packagesView.text?.toString().orEmpty()
        if (content.isBlank() || content.startsWith("Chưa có dữ liệu")) {
            Toast.makeText(this, "Chưa có danh sách để chia sẻ.", Toast.LENGTH_SHORT).show()
            return
        }
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Danh sách package Android TV - YouClean")
            putExtra(Intent.EXTRA_TEXT, content)
        }
        startActivity(Intent.createChooser(sendIntent, "Chia sẻ danh sách package"))
    }

    private fun shareDiagnosticReport() {
        if (packageRows.isEmpty()) {
            Toast.makeText(this, "Hãy kết nối TV và quét package trước.", Toast.LENGTH_SHORT).show()
            return
        }
        val report = buildString {
            appendLine("BÁO CÁO CHẨN ĐOÁN YOUCLEAN")
            appendLine("Phiên bản ứng dụng: " + (packageManager.getPackageInfo(packageName, 0).versionName ?: "Không rõ"))
            appendLine("Thời điểm xuất: " + java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date()))
            appendLine()
            appendLine(deviceInfo.text?.toString().orEmpty())
            appendLine()
            appendLine("Phạm vi quét: " + if (showingAllPackages) "Ứng dụng người dùng và package hệ thống" else "Chỉ ứng dụng người dùng")
            appendLine("Tổng package đã đọc: " + packageRows.size)
            appendLine("Package hệ thống: " + systemPackages.size)
            appendLine("Ứng dụng người dùng: " + packageRows.count { it !in systemPackages })
            appendLine("Đã nhận diện trong danh sách bảo vệ: " + packageRows.count { it in protectedPackages })
            appendLine("Chưa được đánh giá: " + packageRows.count { it !in protectedPackages })
            appendLine()
            appendLine("DANH SÁCH PACKAGE")
            packageRows.sorted().forEach { name ->
                val category = when {
                    name in protectedPackages -> "ĐÃ NHẬN DIỆN CẦN GIỮ"
                    name in systemPackages -> "HỆ THỐNG - CHƯA ĐÁNH GIÁ"
                    else -> "CÀI THÊM - CHƯA ĐÁNH GIÁ"
                }
                appendLine("$name [$category]")
            }
            appendLine()
            appendLine("GIỚI HẠN: Danh sách bảo vệ hiện tại chỉ là tập nhận diện cơ bản, không bao phủ mọi hãng và firmware. Nhãn 'chưa được đánh giá' không có nghĩa là không quan trọng.")
            appendLine("QUYỀN RIÊNG TƯ: Báo cáo có thể chứa địa chỉ IP TV và tên package. Hãy kiểm tra nội dung trước khi gửi. Mã ghép đôi và khóa ADB riêng không được đưa vào báo cáo.")
            appendLine("LƯU Ý: Báo cáo chỉ chứa thông tin đọc qua ADB. Package chưa đánh giá không được xác nhận an toàn để tắt. Không có thao tác thay đổi nào được thực hiện trên TV.")
        }
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Báo cáo chẩn đoán YouClean")
            putExtra(Intent.EXTRA_TEXT, report)
        }
        startActivity(Intent.createChooser(sendIntent, "Xuất báo cáo YouClean"))
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
        val selectedFilter = if (::packageFilter.isInitialized) packageFilter.selectedItemPosition else 0
        val filtered = packageRows.filter { name ->
            val matchesQuery = name.contains(query, ignoreCase = true)
            val matchesFilter = when (selectedFilter) {
                1 -> name !in systemPackages
                2 -> name in systemPackages
                3 -> name in protectedPackages
                4 -> name !in protectedPackages
                else -> true
            }
            matchesQuery && matchesFilter
        }
        packagesView.text = when {
            packageRows.isEmpty() -> "Chưa có dữ liệu. Bản thử nghiệm chỉ đọc package; chưa có thao tác tắt hoặc gỡ ứng dụng."
            filtered.isEmpty() -> "Không tìm thấy package phù hợp."
            else -> {
                val userCount = packageRows.count { it !in systemPackages }
                val systemCount = packageRows.size - userCount
                val protectedCount = packageRows.count { it in protectedPackages }
                val unreviewedCount = packageRows.size - protectedCount
                "Tổng: ${packageRows.size} | Người dùng: $userCount | Hệ thống: $systemCount\n" +
                    "Đã nhận diện cần giữ (danh sách một phần): $protectedCount | Chưa đánh giá: $unreviewedCount\n" +
                    "CẢNH BÁO: danh sách package cốt lõi chưa đầy đủ. Package không nằm trong danh sách bảo vệ vẫn có thể quan trọng với TV.\n" +
                    "Không có package nào được xác nhận an toàn để tắt; bản này chỉ đọc dữ liệu, không vô hiệu hóa hoặc gỡ ứng dụng.\n" +
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

    private fun visiblePackages(): List<String> {
        val query = packageSearch.text.toString().trim()
        val selectedFilter = if (::packageFilter.isInitialized) packageFilter.selectedItemPosition else 0
        return packageRows.filter { name ->
            val matchesQuery = name.contains(query, ignoreCase = true)
            val matchesFilter = when (selectedFilter) {
                1 -> name !in systemPackages
                2 -> name in systemPackages
                3 -> name in protectedPackages
                4 -> name !in protectedPackages
                else -> true
            }
            matchesQuery && matchesFilter
        }
    }

    private fun openPackagePicker() {
        val names = visiblePackages()
        if (names.isEmpty()) {
            Toast.makeText(this, "Không có package phù hợp để xem.", Toast.LENGTH_SHORT).show()
            return
        }
        android.app.AlertDialog.Builder(this)
            .setTitle("Chọn package để xem chi tiết")
            .setItems(names.toTypedArray()) { _, which -> showPackageDetails(names[which]) }
            .setNegativeButton("Đóng", null)
            .show()
    }

    private fun showPackageDetails(name: String) {
        val type = when {
            name in protectedPackages -> "ĐÃ NHẬN DIỆN CẦN GIỮ (danh sách bảo vệ một phần)"
            name in systemPackages -> "PACKAGE HỆ THỐNG - CHƯA ĐÁNH GIÁ"
            else -> "ỨNG DỤNG NGƯỜI DÙNG - CHƯA ĐÁNH GIÁ"
        }
        if (!name.matches(Regex("[A-Za-z0-9_.]+"))) {
            Toast.makeText(this, "Tên package không hợp lệ.", Toast.LENGTH_SHORT).show()
            return
        }
        val host = ip.text.toString().trim()
        val connect = port(connectPort.text.toString())
        if (host.isBlank() || connect == null) {
            showPackageDetailsDialog(name, type, "Chưa có kết nối để đọc thông tin chi tiết.")
            return
        }
        setBusy(true, "Đang đọc thông tin package $name…")
        scope.launch {
            try {
                val details = withContext(Dispatchers.IO) {
                    AdbClient.connectTls(host, connect, keyProvider).use { client ->
                        val pathOutput = client.shell("pm path $name").stdoutText
                        val dump = client.shell("dumpsys package $name").stdoutText
                        fun value(pattern: String): String =
                            Regex(pattern, RegexOption.MULTILINE).find(dump)?.groupValues?.getOrNull(1)?.trim()
                                ?.takeIf { it.isNotEmpty() } ?: "Không rõ"
                        val paths = pathOutput.lineSequence()
                            .filter { it.startsWith("package:") }
                            .map { it.removePrefix("package:").trim() }
                            .filter { it.isNotEmpty() }
                            .distinct()
                            .toList()
                        buildString {
                            appendLine("Số tệp APK: " + paths.size)
                            appendLine("Kiểu cài đặt: " + when {
                                paths.size > 1 -> "Nhiều APK / split package"
                                paths.size == 1 -> "Một APK hoặc base APK"
                                else -> "Không xác định"
                            })
                            appendLine("Tên phiên bản: " + value("^\\s*versionName=([^\\r\\n]+)"))
                            appendLine("Mã phiên bản: " + value("^\\s*versionCode=([^\\s]+)"))
                            appendLine("UID ứng dụng: " + value("^\\s*(?:userId|appId)=([^\\s]+)"))
                            appendLine("Đường dẫn cài đặt: " + value("^\\s*codePath=([^\\r\\n]+)"))
                            appendLine("Thư mục dữ liệu: " + value("^\\s*dataDir=([^\\r\\n]+)"))
                            appendLine("Cờ package: " + value("^\\s*pkgFlags=\\[([^\\]]*)\\]"))
                            appendLine("Nguồn cài đặt: " + value("^\\s*installerPackageName=([^\\r\\n]+)"))
                            appendLine("Cài lần đầu: " + value("^\\s*firstInstallTime=([^\\r\\n]+)"))
                            appendLine("Cập nhật gần nhất: " + value("^\\s*lastUpdateTime=([^\\r\\n]+)"))
                            appendLine("Đường dẫn APK:")
                            append(if (paths.isEmpty()) "Không đọc được đường dẫn. Phản hồi TV: " + pathOutput.trim().take(500).ifBlank { "trống" } else paths.joinToString("\n"))
                        }
                    }
                }
                showPackageDetailsDialog(name, type, details)
                status("Đã đọc chi tiết package $name. Không thay đổi ứng dụng trên TV.")
            } catch (e: Exception) {
                showPackageDetailsDialog(name, type, "Không đọc được chi tiết từ TV: " +
                    (e.message ?: e.javaClass.simpleName) + "\nBạn vẫn có thể xem tên và phân loại package.")
                status("Không đọc được chi tiết package $name.")
            } finally { busy = false }
        }
    }

    private fun showPackageDetailsDialog(name: String, type: String, technicalInfo: String) {
        val detail = "Tên package:\n$name\n\nPhân loại:\n$type\n\n" +
            "Thông tin kỹ thuật:\n$technicalInfo\n\n" +
            "Đánh giá an toàn: CHƯA XÁC MINH. Không thể kết luận package an toàn để tắt chỉ dựa vào tên.\n" +
            "YouClean hiện chỉ đọc dữ liệu; không tắt, gỡ hoặc thay đổi ứng dụng trên TV."
        android.app.AlertDialog.Builder(this)
            .setTitle("Chi tiết package")
            .setMessage(detail)
            .setPositiveButton("Sao chép tên") { _, _ ->
                val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Android package", name))
                Toast.makeText(this, "Đã sao chép tên package.", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Đóng", null)
            .show()
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
                        suspend fun property(name: String): String =
                            client.shell("getprop $name").stdoutText.trim().ifBlank { "Không rõ" }
                        val manufacturer = property("ro.product.manufacturer")
                        val model = property("ro.product.model")
                        val device = property("ro.product.device")
                        val androidVersion = property("ro.build.version.release")
                        val sdk = property("ro.build.version.sdk")
                        val info = "Thiết bị: $manufacturer $model\nMã thiết bị: $device\nAndroid: $androidVersion (API $sdk)\nĐịa chỉ TV: $host"
                        Triple(if (includeSystem) (users + systems).sorted() else users.sorted(), systems, info)
                    }
                }
                packageRows = result.first
                systemPackages = result.second
                deviceInfo.text = result.third
                renderPackages()
                val userCount = packageRows.count { it !in systemPackages }
                val systemCount = packageRows.size - userCount
                status("Đã đọc dữ liệu TV qua ADB. $userCount ứng dụng người dùng, $systemCount package hệ thống. Phiên ADB đã đóng sau khi quét; chưa thay đổi ứng dụng trên TV.")
            } catch (e: Exception) { status("Kết nối thất bại: " + (e.message ?: e.javaClass.simpleName) + ". Kiểm tra lại IP, cổng và trạng thái ghép đôi.") }
            finally { busy = false }
        }
    }

    override fun onDestroy() { job.cancel(); super.onDestroy() }
}
