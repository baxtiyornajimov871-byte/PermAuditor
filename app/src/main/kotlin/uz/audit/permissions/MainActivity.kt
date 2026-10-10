package uz.audit.permissions

import android.app.Activity
import android.app.AlertDialog
import android.app.AppOpsManager
import android.app.Dialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import android.text.Editable
import android.text.InputType
import android.text.TextUtils
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private var apps: List<AppRisk> = emptyList()
    private var checks: List<Check> = emptyList()
    private var loaded = false
    private var filter = "ALL"
    private var query = ""
    private var screen = "apps"
    private var unlocked = false
    private var lastStop = 0L
    private var secMsg = ""
    private var detailDialog: Dialog? = null

    private lateinit var titleView: TextView
    private lateinit var appsView: View
    private lateinit var phoneView: View
    private lateinit var chatView: View
    private lateinit var secView: View
    private lateinit var privView: View
    private lateinit var timerView: View
    private lateinit var timerBox: LinearLayout
    private lateinit var drawer: LinearLayout
    private lateinit var scrim: View
    private lateinit var lockView: LinearLayout
    private lateinit var lockInput: EditText
    private lateinit var lockMsg: TextView
    private lateinit var summaryBox: LinearLayout
    private lateinit var listBox: LinearLayout
    private lateinit var phoneBox: LinearLayout
    private lateinit var secBox: LinearLayout
    private lateinit var chatLog: LinearLayout
    private lateinit var chatScroll: ScrollView
    private val filterButtons = mutableMapOf<String, Button>()

    // ---------- yordamchilar ----------
    private fun t(uz: String, en: String, ru: String) = L.t(uz, en, ru)
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun label(s: String, size: Float, bold: Boolean = false, color: Int = Color.DKGRAY) =
        TextView(this).apply {
            text = s
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(null, Typeface.BOLD)
        }

    private fun params(top: Int = 0) = LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, dp(top), 0, 0) }

    private val green = Color.rgb(46, 125, 50)
    private val orange = Color.rgb(230, 126, 0)
    private val red = Color.rgb(198, 40, 40)

    private fun levelColor(level: String) = when (level) { "HIGH" -> red; "MED" -> orange; else -> green }

    private fun levelName(level: String) = when (level) {
        "HIGH" -> t("yuqori", "high", "высокий")
        "MED" -> t("o'rta", "medium", "средний")
        else -> t("past", "low", "низкий")
    }

    private fun tint(color: Int) = Color.argb(35, Color.red(color), Color.green(color), Color.blue(color))

    private fun rounded(fill: Int, radius: Int, stroke: Int = 0) = GradientDrawable().apply {
        setColor(fill)
        cornerRadius = dp(radius).toFloat()
        if (stroke != 0) setStroke(dp(1), stroke)
    }

    private fun watcher(f: (String) -> Unit) = object : TextWatcher {
        override fun afterTextChanged(s: Editable?) { f(s?.toString() ?: "") }
        override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
        override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
    }

    private fun openLink(url: String) {
        try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
        catch (e: Exception) { Toast.makeText(this, t("Havolani ochib bo'lmadi", "Could not open the link", "Не удалось открыть ссылку"), Toast.LENGTH_SHORT).show() }
    }

    // ---------- nomlar (tarjima) ----------
    private fun permName(k: String): String = when (k) {
        "sms_read" -> t("SMS o'qish", "Read SMS", "Чтение SMS")
        "sms_recv" -> t("SMS qabul qilish", "Receive SMS", "Приём SMS")
        "sms_send" -> t("SMS yuborish", "Send SMS", "Отправка SMS")
        "mic" -> t("Mikrofon", "Microphone", "Микрофон")
        "camera" -> t("Kamera", "Camera", "Камера")
        "contacts_read" -> t("Kontaktlarni o'qish", "Read contacts", "Чтение контактов")
        "calllog" -> t("Qo'ng'iroqlar tarixi", "Call log", "Журнал звонков")
        "call_phone" -> t("Qo'ng'iroq qilish", "Make calls", "Совершение звонков")
        "answer_calls" -> t("Qo'ng'iroqqa javob berish", "Answer calls", "Ответ на звонки")
        "outgoing_calls" -> t("Chiquvchi qo'ng'iroqlarni kuzatish", "Monitor outgoing calls", "Контроль исходящих звонков")
        "phone_state" -> t("Telefon holati", "Phone state", "Состояние телефона")
        "phone_number" -> t("Telefon raqami", "Phone number", "Номер телефона")
        "loc_fine" -> t("Aniq joylashuv", "Precise location", "Точное местоположение")
        "loc_coarse" -> t("Taxminiy joylashuv", "Approximate location", "Примерное местоположение")
        "loc_bg" -> t("Fonda joylashuv", "Background location", "Местоположение в фоне")
        "calendar" -> t("Kalendarni o'qish", "Read calendar", "Чтение календаря")
        "sensors" -> t("Tana datchiklari", "Body sensors", "Датчики тела")
        "activity" -> t("Jismoniy faollik", "Physical activity", "Физическая активность")
        "storage" -> t("Fayl va media o'qish", "Read files and media", "Чтение файлов и медиа")
        "all_files" -> t("Barcha fayllarga kirish", "Access all files", "Доступ ко всем файлам")
        "bluetooth" -> "Bluetooth"
        "nearby" -> t("Yaqin atrofdagi qurilmalar", "Nearby devices", "Устройства поблизости")
        "overlay" -> t("Boshqa ilovalar ustidan chizish", "Draw over other apps", "Отображение поверх других приложений")
        "install_pkgs" -> t("Ilova o'rnatish", "Install apps", "Установка приложений")
        "usage_stats" -> t("Ilovalardan foydalanish statistikasi", "App usage statistics", "Статистика использования приложений")
        "write_settings" -> t("Tizim sozlamalarini o'zgartirish", "Modify system settings", "Изменение системных настроек")
        "a11y_on" -> t("Maxsus imkoniyatlar (Accessibility) xizmati, YOQILGAN", "Accessibility service, ENABLED", "Служба спец. возможностей, ВКЛЮЧЕНА")
        "a11y_off" -> t("Maxsus imkoniyatlar (Accessibility) xizmati, yoqilmagan", "Accessibility service, not enabled", "Служба спец. возможностей, не включена")
        "notif_on" -> t("Bildirishnomalarni o'qish xizmati, YOQILGAN", "Notification listener, ENABLED", "Чтение уведомлений, ВКЛЮЧЕНО")
        "notif_off" -> t("Bildirishnomalarni o'qish xizmati, yoqilmagan", "Notification listener, not enabled", "Чтение уведомлений, не включено")
        "admin_on" -> t("Qurilma administratori, FAOL", "Device administrator, ACTIVE", "Администратор устройства, АКТИВЕН")
        "admin_off" -> t("Qurilma administratori huquqi so'ralgan", "Device administrator requested", "Запрошены права администратора")
        "not_official" -> t("Rasmiy do'kondan o'rnatilmagan", "Not installed from an official store", "Установлено не из официального магазина")
        "old_target" -> t("Eski Android versiyasi uchun yozilgan", "Built for an old Android version", "Создано для старой версии Android")
        "bg_fgs" -> t("Fon xizmati", "Background service", "Фоновая служба")
        "bg_boot" -> t("Telefon yonganda o'zi ishga tushadi", "Starts when the phone boots", "Запускается при включении телефона")
        "bg_alarm" -> t("Aniq vaqtli signal", "Exact-time alarms", "Точные будильники")
        "bg_battery" -> t("Batareya cheklovidan chiqarilgan", "Exempt from battery limits", "Исключено из ограничений батареи")
        else -> k
    }

    private fun srcName(k: String) = when (k) {
        "play" -> "Play Market"
        "getapps" -> "Xiaomi GetApps"
        "galaxy" -> "Samsung Galaxy Store"
        "appgallery" -> "Huawei AppGallery"
        "amazon" -> "Amazon Appstore"
        "unknown" -> t("Noma'lum (APK fayl)", "Unknown (APK file)", "Неизвестно (APK-файл)")
        else -> t("Boshqa manba (APK)", "Other source (APK)", "Другой источник (APK)")
    }

    private fun catName(k: String) = when (k) {
        "SOCIAL" -> t("Ijtimoiy tarmoq / xabar almashish", "Social / messaging", "Соцсети / мессенджер")
        "MAPS" -> t("Xarita / navigatsiya", "Maps / navigation", "Карты / навигация")
        "IMAGE" -> t("Foto / kamera", "Photo / camera", "Фото / камера")
        "GAME" -> t("O'yin", "Game", "Игра")
        else -> t("Aniqlanmagan", "Unknown", "Не определён")
    }

    private fun warnText(k: String) = when (k) {
        "warn_sensitive_bg" -> t(
            "Mikrofon, kamera yoki joylashuv ruxsati fonda ishlash imkoniyati bilan birga bor. Bu ilova turiga mos kelishini tekshiring.",
            "Microphone, camera or location access is combined with background capability. Check that this fits the app's purpose.",
            "Доступ к микрофону, камере или местоположению сочетается с работой в фоне. Проверьте, соответствует ли это назначению приложения.")
        "warn_game" -> t(
            "O'yin uchun mikrofon, kamera, SMS yoki kontaktlar ruxsati odatiy emas.",
            "Microphone, camera, SMS or contacts access is unusual for a game.",
            "Для игры доступ к микрофону, камере, SMS или контактам необычен.")
        else -> selfWarn()
    }

    // SELF_START
    private fun selfWarn() = t(
        "Bu BAXTIYOR AUDITning o'zi. U ham boshqa ilovalar kabi bir xil qoidalar bilan baholanadi. Vaqt limiti va qulf funksiyasi uchun u foydalanish statistikasi, boshqa ilovalar ustidan chizish va fon xizmati ruxsatlarini so'raydi; ular faqat siz yoqsangiz ishlaydi.",
        "This is BAXTIYOR AUDIT itself. It is scored by the same rules as any other app. For the time limit and lock feature it asks for usage access, draw-over-apps and a background service; these work only if you turn them on.",
        "Это само приложение BAXTIYOR AUDIT. Оно оценивается по тем же правилам, что и другие приложения. Для лимитов времени и блокировки оно запрашивает доступ к статистике, отображение поверх приложений и фоновую службу; они работают, только если вы их включите.")
    // SELF_END

    private fun checkTitle(c: Check) = when (c.key) {
        "lock" -> t("Ekran qulfi", "Screen lock", "Блокировка экрана")
        "patch" -> t("Xavfsizlik yangilanishi", "Security update", "Обновление безопасности")
        "dev" -> t("Dasturchi rejimi", "Developer mode", "Режим разработчика")
        "adb" -> t("USB orqali nosozliklarni tuzatish (USB debugging)", "USB debugging", "Отладка по USB")
        "root" -> t("Tizim huquqlarini buzish (root) belgilari", "Root indicators", "Признаки root-доступа")
        "enc" -> t("Xotira shifrlanishi", "Storage encryption", "Шифрование памяти")
        "a11y" -> t("Yoqilgan maxsus imkoniyatlar (Accessibility) xizmatlari", "Enabled accessibility services", "Включённые службы спец. возможностей")
        else -> t("Bildirishnomalarni o'qiydigan xizmatlar", "Notification listener services", "Службы чтения уведомлений")
    }

    private fun checkDetail(c: Check): String = when (c.key) {
        "lock" -> if (c.ok) t("PIN, parol yoki barmoq izi o'rnatilgan", "PIN, password or fingerprint is set", "Установлен PIN, пароль или отпечаток")
        else t("Ekran qulfi yo'q, telefon himoyasiz", "No screen lock, the phone is unprotected", "Блокировки экрана нет, телефон не защищён")
        "patch" -> {
            val parts = c.arg.split("|")
            val date = parts[0]
            val days = parts.getOrNull(1) ?: ""
            if (days.isEmpty()) t("Sana aniqlanmadi: $date", "Date unknown: $date", "Дата не определена: $date")
            else {
                val base = t("Oxirgi yangilanish: $date ($days kun oldin)", "Last update: $date ($days days ago)", "Последнее обновление: $date ($days дн. назад)")
                if (c.ok) base else base + t(". Yangilash tavsiya etiladi", ". Updating is recommended", ". Рекомендуется обновить")
            }
        }
        "dev" -> if (c.ok) t("O'chiq", "Off", "Выключен")
        else t("Yoqilgan. Oddiy foydalanuvchi uchun o'chirib qo'ygan ma'qul", "Enabled. Better turned off for regular users", "Включён. Обычному пользователю лучше выключить")
        "adb" -> if (c.ok) t("O'chiq", "Off", "Выключена")
        else t("Yoqilgan. Kompyuter orqali telefonga kirish mumkin", "Enabled. The phone can be accessed from a computer", "Включена. К телефону можно подключиться с компьютера")
        "root" -> if (c.ok) t("Belgilari topilmadi (taxminiy tekshiruv)", "No indicators found (approximate check)", "Признаки не найдены (приблизительная проверка)")
        else t("Belgilari topildi (taxminiy tekshiruv)", "Indicators found (approximate check)", "Признаки найдены (приблизительная проверка)")
        "enc" -> if (c.ok) t("Telefon xotirasi shifrlangan", "Phone storage is encrypted", "Память телефона зашифрована")
        else t("Shifrlanmagan yoki aniqlanmadi", "Not encrypted or unknown", "Не зашифрована или не определено")
        else -> if (c.ok) t("Yoqilgan xizmat yo'q", "None enabled", "Включённых нет")
        else t("Yoqilgan: ", "Enabled: ", "Включено: ") + c.arg
    }

    // ---------- parol kuchi ----------
    private data class Strength(val rank: Int, val bits: Double, val secs: Double)

    private val common = setOf("123456", "1234567", "12345678", "123456789", "qwerty", "qwerty123", "password",
        "parol", "parol123", "111111", "000000", "admin", "1q2w3e4r", "iloveyou", "uzbekistan", "12345", "1234", "0000", "1111")

    private fun strength(p: String): Strength {
        if (p.isEmpty()) return Strength(-1, 0.0, 0.0)
        val low = p.any { it in 'a'..'z' }
        val up = p.any { it in 'A'..'Z' }
        val dg = p.any { it in '0'..'9' }
        val sy = p.any { !(it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9') }
        var pool = (if (low) 26 else 0) + (if (up) 26 else 0) + (if (dg) 10 else 0) + (if (sy) 33 else 0)
        if (pool == 0) pool = 1
        val bits = p.length * Math.log(pool.toDouble()) / Math.log(2.0)
        val isCommon = p.lowercase() in common
        val secs = if (isCommon) 0.0 else Math.pow(2.0, bits) / 2.0 / 1e10
        val rank = when { isCommon || bits < 28 -> 0; bits < 36 -> 1; bits < 60 -> 2; else -> 3 }
        return Strength(rank, bits, secs)
    }

    private fun rankName(r: Int) = when (r) {
        0 -> t("Juda zaif", "Very weak", "Очень слабый")
        1 -> t("Zaif", "Weak", "Слабый")
        2 -> t("O'rtacha", "Medium", "Средний")
        3 -> t("Kuchli", "Strong", "Сильный")
        else -> t("Parol kiritilmagan", "No password entered", "Пароль не введён")
    }

    private fun rankColor(r: Int) = when (r) { 0, 1 -> red; 2 -> orange; 3 -> green; else -> Color.DKGRAY }

    private fun fmtTime(s: Double): String = when {
        s < 1.0 -> t("bir zumda", "instantly", "мгновенно")
        s < 60.0 -> "${s.toInt()} " + t("soniya", "seconds", "сек.")
        s < 3600.0 -> "${(s / 60).toInt()} " + t("daqiqa", "minutes", "мин.")
        s < 86400.0 -> "${(s / 3600).toInt()} " + t("soat", "hours", "ч.")
        s < 31536000.0 -> "${(s / 86400).toInt()} " + t("kun", "days", "дн.")
        else -> {
            val y = s / 31536000.0
            if (y > 1e9) t("milliardlab yil", "billions of years", "миллиарды лет")
            else if (y > 1e6) "${(y / 1e6).toInt()} " + t("million yil", "million years", "млн лет")
            else "${y.toLong()} " + t("yil", "years", "лет")
        }
    }

    // ---------- qobiq ----------
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        L.load(this)
        unlocked = !Sec.has(this)
        buildUi()
        rescan()
        ensureGuard()
    }

    private fun buildUi() {
        filterButtons.clear()
        val root = FrameLayout(this)
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(12), dp(16), dp(8))
            setBackgroundColor(Color.WHITE)
        }
        bar.addView(TextView(this).apply {
            text = "☰"
            textSize = 26f
            setTextColor(Color.BLACK)
            setPadding(dp(12), dp(4), dp(16), dp(4))
            setOnClickListener { openDrawer() }
        })
        titleView = label("", 20f, true, Color.BLACK)
        bar.addView(titleView)
        column.addView(bar, LinearLayout.LayoutParams(-1, -2))

        appsView = buildApps()
        phoneView = buildPhone()
        chatView = buildChat()
        secView = buildSecurity()
        privView = buildPrivacy()
        timerView = buildTimer()
        val content = FrameLayout(this)
        for (v in listOf(appsView, phoneView, chatView, secView, privView, timerView)) content.addView(v)
        column.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(column, FrameLayout.LayoutParams(-1, -1))

        scrim = View(this).apply {
            setBackgroundColor(Color.argb(120, 0, 0, 0))
            visibility = View.GONE
            setOnClickListener { closeDrawer() }
        }
        root.addView(scrim, FrameLayout.LayoutParams(-1, -1))
        drawer = buildDrawer()
        root.addView(drawer, FrameLayout.LayoutParams(dp(280), -1, Gravity.START))
        lockView = buildLock()
        root.addView(lockView, FrameLayout.LayoutParams(-1, -1))

        setContentView(root)
        applySecureFlag()
        showScreen(screen)
        if (!unlocked) showLock()
    }

    private fun secureOn() = getSharedPreferences("sec", MODE_PRIVATE).getBoolean("secure", false)

    private fun applySecureFlag() {
        if (secureOn()) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    override fun onStop() {
        super.onStop()
        lastStop = System.currentTimeMillis()
    }

    override fun onResume() {
        super.onResume()
        if (Sec.has(this) && unlocked && lastStop > 0 && System.currentTimeMillis() - lastStop > 10_000) showLock()
        lastStop = 0
        if (loaded) rescan()
        if (screen == "timer") renderTimer()
    }

    // ---------- qulf oynasi ----------
    private fun buildLock(): LinearLayout {
        val l = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.WHITE)
            setPadding(dp(32), dp(32), dp(32), dp(32))
            visibility = View.GONE
            isClickable = true
        }
        l.addView(label("BAXTIYOR AUDIT", 24f, true, Color.BLACK))
        l.addView(label(t("Davom etish uchun parolni kiriting", "Enter your password to continue", "Введите пароль, чтобы продолжить"), 14f), params(8))
        lockInput = EditText(this).apply {
            hint = t("Parol", "Password", "Пароль")
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        l.addView(lockInput, params(16))
        l.addView(Button(this).apply {
            text = t("Ochish", "Unlock", "Открыть")
            setOnClickListener { tryUnlock(lockInput.text.toString()) }
        }, params(8))
        lockMsg = label("", 13f, false, red)
        l.addView(lockMsg, params(8))
        return l
    }

    private fun showLock() {
        unlocked = false
        detailDialog?.dismiss()
        closeDrawer()
        lockInput.setText("")
        lockMsg.text = ""
        lockView.visibility = View.VISIBLE
    }

    private fun tryUnlock(input: String) {
        val err = Sec.attempt(this, input)
        lockInput.setText("")
        if (err == null) {
            unlocked = true
            lockView.visibility = View.GONE
        } else lockMsg.text = err
    }

    // ---------- menyu ----------
    private fun buildDrawer(): LinearLayout {
        val inner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(48), dp(8), dp(16))
        }
        inner.addView(label("BAXTIYOR AUDIT", 20f, true, Color.BLACK)
            .apply { setPadding(dp(16), 0, dp(16), dp(16)) })
        fun item(text: String, action: () -> Unit) {
            inner.addView(label(text, 16f, false, Color.BLACK).apply {
                setPadding(dp(16), dp(13), dp(16), dp(13))
                setOnClickListener { action() }
            })
        }
        item(t("Ilovalar", "Apps", "Приложения")) { showScreen("apps") }
        item(t("Telefon xavfsizligi", "Phone security", "Безопасность телефона")) { showScreen("phone") }
        item(t("Yordamchi (chat)", "Assistant (chat)", "Помощник (чат)")) { showScreen("chat") }
        item(t("Ilova taymeri va qulf", "App timer & lock", "Таймер и блокировка приложений")) { showScreen("timer") }
        item(t("Parol va xavfsizlik", "Password & security", "Пароль и безопасность")) { showScreen("sec") }
        item(t("Maxfiylik va baholash", "Privacy & scoring", "Конфиденциальность и оценка")) { showScreen("priv") }
        item(t("Hisobotni ulashish", "Share report", "Поделиться отчётом")) { closeDrawer(); shareReport() }
        item("Til / Language / Язык") { closeDrawer(); chooseLanguage() }
        inner.addView(View(this).apply { setBackgroundColor(Color.LTGRAY) },
            LinearLayout.LayoutParams(-1, dp(1)).apply { setMargins(dp(16), dp(8), dp(16), dp(8)) })
        item(t("Taklif va murojaatlar", "Feedback & contact", "Предложения и обращения")) { closeDrawer(); openLink("https://t.me/BAXTIYOR_07_77") }
        return LinearLayout(this).apply {
            setBackgroundColor(Color.WHITE)
            visibility = View.GONE
            addView(ScrollView(this@MainActivity).apply { addView(inner) }, LinearLayout.LayoutParams(-1, -1))
        }
    }

    private fun chooseLanguage() {
        val items = arrayOf("O'zbekcha", "English", "Русский")
        AlertDialog.Builder(this)
            .setTitle("Til / Language / Язык")
            .setItems(items) { _, i ->
                L.save(this, when (i) { 1 -> "en"; 2 -> "ru"; else -> "uz" })
                buildUi()
                refreshAll()
            }.show()
    }

    private fun openDrawer() { scrim.visibility = View.VISIBLE; drawer.visibility = View.VISIBLE }
    private fun closeDrawer() { scrim.visibility = View.GONE; drawer.visibility = View.GONE }

    private fun showScreen(name: String) {
        screen = name
        appsView.visibility = if (name == "apps") View.VISIBLE else View.GONE
        phoneView.visibility = if (name == "phone") View.VISIBLE else View.GONE
        chatView.visibility = if (name == "chat") View.VISIBLE else View.GONE
        secView.visibility = if (name == "sec") View.VISIBLE else View.GONE
        privView.visibility = if (name == "priv") View.VISIBLE else View.GONE
        timerView.visibility = if (name == "timer") View.VISIBLE else View.GONE
        titleView.text = when (name) {
            "phone" -> t("Telefon xavfsizligi", "Phone security", "Безопасность телефона")
            "chat" -> t("Yordamchi", "Assistant", "Помощник")
            "sec" -> t("Parol va xavfsizlik", "Password & security", "Пароль и безопасность")
            "priv" -> t("Maxfiylik va baholash", "Privacy & scoring", "Конфиденциальность и оценка")
            "timer" -> t("Taymer va qulf", "Timer & lock", "Таймер и блокировка")
            else -> t("Ilovalar", "Apps", "Приложения")
        }
        if (name == "sec") renderSecurity()
        if (name == "timer") renderTimer()
        closeDrawer()
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        when {
            lockView.visibility == View.VISIBLE -> moveTaskToBack(true)
            drawer.visibility == View.VISIBLE -> closeDrawer()
            screen != "apps" -> showScreen("apps")
            else -> super.onBackPressed()
        }
    }

    private fun rescan() {
        thread {
            val a = RiskScorer.scan(this)
            val c = RiskScorer.deviceChecks(this)
            runOnUiThread {
                apps = a
                checks = c
                loaded = true
                refreshAll()
            }
        }
    }

    private fun refreshAll() {
        if (!loaded) return
        renderSummary()
        renderList()
        renderPhone()
    }

    // ---------- ilovalar ----------
    private fun buildApps(): View {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(24))
        }
        summaryBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = rounded(Color.rgb(238, 238, 238), 12)
        }
        summaryBox.addView(label(t("Tekshirilmoqda...", "Scanning...", "Проверка..."), 15f))
        col.addView(summaryBox, params(4))

        col.addView(EditText(this).apply {
            hint = t("Ilova nomini qidiring...", "Search apps...", "Поиск приложения...")
            setSingleLine()
            addTextChangedListener(watcher { query = it; renderList() })
        }, params(8))

        val filterRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val names = listOf(
            "ALL" to t("Hammasi", "All", "Все"),
            "HIGH" to t("Yuqori", "High", "Высокий"),
            "MED" to t("O'rta", "Medium", "Средний"),
            "LOW" to t("Past", "Low", "Низкий")
        )
        for ((key, text) in names) {
            val b = Button(this).apply {
                this.text = text
                setOnClickListener { filter = key; renderList() }
            }
            filterButtons[key] = b
            filterRow.addView(b, LinearLayout.LayoutParams(0, -2, 1f))
        }
        col.addView(filterRow, params(4))
        listBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(listBox, params(8))
        return ScrollView(this).apply { addView(col) }
    }

    private fun overallScore(): Int {
        val high = apps.count { it.level == "HIGH" }
        val med = apps.count { it.level == "MED" }
        val issues = checks.count { !it.ok }
        return (100 - high * 10 - med * 4 - issues * 10).coerceIn(0, 100)
    }

    private fun renderSummary() {
        val high = apps.count { it.level == "HIGH" }
        val med = apps.count { it.level == "MED" }
        val low = apps.count { it.level == "LOW" }
        val score = overallScore()
        val color = when { score >= 70 -> green; score >= 40 -> orange; else -> red }
        summaryBox.removeAllViews()
        summaryBox.background = rounded(tint(color), 12)
        summaryBox.addView(label(t("Umumiy xavfsizlik bali: $score/100", "Overall security score: $score/100", "Общая оценка безопасности: $score/100"), 20f, true, color))
        summaryBox.addView(label(t("${apps.size} ta ilova tekshirildi", "${apps.size} apps checked", "Проверено приложений: ${apps.size}"), 14f), params(6))
        summaryBox.addView(label(t("Yuqori xavf: $high    O'rta: $med    Past: $low", "High risk: $high    Medium: $med    Low: $low", "Высокий риск: $high    Средний: $med    Низкий: $low"), 14f), params(2))
        summaryBox.addView(label(t("Telefon sozlamalarida muammo: ${checks.count { !it.ok }}", "Phone settings issues: ${checks.count { !it.ok }}", "Проблем в настройках телефона: ${checks.count { !it.ok }}"), 14f), params(2))
        summaryBox.addView(label(t(
            "Ilovaning xavf bali uning zararli ekanini emas, sezgir imkoniyatlari ko'pligini ko'rsatadi.",
            "An app's risk score shows how many sensitive capabilities it has, not that it is malicious.",
            "Балл риска показывает количество чувствительных возможностей приложения, а не то, что оно вредоносное."), 11f), params(6))
    }

    private fun renderList() {
        for ((key, b) in filterButtons) b.alpha = if (key == filter) 1f else 0.5f
        listBox.removeAllViews()
        if (!loaded) { listBox.addView(label(t("Tekshirilmoqda...", "Scanning...", "Проверка..."), 14f)); return }
        val shown = apps.filter {
            (filter == "ALL" || it.level == filter) &&
                (query.isBlank() || it.name.contains(query, true) || it.pkg.contains(query, true))
        }
        if (shown.isEmpty()) { listBox.addView(label(t("Ilova topilmadi", "No apps found", "Приложения не найдены"), 14f)); return }
        for (a in shown) listBox.addView(appRow(a), params(8))
    }

    private fun appRow(a: AppRisk): View {
        val color = levelColor(a.level)
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = rounded(Color.rgb(247, 247, 247), 12, Color.rgb(225, 225, 225))
            setOnClickListener { showDetail(a) }
        }
        val icon = ImageView(this)
        try { icon.setImageDrawable(packageManager.getApplicationIcon(a.pkg)) } catch (e: Exception) {}
        row.addView(icon, LinearLayout.LayoutParams(dp(44), dp(44)))

        val info = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        info.addView(label(a.name, 15f, true, Color.BLACK).apply { setSingleLine(); ellipsize = TextUtils.TruncateAt.END })
        val lv = levelName(a.level)
        info.addView(label(t("Xavf bali: ${a.score}/100 · $lv daraja", "Risk score: ${a.score}/100 · $lv level", "Балл риска: ${a.score}/100 · уровень: $lv"), 12f, false, color), params(2))
        val bar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; weightSum = 100f }
        bar.addView(View(this).apply { setBackgroundColor(color) }, LinearLayout.LayoutParams(0, -1, a.score.toFloat()))
        bar.addView(View(this).apply { setBackgroundColor(Color.rgb(224, 224, 224)) }, LinearLayout.LayoutParams(0, -1, (100 - a.score).toFloat()))
        info.addView(bar, LinearLayout.LayoutParams(-1, dp(6)).apply { setMargins(0, dp(6), 0, 0) })
        row.addView(info, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(12), 0, 0, 0) })
        return row
    }

    // ---------- tahlil oynasi ----------
    private fun summaryText(a: AppRisk) = when (a.level) {
        "HIGH" -> t("Ko'p sezgir imkoniyat so'ralgan yoki berilgan. Kerak bo'lmasa ruxsatlarni qaytarib oling yoki ilovani o'chiring.",
            "Many sensitive capabilities are requested or granted. If you do not need them, revoke the permissions or uninstall the app.",
            "Запрошено или выдано много чувствительных возможностей. Если они не нужны, отзовите разрешения или удалите приложение.")
        "MED" -> t("Ba'zi ruxsatlar bor. Ular ilova vazifasiga mos kelishini tekshiring.",
            "Some permissions are present. Check that they fit the app's purpose.",
            "Есть некоторые разрешения. Проверьте, соответствуют ли они назначению приложения.")
        else -> t("Sezgir ruxsatlar kam. Odatda xavotirga o'rin yo'q.",
            "Few sensitive permissions. Usually nothing to worry about.",
            "Чувствительных разрешений мало. Обычно поводов для беспокойства нет.")
    }

    private fun showDetail(a: AppRisk) {
        val color = levelColor(a.level)
        val dlg = Dialog(this)
        detailDialog = dlg
        dlg.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(24))
        }
        val head = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val icon = ImageView(this)
        try { icon.setImageDrawable(packageManager.getApplicationIcon(a.pkg)) } catch (e: Exception) {}
        head.addView(icon, LinearLayout.LayoutParams(dp(52), dp(52)))
        val names = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        names.addView(label(a.name, 19f, true, Color.BLACK))
        names.addView(label(t("Manba: ", "Source: ", "Источник: ") + srcName(a.source), 12f))
        head.addView(names, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(12), 0, dp(8), 0) })
        head.addView(label("${a.score}/100", 16f, true, color).apply {
            setPadding(dp(10), dp(4), dp(10), dp(4)); background = rounded(tint(color), 8)
        })
        col.addView(head)
        col.addView(label(a.pkg, 11f), params(4))
        val catNote = if (a.category == "NONE") "" else if (a.categoryDeclared)
            t(" (Android e'lon qilgan)", " (declared by Android)", " (указан в Android)")
        else t(" (taxminiy)", " (estimated)", " (предположительно)")
        col.addView(label(t("Ilova turi: ", "App type: ", "Тип приложения: ") + catName(a.category) + catNote, 12f), params(2))
        col.addView(label(t("Xavf darajasi: ", "Risk level: ", "Уровень риска: ") + levelName(a.level), 13f, true, color), params(2))

        fun section(s: String) = col.addView(label(s, 14f, true, Color.BLACK), params(16))
        fun line(left: String, right: String, rc: Int = Color.DKGRAY) {
            val r = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            r.addView(label(left, 13f), LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(0, 0, dp(8), 0) })
            r.addView(label(right, 13f, false, rc))
            col.addView(r, params(6))
        }

        section(t("Ruxsatlar va ball", "Permissions and score", "Разрешения и баллы"))
        if (a.findings.isEmpty()) col.addView(label(t("Sezgir ruxsat topilmadi", "No sensitive permissions found", "Чувствительных разрешений не найдено"), 13f), params(6))
        for (f in a.findings) {
            val isPerm = f.key in RiskScorer.permKeys
            val right = if (isPerm) (if (f.granted) t("berilgan", "granted", "выдано") else t("so'ralgan", "requested", "запрошено")) + ", +${f.points}"
            else "+${f.points} " + t("ball", "pts", "б.")
            line(permName(f.key), right, if (f.granted && f.points >= 10) red else Color.DKGRAY)
            if (f.note == "expected") col.addView(label("   " + t("ilova turiga mos, yarmi hisoblandi", "typical for this app type, counted at half", "типично для этого типа, засчитано наполовину"), 11f), params(0))
        }

        section(t("Fonda ishlash imkoniyati", "Background capability", "Работа в фоне"))
        for (f in a.background) line(permName(f.key), if (f.granted) t("bor", "yes", "да") + ", +${f.points}" else t("yo'q", "no", "нет"))
        col.addView(label(t("Eslatma: Android boshqa ilova hozir ishlayotganini ko'rsatmaydi. Bu faqat imkoniyat.",
            "Note: Android does not show whether another app is running right now. This is only a capability.",
            "Примечание: Android не показывает, работает ли другое приложение прямо сейчас. Это лишь возможность."), 11f), params(6))
        for (w in a.warnings) {
            val c = if (w == "warn_self") Color.DKGRAY else red
            col.addView(label(warnText(w), 13f, false, c).apply {
                setPadding(dp(10), dp(8), dp(10), dp(8)); background = rounded(tint(c), 8)
            }, params(10))
        }

        section(t("Xulosa", "Summary", "Вывод"))
        col.addView(label(summaryText(a), 13f), params(6))

        fun btn(text: String, c: Int = Color.BLACK, action: () -> Unit) =
            col.addView(Button(this).apply { this.text = text; setTextColor(c); setOnClickListener { action() } }, params(8))
        val failMsg = t("Sozlamalarni ochib bo'lmadi", "Could not open settings", "Не удалось открыть настройки")
        btn(t("Ilova sozlamalarini ochish", "Open app settings", "Открыть настройки приложения")) {
            try { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${a.pkg}"))) }
            catch (e: Exception) { Toast.makeText(this, failMsg, Toast.LENGTH_SHORT).show() }
        }
        btn(t("Batareya sozlamalarini ochish", "Open battery settings", "Открыть настройки батареи")) {
            try { startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
            catch (e: Exception) { Toast.makeText(this, failMsg, Toast.LENGTH_SHORT).show() }
        }
        btn(t("Ilovani o'chirish", "Uninstall app", "Удалить приложение"), red) {
            try { startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:${a.pkg}"))) }
            catch (e: Exception) { Toast.makeText(this, failMsg, Toast.LENGTH_SHORT).show() }
        }
        btn(t("Yopish", "Close", "Закрыть")) { dlg.dismiss() }
        col.addView(label(t("Yuqori ball ilova zararli ekanini isbotlamaydi. U faqat qanday sezgir imkoniyatlar mavjudligini ko'rsatadi.",
            "A high score does not prove an app is harmful. It only shows which sensitive capabilities exist.",
            "Высокий балл не доказывает, что приложение вредоносно. Он лишь показывает, какие чувствительные возможности есть."), 11f), params(12))

        val sheet = ScrollView(this).apply {
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                val r = dp(22).toFloat()
                cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
            }
            addView(col)
        }
        dlg.setContentView(sheet)
        dlg.setCanceledOnTouchOutside(true)
        dlg.window?.let { w ->
            w.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            w.setLayout(WindowManager.LayoutParams.MATCH_PARENT, (resources.displayMetrics.heightPixels * 0.85).toInt())
            w.setGravity(Gravity.BOTTOM)
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            w.setDimAmount(0.55f)
            if (secureOn()) w.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        dlg.show()
    }

    // ---------- telefon ----------
    private fun buildPhone(): View {
        phoneBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(24))
        }
        return ScrollView(this).apply { addView(phoneBox) }
    }

    private fun renderPhone() {
        phoneBox.removeAllViews()
        for (c in checks) {
            val color = if (c.ok) green else orange
            phoneBox.addView(label((if (c.ok) "✓ " else "⚠ ") + checkTitle(c) + "\n" + checkDetail(c), 14f).apply {
                setPadding(dp(12), dp(10), dp(12), dp(10)); background = rounded(tint(color), 10)
            }, params(8))
        }
        phoneBox.addView(label(t("Eslatma: bu tekshiruvlar taxminiy va qurilma xavfsizligining to'liq kafolati emas.",
            "Note: these checks are approximate and not a full guarantee of device security.",
            "Примечание: эти проверки приблизительные и не гарантируют полную безопасность устройства."), 12f), params(12))
    }

    // ---------- parol va xavfsizlik ----------
    private fun buildSecurity(): View {
        secBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(32))
        }
        return ScrollView(this).apply { addView(secBox) }
    }

    private fun pwField(h: String) = EditText(this).apply {
        hint = h
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
    }

    private fun setPwVisible(e: EditText, on: Boolean) {
        e.inputType = InputType.TYPE_CLASS_TEXT or
            (if (on) InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD else InputType.TYPE_TEXT_VARIATION_PASSWORD)
        e.setSelection(e.text.length)
    }

    private fun renderSecurity() {
        secBox.removeAllViews()
        val has = Sec.has(this)
        secBox.addView(label(
            if (has) t("Ilovaga kirish paroli: o'rnatilgan", "App password: set", "Пароль приложения: установлен")
            else t("Ilovaga kirish paroli: o'rnatilmagan", "App password: not set", "Пароль приложения: не установлен"),
            16f, true, if (has) green else orange))
        secBox.addView(label(t(
            "Parol ilova ochilganda va 10 soniyadan ko'p fonda turib qaytganda so'raladi. Parol telefonda ochiq holda emas, tasodifiy tuz bilan xeshlangan (PBKDF2) holda saqlanadi. 5 marta xato kiritilsa, kutish vaqti qo'yiladi. Parol esdan chiqsa, ilovani o'chirib qayta o'rnatish kerak bo'ladi.",
            "The password is asked when the app opens and when you return after more than 10 seconds in the background. It is stored as a salted hash (PBKDF2), not in plain text. After 5 wrong attempts a waiting time applies. If you forget it, you must uninstall and reinstall the app.",
            "Пароль запрашивается при открытии и при возврате после более чем 10 секунд в фоне. Он хранится как хеш с солью (PBKDF2), а не открытым текстом. После 5 ошибок вводится время ожидания. Если забудете пароль, придётся удалить и заново установить приложение."), 12f), params(4))

        val curField = if (has) pwField(t("Joriy parol", "Current password", "Текущий пароль")) else null
        if (curField != null) secBox.addView(curField, params(12))
        val newPw = pwField(t("Yangi parol", "New password", "Новый пароль"))
        val conf = pwField(t("Parolni takrorlang", "Repeat password", "Повторите пароль"))
        secBox.addView(newPw, params(8))
        secBox.addView(CheckBox(this).apply {
            text = t("Parolni ko'rsatish", "Show password", "Показать пароль")
            setOnCheckedChangeListener { _, on -> for (e in listOfNotNull(curField, newPw, conf)) setPwVisible(e, on) }
        }, params(0))

        val fill = View(this)
        val rest = View(this).apply { setBackgroundColor(Color.rgb(224, 224, 224)) }
        val bar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        bar.addView(fill, LinearLayout.LayoutParams(0, -1, 0f))
        bar.addView(rest, LinearLayout.LayoutParams(0, -1, 100f))
        secBox.addView(bar, LinearLayout.LayoutParams(-1, dp(6)).apply { setMargins(0, dp(8), 0, 0) })
        val lvl = label("", 15f, true)
        val crack = label("", 12f)
        val ent = label("", 12f)
        val rows = List(5) { label("", 13f) }
        secBox.addView(lvl, params(6))
        secBox.addView(crack, params(2))
        for (r in rows) secBox.addView(r, params(2))
        secBox.addView(ent, params(4))
        secBox.addView(conf, params(10))

        val msg = label(secMsg, 13f, false, green)
        secMsg = ""
        fun say(s: String, ok: Boolean) { msg.text = s; msg.setTextColor(if (ok) green else red) }

        fun update(p: String) {
            val s = strength(p)
            val col = rankColor(s.rank)
            val w = when (s.rank) { -1 -> 0; 0 -> 15; 1 -> 35; 2 -> 65; else -> 100 }
            fill.setBackgroundColor(col)
            fill.layoutParams = LinearLayout.LayoutParams(0, -1, w.toFloat())
            rest.layoutParams = LinearLayout.LayoutParams(0, -1, (100 - w).toFloat())
            lvl.text = rankName(s.rank)
            lvl.setTextColor(col)
            crack.text = if (p.isEmpty()) "" else t("Taxminiy buzish vaqti: ", "Estimated crack time: ", "Примерное время взлома: ") + fmtTime(s.secs)
            ent.text = if (p.isEmpty()) "" else t(
                "Entropiya: ${s.bits.toInt()} bit (soniyasiga 10 milliard taxmin deb hisoblangan)",
                "Entropy: ${s.bits.toInt()} bits (assuming 10 billion guesses per second)",
                "Энтропия: ${s.bits.toInt()} бит (при 10 млрд попыток в секунду)")
            val oks = listOf(
                (p.length >= 8) to t("Kamida 8 ta belgi", "At least 8 characters", "Не менее 8 символов"),
                p.any { it in 'A'..'Z' } to t("Katta harf", "Uppercase letter", "Заглавная буква"),
                p.any { it in '0'..'9' } to t("Raqam", "Digit", "Цифра"),
                p.any { !(it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9') } to t("Maxsus belgi (!, #, @ ...)", "Special character (!, #, @ ...)", "Спецсимвол (!, #, @ ...)"),
                (p.isNotEmpty() && p.lowercase() !in common) to t("Ko'p ishlatiladigan parol emas", "Not a commonly used password", "Не распространённый пароль")
            )
            for ((i, pair) in oks.withIndex()) {
                rows[i].text = (if (pair.first) "✓ " else "✗ ") + pair.second
                rows[i].setTextColor(if (pair.first) green else red)
            }
        }
        newPw.addTextChangedListener(watcher { update(it) })
        update("")

        secBox.addView(Button(this).apply {
            text = if (has) t("Parolni almashtirish", "Change password", "Сменить пароль") else t("Parolni o'rnatish", "Set password", "Установить пароль")
            setOnClickListener {
                if (has && !Sec.verify(this@MainActivity, curField?.text?.toString() ?: "")) {
                    say(t("Joriy parol noto'g'ri.", "Current password is wrong.", "Текущий пароль неверный."), false); return@setOnClickListener
                }
                val np = newPw.text.toString()
                val s = strength(np)
                when {
                    np.isEmpty() -> say(t("Yangi parolni kiriting.", "Enter a new password.", "Введите новый пароль."), false)
                    np.length < 6 || s.rank < 2 -> say(t("Parol juda zaif. Kamida \"O'rtacha\" daraja kerak (harf va raqamni aralashtiring).",
                        "Password is too weak. At least \"Medium\" is required (mix letters and digits).",
                        "Пароль слишком слабый. Нужен уровень не ниже «Средний» (смешайте буквы и цифры)."), false)
                    np != conf.text.toString() -> say(t("Parollar mos kelmadi.", "Passwords do not match.", "Пароли не совпадают."), false)
                    else -> {
                        Sec.save(this@MainActivity, np)
                        unlocked = true
                        secMsg = t("Parol o'rnatildi.", "Password set.", "Пароль установлен.")
                        renderSecurity()
                    }
                }
            }
        }, params(12))
        if (has) {
            secBox.addView(Button(this).apply {
                text = t("Parolni o'chirish", "Remove password", "Удалить пароль")
                setTextColor(red)
                setOnClickListener {
                    if (!Sec.verify(this@MainActivity, curField?.text?.toString() ?: "")) {
                        say(t("Joriy parolni kiriting.", "Enter the current password.", "Введите текущий пароль."), false); return@setOnClickListener
                    }
                    Sec.remove(this@MainActivity)
                    secMsg = t("Parol o'chirildi.", "Password removed.", "Пароль удалён.")
                    renderSecurity()
                }
            }, params(4))
        }
        secBox.addView(msg, params(8))

        secBox.addView(label(t("Skrinshotdan himoya", "Screenshot protection", "Защита от скриншотов"), 15f, true, Color.BLACK), params(20))
        secBox.addView(label(t(
            "Yoqilsa, ilova oynasi skrinshotda va so'nggi ilovalar ro'yxatida ko'rinmaydi. Taqdimot uchun skrinshot olmoqchi bo'lsangiz, o'chirib qo'ying.",
            "When on, the app window is hidden from screenshots and the recent apps list. Turn it off if you want to take screenshots for a presentation.",
            "Если включено, окно приложения скрыто на скриншотах и в списке недавних приложений. Выключите, если нужны скриншоты для презентации."), 12f), params(4))
        secBox.addView(Button(this).apply {
            text = if (secureOn()) t("Hozir: YOQILGAN (o'chirish)", "Now: ON (turn off)", "Сейчас: ВКЛ (выключить)")
            else t("Hozir: O'CHIQ (yoqish)", "Now: OFF (turn on)", "Сейчас: ВЫКЛ (включить)")
            setOnClickListener {
                getSharedPreferences("sec", MODE_PRIVATE).edit().putBoolean("secure", !secureOn()).apply()
                applySecureFlag()
                renderSecurity()
            }
        }, params(6))
    }

    // ---------- taymer va qulf ----------
    private fun guardOn() = getSharedPreferences("sec", MODE_PRIVATE).getBoolean("guard", false)

    @Suppress("DEPRECATION")
    private fun hasUsageAccess(): Boolean = try {
        val ops = getSystemService(APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= 29)
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName)
        else ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName)
        mode == AppOpsManager.MODE_ALLOWED
    } catch (e: Exception) { false }

    private fun hasOverlay() = Settings.canDrawOverlays(this)

    private fun hasNotifPerm() = Build.VERSION.SDK_INT < 33 ||
        checkSelfPermission("android.permission.POST_NOTIFICATIONS") == PackageManager.PERMISSION_GRANTED

    private fun batteryOk() = try {
        (getSystemService(POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(packageName)
    } catch (e: Exception) { false }

    private fun startGuard() {
        val i = Intent(this, GuardService::class.java)
        try { if (Build.VERSION.SDK_INT >= 26) startForegroundService(i) else startService(i) }
        catch (e: Exception) { Toast.makeText(this, e.message ?: "error", Toast.LENGTH_LONG).show() }
    }

    private fun ensureGuard() { if (guardOn() && hasUsageAccess() && hasOverlay()) startGuard() }

    private fun buildTimer(): View {
        timerBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(32))
        }
        return ScrollView(this).apply { addView(timerBox) }
    }

    private fun openSettings(i: Intent) {
        try { startActivity(i) } catch (e: Exception) {
            Toast.makeText(this, t("Sozlamalarni ochib bo'lmadi", "Could not open settings", "Не удалось открыть настройки"), Toast.LENGTH_SHORT).show()
        }
    }

    private fun permRow(title: String, desc: String, ok: Boolean, btnText: String, action: () -> Unit): View {
        val c = if (ok) green else orange
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = rounded(tint(c), 10)
        }
        col.addView(label((if (ok) "✓ " else "⚠ ") + title, 14f, true, Color.BLACK))
        col.addView(label(desc, 12f), params(2))
        if (!ok) col.addView(Button(this).apply { text = btnText; setOnClickListener { action() } }, params(4))
        return col
    }

    private fun renderTimer() {
        timerBox.removeAllViews()
        timerBox.addView(label(t("Ilovalarga vaqt limiti va parol qulfi", "App time limits and password lock", "Лимиты времени и пароль для приложений"), 16f, true, Color.BLACK))
        timerBox.addView(label(t(
            "Masalan, Instagram uchun kuniga 15 daqiqa limit qo'ying. Limit tugagach ilova bosh ekranga chiqarib yuboriladi yoki parol so'raladi. Ilovani har safar ochganda parol so'rashni ham tanlash mumkin. Buning uchun quyidagi ruxsatlar kerak: ularni siz o'zingiz berasiz va istalgan payt qaytarib olasiz.",
            "For example, set a limit of 15 minutes a day for Instagram. When the limit ends, the app is sent to the home screen or asks for a password. You can also choose to ask for a password every time the app is opened. This needs the permissions below: you grant them yourself and can take them back at any time.",
            "Например, задайте для Instagram лимит 15 минут в день. Когда лимит закончится, приложение закроется (переход на главный экран) или запросит пароль. Можно также запрашивать пароль при каждом открытии. Для этого нужны разрешения ниже: вы выдаёте их сами и можете отозвать в любой момент."), 12f), params(4))

        val usage = hasUsageAccess()
        val overlay = hasOverlay()
        timerBox.addView(permRow(
            t("Foydalanish statistikasi", "Usage access", "Доступ к статистике использования"),
            t("Qaysi ilova ochiqligini va qancha vaqt ishlatilganini bilish uchun.", "To know which app is open and for how long.", "Чтобы знать, какое приложение открыто и как долго."),
            usage, t("Ruxsat berish", "Grant access", "Дать доступ")) { openSettings(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }, params(10))
        timerBox.addView(permRow(
            t("Boshqa ilovalar ustidan chizish", "Draw over other apps", "Отображение поверх других приложений"),
            t("Limit tugaganda parol oynasini ilova ustiga chiqarish va bosh ekranga qaytarish uchun.", "To show the password window over the app and return to the home screen when the limit ends.", "Чтобы показать окно пароля поверх приложения и вернуться на главный экран, когда лимит закончится."),
            overlay, t("Ruxsat berish", "Grant access", "Дать доступ")) {
            openSettings(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }, params(8))
        if (Build.VERSION.SDK_INT >= 33) {
            timerBox.addView(permRow(
                t("Bildirishnomalar", "Notifications", "Уведомления"),
                t("Himoya ishlayotganini ko'rsatuvchi doimiy bildirishnoma (Android talabi).", "A permanent notification showing that protection is running (an Android requirement).", "Постоянное уведомление о работе защиты (требование Android)."),
                hasNotifPerm(), t("Ruxsat berish", "Grant access", "Дать доступ")) {
                requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"), 11)
            }, params(8))
        }
        timerBox.addView(permRow(
            t("Batareya cheklovidan chiqarish (tavsiya)", "Battery exemption (recommended)", "Исключение из ограничений батареи (рекомендуется)"),
            t("Tizim himoya xizmatini o'chirib qo'ymasligi uchun. Xiaomi, Samsung va Huawei'da muhim.", "So the system does not stop the protection service. Important on Xiaomi, Samsung and Huawei.", "Чтобы система не останавливала службу защиты. Важно на Xiaomi, Samsung и Huawei."),
            batteryOk(), t("Sozlamalarni ochish", "Open settings", "Открыть настройки")) {
            openSettings(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }, params(8))

        val on = guardOn()
        timerBox.addView(Button(this).apply {
            text = if (on) t("Himoyani o'chirish", "Turn protection off", "Выключить защиту") else t("Himoyani yoqish", "Turn protection on", "Включить защиту")
            setOnClickListener {
                if (!on && !(usage && overlay)) {
                    Toast.makeText(this@MainActivity, t("Avval yuqoridagi ikkita asosiy ruxsatni bering", "First grant the two main permissions above", "Сначала выдайте два основных разрешения выше"), Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                getSharedPreferences("sec", MODE_PRIVATE).edit().putBoolean("guard", !on).apply()
                if (!on) startGuard() else stopService(Intent(this@MainActivity, GuardService::class.java))
                renderTimer()
            }
        }, params(14))
        timerBox.addView(label(
            if (on) t("Holat: himoya yoqilgan", "Status: protection is on", "Состояние: защита включена")
            else t("Holat: himoya o'chiq", "Status: protection is off", "Состояние: защита выключена"),
            12f, true, if (on) green else orange), params(4))

        timerBox.addView(label(t("Qoidalar", "Rules", "Правила"), 16f, true, Color.BLACK), params(18))
        val rules = Rules.load(this)
        if (rules.isEmpty()) timerBox.addView(label(t("Hali qoida yo'q.", "No rules yet.", "Правил пока нет."), 13f), params(6))
        for (r in rules) timerBox.addView(ruleRow(r), params(8))
        timerBox.addView(Button(this).apply {
            text = t("+ Qoida qo'shish", "+ Add rule", "+ Добавить правило")
            setOnClickListener { addRuleDialog() }
        }, params(10))
        if (!Sec.has(this)) timerBox.addView(label(
            t("Parol rejimi uchun avval \"Parol va xavfsizlik\" bo'limida parol o'rnating.",
                "For the password mode, first set a password in \"Password & security\".",
                "Для режима с паролем сначала установите пароль в разделе «Пароль и безопасность»."), 12f, false, orange), params(8))
        timerBox.addView(label(t(
            "Eslatma: Android'da limit tugagach ilovani majburan yopib bo'lmaydi. Bu funksiya ilova ustiga parol oynasini chiqaradi yoki bosh ekranga qaytaradi. Foydalanuvchi himoyani o'chirib qo'yishi mumkin.",
            "Note: Android does not allow force-closing another app. This feature shows a password window over the app or sends you to the home screen. The user can turn protection off.",
            "Примечание: Android не позволяет принудительно закрыть другое приложение. Эта функция показывает окно пароля поверх приложения или возвращает на главный экран. Пользователь может выключить защиту."), 11f), params(14))
    }

    private fun ruleRow(r: Rule): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = rounded(Color.rgb(247, 247, 247), 12, Color.rgb(225, 225, 225))
        }
        val icon = ImageView(this)
        try { icon.setImageDrawable(packageManager.getApplicationIcon(r.pkg)) } catch (e: Exception) {}
        row.addView(icon, LinearLayout.LayoutParams(dp(40), dp(40)))
        val info = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        info.addView(label(r.name, 15f, true, Color.BLACK))
        val limitText = if (r.limitMin == 0) t("Har safar parol so'raladi", "Password asked every time", "Пароль запрашивается каждый раз")
        else t("Limit: ${r.limitMin} daqiqa / kun", "Limit: ${r.limitMin} min / day", "Лимит: ${r.limitMin} мин / день")
        val modeText = if (r.mode == "PASS") t("parol bilan ochish", "open with password", "открытие по паролю")
        else t("bosh ekranga chiqarish", "send to home screen", "переход на главный экран")
        info.addView(label("$limitText · $modeText", 12f), params(2))
        if (r.limitMin > 0) {
            val used = Rules.usedMs(this, r.pkg) / 60000
            info.addView(label(t("Bugun: $used daqiqa", "Today: $used min", "Сегодня: $used мин"), 12f, false, orange), params(2))
        }
        row.addView(info, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(12), 0, dp(8), 0) })
        row.addView(Button(this).apply {
            text = t("O'chirish", "Remove", "Удалить")
            setOnClickListener {
                Rules.save(this@MainActivity, Rules.load(this@MainActivity).filter { it.pkg != r.pkg })
                renderTimer()
            }
        })
        return row
    }

    private fun addRuleDialog() {
        val list = apps.filter { it.pkg != packageName }.sortedBy { it.name.lowercase() }
        if (!loaded || list.isEmpty()) {
            Toast.makeText(this, t("Ilovalar ro'yxati hali tayyor emas", "The app list is not ready yet", "Список приложений ещё не готов"), Toast.LENGTH_SHORT).show()
            return
        }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(12), dp(20), dp(4))
        }
        val appSpin = Spinner(this)
        appSpin.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, list.map { it.name })
        val limits = listOf(0, 1, 5, 10, 15, 30, 45, 60, 90, 120)
        val limSpin = Spinner(this)
        limSpin.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, limits.map {
            if (it == 0) t("Cheklovsiz, har safar parol so'rash", "No limit, ask password every time", "Без лимита, пароль каждый раз")
            else t("$it daqiqa / kun", "$it min / day", "$it мин / день")
        })
        val modes = listOf("KICK", "PASS")
        val modeSpin = Spinner(this)
        modeSpin.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, listOf(
            t("Bosh ekranga chiqarib yuborish", "Send to home screen", "Выход на главный экран"),
            t("Parol bilan ochish", "Open with password", "Открыть по паролю")))
        box.addView(label(t("Ilova", "App", "Приложение"), 12f))
        box.addView(appSpin)
        box.addView(label(t("Kunlik limit", "Daily limit", "Лимит в день"), 12f), params(10))
        box.addView(limSpin)
        box.addView(label(t("Limit tugagach", "When the limit ends", "Когда лимит закончится"), 12f), params(10))
        box.addView(modeSpin)
        AlertDialog.Builder(this)
            .setTitle(t("Yangi qoida", "New rule", "Новое правило"))
            .setView(box)
            .setNegativeButton(t("Bekor qilish", "Cancel", "Отмена"), null)
            .setPositiveButton(t("Saqlash", "Save", "Сохранить")) { _, _ ->
                val app = list[appSpin.selectedItemPosition]
                val lim = limits[limSpin.selectedItemPosition]
                var mode = modes[modeSpin.selectedItemPosition]
                if (lim == 0) mode = "PASS"
                if (mode == "PASS" && !Sec.has(this)) {
                    Toast.makeText(this, t("Avval \"Parol va xavfsizlik\" bo'limida parol o'rnating", "First set a password in \"Password & security\"", "Сначала установите пароль в разделе «Пароль и безопасность»"), Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }
                Rules.save(this, Rules.load(this).filter { it.pkg != app.pkg } + Rule(app.pkg, app.name, lim, mode))
                renderTimer()
            }.show()
    }

    // ---------- maxfiylik ----------
    private fun buildPrivacy(): View {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(32))
        }
        fun h(s: String) = col.addView(label(s, 16f, true, Color.BLACK), params(16))
        fun p(s: String) = col.addView(label(s, 13f), params(6))
        h(t("Ma'lumotlaringiz qayerda?", "Where is your data?", "Где ваши данные?"))
        p(t("Barcha tekshiruv telefoningizning o'zida bajariladi. Ilovaning internetga ruxsati yo'q, shuning uchun ma'lumot tashqariga yuborilmaydi. Natija faqat siz \"Hisobotni ulashish\" ni bosib, ilovani tanlaganingizda ketadi.",
            "All checks run on your phone. The app has no internet permission, so no data leaves the device. A result is sent only when you tap \"Share report\" and pick an app.",
            "Все проверки выполняются на вашем телефоне. У приложения нет доступа в интернет, поэтому данные не покидают устройство. Результат отправляется только если вы нажмёте «Поделиться отчётом» и выберете приложение."))
        h(t("Ilova qaysi ruxsatlarni oladi?", "Which permissions does the app use?", "Какие разрешения использует приложение?"))
        p(usedPermsText())
        h(t("Xavf bali qanday hisoblanadi?", "How is the risk score calculated?", "Как считается балл риска?"))
        p(t("Ball Android'ning \"xavfli\" (dangerous) ruxsatlari ro'yxatiga va zararli dasturlar ko'p suiiste'mol qiladigan maxsus imkoniyatlarga asoslangan. Har bir sezgir ruxsatga ball beriladi (SMS o'qish 20, mikrofon 15, kamera 10 va hokazo). Berilgan ruxsat to'liq, faqat so'ralgani uchdan bir ball oladi.",
            "The score is based on Android's \"dangerous\" permission list and special capabilities that malware often abuses. Each sensitive permission adds points (read SMS 20, microphone 15, camera 10, and so on). A granted permission counts fully, one that is only requested counts one third.",
            "Оценка основана на списке «опасных» разрешений Android и особых возможностях, которые часто используют вредоносные программы. Каждое чувствительное разрешение добавляет баллы (чтение SMS 20, микрофон 15, камера 10 и т. д.). Выданное разрешение учитывается полностью, только запрошенное — на треть."))
        p(t("Maxsus xizmatlar: Accessibility, bildirishnomalarni o'qish va qurilma administratori yoqilgan bo'lsa 25-30 ball. Rasmiy do'kondan (Play Market, Xiaomi GetApps, Samsung Galaxy Store, Huawei AppGallery, Amazon Appstore) o'rnatilmagan ilovaga +15, eski Android uchun yozilgan ilovaga +5. Fon xizmati, avtoishga tushish, aniq signal va batareya cheklovidan chiqish uchun kichik ballar qo'shiladi.",
            "Special services: accessibility, notification listener and device administrator add 25-30 points when enabled. Apps not installed from an official store (Play Market, Xiaomi GetApps, Samsung Galaxy Store, Huawei AppGallery, Amazon Appstore) get +15, apps built for an old Android version +5. Small points are added for a background service, auto-start, exact alarms and battery-limit exemption.",
            "Особые службы: спец. возможности, чтение уведомлений и администратор устройства добавляют 25-30 баллов, если включены. Приложение не из официального магазина (Play Market, Xiaomi GetApps, Samsung Galaxy Store, Huawei AppGallery, Amazon Appstore) получает +15, созданное для старой версии Android +5. Небольшие баллы добавляются за фоновую службу, автозапуск, точные будильники и исключение из ограничений батареи."))
        p(t("Ilova turi (ijtimoiy tarmoq, xarita, foto) bo'yicha odatiy ruxsatlar yarim ball oladi. Masalan, ijtimoiy tarmoqqa kamera va mikrofon odatiy. O'yinga esa bu ruxsatlar uchun ogohlantirish chiqadi.",
            "Permissions that are typical for the app type (social, maps, photo) count at half. For example, camera and microphone are typical for a social app. For a game, the same permissions raise a warning.",
            "Типичные для вида приложения разрешения (соцсети, карты, фото) засчитываются наполовину. Например, камера и микрофон типичны для соцсети. Для игры такие разрешения вызывают предупреждение."))
        p(t("Daraja: 0-29 past, 30-59 o'rta, 60 va undan yuqori yuqori xavf. Ballar qo'lda belgilangan ekspert baholari, ilmiy standart emas.",
            "Level: 0-29 low, 30-59 medium, 60 and above high. The points are manually set expert estimates, not a scientific standard.",
            "Уровень: 0-29 низкий, 30-59 средний, от 60 высокий. Баллы заданы вручную как экспертные оценки, это не научный стандарт."))
        h(t("Cheklovlar", "Limitations", "Ограничения"))
        p(t("• Ilova virus qidirmaydi, faqat ruxsat va imkoniyatlarni baholaydi.\n• Yuqori ball ilova zararli ekanini isbotlamaydi, past ball xavfsizlik kafolati emas.\n• Android boshqa ilova hozir ishlayotganini ko'rsatmaydi, faqat fonda ishlash imkoniyatini ko'rish mumkin.\n• Ilova reklama yoki kuzatuvchi kutubxonalarni ko'rmaydi.\n• Ilova turi har doim aniq aniqlanmaydi.",
            "• The app does not scan for viruses, it only rates permissions and capabilities.\n• A high score does not prove an app is harmful, a low score is not a guarantee of safety.\n• Android does not show whether another app is running now, only its background capability.\n• The app cannot see advertising or tracking libraries.\n• The app type is not always detected accurately.",
            "• Приложение не ищет вирусы, а только оценивает разрешения и возможности.\n• Высокий балл не доказывает вредоносность, низкий балл не гарантирует безопасность.\n• Android не показывает, работает ли другое приложение сейчас, видна только возможность работы в фоне.\n• Приложение не видит рекламные и отслеживающие библиотеки.\n• Тип приложения определяется не всегда точно."))
        return ScrollView(this).apply { addView(col) }
    }

    // USED_PERMS_START
    private fun usedPermsText() = t(
        "• O'rnatilgan ilovalar ro'yxatini ko'rish: ruxsatlarni tahlil qilish uchun.\n• Ilovani o'chirish oynasini ochish: faqat siz \"Ilovani o'chirish\" tugmasini bosganda.\n• Foydalanish statistikasi: faqat siz vaqt limiti funksiyasini yoqsangiz, qaysi ilova ochiqligini bilish uchun.\n• Boshqa ilovalar ustidan chizish: limit tugaganda parol oynasini ko'rsatish uchun.\n• Fon xizmati va bildirishnoma: himoya yoqilganda ishlab turishi uchun (Android talabi).\n• Telefon yonganda ishga tushish: himoyani qayta yoqish uchun.\nBu ruxsatlarni siz o'zingiz berasiz va istalgan payt Sozlamalardan qaytarib olasiz. Ilovaning internetga ruxsati baribir yo'q.",
        "• Viewing the list of installed apps: to analyze permissions.\n• Opening the uninstall dialog: only when you tap \"Uninstall app\".\n• Usage access: only if you turn on the time limit feature, to know which app is open.\n• Draw over other apps: to show the password window when a limit ends.\n• Background service and notification: so protection can keep running (an Android requirement).\n• Start on boot: to turn protection back on.\nYou grant these permissions yourself and can take them back in Settings at any time. The app still has no internet permission.",
        "• Просмотр списка установленных приложений: для анализа разрешений.\n• Открытие окна удаления: только когда вы нажимаете «Удалить приложение».\n• Доступ к статистике: только если вы включите лимиты времени, чтобы знать, какое приложение открыто.\n• Отображение поверх других приложений: чтобы показать окно пароля, когда лимит закончится.\n• Фоновая служба и уведомление: чтобы защита продолжала работать (требование Android).\n• Запуск при включении: чтобы снова включить защиту.\nЭти разрешения вы выдаёте сами и можете отозвать в настройках в любой момент. Доступа в интернет у приложения по-прежнему нет.")
    // USED_PERMS_END

    // ---------- hisobot ----------
    private fun shareReport() {
        if (!loaded) { Toast.makeText(this, t("Tekshiruv tugamadi, biroz kuting", "Scan not finished, please wait", "Проверка не завершена, подождите"), Toast.LENGTH_SHORT).show(); return }
        val sb = StringBuilder()
        sb.append(t("BAXTIYOR AUDIT hisoboti", "BAXTIYOR AUDIT report", "Отчёт BAXTIYOR AUDIT")).append("\n")
        sb.append(t("Umumiy xavfsizlik bali: ", "Overall security score: ", "Общая оценка безопасности: ")).append("${overallScore()}/100\n")
        sb.append(t("Tekshirilgan ilovalar: ", "Apps checked: ", "Проверено приложений: ")).append("${apps.size} (")
        sb.append(t("yuqori ", "high ", "высокий ")).append("${apps.count { it.level == "HIGH" }}, ")
        sb.append(t("o'rta ", "medium ", "средний ")).append("${apps.count { it.level == "MED" }}, ")
        sb.append(t("past ", "low ", "низкий ")).append("${apps.count { it.level == "LOW" }})\n\n")
        sb.append(t("Eng yuqori xavf balli ilovalar:", "Apps with the highest risk score:", "Приложения с наибольшим баллом риска:")).append("\n")
        for (a in apps.take(5)) sb.append("• ${a.name}: ${a.score}/100 (${levelName(a.level)})\n")
        sb.append("\n").append(t("Telefon sozlamalari:", "Phone settings:", "Настройки телефона:")).append("\n")
        for (c in checks) sb.append((if (c.ok) "✓ " else "⚠ ") + checkTitle(c) + ": " + checkDetail(c) + "\n")
        sb.append("\n").append(t("Eslatma: xavf bali ilova zararli ekanini isbotlamaydi.",
            "Note: a risk score does not prove an app is harmful.", "Примечание: балл риска не доказывает, что приложение вредоносно."))
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, sb.toString())
        }
        startActivity(Intent.createChooser(send, t("Hisobotni ulashish", "Share report", "Поделиться отчётом")))
    }

    // ---------- yordamchi (tayyor qoidalar) ----------
    private fun buildChat(): View {
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        chatLog = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(8), dp(12), dp(8)) }
        chatScroll = ScrollView(this).apply { addView(chatLog) }

        val quick = listOf(
            t("Eng xavfli ilova qaysi?", "Which app is the riskiest?", "Какое приложение самое опасное?"),
            t("Telefonim xavfsizmi?", "Is my phone secure?", "Мой телефон безопасен?"),
            t("Ruxsat nima?", "What is a permission?", "Что такое разрешение?"),
            t("Qanday himoyalanaman?", "How do I protect myself?", "Как защититься?")
        )
        val chips = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(dp(8), 0, dp(8), 0) }
        for (q in quick) chips.addView(Button(this).apply {
            text = q; textSize = 12f; isAllCaps = false
            setOnClickListener { send(q) }
        })
        col.addView(HorizontalScrollView(this).apply { addView(chips) }, LinearLayout.LayoutParams(-1, -2))
        col.addView(chatScroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val input = EditText(this).apply { hint = t("Savol yozing...", "Type a question...", "Напишите вопрос..."); setSingleLine() }
        val sendBtn = Button(this).apply {
            text = t("Yuborish", "Send", "Отправить")
            setOnClickListener {
                val s = input.text.toString().trim()
                if (s.isNotEmpty()) { input.setText(""); send(s) }
            }
        }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(dp(8), dp(4), dp(8), dp(8)) }
        row.addView(input, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(sendBtn)
        col.addView(row, LinearLayout.LayoutParams(-1, -2))

        bubble(t("Salom! Men telefon xavfsizligi bo'yicha yordamchiman. Javoblarim tayyor qoidalarga va sizning tekshiruv natijalaringizga asoslanadi, internet kerak emas.",
            "Hello! I am your phone security assistant. My answers are based on ready-made rules and your scan results, no internet needed.",
            "Здравствуйте! Я помощник по безопасности телефона. Мои ответы основаны на готовых правилах и результатах вашей проверки, интернет не нужен."), false)
        return col
    }

    private fun bubble(text: String, mine: Boolean) {
        val c = if (mine) Color.rgb(25, 118, 210) else Color.rgb(96, 96, 96)
        chatLog.addView(TextView(this).apply {
            this.text = text
            textSize = 14f
            setTextColor(Color.BLACK)
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = rounded(tint(c), 12)
            maxWidth = (resources.displayMetrics.widthPixels * 0.82).toInt()
        }, LinearLayout.LayoutParams(-2, -2).apply {
            gravity = if (mine) Gravity.END else Gravity.START
            setMargins(0, dp(6), 0, 0)
        })
        chatScroll.post { chatScroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun send(q: String) {
        bubble(q, true)
        bubble(answer(q), false)
    }

    private fun answer(q0: String): String {
        val q = q0.lowercase()
        fun has(vararg w: String) = w.any { q.contains(it) }
        if (!loaded) return t("Tekshiruv hali tugamadi. Bir necha soniyadan keyin qayta so'rang.",
            "The scan has not finished yet. Ask again in a few seconds.", "Проверка ещё не завершена. Спросите снова через несколько секунд.")
        if (has("eng xavfli", "xavfli ilova", "riskiest", "dangerous", "risky", "опасн")) {
            val top = apps.take(3).filter { it.score > 0 }
            return if (top.isEmpty()) t("Sezgir ruxsatli ilova topilmadi.", "No app with sensitive permissions found.", "Приложений с чувствительными разрешениями не найдено.")
            else t("Eng yuqori xavf balli ilovalar:\n", "Apps with the highest risk score:\n", "Приложения с наибольшим баллом риска:\n") +
                top.joinToString("\n") { "• ${it.name}: ${it.score}/100 (${levelName(it.level)})" } +
                t("\nBatafsil ko'rish uchun Ilovalar bo'limida ustiga bosing.", "\nTap an app in the Apps section for details.", "\nНажмите на приложение в разделе «Приложения», чтобы увидеть подробности.")
        }
        if (has("telefon", "qulf", "yangilan", "root", "xavfsizmi", "phone", "screen lock", "secure", "телефон", "безопасн", "блокировк")) {
            val bad = checks.filter { !it.ok }
            return if (bad.isEmpty()) t("Telefon sozlamalarida muammo topilmadi.", "No problems found in phone settings.", "Проблем в настройках телефона не найдено.")
            else t("Telefon sozlamalarida ${bad.size} ta muammo bor:\n", "${bad.size} problem(s) in phone settings:\n", "Проблем в настройках телефона: ${bad.size}:\n") +
                bad.joinToString("\n") { "• ${checkTitle(it)}: ${checkDetail(it)}" }
        }
        if (has("himoyalan", "maslahat", "qanday", "protect", "tips", "how", "защит", "совет", "как")) {
            return t("Asosiy maslahatlar:\n• Ekran qulfini yoqing.\n• Ilovalarni faqat rasmiy do'kondan o'rnating.\n• Ilova vazifasiga kerak bo'lmagan ruxsatni bermang.\n• Telefonni yangilab turing.\n• Noma'lum havola va SMS kodlarni hech kimga bermang.",
                "Main tips:\n• Turn on a screen lock.\n• Install apps only from official stores.\n• Do not grant permissions an app does not need.\n• Keep the phone updated.\n• Never share unknown links or SMS codes.",
                "Основные советы:\n• Включите блокировку экрана.\n• Устанавливайте приложения только из официальных магазинов.\n• Не давайте приложению ненужные разрешения.\n• Обновляйте телефон.\n• Никому не сообщайте незнакомые ссылки и коды из SMS.")
        }
        if (has("sms", "смс")) return t("SMS ruxsati xavfli, chunki ilova bir martalik tasdiqlash kodlarini (banklar, ijtimoiy tarmoqlar) o'qib olishi mumkin. Faqat xabar almashish ilovalariga bering.",
            "SMS access is risky because an app can read one-time confirmation codes (banks, social networks). Give it only to messaging apps.",
            "Доступ к SMS опасен: приложение может прочитать одноразовые коды подтверждения (банки, соцсети). Давайте его только мессенджерам.")
        if (has("mikrofon", "microphone", "микрофон")) return t("Mikrofon ruxsati ilovaga atrofdagi ovozni yozishga imkon beradi. Qo'ng'iroq, ovozli xabar va yozuv ilovalariga kerak, kalkulyator yoki o'yinga kerak emas.",
            "Microphone access lets an app record surrounding sound. It is needed by calling, voice message and recording apps, not by a calculator or a game.",
            "Доступ к микрофону позволяет приложению записывать звук вокруг. Он нужен приложениям для звонков, голосовых сообщений и записи, но не калькулятору или игре.")
        if (has("kamera", "camera", "камера")) return t("Kamera ruxsati faqat suratga olish uchun kerak bo'lgan ilovalarga berilishi kerak. Sozlamalarda 'faqat ishlatilayotganda' variantini tanlang.",
            "Camera access should be given only to apps that need to take photos. Choose \"only while using the app\" in settings.",
            "Доступ к камере нужен только приложениям, которые делают снимки. В настройках выбирайте «только при использовании».")
        if (has("joylashuv", "lokatsiya", "gps", "location", "местополож", "геолок")) return t("Joylashuv, ayniqsa fonda, harakatlaringizni kuzatishga imkon beradi. Xarita va yetkazib berish ilovalaridan boshqasiga 'faqat ishlatilayotganda' bering.",
            "Location, especially in the background, lets an app track your movements. Except for maps and delivery apps, allow it only while using the app.",
            "Местоположение, особенно в фоне, позволяет отслеживать ваши перемещения. Кроме карт и доставки, разрешайте его только при использовании.")
        if (has("accessibility", "maxsus imkon", "спец. возможност", "специальные возможности")) return t("Maxsus imkoniyatlar (Accessibility) xizmati ekrandagi hamma narsani o'qiy oladi va ilova nomidan harakat qila oladi. Zararli dasturlar tez-tez shuni so'raydi. Bilmagan ilovaga bermang.",
            "The accessibility service can read everything on the screen and act on your behalf. Malware often asks for it. Do not give it to apps you do not know.",
            "Служба спец. возможностей может читать всё на экране и действовать от вашего имени. Её часто запрашивают вредоносные программы. Не давайте её незнакомым приложениям.")
        if (has("batareya", "fon", "battery", "background", "батаре", "фон")) return t("Batareya cheklovidan chiqarilgan ilova fonda erkin ishlay oladi. Bu o'z-o'zidan xavfli emas, lekin mikrofon yoki joylashuv bilan birga bo'lsa, tekshirib ko'ring.",
            "An app exempt from battery limits can run freely in the background. That is not dangerous by itself, but check it if it also has microphone or location access.",
            "Приложение, исключённое из ограничений батареи, может свободно работать в фоне. Само по себе это не опасно, но проверьте, если у него есть доступ к микрофону или местоположению.")
        if (has("ball", "baho", "hisob", "score", "rating", "балл", "оценк")) return t("Xavf bali 0 dan 100 gacha. Berilgan sezgir ruxsatlar to'liq, faqat so'ralganlari uchdan bir ball oladi. Ilova turiga mos ruxsatlar yarim ball oladi. Rasmiy do'kondan emas manba +15. 60 dan yuqori bu yuqori xavf. Batafsil: menyudagi 'Maxfiylik va baholash'.",
            "The risk score is 0 to 100. Granted sensitive permissions count fully, only requested ones count one third. Permissions typical for the app type count half. A non-official source adds 15. 60 and above is high risk. Details: \"Privacy & scoring\" in the menu.",
            "Балл риска от 0 до 100. Выданные чувствительные разрешения учитываются полностью, только запрошенные — на треть. Типичные для вида приложения — наполовину. Неофициальный источник добавляет 15. От 60 — высокий риск. Подробнее: «Конфиденциальность и оценка» в меню.")
        if (has("taymer", "limit", "timer", "таймер", "лимит")) return t("Ilova taymeri: menyudan 'Ilova taymeri va qulf' bo'limini oching. Masalan, Instagram uchun kuniga 15 daqiqa qo'ying: limit tugagach ilova bosh ekranga chiqarib yuboriladi yoki parol so'raladi.",
            "App timer: open \"App timer & lock\" in the menu. For example, set 15 minutes a day for Instagram: when the limit ends the app is sent to the home screen or asks for a password.",
            "Таймер приложений: откройте «Таймер и блокировка приложений» в меню. Например, задайте для Instagram 15 минут в день: когда лимит закончится, приложение закроется или запросит пароль.")
        if (has("parol", "password", "пароль")) return t("Ilovaga parol qo'yish uchun menyudan 'Parol va xavfsizlik' bo'limini oching. Kuchli parol: kamida 8 belgi, katta va kichik harf, raqam va maxsus belgi.",
            "To set an app password, open \"Password & security\" in the menu. A strong password: at least 8 characters, upper and lower case letters, a digit and a special character.",
            "Чтобы установить пароль приложения, откройте «Пароль и безопасность» в меню. Надёжный пароль: не менее 8 символов, заглавные и строчные буквы, цифра и спецсимвол.")
        if (has("maxfiy", "internet", "privacy", "конфиденц", "интернет")) return t("Ilovaning internetga ruxsati yo'q, barcha tekshiruv telefoningizda bajariladi. Batafsil: menyudagi 'Maxfiylik va baholash'.",
            "The app has no internet permission, all checks run on your phone. Details: \"Privacy & scoring\" in the menu.",
            "У приложения нет доступа в интернет, все проверки выполняются на вашем телефоне. Подробнее: «Конфиденциальность и оценка» в меню.")
        if (has("o'chir", "ochir", "o‘chir", "uninstall", "delete", "удал")) return t("Ilovani o'chirish uchun Ilovalar bo'limida uni bosing va pastdagi 'Ilovani o'chirish' tugmasini tanlang.",
            "To uninstall an app, tap it in the Apps section and choose \"Uninstall app\" at the bottom.",
            "Чтобы удалить приложение, нажмите на него в разделе «Приложения» и выберите «Удалить приложение» внизу.")
        if (has("ruxsat", "permission", "разрешени")) return t("Ruxsat bu ilovaning telefon imkoniyatlaridan (kamera, SMS, joylashuv) foydalanish huquqi. Yaxshi qoida: ilova vazifasiga kerak bo'lmagan ruxsatni bermang.",
            "A permission is an app's right to use phone features (camera, SMS, location). A good rule: do not grant permissions the app does not need.",
            "Разрешение — это право приложения использовать возможности телефона (камеру, SMS, местоположение). Хорошее правило: не давайте разрешения, которые приложению не нужны.")
        if (has("salom", "assalom", "hi", "hello", "привет", "здравств")) return t("Salom! Savol bering yoki yuqoridagi tugmalardan birini bosing.",
            "Hello! Ask a question or tap one of the buttons above.", "Здравствуйте! Задайте вопрос или нажмите одну из кнопок выше.")
        return t("Bu savolni tushunmadim. Quyidagilarni so'rab ko'ring: eng xavfli ilova, telefon xavfsizligi, SMS, mikrofon, kamera, joylashuv, batareya, parol, xavf bali qanday hisoblanadi.",
            "I did not understand the question. Try asking about: the riskiest app, phone security, SMS, microphone, camera, location, battery, password, how the score is calculated.",
            "Я не понял вопрос. Попробуйте спросить про: самое опасное приложение, безопасность телефона, SMS, микрофон, камеру, местоположение, батарею, пароль, как считается балл.")
    }
}
