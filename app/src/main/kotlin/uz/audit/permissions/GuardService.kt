package uz.audit.permissions

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.util.Calendar

/**
 * Fonda ishlaydi: qaysi ilova ochiqligini kuzatadi, vaqtni hisoblaydi.
 * Limit tugasa: bosh ekranga chiqaradi yoki parol oynasini ilova ustiga chiqaradi.
 */
class GuardService : Service() {
    @Volatile private var running = false
    @Volatile private var overlayPkg: String? = null
    private var overlayView: View? = null
    private var cooldownUntil = 0L
    private val warned = HashSet<String>()
    private val handler = Handler(Looper.getMainLooper())
    private var wm: WindowManager? = null
    private var rulesCache: List<Rule> = emptyList()
    private var rulesAt = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        L.load(this)
        try { startFg() } catch (e: Exception) { stopSelf(); return START_NOT_STICKY }
        if (!running) {
            running = true
            Thread { loop() }.start()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        removeOverlay()
        super.onDestroy()
    }

    private fun startFg() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(NotificationChannel("guard", "BAXTIYOR AUDIT", NotificationManager.IMPORTANCE_LOW))
        }
        val pi = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        @Suppress("DEPRECATION")
        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, "guard") else Notification.Builder(this)
        val n = b.setContentTitle("BAXTIYOR AUDIT")
            .setContentText(L.t("Himoya yoqilgan: ilova vaqt limitlari kuzatilmoqda", "Protection is on: app time limits are being watched", "Защита включена: отслеживаются лимиты времени"))
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(1, n)
    }

    private fun loop() {
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        var lastQuery = System.currentTimeMillis() - 3_600_000L
        var fg: String? = null
        var lastTick = System.currentTimeMillis()
        while (running) {
            val now = System.currentTimeMillis()
            try {
                val ev = usm.queryEvents(lastQuery, now)
                val e = UsageEvents.Event()
                while (ev.hasNextEvent()) {
                    ev.getNextEvent(e)
                    when (e.eventType) {
                        1 -> fg = e.packageName                       // ilova ochildi
                        2 -> if (fg == e.packageName) fg = null       // ilova to'xtadi
                        16 -> fg = null                               // ekran o'chdi
                    }
                }
            } catch (ex: Exception) { }
            lastQuery = now
            val dt = (now - lastTick).coerceIn(0L, 3000L)
            lastTick = now
            try { tick(usm, fg, dt) } catch (ex: Exception) { }
            try { Thread.sleep(1000) } catch (ex: InterruptedException) { break }
        }
    }

    private fun rules(): List<Rule> {
        val n = System.currentTimeMillis()
        if (n - rulesAt > 3000) { rulesCache = Rules.load(this); rulesAt = n }
        return rulesCache
    }

    private fun tick(usm: UsageStatsManager, fg: String?, dt: Long) {
        val now = System.currentTimeMillis()
        val op = overlayPkg
        if (op != null) {
            if (fg != op) handler.post { removeOverlay() }
            return
        }
        if (fg == null || now < cooldownUntil) return
        val rule = rules().firstOrNull { it.pkg == fg } ?: return

        var over = true
        if (rule.limitMin > 0) {
            if (!Rules.baselineDone(this, fg)) {
                val start = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                val ms = usm.queryAndAggregateUsageStats(start, now)[fg]?.totalTimeInForeground ?: 0L
                Rules.setUsed(this, fg, maxOf(Rules.usedMs(this, fg), ms))
                Rules.markBaseline(this, fg)
            }
            val used = Rules.usedMs(this, fg) + dt
            Rules.setUsed(this, fg, used)
            val limit = rule.limitMin * 60_000L
            over = used >= limit
            val left = limit - used
            if (!over && left <= 60_000L && warned.add(fg + Rules.todayKey())) {
                handler.post {
                    Toast.makeText(this, L.t("1 daqiqa qoldi: ${rule.name}", "1 minute left: ${rule.name}", "Осталась 1 минута: ${rule.name}"), Toast.LENGTH_LONG).show()
                }
            }
        }
        if (over && Rules.grantUntil(this, fg) <= now) {
            cooldownUntil = now + 2500
            if (rule.mode == "PASS" && Sec.has(this)) handler.post { showOverlay(rule) }
            else handler.post { kick(rule) }
        }
    }

    private fun kick(rule: Rule) {
        Toast.makeText(this, L.t("Vaqt limiti tugadi: ${rule.name}", "Time limit reached: ${rule.name}", "Лимит времени исчерпан: ${rule.name}"), Toast.LENGTH_LONG).show()
        goHome()
    }

    private fun goHome() {
        try {
            startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) { }
    }

    @Suppress("DEPRECATION")
    private fun showOverlay(rule: Rule) {
        if (overlayView != null) return
        L.load(this)
        val density = resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()
        fun tv(s: String, size: Float, bold: Boolean = false) = TextView(this).apply {
            text = s
            textSize = size
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            if (bold) setTypeface(null, Typeface.BOLD)
        }
        fun lp(top: Int) = LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, dp(top), 0, 0) }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.argb(250, 12, 24, 44))
            setPadding(dp(32), dp(32), dp(32), dp(32))
            isClickable = true
        }
        val always = rule.limitMin == 0
        root.addView(tv("BAXTIYOR AUDIT", 13f))
        root.addView(tv(if (always) L.t("Ilova qulflangan", "App is locked", "Приложение заблокировано")
            else L.t("Vaqt limiti tugadi", "Time limit reached", "Лимит времени исчерпан"), 24f, true), lp(12))
        root.addView(tv(rule.name + if (always) "" else " (" + L.t("${rule.limitMin} daqiqa / kun", "${rule.limitMin} min / day", "${rule.limitMin} мин / день") + ")", 15f), lp(6))
        val input = EditText(this).apply {
            hint = L.t("Parol", "Password", "Пароль")
            setHintTextColor(Color.LTGRAY)
            setTextColor(Color.WHITE)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        root.addView(input, lp(24))
        val msg = tv("", 13f).apply { setTextColor(Color.rgb(255, 138, 128)) }

        root.addView(Button(this).apply {
            text = L.t("Ochish (+5 daqiqa)", "Unlock (+5 min)", "Открыть (+5 мин)")
            setOnClickListener {
                val err = Sec.attempt(this@GuardService, input.text.toString())
                if (err == null) {
                    Rules.setGrant(this@GuardService, rule.pkg, System.currentTimeMillis() + 5 * 60_000L)
                    cooldownUntil = System.currentTimeMillis() + 1500
                    removeOverlay()
                } else {
                    msg.text = err
                    input.setText("")
                }
            }
        }, lp(12))
        root.addView(Button(this).apply {
            text = L.t("Chiqish", "Exit", "Выйти")
            setOnClickListener { removeOverlay(); goHome() }
        }, lp(6))
        root.addView(msg, lp(8))

        val type = if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else WindowManager.LayoutParams.TYPE_PHONE
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            type, WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.TRANSLUCENT)
        params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN
        try {
            wm?.addView(root, params)
            overlayView = root
            overlayPkg = rule.pkg
        } catch (e: Exception) {
            kick(rule)
        }
    }

    private fun removeOverlay() {
        val v = overlayView
        if (v != null) { try { wm?.removeView(v) } catch (e: Exception) { } }
        overlayView = null
        overlayPkg = null
    }
}
