package com.keystone.launcher

import android.animation.ValueAnimator
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.provider.AlarmClock
import android.provider.CallLog
import android.provider.ContactsContract
import android.provider.Settings
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import java.util.Calendar
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

/**
 * Samsung SM-B310E (Keystone 2) tarzi arayuz.
 * Her sey 240 birim genisligindeki sanal bir tuvale cizilir ve ekrana olceklenir
 * (Qin F22 ve diger ekranlarda ayni oran korunur). Material/modern bilesen yok.
 *
 * Ekranlar: HOME (ana ekran), MENU (3x3 ana menu), APPS (uygulama listesi).
 */
class KeystoneView(ctx: Context) : View(ctx) {

    private enum class Screen { HOME, MENU, APPS }
    private class MenuItem(val title: String, val tile: Int)
    private class AppEntry(val label: String, val intent: Intent)

    companion object {
        /** true iken ust cubukta son basilan tusun kodu gorunur (soft key kodlarini ogrenmek icin). */
        const val SHOW_KEYCODES = true

        const val VW = 240f        // sanal genislik
        const val STATUS_H = 18f   // durum cubugu
        const val SOFT_H = 24f     // alt yazilim tuslari cubugu
        const val TITLE_H = 22f    // menu baslik cubugu
        const val ROW_H = 22f      // liste satir yuksekligi

        val MONTHS = arrayOf(
            "Ocak", "Şubat", "Mart", "Nisan", "Mayıs", "Haziran",
            "Temmuz", "Ağustos", "Eylül", "Ekim", "Kasım", "Aralık"
        )
        val DAYS = arrayOf("Paz", "Pzt", "Sal", "Çar", "Per", "Cum", "Cmt")
    }

    private val menuItems = listOf(
        MenuItem("Çağrı kayıtları", 0xFF2E9E4F.toInt()),
        MenuItem("Kişiler", 0xFF3C7DD9.toInt()),
        MenuItem("Mesajlar", 0xFFE08A1E.toInt()),
        MenuItem("Müzik", 0xFFC2417A.toInt()),
        MenuItem("İnternet", 0xFF2A9FC9.toInt()),
        MenuItem("Dosyalarım", 0xFFD9A81E.toInt()),
        MenuItem("Takvim", 0xFF7A5CC7.toInt()),
        MenuItem("Alarmlar", 0xFFD9503C.toInt()),
        MenuItem("Ayarlar", 0xFF6B7785.toInt()),
        MenuItem("Uygulamalar", 0xFF2F8F8F.toInt())
    )

    // ---- durum ----
    private var screen = Screen.HOME
    private var sel = 0
    private var scrollRow = 0
    private var apps: List<AppEntry> = emptyList()
    private var appSel = 0
    private var appScroll = 0
    private var battery = 100
    private var lastKey = 0

