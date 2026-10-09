package uz.audit.permissions

import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.InputType
import android.text.TextUtils
import android.text.TextWatcher
import android.util.Base64
import android.view.Gravity
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private var apps: List<AppRisk> = emptyList()
    private var checks: List<Check> = emptyList()
    private var loaded = false
    private var filter = "HAMMASI"
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
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun label(t: String, size: Float, bold: Boolean = false, color: Int = Color.DKGRAY) =
        TextView(this).apply {
            text = t
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(null, Typeface.BOLD)
        }

    private fun params(top: Int = 0) = LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, dp(top), 0, 0) }

    private val green = Color.rgb(46, 125, 50)
    private val orange = Color.rgb(230, 126, 0)
    private val red = Color.rgb(198, 40, 40)

    private fun levelColor(level: String) = when (level) {
        "YUQORI" -> red
        "O'RTA" -> orange
        else -> green
    }

    private fun levelText(level: String) = when (level) {
        "YUQORI" -> "yuqori xavf"
        "O'RTA" -> "o'rta xavf"
        else -> "past xavf"
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
        catch (e: Exception) { Toast.makeText(this, "Havolani ochib bo'lmadi", Toast.LENGTH_SHORT).show() }
    }

    // ---------- parol: saqlash va tekshirish ----------
    private fun prefs() = getSharedPreferences("sec", MODE_PRIVATE)
    private fun hasPassword() = prefs().contains("hash")
    private fun secureOn() = prefs().getBoolean("secure", false)

    private fun hashPw(pw: String, salt: ByteArray, algo: String): ByteArray =
        SecretKeyFactory.getInstance(algo).generateSecret(PBEKeySpec(pw.toCharArray(), salt, 60000, 256)).encoded

    private fun savePassword(pw: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val algo = if (Build.VERSION.SDK_INT >= 26) "PBKDF2WithHmacSHA256" else "PBKDF2WithHmacSHA1"
        val h = hashPw(pw, salt, algo)
        prefs().edit()
            .putString("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString("hash", Base64.encodeToString(h, Base64.NO_WRAP))
            .putString("algo", algo)
            .putInt("fails", 0).putLong("lock_until", 0L).apply()
    }

    private fun verify(pw: String): Boolean {
        val p = prefs()
        val salt = Base64.decode(p.getString("salt", null) ?: return false, Base64.NO_WRAP)
        val want = Base64.decode(p.getString("hash", null) ?: return false, Base64.NO_WRAP)
        val algo = p.getString("algo", "PBKDF2WithHmacSHA1") ?: return false
        return try { MessageDigest.isEqual(want, hashPw(pw, salt, algo)) } catch (e: Exception) { false }
    }

    private fun removePassword() {
        prefs().edit().remove("salt").remove("hash").remove("algo").putInt("fails", 0).putLong("lock_until", 0L).apply()
    }

    private fun applySecureFlag() {
        if (secureOn()) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    // ---------- parol kuchi ----------
    private data class Strength(val rank: Int, val name: String, val bits: Double, val secs: Double)

    private val common = setOf("123456", "1234567", "12345678", "123456789", "qwerty", "qwerty123", "password",
        "parol", "parol123", "111111", "000000", "admin", "1q2w3e4r", "iloveyou", "uzbekistan", "12345", "1234", "0000", "1111")

    private fun strength(p: String): Strength {
        if (p.isEmpty()) return Strength(-1, "Parol kiritilmagan", 0.0, 0.0)
        val low = p.any { it in 'a'..'z' }
        val up = p.any { it in 'A'..'Z' }
        val dg = p.any { it in '0'..'9' }
        val sy = p.any { !(it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9') }
        var pool = (if (low) 26 else 0) + (if (up) 26 else 0) + (if (dg) 10 else 0) + (if (sy) 33 else 0)
        if (pool == 0) pool = 1
        val bits = p.length * Math.log(pool.toDouble()) / Math.log(2.0)
        val isCommon = p.lowercase() in common
        val secs = if (isCommon) 0.0 else Math.pow(2.0, bits) / 2.0 / 1e10
        return when {
            isCommon || bits < 28 -> Strength(0, "Juda zaif", bits, secs)
            bits < 36 -> Strength(1, "Zaif", bits, secs)
            bits < 60 -> Strength(2, "O'rtacha", bits, secs)
            else -> Strength(3, "Kuchli", bits, secs)
        }
    }

    private fun rankColor(r: Int) = when (r) { 0, 1 -> red; 2 -> orange; 3 -> green; else -> Color.DKGRAY }

    private fun fmtTime(s: Double): String = when {
        s < 1.0 -> "bir zumda"
        s < 60.0 -> "${s.toInt()} soniya"
        s < 3600.0 -> "${(s / 60).toInt()} daqiqa"
        s < 86400.0 -> "${(s / 3600).toInt()} soat"
        s < 31536000.0 -> "${(s / 86400).toInt()} kun"
        else -> {
            val y = s / 31536000.0
            if (y > 1e9) "milliardlab yil" else if (y > 1e6) "${(y / 1e6).toInt()} million yil" else "${y.toLong()} yil"
        }
    }

    // ---------- qobiq ----------
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
        titleView = label("Ilovalar", 20f, true, Color.BLACK)
        bar.addView(titleView)
        column.addView(bar, LinearLayout.LayoutParams(-1, -2))

        appsView = buildApps()
        phoneView = buildPhone()
        chatView = buildChat()
        secView = buildSecurity()
        privView = buildPrivacy()
        val content = FrameLayout(this)
        for (v in listOf(appsView, phoneView, chatView, secView, privView)) content.addView(v)
        column.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(column, FrameLayout.LayoutParams(-1, -1))

        scrim = View(this).apply {
            setBackgroundColor(Color.argb(120, 0, 0, 0))
            visibility = View.GONE
            setOnClickListener { closeDrawer() }
        }
        root.addView(scrim, FrameLayout.LayoutParams(-1, -1))
        drawer = buildDrawer()
        root.addView(drawer, FrameLayout.LayoutParams(dp(270), -1, Gravity.START))
        lockView = buildLock()
        root.addView(lockView, FrameLayout.LayoutParams(-1, -1))

        setContentView(root)
        applySecureFlag()
        showScreen("apps")
        if (hasPassword()) showLock() else unlocked = true
        rescan()
    }

    override fun onStop() {
        super.onStop()
        lastStop = System.currentTimeMillis()
    }

    override fun onResume() {
        super.onResume()
        if (hasPassword() && unlocked && lastStop > 0 && System.currentTimeMillis() - lastStop > 10_000) showLock()
        lastStop = 0
        if (loaded) rescan()
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
        l.addView(label("Ruxsat Auditori", 24f, true, Color.BLACK))
        l.addView(label("Davom etish uchun parolni kiriting", 14f), params(8))
        lockInput = EditText(this).apply {
            hint = "Parol"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        l.addView(lockInput, params(16))
        l.addView(Button(this).apply {
            text = "Ochish"
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
        val p = prefs()
        val now = System.currentTimeMillis()
        val until = p.getLong("lock_until", 0L)
        if (now < until) { lockMsg.text = "Ko'p xato kiritildi. ${(until - now) / 1000 + 1} soniya kuting."; return }
        if (verify(input)) {
            p.edit().putInt("fails", 0).putLong("lock_until", 0L).apply()
            unlocked = true
            lockView.visibility = View.GONE
            lockInput.setText("")
            return
        }
        val f = p.getInt("fails", 0) + 1
        var u = 0L
        if (f % 5 == 0) u = now + 30_000L * (1L shl (f / 5 - 1).coerceAtMost(6))
        p.edit().putInt("fails", f).putLong("lock_until", u).apply()
        lockInput.setText("")
        lockMsg.text = if (u > 0) "5 marta xato. ${(u - now) / 1000} soniya kuting." else "Parol noto'g'ri. Urinish: $f"
    }

    // ---------- menyu ----------
    private fun buildDrawer(): LinearLayout {
        val d = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            setPadding(dp(8), dp(48), dp(8), dp(16))
            visibility = View.GONE
        }
        d.addView(label("Ruxsat Auditori", 20f, true, Color.BLACK).apply { setPadding(dp(16), 0, dp(16), dp(16)) })
        fun item(text: String, action: () -> Unit) {
            d.addView(label(text, 16f, false, Color.BLACK).apply {
                setPadding(dp(16), dp(13), dp(16), dp(13))
                setOnClickListener { action() }
            })
        }
        item("Ilovalar") { showScreen("apps") }
        item("Telefon xavfsizligi") { showScreen("phone") }
        item("Yordamchi (chat)") { showScreen("chat") }
        item("Parol va xavfsizlik") { showScreen("sec") }
        item("Maxfiylik va baholash") { showScreen("priv") }
        item("Hisobotni ulashish") { closeDrawer(); shareReport() }
        d.addView(View(this).apply { setBackgroundColor(Color.LTGRAY) },
            LinearLayout.LayoutParams(-1, dp(1)).apply { setMargins(dp(16), dp(8), dp(16), dp(8)) })
        item("Taklif va murojaatlar") { closeDrawer(); openLink("https://t.me/BAXTIYOR_07_77") }
        return d
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
        titleView.text = when (name) {
            "phone" -> "Telefon xavfsizligi"
            "chat" -> "Yordamchi"
            "sec" -> "Parol va xavfsizlik"
            "priv" -> "Maxfiylik va baholash"
            else -> "Ilovalar"
        }
        if (name == "sec") renderSecurity()
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
                renderSummary()
                renderList()
                renderPhone()
            }
        }
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
        summaryBox.addView(label("Tekshirilmoqda...", 15f))
        col.addView(summaryBox, params(4))

        col.addView(EditText(this).apply {
            hint = "Ilova nomini qidiring..."
            setSingleLine()
            addTextChangedListener(watcher { query = it; renderList() })
        }, params(8))

        val filterRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for ((key, text) in listOf("HAMMASI" to "Hammasi", "YUQORI" to "Yuqori", "O'RTA" to "O'rta", "PAST" to "Past")) {
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
        val high = apps.count { it.level == "YUQORI" }
        val med = apps.count { it.level == "O'RTA" }
        val issues = checks.count { !it.ok }
        return (100 - high * 10 - med * 4 - issues * 10).coerceIn(0, 100)
    }

    private fun renderSummary() {
        val high = apps.count { it.level == "YUQORI" }
        val med = apps.count { it.level == "O'RTA" }
        val low = apps.count { it.level == "PAST" }
        val score = overallScore()
        val color = when { score >= 70 -> green; score >= 40 -> orange; else -> red }
        summaryBox.removeAllViews()
        summaryBox.background = rounded(tint(color), 12)
        summaryBox.addView(label("Umumiy xavfsizlik bali: $score/100", 20f, true, color))
        summaryBox.addView(label("${apps.size} ta ilova tekshirildi", 14f), params(6))
        summaryBox.addView(label("Yuqori xavf: $high    O'rta: $med    Past: $low", 14f), params(2))
        summaryBox.addView(label("Telefon sozlamalarida muammo: ${checks.count { !it.ok }}", 14f), params(2))
        summaryBox.addView(label("Ilovaning xavf bali uning zararli ekanini emas, sezgir imkoniyatlari ko'pligini ko'rsatadi.", 11f), params(6))
    }

    private fun renderList() {
        for ((key, b) in filterButtons) b.alpha = if (key == filter) 1f else 0.5f
        listBox.removeAllViews()
        if (!loaded) { listBox.addView(label("Tekshirilmoqda...", 14f)); return }
        val shown = apps.filter {
            (filter == "HAMMASI" || it.level == filter) &&
                (query.isBlank() || it.name.contains(query, true) || it.pkg.contains(query, true))
        }
        if (shown.isEmpty()) { listBox.addView(label("Ilova topilmadi", 14f)); return }
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
        info.addView(label("Xavf bali ${a.score}/100 · ${levelText(a.level)}", 12f, false, color), params(2))
        val bar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; weightSum = 100f }
        bar.addView(View(this).apply { setBackgroundColor(color) }, LinearLayout.LayoutParams(0, -1, a.score.toFloat()))
        bar.addView(View(this).apply { setBackgroundColor(Color.rgb(224, 224, 224)) }, LinearLayout.LayoutParams(0, -1, (100 - a.score).toFloat()))
        info.addView(bar, LinearLayout.LayoutParams(-1, dp(6)).apply { setMargins(0, dp(6), 0, 0) })
        row.addView(info, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(12), 0, 0, 0) })
        return row
    }

    // ---------- tahlil oynasi ----------
    private fun summaryText(a: AppRisk) = when (a.level) {
        "YUQORI" -> "Ko'p sezgir imkoniyat so'ralgan yoki berilgan. Kerak bo'lmasa ruxsatlarni qaytarib oling yoki ilovani o'chiring."
        "O'RTA" -> "Ba'zi ruxsatlar bor. Ular ilova vazifasiga mos kelishini tekshiring."
        else -> "Sezgir ruxsatlar kam. Odatda xavotirga o'rin yo'q."
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
        names.addView(label("Manba: ${a.source}", 12f))
        head.addView(names, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(12), 0, dp(8), 0) })
        head.addView(label("${a.score}/100", 16f, true, color).apply {
            setPadding(dp(10), dp(4), dp(10), dp(4)); background = rounded(tint(color), 8)
        })
        col.addView(head)
        col.addView(label(a.pkg, 11f), params(4))
        val catNote = if (a.category == "Aniqlanmagan") "" else if (a.categoryDeclared) " (Android e'lon qilgan)" else " (taxminiy)"
        col.addView(label("Ilova turi: ${a.category}$catNote", 12f), params(2))
        col.addView(label("${levelText(a.level)} · xavf bali", 12f, true, color), params(2))

        fun section(t: String) = col.addView(label(t, 14f, true, Color.BLACK), params(16))
        fun line(left: String, right: String, rc: Int = Color.DKGRAY) {
            val r = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            r.addView(label(left, 13f), LinearLayout.LayoutParams(0, -2, 1f))
            r.addView(label(right, 13f, false, rc))
            col.addView(r, params(6))
        }

        section("Ruxsatlar va ball")
        if (a.findings.isEmpty()) col.addView(label("Sezgir ruxsat topilmadi", 13f), params(6))
        for (f in a.findings) {
            line(f.text, (if (f.granted) "berilgan" else "so'ralgan") + ", +${f.points}",
                if (f.granted && f.points >= 10) red else Color.DKGRAY)
            if (f.note.isNotEmpty()) col.addView(label("   ${f.note}", 11f), params(0))
        }

        section("Fonda ishlash imkoniyati")
        for (f in a.background) line(f.text, if (f.granted) "bor, +${f.points}" else "yo'q")
        col.addView(label("Eslatma: Android boshqa ilova hozir ishlayotganini ko'rsatmaydi. Bu faqat imkoniyat.", 11f), params(6))
        if (a.warning != null) {
            col.addView(label(a.warning, 13f, false, red).apply {
                setPadding(dp(10), dp(8), dp(10), dp(8)); background = rounded(tint(red), 8)
            }, params(10))
        }

        section("Xulosa")
        col.addView(label(summaryText(a), 13f), params(6))

        fun btn(text: String, c: Int = Color.BLACK, action: () -> Unit) =
            col.addView(Button(this).apply { this.text = text; setTextColor(c); setOnClickListener { action() } }, params(8))
        btn("Ilova sozlamalarini ochish") {
            try { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${a.pkg}"))) }
            catch (e: Exception) { Toast.makeText(this, "Sozlamalarni ochib bo'lmadi", Toast.LENGTH_SHORT).show() }
        }
        btn("Batareya sozlamalarini ochish") {
            try { startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
            catch (e: Exception) { Toast.makeText(this, "Sozlamalarni ochib bo'lmadi", Toast.LENGTH_SHORT).show() }
        }
        btn("Ilovani o'chirish", red) {
            try { startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:${a.pkg}"))) }
            catch (e: Exception) { Toast.makeText(this, "O'chirishni boshlab bo'lmadi", Toast.LENGTH_SHORT).show() }
        }
        btn("Yopish") { dlg.dismiss() }
        col.addView(label("Yuqori ball ilova zararli ekanini isbotlamaydi. U faqat qanday sezgir imkoniyatlar mavjudligini ko'rsatadi.", 11f), params(12))

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
            phoneBox.addView(label((if (c.ok) "✓ " else "⚠ ") + c.title + "\n" + c.detail, 14f).apply {
                setPadding(dp(12), dp(10), dp(12), dp(10)); background = rounded(tint(color), 10)
            }, params(8))
        }
        phoneBox.addView(label("Eslatma: bu tekshiruvlar taxminiy va qurilma xavfsizligining to'liq kafolati emas.", 12f), params(12))
    }

    // ---------- parol va xavfsizlik ekrani ----------
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
        val has = hasPassword()
        secBox.addView(label(if (has) "Ilovaga kirish paroli: o'rnatilgan" else "Ilovaga kirish paroli: o'rnatilmagan", 16f, true, if (has) green else orange))
        secBox.addView(label("Parol ilova ochilganda va 10 soniyadan ko'p fonda turib qaytganda so'raladi. Parol telefonda ochiq holda emas, tasodifiy tuz bilan xeshlangan (PBKDF2) holda saqlanadi. 5 marta xato kiritilsa, kutish vaqti qo'yiladi. Parol esdan chiqsa, ilovani o'chirib qayta o'rnatish kerak bo'ladi.", 12f), params(4))

        val curField = if (has) pwField("Joriy parol") else null
        if (curField != null) secBox.addView(curField, params(12))
        val newPw = pwField("Yangi parol")
        val conf = pwField("Parolni takrorlang")
        secBox.addView(newPw, params(8))
        secBox.addView(CheckBox(this).apply {
            text = "Parolni ko'rsatish"
            setOnCheckedChangeListener { _, on ->
                for (e in listOfNotNull(curField, newPw, conf)) setPwVisible(e, on)
            }
        }, params(0))

        val fill = View(this)
        val rest = View(this).apply { setBackgroundColor(Color.rgb(224, 224, 224)) }
        val bar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        bar.addView(fill, LinearLayout.LayoutParams(0, -1, 0f))
        bar.addView(rest, LinearLayout.LayoutParams(0, -1, 100f))
        secBox.addView(bar, LinearLayout.LayoutParams(-1, dp(6)).apply { setMargins(0, dp(8), 0, 0) })
        val lvl = label("Parol kiritilmagan", 15f, true)
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
        fun say(t: String, ok: Boolean) { msg.text = t; msg.setTextColor(if (ok) green else red) }

        fun update(p: String) {
            val s = strength(p)
            val col = rankColor(s.rank)
            val w = when (s.rank) { -1 -> 0; 0 -> 15; 1 -> 35; 2 -> 65; else -> 100 }
            fill.setBackgroundColor(col)
            fill.layoutParams = LinearLayout.LayoutParams(0, -1, w.toFloat())
            rest.layoutParams = LinearLayout.LayoutParams(0, -1, (100 - w).toFloat())
            lvl.text = s.name
            lvl.setTextColor(col)
            crack.text = if (p.isEmpty()) "" else "Taxminiy buzish vaqti: " + fmtTime(s.secs)
            ent.text = if (p.isEmpty()) "" else "Entropiya: ${s.bits.toInt()} bit (soniyasiga 10 milliard taxmin deb hisoblangan)"
            val oks = listOf(
                (p.length >= 8) to "Kamida 8 ta belgi",
                p.any { it in 'A'..'Z' } to "Katta harf",
                p.any { it in '0'..'9' } to "Raqam",
                p.any { !(it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9') } to "Maxsus belgi (!, #, @ ...)",
                (p.isNotEmpty() && p.lowercase() !in common) to "Ko'p ishlatiladigan parol emas"
            )
            for ((i, pair) in oks.withIndex()) {
                rows[i].text = (if (pair.first) "✓ " else "✗ ") + pair.second
                rows[i].setTextColor(if (pair.first) green else red)
            }
        }
        newPw.addTextChangedListener(watcher { update(it) })
        update("")

        secBox.addView(Button(this).apply {
            text = if (has) "Parolni almashtirish" else "Parolni o'rnatish"
            setOnClickListener {
                if (has && !verify(curField?.text?.toString() ?: "")) { say("Joriy parol noto'g'ri.", false); return@setOnClickListener }
                val np = newPw.text.toString()
                val s = strength(np)
                when {
                    np.isEmpty() -> say("Yangi parolni kiriting.", false)
                    np.length < 6 || s.rank < 2 -> say("Parol juda zaif. Kamida \"O'rtacha\" daraja kerak (harf va raqamni aralashtiring).", false)
                    np != conf.text.toString() -> say("Parollar mos kelmadi.", false)
                    else -> {
                        savePassword(np)
                        unlocked = true
                        secMsg = "Parol o'rnatildi."
                        renderSecurity()
                    }
                }
            }
        }, params(12))
        if (has) {
            secBox.addView(Button(this).apply {
                text = "Parolni o'chirish"
                setTextColor(red)
                setOnClickListener {
                    if (!verify(curField?.text?.toString() ?: "")) { say("Joriy parolni kiriting.", false); return@setOnClickListener }
                    removePassword()
                    secMsg = "Parol o'chirildi."
                    renderSecurity()
                }
            }, params(4))
        }
        secBox.addView(msg, params(8))

        secBox.addView(label("Skrinshotdan himoya", 15f, true, Color.BLACK), params(20))
        secBox.addView(label("Yoqilsa, ilova oynasi skrinshotda va so'nggi ilovalar ro'yxatida ko'rinmaydi. Taqdimot uchun skrinshot olmoqchi bo'lsangiz, o'chirib qo'ying.", 12f), params(4))
        secBox.addView(Button(this).apply {
            text = if (secureOn()) "Hozir: YOQILGAN (o'chirish)" else "Hozir: O'CHIQ (yoqish)"
            setOnClickListener {
                prefs().edit().putBoolean("secure", !secureOn()).apply()
                applySecureFlag()
                renderSecurity()
            }
        }, params(6))
    }

    // ---------- maxfiylik ----------
    private fun buildPrivacy(): View {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(32))
        }
        fun h(t: String) = col.addView(label(t, 16f, true, Color.BLACK), params(16))
        fun p(t: String) = col.addView(label(t, 13f), params(6))
        h("Ma'lumotlaringiz qayerda?")
        p("Barcha tekshiruv telefoningizning o'zida bajariladi. Ilovaning internetga ruxsati yo'q, shuning uchun ma'lumot tashqariga yuborilmaydi. Natija faqat siz \"Hisobotni ulashish\" ni bosib, ilovani tanlaganingizda ketadi.")
        h("Ilova qaysi ruxsatlarni oladi?")
        p("• O'rnatilgan ilovalar ro'yxatini ko'rish: ruxsatlarni tahlil qilish uchun.\n• Ilovani o'chirish oynasini ochish: faqat siz \"Ilovani o'chirish\" tugmasini bosganda.")
        h("Xavf bali qanday hisoblanadi?")
        p("Har bir sezgir ruxsatga ball beriladi (masalan SMS o'qish 20, mikrofon 15, kamera 10). Berilgan ruxsat to'liq, faqat so'ralgan ruxsat uchdan bir ball oladi. Accessibility xizmati yoqilgan bo'lsa 30 ball.")
        p("Rasmiy do'kondan (Play Market, Xiaomi GetApps, Samsung Galaxy Store, Huawei AppGallery, Amazon Appstore) o'rnatilmagan ilovaga +15. Fon xizmati, avtoishga tushish, aniq signal va batareya cheklovidan chiqish uchun kichik ballar qo'shiladi.")
        p("Ilova turi (ijtimoiy tarmoq, xarita, foto) bo'yicha odatiy ruxsatlar yarim ball oladi. Masalan, ijtimoiy tarmoqqa kamera va mikrofon odatiy. O'yinga esa bu ruxsatlar uchun ogohlantirish chiqadi.")
        p("Daraja: 0-29 past, 30-59 o'rta, 60 va undan yuqori yuqori xavf. Ballar mening qo'lda belgilagan baholarim, ilmiy standart emas.")
        h("Cheklovlar")
        p("• Ilova virus qidirmaydi, faqat ruxsat va imkoniyatlarni baholaydi.\n• Yuqori ball ilova zararli ekanini isbotlamaydi, past ball xavfsizlik kafolati emas.\n• Android boshqa ilova hozir ishlayotganini ko'rsatmaydi, faqat fonda ishlash imkoniyatini ko'rish mumkin.\n• Ilova turi har doim aniq aniqlanmaydi.")
        return ScrollView(this).apply { addView(col) }
    }

    // ---------- hisobot ----------
    private fun shareReport() {
        if (!loaded) { Toast.makeText(this, "Tekshiruv tugamadi, biroz kuting", Toast.LENGTH_SHORT).show(); return }
        val sb = StringBuilder()
        sb.append("Ruxsat Auditori hisoboti\n")
        sb.append("Umumiy xavfsizlik bali: ${overallScore()}/100\n")
        sb.append("Tekshirilgan ilovalar: ${apps.size} (yuqori ${apps.count { it.level == "YUQORI" }}, ")
        sb.append("o'rta ${apps.count { it.level == "O'RTA" }}, past ${apps.count { it.level == "PAST" }})\n\n")
        sb.append("Eng yuqori xavf balli ilovalar:\n")
        for (a in apps.take(5)) sb.append("• ${a.name}: ${a.score}/100 (${levelText(a.level)})\n")
        sb.append("\nTelefon sozlamalari:\n")
        for (c in checks) sb.append((if (c.ok) "✓ " else "⚠ ") + "${c.title}: ${c.detail}\n")
        sb.append("\nEslatma: xavf bali ilova zararli ekanini isbotlamaydi.")
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, sb.toString())
        }
        startActivity(Intent.createChooser(send, "Hisobotni ulashish"))
    }

    // ---------- yordamchi (tayyor qoidalar) ----------
    private val quick = listOf("Eng xavfli ilova qaysi?", "Telefonim xavfsizmi?", "Ruxsat nima?", "Qanday himoyalanaman?")

    private fun buildChat(): View {
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        chatLog = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(8), dp(12), dp(8)) }
        chatScroll = ScrollView(this).apply { addView(chatLog) }

        val chips = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(dp(8), 0, dp(8), 0) }
        for (q in quick) chips.addView(Button(this).apply {
            text = q; textSize = 12f; isAllCaps = false
            setOnClickListener { send(q) }
        })
        col.addView(HorizontalScrollView(this).apply { addView(chips) }, LinearLayout.LayoutParams(-1, -2))
        col.addView(chatScroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val input = EditText(this).apply { hint = "Savol yozing..."; setSingleLine() }
        val sendBtn = Button(this).apply {
            text = "Yuborish"
            setOnClickListener {
                val t = input.text.toString().trim()
                if (t.isNotEmpty()) { input.setText(""); send(t) }
            }
        }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(dp(8), dp(4), dp(8), dp(8)) }
        row.addView(input, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(sendBtn)
        col.addView(row, LinearLayout.LayoutParams(-1, -2))

        bubble("Salom! Men telefon xavfsizligi bo'yicha yordamchiman. Javoblarim tayyor qoidalarga va sizning tekshiruv natijalaringizga asoslanadi, internet kerak emas.", false)
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

    private val topics = listOf(
        listOf("sms") to "SMS ruxsati xavfli, chunki ilova bir martalik tasdiqlash kodlarini (banklar, ijtimoiy tarmoqlar) o'qib olishi mumkin. Faqat xabar almashish ilovalariga bering.",
        listOf("mikrofon") to "Mikrofon ruxsati ilovaga atrofdagi ovozni yozishga imkon beradi. Qo'ng'iroq, ovozli xabar va yozuv ilovalariga kerak, kalkulyator yoki o'yinga kerak emas.",
        listOf("kamera") to "Kamera ruxsati faqat suratga olish uchun kerak bo'lgan ilovalarga berilishi kerak. Sozlamalarda 'faqat ishlatilayotganda' variantini tanlang.",
        listOf("joylashuv", "lokatsiya", "gps") to "Joylashuv, ayniqsa fonda, harakatlaringizni kuzatishga imkon beradi. Xarita va yetkazib berish ilovalaridan boshqasiga 'faqat ishlatilayotganda' bering.",
        listOf("accessibility", "maxsus imkon") to "Accessibility xizmati ekrandagi hamma narsani o'qiy oladi va ilova nomidan harakat qila oladi. Zararli dasturlar tez-tez shuni so'raydi. Bilmagan ilovaga bermang.",
        listOf("batareya", "fon", "fonda") to "Batareya cheklovidan chiqarilgan ilova fonda erkin ishlay oladi. Bu o'z-o'zidan xavfli emas, lekin mikrofon yoki joylashuv bilan birga bo'lsa, tekshirib ko'ring.",
        listOf("ball", "baho", "hisob") to "Xavf bali 0 dan 100 gacha. Berilgan sezgir ruxsatlar to'liq, faqat so'ralganlari uchdan bir ball oladi. Ilova turiga mos ruxsatlar yarim ball oladi. Rasmiy do'kondan emas manba +15. 60 dan yuqori bu yuqori xavf. Batafsil: menyudagi 'Maxfiylik va baholash'.",
        listOf("parol") to "Ilovaga parol qo'yish uchun menyudan 'Parol va xavfsizlik' bo'limini oching. Kuchli parol: kamida 8 belgi, katta va kichik harf, raqam va maxsus belgi.",
        listOf("maxfiy", "internet") to "Ilovaning internetga ruxsati yo'q, barcha tekshiruv telefoningizda bajariladi. Batafsil: menyudagi 'Maxfiylik va baholash'.",
        listOf("o'chir", "ochir", "o‘chir") to "Ilovani o'chirish uchun Ilovalar bo'limida uni bosing va pastdagi 'Ilovani o'chirish' tugmasini tanlang.",
        listOf("ruxsat") to "Ruxsat bu ilovaning telefon imkoniyatlaridan (kamera, SMS, joylashuv) foydalanish huquqi. Yaxshi qoida: ilova vazifasiga kerak bo'lmagan ruxsatni bermang.",
        listOf("salom", "assalom", "hi", "hello") to "Salom! Savol bering yoki yuqoridagi tugmalardan birini bosing."
    )

    private fun answer(q0: String): String {
        val q = q0.lowercase()
        fun has(vararg w: String) = w.any { q.contains(it) }
        if (!loaded) return "Tekshiruv hali tugamadi. Bir necha soniyadan keyin qayta so'rang."
        if (has("eng xavfli", "xavfli ilova")) {
            val top = apps.take(3).filter { it.score > 0 }
            return if (top.isEmpty()) "Sezgir ruxsatli ilova topilmadi."
            else "Eng yuqori xavf balli ilovalar:\n" + top.joinToString("\n") { "• ${it.name}: ${it.score}/100 (${levelText(it.level)})" } +
                "\nBatafsil ko'rish uchun Ilovalar bo'limida ustiga bosing."
        }
        if (has("telefon", "qulf", "yangilan", "root", "xavfsizmi")) {
            val bad = checks.filter { !it.ok }
            return if (bad.isEmpty()) "Telefon sozlamalarida muammo topilmadi."
            else "Telefon sozlamalarida ${bad.size} ta muammo bor:\n" + bad.joinToString("\n") { "• ${it.title}: ${it.detail}" }
        }
        if (has("himoyalan", "maslahat", "qanday")) {
            return "Asosiy maslahatlar:\n• Ekran qulfini yoqing.\n• Ilovalarni faqat rasmiy do'kondan o'rnating.\n• Ilova vazifasiga kerak bo'lmagan ruxsatni bermang.\n• Telefonni yangilab turing.\n• Noma'lum havola va SMS kodlarni hech kimga bermang."
        }
        for ((keys, text) in topics) if (keys.any { q.contains(it) }) return text
        return "Bu savolni tushunmadim. Quyidagilarni so'rab ko'ring: eng xavfli ilova, telefon xavfsizligi, SMS, mikrofon, kamera, joylashuv, batareya, parol, xavf bali qanday hisoblanadi."
    }
}
