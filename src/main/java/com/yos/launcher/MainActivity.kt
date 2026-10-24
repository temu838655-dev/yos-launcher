package com.yos.launcher

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.BatteryManager
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.*
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

class AppItem(val label: String, val pkg: String, val intent: Intent, val icon: Bitmap)

class BarView(c: Context) : View(c) {
    var frac = 0f
        set(v) { field = v; invalidate() }
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(canvas: Canvas) {
        val h = height.toFloat()
        p.color = 0x59FFFFFF
        canvas.drawRoundRect(0f, 0f, width.toFloat(), h, h / 2, h / 2, p)
        p.color = Color.WHITE
        canvas.drawRoundRect(0f, 0f, width * frac, h, h / 2, h / 2, p)
    }
}

class Pager(c: Context) : HorizontalScrollView(c) {
    var pageW = 1
    var onPage: ((Int) -> Unit)? = null
    private var startPage = 0
    private var downX = 0f
    private var last = -1

    fun pages(): Int {
        val w = getChildAt(0)?.width ?: pageW
        return max(1, (w + pageW / 2) / pageW)
    }

    fun goTo(p: Int) {
        smoothScrollTo(p.coerceIn(0, pages() - 1) * pageW, 0)
    }

    override fun fling(velocityX: Int) {}

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.actionMasked == MotionEvent.ACTION_DOWN) {
            downX = e.rawX
            startPage = (scrollX.toFloat() / pageW).roundToInt()
        }
        val r = super.onTouchEvent(e)
        if (e.actionMasked == MotionEvent.ACTION_UP || e.actionMasked == MotionEvent.ACTION_CANCEL) {
            val d = downX - e.rawX
            var t = startPage
            if (d > pageW * 0.15f) t++ else if (d < -pageW * 0.15f) t--
            goTo(t)
        }
        return r
    }

    override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
        super.onScrollChanged(l, t, oldl, oldt)
        val p = (l.toFloat() / pageW).roundToInt()
        if (p != last) {
            last = p
            onPage?.invoke(p)
        }
    }
}

fun squircle(d: Drawable, px: Int): Bitmap {
    val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
    val cv = Canvas(bmp)
    if (d is AdaptiveIconDrawable) {
        val o = (px * 0.25f).toInt()
        val box = Rect(-o, -o, px + o, px + o)
        val bg = d.background
        val fg = d.foreground
        cv.drawColor(Color.WHITE)
        if (bg != null) { bg.setBounds(box); bg.draw(cv) }
        if (fg != null) { fg.setBounds(box); fg.draw(cv) }
    } else {
        cv.drawColor(Color.WHITE)
        val i = (px * 0.12f).toInt()
        d.setBounds(i, i, px - i, px - i)
        d.draw(cv)
    }
    val r = px * 0.2237f
    val path = Path()
    path.addRoundRect(RectF(0f, 0f, px.toFloat(), px.toFloat()), r, r, Path.Direction.CW)
    path.fillType = Path.FillType.INVERSE_WINDING
    val clear = Paint(Paint.ANTI_ALIAS_FLAG)
    clear.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    cv.drawPath(path, clear)
    return bmp
}

@Suppress("DEPRECATION")
class MainActivity : Activity() {

    private var density = 1f
    private var screenW = 0
    private var topInset = 0
    private lateinit var root: FrameLayout
    private lateinit var pager: Pager
    private lateinit var pagesBox: LinearLayout
    private lateinit var dots: LinearLayout
    private lateinit var dock: LinearLayout
    private lateinit var overlay: LinearLayout
    private lateinit var searchInput: EditText
    private lateinit var results: LinearLayout
    private lateinit var batText: TextView
    private lateinit var batState: TextView
    private lateinit var batBar: BarView
    private var apps: List<AppItem> = emptyList()
    private val iconCache = HashMap<String, Bitmap>()
    private var searchOpen = false
    private var dX = 0f
    private var dY = 0f
    private var dT = 0L