    // ---- cizim ----
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var scale = 1f
    private var vh = 320f
    private var anim = 1f
    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 160
        addUpdateListener { anim = it.animatedValue as Float; invalidate() }
    }

    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() { invalidate(); scheduleTick() }
    }
    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) { updateBattery(i); invalidate() }
    }

    // ------------------------------------------------------------------
    // Yasam dongusu (arka plan servisi yok; sadece gorunurken dakikada bir yenilenir)
    // ------------------------------------------------------------------
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        updateBattery(context.registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED)))
        scheduleTick()
    }

    override fun onDetachedFromWindow() {
        try { context.unregisterReceiver(batteryReceiver) } catch (e: Exception) { }
        handler.removeCallbacks(tick)
        animator.cancel()
        super.onDetachedFromWindow()
    }

    private fun scheduleTick() {
        handler.removeCallbacks(tick)
        handler.postDelayed(tick, 60000L - System.currentTimeMillis() % 60000L + 50L)
    }

    private fun updateBattery(i: Intent?) {
        if (i == null) return
        val l = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val s = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        if (l >= 0 && s > 0) battery = l * 100 / s
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        scale = w / VW
        vh = h / scale
    }

    // ------------------------------------------------------------------
    // Gezinme
    // ------------------------------------------------------------------
    private fun goTo(s: Screen) {
        screen = s
        animator.cancel()
        animator.start()
        invalidate()
    }

    fun goHome() { if (screen != Screen.HOME) goTo(Screen.HOME) }

    fun goBack() {
        when (screen) {
            Screen.HOME -> {}
            Screen.MENU -> goTo(Screen.HOME)
            Screen.APPS -> goTo(Screen.MENU)
        }
    }

    /** Fiziksel tus olaylari. true donerse tus tuketilmis demektir. */
    fun handleKey(code: Int): Boolean {
        lastKey = code
        var handled = true
        when (screen) {
            Screen.HOME -> when (code) {
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
                KeyEvent.KEYCODE_SOFT_LEFT, KeyEvent.KEYCODE_MENU -> goTo(Screen.MENU)
                KeyEvent.KEYCODE_SOFT_RIGHT, KeyEvent.KEYCODE_CONTACTS -> launch(contactsIntent())
                KeyEvent.KEYCODE_CALL -> launch(callLogIntent())
                in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 ->
                    launch(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + (code - KeyEvent.KEYCODE_0))))
                KeyEvent.KEYCODE_STAR -> launch(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode("*"))))
                KeyEvent.KEYCODE_POUND -> launch(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode("#"))))
                KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ENDCALL,
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {}
                else -> handled = false
            }
            Screen.MENU -> {
                val n = menuItems.size
                when (code) {
                    KeyEvent.KEYCODE_DPAD_RIGHT -> sel = (sel + 1) % n
                    KeyEvent.KEYCODE_DPAD_LEFT -> sel = (sel - 1 + n) % n
                    KeyEvent.KEYCODE_DPAD_DOWN -> sel = if (sel + 3 < n) sel + 3 else sel % 3
                    KeyEvent.KEYCODE_DPAD_UP -> {
                        sel = if (sel - 3 >= 0) sel - 3 else {
                            var last = sel + ((n - 1 - sel) / 3) * 3
                            if (last >= n) last -= 3
                            last
                        }
                    }
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
                    KeyEvent.KEYCODE_SOFT_LEFT, KeyEvent.KEYCODE_MENU -> activate()
                    KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_SOFT_RIGHT -> goTo(Screen.HOME)
                    KeyEvent.KEYCODE_ENDCALL -> goTo(Screen.HOME)
                    in KeyEvent.KEYCODE_1..KeyEvent.KEYCODE_9 -> { sel = code - KeyEvent.KEYCODE_1; activate() }
                    KeyEvent.KEYCODE_0 -> { sel = 9; activate() }
                    else -> handled = false
                }
                fixMenuScroll()
            }
            Screen.APPS -> {
                val n = apps.size
                when (code) {
                    KeyEvent.KEYCODE_DPAD_DOWN -> if (n > 0) appSel = (appSel + 1) % n
                    KeyEvent.KEYCODE_DPAD_UP -> if (n > 0) appSel = (appSel - 1 + n) % n
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
                    KeyEvent.KEYCODE_SOFT_LEFT, KeyEvent.KEYCODE_MENU -> if (n > 0) launch(apps[appSel].intent)
                    KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_SOFT_RIGHT -> goTo(Screen.MENU)
                    KeyEvent.KEYCODE_ENDCALL -> goTo(Screen.HOME)
                    KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {}
                    else -> handled = false
                }
                fixAppScroll()
            }
        }
        invalidate()
        return handled
    }

    private fun fixMenuScroll() {
        val row = sel / 3
        if (row < scrollRow) scrollRow = row
        if (row >= scrollRow + 3) scrollRow = row - 2
    }

    private fun fixAppScroll() {
        val vis = visibleAppRows()
        if (appSel < appScroll) appScroll = appSel
        if (appSel >= appScroll + vis) appScroll = appSel - vis + 1
    }

    private fun visibleAppRows(): Int =
        ((vh - SOFT_H - (STATUS_H + TITLE_H)) / ROW_H).toInt().coerceAtLeast(1)

    // ------------------------------------------------------------------
    // Menu eylemleri: gercek Android islevleri Intent ile acilir
    // ------------------------------------------------------------------
    private fun activate() {
        when (sel) {
            0 -> launch(callLogIntent())
            1 -> launch(contactsIntent())
            2 -> launch(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MESSAGING))
            3 -> launch(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MUSIC))
            4 -> launch(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_BROWSER))
            5 -> launch(Intent("android.intent.action.VIEW_DOWNLOADS"))
            6 -> launch(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR))
            7 -> launch(Intent(AlarmClock.ACTION_SHOW_ALARMS))
            8 -> launch(Intent(Settings.ACTION_SETTINGS))
            9 -> { loadApps(); appSel = 0; appScroll = 0; goTo(Screen.APPS) }
        }
    }

    private fun contactsIntent() = Intent(Intent.ACTION_VIEW, ContactsContract.Contacts.CONTENT_URI)
    private fun callLogIntent() = Intent(Intent.ACTION_VIEW).setType(CallLog.Calls.CONTENT_TYPE)

    private fun launch(i: Intent) {
        try {
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(i)
        } catch (e: Exception) {
            Toast.makeText(context, "Uygulama bulunamadı", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadApps() {
        val pm = context.packageManager
        val q = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        apps = pm.queryIntentActivities(q, 0)
            .filter { it.activityInfo.packageName != context.packageName }
            .map {
                AppEntry(
                    it.loadLabel(pm).toString(),
                    Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                        .setClassName(it.activityInfo.packageName, it.activityInfo.name)
                )
            }
            .sortedBy { it.label.lowercase(Locale.forLanguageTag("tr")) }
    }

    // ------------------------------------------------------------------
    // Dokunmatik
    // ------------------------------------------------------------------
    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.action != MotionEvent.ACTION_UP) return true
        val x = e.x / scale
        val y = e.y / scale
        if (y > vh - SOFT_H) {
            handleKey(if (x < VW / 2) KeyEvent.KEYCODE_SOFT_LEFT else KeyEvent.KEYCODE_SOFT_RIGHT)
            return true
        }
        val top = STATUS_H + TITLE_H
        when (screen) {
            Screen.MENU -> if (y >= top) {
                val cellH = (vh - SOFT_H - top) / 3f
                val col = (x / (VW / 3f)).toInt().coerceIn(0, 2)
                val vr = ((y - top) / cellH).toInt().coerceIn(0, 2)
                val idx = (scrollRow + vr) * 3 + col
                if (idx < menuItems.size) {
                    if (idx == sel) activate() else { sel = idx; invalidate() }
                }
            }
            Screen.APPS -> if (y >= top) {
                val idx = appScroll + ((y - top) / ROW_H).toInt()
                if (idx < apps.size) {
                    if (idx == appSel) launch(apps[idx].intent) else { appSel = idx; invalidate() }
                }
            }
            Screen.HOME -> {}
        }
        return true
    }

    // ------------------------------------------------------------------
    // Cizim yardimcilari
    // ------------------------------------------------------------------
    private fun fillMode() {
        paint.style = Paint.Style.FILL
        paint.shader = null
        paint.strokeCap = Paint.Cap.BUTT
    }

    private fun txt(
        c: Canvas, s: String, x: Float, y: Float, size: Float, color: Int,
        align: Paint.Align = Paint.Align.LEFT, bold: Boolean = false
    ) {
        fillMode()
        paint.textSize = size
        paint.color = color
        paint.textAlign = align
        paint.typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        c.drawText(s, x, y, paint)
    }

    private fun rect(c: Canvas, l: Float, t: Float, r: Float, b: Float, color: Int) {
        fillMode()
        paint.color = color
        c.drawRect(l, t, r, b, paint)
    }

    private fun vGrad(c: Canvas, l: Float, t: Float, r: Float, b: Float, c1: Int, c2: Int) {
        fillMode()
        paint.shader = LinearGradient(l, t, l, b, c1, c2, Shader.TileMode.CLAMP)
        c.drawRect(l, t, r, b, paint)
        paint.shader = null
    }

    private fun lighten(col: Int): Int {
        val r = Color.red(col) + (255 - Color.red(col)) * 35 / 100
        val g = Color.green(col) + (255 - Color.green(col)) * 35 / 100
        val b = Color.blue(col) + (255 - Color.blue(col)) * 35 / 100
        return Color.rgb(r, g, b)
    }

    /** Metni maxW genisligine sigacak sekilde keser. Once paint.textSize ayarlanmis olmali. */
    private fun fit(s: String, maxW: Float): String {
        var t = s
        while (t.length > 1 && paint.measureText(t) > maxW) t = t.dropLast(1)
        return t
    }

    // ------------------------------------------------------------------
    // Cizim
    // ------------------------------------------------------------------
    override fun onDraw(c: Canvas) {
        c.drawColor(Color.BLACK)

        // Govde: gecis animasyonunda asagidan yukari kayar
        c.save()
        c.scale(scale, scale)
        if (anim < 1f) c.translate(0f, (1f - anim) * 14f)
        when (screen) {
            Screen.HOME -> drawHome(c)
            Screen.MENU -> drawMenu(c)
            Screen.APPS -> drawApps(c)
        }
        c.restore()

        // Sabit parcalar: durum cubugu ve yazilim tuslari
        c.save()
        c.scale(scale, scale)
        drawStatus(c)
        when (screen) {
            Screen.HOME -> drawSoft(c, "Menü", "Kişiler")
            Screen.MENU -> drawSoft(c, "Seç", "Geri")
            Screen.APPS -> drawSoft(c, "Aç", "Geri")
        }
        c.restore()
    }

    private fun drawStatus(c: Canvas) {
        vGrad(c, 0f, 0f, VW, STATUS_H, 0xFF3A3A3A.toInt(), 0xFF000000.toInt())
        // Sinyal cubuklari (gercek sinyal icin READ_PHONE_STATE izni gerekir; simdilik sabit)
        for (i in 0 until 4) {
            val bh = 3f + i * 2.5f
            rect(c, 5f + i * 4f, 14f - bh, 8f + i * 4f, 14f, Color.WHITE)
        }
        // Pil
        val x0 = VW - 24f
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        paint.color = Color.WHITE
        c.drawRect(x0, 4f, x0 + 16f, 13f, paint)
        rect(c, x0 + 16f, 6f, x0 + 18f, 11f, Color.WHITE)
        val w = 14f * battery / 100f
        rect(c, x0 + 1f, 5f, x0 + 1f + w, 12f,
            if (battery <= 15) 0xFFE03A2E.toInt() else 0xFF5CD65C.toInt())
        // Saat
        val cal = Calendar.getInstance()
        val time = String.format(Locale.US, "%02d:%02d", cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
        txt(c, time, x0 - 4f, 13f, 11f, Color.WHITE, Paint.Align.RIGHT)
        if (SHOW_KEYCODES && lastKey != 0) {
            txt(c, "K:$lastKey", VW / 2f - 10f, 12f, 9f, 0xFFFFD84D.toInt(), Paint.Align.CENTER)
        }
    }

    private fun drawSoft(c: Canvas, left: String, right: String) {
        val top = vh - SOFT_H
        vGrad(c, 0f, top, VW, vh, 0xFF404040.toInt(), 0xFF000000.toInt())
        txt(c, left, 6f, vh - 8f, 12f, Color.WHITE, Paint.Align.LEFT, true)
        txt(c, right, VW - 6f, vh - 8f, 12f, Color.WHITE, Paint.Align.RIGHT, true)
    }

    private fun drawHome(c: Canvas) {
        // Duvar kagidi: koyu laciverttten acik maviye gecis + ince dalgalar
        vGrad(c, 0f, 0f, VW, vh, 0xFF081C3F.toInt(), 0xFF3A8AD0.toInt())
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.5f
        paint.color = 0x30FFFFFF
        for (i in 0 until 6) {
            c.drawOval(RectF(-60f + i * 25f, vh * 0.55f - i * 10f, VW + 60f - i * 20f, vh * 0.95f + i * 6f), paint)
        }
        val cal = Calendar.getInstance()
        val time = String.format(Locale.US, "%02d:%02d", cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
        val date = "${DAYS[cal.get(Calendar.DAY_OF_WEEK) - 1]}, ${cal.get(Calendar.DAY_OF_MONTH)} ${MONTHS[cal.get(Calendar.MONTH)]}"
        val mid = STATUS_H + (vh - STATUS_H - SOFT_H) * 0.42f
        txt(c, time, VW / 2f, mid, 48f, Color.WHITE, Paint.Align.CENTER)
        txt(c, date, VW / 2f, mid + 22f, 14f, 0xFFDDEBFA.toInt(), Paint.Align.CENTER)
    }

    private fun drawMenu(c: Canvas) {
        val top = STATUS_H + TITLE_H
        vGrad(c, 0f, STATUS_H, VW, vh - SOFT_H, 0xFFFFFFFF.toInt(), 0xFFD6E4F2.toInt())
        vGrad(c, 0f, STATUS_H, VW, top, 0xFF3A78C8.toInt(), 0xFF1F4F96.toInt())
        txt(c, menuItems[sel].title, VW / 2f, STATUS_H + 15f, 13f, Color.WHITE, Paint.Align.CENTER, true)

        val cellW = VW / 3f
        val cellH = (vh - SOFT_H - top) / 3f
        for (i in menuItems.indices) {
            val vr = i / 3 - scrollRow
            if (vr < 0 || vr > 2) continue
            val x0 = (i % 3) * cellW
            val y0 = top + vr * cellH
            val selected = i == sel
            if (selected) {
                rect(c, x0 + 3f, y0 + 3f, x0 + cellW - 3f, y0 + cellH - 3f, 0x553A78C8)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 2f
                paint.color = 0xFF1F4F96.toInt()
                c.drawRect(x0 + 3f, y0 + 3f, x0 + cellW - 3f, y0 + cellH - 3f, paint)
            }
            drawIcon(c, i, x0 + cellW / 2f, y0 + cellH * 0.42f, if (selected) 42f else 36f)
            paint.textSize = 10f
            val label = fit(menuItems[i].title, cellW - 6f)
            txt(c, label, x0 + cellW / 2f, y0 + cellH - 9f, 10f, Color.BLACK, Paint.Align.CENTER, selected)
        }
        // Kaydirma gostergesi
        val rows = (menuItems.size + 2) / 3
        if (rows > 3) {
            val trackH = vh - SOFT_H - top
            rect(c, VW - 3f, top + trackH * scrollRow / rows, VW - 1f, top + trackH * (scrollRow + 3) / rows, 0xFF1F4F96.toInt())
        }
    }

    private fun drawApps(c: Canvas) {
        val top = STATUS_H + TITLE_H
        rect(c, 0f, STATUS_H, VW, vh - SOFT_H, Color.WHITE)
        vGrad(c, 0f, STATUS_H, VW, top, 0xFF3A78C8.toInt(), 0xFF1F4F96.toInt())
        txt(c, "Uygulamalar", VW / 2f, STATUS_H + 15f, 13f, Color.WHITE, Paint.Align.CENTER, true)
        if (apps.isEmpty()) {
            txt(c, "Uygulama yok", VW / 2f, top + 30f, 12f, Color.BLACK, Paint.Align.CENTER)
            return
        }
        val vis = visibleAppRows()
        for (r in 0 until vis) {
            val i = appScroll + r
            if (i >= apps.size) break
            val y0 = top + r * ROW_H
            val selected = i == appSel
            if (selected) vGrad(c, 0f, y0, VW, y0 + ROW_H, 0xFF4F8FDF.toInt(), 0xFF1F4F96.toInt())
            else rect(c, 0f, y0 + ROW_H - 1f, VW, y0 + ROW_H, 0xFFC8D4E0.toInt())
            paint.textSize = 12f
            val label = fit("${i + 1}. ${apps[i].label}", VW - 14f)
            txt(c, label, 6f, y0 + 15f, 12f, if (selected) Color.WHITE else Color.BLACK, Paint.Align.LEFT, selected)
        }
    }

    /** Menu ikonlari: renkli kare zemin + beyaz cizim. 36x36 referans boyutunda cizilir. */
    private fun drawIcon(c: Canvas, idx: Int, cx: Float, cy: Float, s: Float) {
        val h = s / 2f
        val tile = menuItems[idx].tile
        fillMode()
        paint.shader = LinearGradient(cx, cy - h, cx, cy + h, lighten(tile), tile, Shader.TileMode.CLAMP)
        c.drawRoundRect(cx - h, cy - h, cx + h, cy + h, 4f, 4f, paint)
        paint.shader = null

        c.save()
        c.translate(cx, cy)
        c.scale(s / 36f, s / 36f)
        fillMode()
        paint.color = Color.WHITE
        when (idx) {
            0 -> { // ahize
                paint.style = Paint.Style.STROKE; paint.strokeWidth = 6f; paint.strokeCap = Paint.Cap.ROUND
                c.drawLine(-8f, 8f, 8f, -8f, paint)
                fillMode(); paint.color = Color.WHITE
                c.drawCircle(-9f, 9f, 3.5f, paint); c.drawCircle(9f, -9f, 3.5f, paint)
            }
            1 -> { // kisi
                c.drawCircle(0f, -5f, 5f, paint)
                c.drawArc(RectF(-10f, 2f, 10f, 22f), 180f, 180f, true, paint)
            }
            2 -> { // zarf
                c.drawRect(-11f, -7f, 11f, 7f, paint)
                paint.style = Paint.Style.STROKE; paint.strokeWidth = 1.5f; paint.color = tile
                c.drawLine(-11f, -7f, 0f, 2f, paint); c.drawLine(0f, 2f, 11f, -7f, paint)
            }
            3 -> { // nota
                c.drawCircle(-4f, 8f, 4.5f, paint)
                c.drawRect(0f, -10f, 2f, 8f, paint)
                paint.style = Paint.Style.STROKE; paint.strokeWidth = 3f
                c.drawLine(2f, -10f, 9f, -5f, paint)
            }
            4 -> { // kure
                paint.style = Paint.Style.STROKE; paint.strokeWidth = 1.8f
                c.drawCircle(0f, 0f, 11f, paint)
                c.drawOval(RectF(-5f, -11f, 5f, 11f), paint)
                c.drawLine(-11f, 0f, 11f, 0f, paint)
            }
            5 -> { // klasor
                c.drawRect(-11f, -9f, -2f, -5f, paint)
                c.drawRect(-11f, -6f, 11f, 9f, paint)
            }
            6 -> { // takvim
                c.drawRect(-10f, -9f, 10f, 10f, paint)
                paint.color = tile
                c.drawRect(-10f, -9f, 10f, -4f, paint)
                for (r in 0 until 2) for (k in 0 until 3) {
                    c.drawRect(-7f + k * 6f, 0f + r * 5f, -4f + k * 6f, 3f + r * 5f, paint)
                }
            }
            7 -> { // calar saat
                paint.style = Paint.Style.STROKE; paint.strokeWidth = 2f
                c.drawCircle(0f, 1f, 9f, paint)
                c.drawLine(0f, 1f, 0f, -5f, paint); c.drawLine(0f, 1f, 4f, 3f, paint)
                fillMode(); paint.color = Color.WHITE
                c.drawCircle(-8f, -8f, 3f, paint); c.drawCircle(8f, -8f, 3f, paint)
            }
            8 -> { // disli
                paint.style = Paint.Style.STROKE; paint.strokeWidth = 3f
                c.drawCircle(0f, 0f, 6.5f, paint)
                paint.strokeWidth = 3.5f
                for (k in 0 until 8) {
                    val a = k * Math.PI / 4
                    c.drawLine((8f * cos(a)).toFloat(), (8f * sin(a)).toFloat(),
                        (11.5f * cos(a)).toFloat(), (11.5f * sin(a)).toFloat(), paint)
                }
            }
            9 -> { // uygulamalar
                c.drawRect(-10f, -10f, -1f, -1f, paint); c.drawRect(1f, -10f, 10f, -1f, paint)
                c.drawRect(-10f, 1f, -1f, 10f, paint); c.drawRect(1f, 1f, 10f, 10f, paint)
            }
        }
        c.restore()
        fillMode()
    }
}