    private val batRx = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            if (i != null) updateBattery(i)
        }
    }
    private val pkgRx = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            loadApps()
        }
    }

    private fun dp(v: Int): Int = (v * density + 0.5f).toInt()

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        density = resources.displayMetrics.density
        screenW = resources.displayMetrics.widthPixels
        val sb = resources.getIdentifier("status_bar_height", "dimen", "android")
        topInset = if (sb > 0) resources.getDimensionPixelSize(sb) else dp(24)

        val wall = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(0xFF1E2F7A.toInt(), 0xFF7A3E96.toInt(), 0xFFF08A5D.toInt())
        )
        window.setBackgroundDrawable(wall)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN

        root = FrameLayout(this)
        setContentView(root)
        buildStatic()
        loadApps()
    }

    override fun onStart() {
        super.onStart()
        val sticky = registerReceiver(batRx, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        if (sticky != null) updateBattery(sticky)
        val f = IntentFilter()
        f.addAction(Intent.ACTION_PACKAGE_ADDED)
        f.addAction(Intent.ACTION_PACKAGE_REMOVED)
        f.addAction(Intent.ACTION_PACKAGE_REPLACED)
        f.addDataScheme("package")
        registerReceiver(pkgRx, f)
    }

    override fun onStop() {
        super.onStop()
        unregisterReceiver(batRx)
        unregisterReceiver(pkgRx)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        closeSearch()
        pager.goTo(0)
    }

    override fun onBackPressed() {
        if (searchOpen) closeSearch() else pager.goTo(0)
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
            dX = ev.rawX; dY = ev.rawY; dT = ev.eventTime
        } else if (ev.actionMasked == MotionEvent.ACTION_UP) {
            val dy = ev.rawY - dY
            val dx = ev.rawX - dX
            if (!searchOpen && dy > dp(120) && abs(dy) > abs(dx) * 2 && ev.eventTime - dT < 600) {
                openSearch()
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun glass(radiusDp: Int, alpha: Int): GradientDrawable {
        val g = GradientDrawable()
        g.setColor(Color.argb(alpha, 255, 255, 255))
        g.cornerRadius = dp(radiusDp).toFloat()
        return g
    }

    private fun style(t: TextView, sp: Float, bold: Boolean) {
        t.setTextColor(Color.WHITE)
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        t.setShadowLayer(3f, 0f, 1f, 0x66000000)
        if (bold) t.typeface = Typeface.DEFAULT_BOLD
    }

    private fun buildStatic() {
        pager = Pager(this)
        pager.isHorizontalScrollBarEnabled = false
        pager.overScrollMode = View.OVER_SCROLL_NEVER
        pager.isFillViewport = true
        pager.pageW = screenW
        pagesBox = LinearLayout(this)
        pagesBox.orientation = LinearLayout.HORIZONTAL
        pager.addView(pagesBox, FrameLayout.LayoutParams(WRAP, MATCH))
        pager.onPage = { updateDots(it) }
        root.addView(pager, FrameLayout.LayoutParams(MATCH, MATCH))

        dots = LinearLayout(this)
        dots.gravity = Gravity.CENTER
        root.addView(dots, FrameLayout.LayoutParams(MATCH, WRAP, Gravity.BOTTOM).apply {
            bottomMargin = dp(108)
        })

        dock = LinearLayout(this)
        dock.orientation = LinearLayout.HORIZONTAL
        dock.gravity = Gravity.CENTER
        dock.background = glass(30, 0x40)
        dock.setPadding(dp(8), dp(12), dp(8), dp(12))
        root.addView(dock, FrameLayout.LayoutParams(MATCH, WRAP, Gravity.BOTTOM).apply {
            setMargins(dp(14), 0, dp(14), dp(14))
        })

        overlay = LinearLayout(this)
        overlay.orientation = LinearLayout.VERTICAL
        overlay.setBackgroundColor(0xE6101018.toInt())
        overlay.setPadding(dp(16), topInset + dp(12), dp(16), dp(8))
        overlay.visibility = View.GONE
        overlay.isClickable = true

        searchInput = EditText(this)
        searchInput.hint = "Tìm kiếm"
        searchInput.setHintTextColor(0x99FFFFFF.toInt())
        searchInput.setTextColor(Color.WHITE)
        searchInput.textSize = 17f
        searchInput.isSingleLine = true
        searchInput.background = glass(14, 0x30)
        searchInput.setPadding(dp(14), dp(10), dp(14), dp(10))
        searchInput.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { renderResults(s?.toString() ?: "") }
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
        })
        val cancel = TextView(this)
        style(cancel, 16f, false)
        cancel.text = "Hủy"
        cancel.setPadding(dp(14), dp(10), dp(4), dp(10))
        cancel.setOnClickListener { closeSearch() }
        val bar = LinearLayout(this)
        bar.gravity = Gravity.CENTER_VERTICAL
        bar.addView(searchInput, LinearLayout.LayoutParams(0, WRAP, 1f))
        bar.addView(cancel)
        overlay.addView(bar, LinearLayout.LayoutParams(MATCH, WRAP))

        val sv = ScrollView(this)
        results = LinearLayout(this)
        results.orientation = LinearLayout.VERTICAL
        sv.addView(results)
        overlay.addView(sv, LinearLayout.LayoutParams(MATCH, 0, 1f).apply { topMargin = dp(8) })
        root.addView(overlay, FrameLayout.LayoutParams(MATCH, MATCH))
    }

    private fun loadApps() {
        val pm = packageManager
        val q = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val px = dp(60)
        val out = ArrayList<AppItem>()
        for (ri in pm.queryIntentActivities(q, 0)) {
            val pkg = ri.activityInfo.packageName
            if (pkg == packageName) continue
            val cls = ri.activityInfo.name
            val icon = iconCache.getOrPut("$pkg/$cls") { squircle(ri.loadIcon(pm), px) }
            val go = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                .setComponent(ComponentName(pkg, cls))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            out.add(AppItem(ri.loadLabel(pm).toString(), pkg, go, icon))
        }
        out.sortBy { it.label.lowercase() }
        apps = out

        val dockPkgs = ArrayList<String>()
        fun pick(i: Intent) {
            val r = pm.resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY)
            val p = r?.activityInfo?.packageName
            if (p != null && p != "android" && p != packageName &&
                !dockPkgs.contains(p) && out.any { a -> a.pkg == p }) {
                dockPkgs.add(p)
            }
        }
        pick(Intent(Intent.ACTION_DIAL))
        pick(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MESSAGING))
        pick(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_BROWSER))
        pick(Intent(MediaStore.ACTION_IMAGE_CAPTURE))

        val dockApps = dockPkgs.map { p -> out.first { a -> a.pkg == p } }
        val grid = out.filter { a -> !dockPkgs.contains(a.pkg) }

        dock.removeAllViews()
        for (a in dockApps) dock.addView(cell(a, false), LinearLayout.LayoutParams(0, WRAP, 1f))
        renderPages(grid)

        val sticky = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        if (sticky != null) updateBattery(sticky)
    }

    private fun renderPages(grid: List<AppItem>) {
        pagesBox.removeAllViews()
        val widgetSize = (screenW - dp(28) - dp(14)) / 2
        val availOther = resources.displayMetrics.heightPixels - dp(124) - topInset - dp(12)
        val rowsOther = max(3, availOther / dp(100))
        val rows0 = max(2, (availOther - widgetSize) / dp(100))
        var idx = 0
        var pageNo = 0
        while (pageNo == 0 || idx < grid.size) {
            val rows = if (pageNo == 0) rows0 else rowsOther
            val page = LinearLayout(this)
            page.orientation = LinearLayout.VERTICAL
            page.setPadding(dp(14), topInset + dp(12), dp(14), 0)
            if (pageNo == 0) {
                page.addView(widgetRow(widgetSize), LinearLayout.LayoutParams(MATCH, WRAP))
            }
            for (r in 0 until rows) {
                if (idx >= grid.size) break
                val row = LinearLayout(this)
                row.orientation = LinearLayout.HORIZONTAL
                for (c in 0 until 4) {
                    if (idx < grid.size) {
                        row.addView(cell(grid[idx], true), LinearLayout.LayoutParams(0, WRAP, 1f))
                        idx++
                    } else {
                        row.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
                    }
                }
                page.addView(row, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(18) })
            }
            pagesBox.addView(page, LinearLayout.LayoutParams(screenW, MATCH))
            pageNo++
        }
        buildDots(pageNo)
        pager.scrollTo(0, 0)
    }

    private fun widgetRow(size: Int): View {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL

        val clock = LinearLayout(this)
        clock.orientation = LinearLayout.VERTICAL
        clock.background = glass(24, 0x38)
        clock.setPadding(dp(16), dp(14), dp(16), dp(14))
        val day = TextClock(this)
        day.format12Hour = "EEEE"
        day.format24Hour = "EEEE"
        style(day, 15f, true)
        val time = TextClock(this)
        time.format12Hour = "HH:mm"
        time.format24Hour = "HH:mm"
        style(time, 38f, true)
        val date = TextClock(this)
        date.format12Hour = "d MMMM"
        date.format24Hour = "d MMMM"
        style(date, 14f, false)
        clock.addView(day)
        clock.addView(Space(this), LinearLayout.LayoutParams(1, 0, 1f))
        clock.addView(time)
        clock.addView(date)
        row.addView(clock, LinearLayout.LayoutParams(size, size))

        val bat = LinearLayout(this)
        bat.orientation = LinearLayout.VERTICAL
        bat.background = glass(24, 0x38)
        bat.setPadding(dp(16), dp(14), dp(16), dp(14))
        batState = TextView(this)
        style(batState, 15f, true)
        batState.text = "Pin"
        batText = TextView(this)
        style(batText, 38f, true)
        batText.text = "--%"
        batBar = BarView(this)
        bat.addView(batState)
        bat.addView(Space(this), LinearLayout.LayoutParams(1, 0, 1f))
        bat.addView(batText)
        bat.addView(batBar, LinearLayout.LayoutParams(MATCH, dp(8)).apply { topMargin = dp(8) })
        row.addView(bat, LinearLayout.LayoutParams(size, size).apply { leftMargin = dp(14) })
        return row
    }

    private fun cell(a: AppItem, showLabel: Boolean): View {
        val c = LinearLayout(this)
        c.orientation = LinearLayout.VERTICAL
        c.gravity = Gravity.CENTER_HORIZONTAL
        val iv = ImageView(this)
        iv.setImageBitmap(a.icon)
        c.addView(iv, LinearLayout.LayoutParams(dp(60), dp(60)))
        if (showLabel) {
            val t = TextView(this)
            style(t, 11.5f, false)
            t.text = a.label
            t.maxLines = 1
            t.ellipsize = TextUtils.TruncateAt.END
            t.gravity = Gravity.CENTER
            c.addView(t, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(5) })
        }
        c.setOnClickListener { open(a) }
        c.setOnLongClickListener { showMenu(c, a); true }
        return c
    }

    private fun open(a: AppItem) {
        try { startActivity(a.intent) } catch (e: Exception) {}
    }

    private fun showMenu(anchor: View, a: AppItem) {
        val menu = PopupMenu(this, anchor)
        menu.menu.add(0, 1, 0, "Thông tin ứng dụng")
        menu.menu.add(0, 2, 1, "Gỡ cài đặt")
        menu.setOnMenuItemClickListener { m ->
            try {
                val uri = Uri.parse("package:" + a.pkg)
                if (m.itemId == 1) {
                    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, uri))
                } else {
                    startActivity(Intent(Intent.ACTION_DELETE, uri))
                }
            } catch (e: Exception) {}
            true
        }
        menu.show()
    }

    private fun buildDots(n: Int) {
        dots.removeAllViews()
        if (n < 2) return
        for (i in 0 until n) {
            val v = View(this)
            val g = GradientDrawable()
            g.shape = GradientDrawable.OVAL
            g.setColor(Color.WHITE)
            v.background = g
            v.alpha = if (i == 0) 1f else 0.4f
            dots.addView(v, LinearLayout.LayoutParams(dp(7), dp(7)).apply {
                setMargins(dp(4), 0, dp(4), 0)
            })
        }
    }

    private fun updateDots(p: Int) {
        for (i in 0 until dots.childCount) {
            dots.getChildAt(i).alpha = if (i == p) 1f else 0.4f
        }
    }

    private fun updateBattery(i: Intent) {
        if (!::batText.isInitialized) return
        val lv = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val sc = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val st = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        if (lv < 0) return
        val pct = lv * 100 / max(sc, 1)
        batText.text = "$pct%"
        batBar.frac = pct / 100f
        val charging = st == BatteryManager.BATTERY_STATUS_CHARGING ||
            st == BatteryManager.BATTERY_STATUS_FULL
        batState.text = if (charging) "Đang sạc" else "Pin"
    }

    private fun openSearch() {
        searchOpen = true
        overlay.visibility = View.VISIBLE
        renderResults("")
        searchInput.requestFocus()
        searchInput.post {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(searchInput, 0)
        }
    }

    private fun closeSearch() {
        if (!searchOpen) return
        searchOpen = false
        searchInput.setText("")
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(searchInput.windowToken, 0)
        overlay.visibility = View.GONE
    }

    private fun renderResults(q: String) {
        results.removeAllViews()
        val key = q.trim().lowercase()
        for (a in apps) {
            if (key.isNotEmpty() && !a.label.lowercase().contains(key)) continue
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            row.setPadding(0, dp(8), 0, dp(8))
            val iv = ImageView(this)
            iv.setImageBitmap(a.icon)
            row.addView(iv, LinearLayout.LayoutParams(dp(44), dp(44)))
            val t = TextView(this)
            style(t, 16f, false)
            t.text = a.label
            row.addView(t, LinearLayout.LayoutParams(WRAP, WRAP).apply { leftMargin = dp(14) })
            row.setOnClickListener { closeSearch(); open(a) }
            results.addView(row, LinearLayout.LayoutParams(MATCH, WRAP))
        }
    }
}
