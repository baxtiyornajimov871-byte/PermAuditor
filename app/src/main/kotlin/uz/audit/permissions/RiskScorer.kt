package uz.audit.permissions

import android.app.KeyguardManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

data class Finding(val text: String, val points: Int, val granted: Boolean, val note: String = "")

data class AppRisk(
    val name: String,
    val pkg: String,
    val score: Int,
    val level: String,
    val findings: List<Finding>,
    val background: List<Finding>,
    val source: String,
    val installer: String?,
    val warning: String?,
    val category: String,
    val categoryDeclared: Boolean
)

data class Check(val title: String, val ok: Boolean, val detail: String)

object RiskScorer {
    private val weights = mapOf(
        "android.permission.READ_SMS" to ("SMS o'qish" to 20),
        "android.permission.RECEIVE_SMS" to ("SMS qabul qilish" to 15),
        "android.permission.SEND_SMS" to ("SMS yuborish" to 20),
        "android.permission.RECORD_AUDIO" to ("Mikrofon" to 15),
        "android.permission.CAMERA" to ("Kamera" to 10),
        "android.permission.READ_CONTACTS" to ("Kontaktlarni o'qish" to 10),
        "android.permission.READ_CALL_LOG" to ("Qo'ng'iroqlar tarixi" to 15),
        "android.permission.CALL_PHONE" to ("Qo'ng'iroq qilish" to 10),
        "android.permission.READ_PHONE_STATE" to ("Telefon holati" to 5),
        "android.permission.ACCESS_FINE_LOCATION" to ("Aniq joylashuv" to 10),
        "android.permission.ACCESS_BACKGROUND_LOCATION" to ("Fonda joylashuv" to 15),
        "android.permission.SYSTEM_ALERT_WINDOW" to ("Boshqa ilovalar ustidan chizish" to 20),
        "android.permission.REQUEST_INSTALL_PACKAGES" to ("Ilova o'rnatish" to 15),
        "android.permission.MANAGE_EXTERNAL_STORAGE" to ("Barcha fayllarga kirish" to 15)
    )
    private val sensitive = setOf("Mikrofon", "Kamera", "Aniq joylashuv", "Fonda joylashuv")
    private const val ACCESSIBILITY = "android.permission.BIND_ACCESSIBILITY_SERVICE"

    private val stores = mapOf(
        "com.android.vending" to "Play Market",
        "com.xiaomi.mipicks" to "Xiaomi GetApps",
        "com.sec.android.app.samsungapps" to "Samsung Galaxy Store",
        "com.huawei.appmarket" to "Huawei AppGallery",
        "com.amazon.venezia" to "Amazon Appstore"
    )
    private val expectedByCat = mapOf(
        "SOCIAL" to setOf("Kamera", "Mikrofon", "Kontaktlarni o'qish", "Aniq joylashuv", "Telefon holati"),
        "MAPS" to setOf("Aniq joylashuv", "Fonda joylashuv", "Mikrofon"),
        "IMAGE" to setOf("Kamera", "Mikrofon", "Aniq joylashuv")
    )
    private val categoryNames = mapOf(
        "SOCIAL" to "Ijtimoiy tarmoq / xabar almashish",
        "MAPS" to "Xarita / navigatsiya",
        "IMAGE" to "Foto / kamera",
        "GAME" to "O'yin",
        "NONE" to "Aniqlanmagan"
    )
    private val gameOdd = setOf("Mikrofon", "Kamera", "SMS o'qish", "SMS yuborish", "SMS qabul qilish",
        "Kontaktlarni o'qish", "Qo'ng'iroqlar tarixi")
    private val socialPkgs = listOf("com.instagram.", "com.facebook.", "com.whatsapp", "org.telegram.", "com.snapchat.",
        "com.zhiliaoapp.musically", "com.twitter.", "com.viber.", "com.discord", "org.thoughtcrime.securesms",
        "com.skype.", "us.zoom.", "com.google.android.apps.tachyon", "com.vkontakte.", "ru.ok.", "com.imo.")
    private val mapsPkgs = listOf("com.google.android.apps.maps", "com.waze", "ru.yandex.yandexmaps",
        "ru.yandex.yandexnavi", "com.yandex.maps", "com.here.", "app.organicmaps", "com.mapswithme")
    private val imagePkgs = listOf("com.google.android.apps.photos", "com.google.android.GoogleCamera",
        "com.sec.android.app.camera", "com.adobe.lrmobile", "com.picsart.studio", "com.camerasideas")

    @Suppress("DEPRECATION")
    private fun detectCategory(ai: ApplicationInfo, pkg: String): Pair<String, Boolean> {
        if ((ai.flags and ApplicationInfo.FLAG_IS_GAME) != 0) return "GAME" to true
        if (Build.VERSION.SDK_INT >= 26) {
            when (ai.category) {
                ApplicationInfo.CATEGORY_GAME -> return "GAME" to true
                ApplicationInfo.CATEGORY_SOCIAL -> return "SOCIAL" to true
                ApplicationInfo.CATEGORY_MAPS -> return "MAPS" to true
                ApplicationInfo.CATEGORY_IMAGE -> return "IMAGE" to true
            }
        }
        if (socialPkgs.any { pkg.startsWith(it) }) return "SOCIAL" to false
        if (mapsPkgs.any { pkg.startsWith(it) }) return "MAPS" to false
        if (imagePkgs.any { pkg.startsWith(it) }) return "IMAGE" to false
        return "NONE" to false
    }

    fun levelFor(score: Int) = when {
        score < 30 -> "PAST"
        score < 60 -> "O'RTA"
        else -> "YUQORI"
    }

    fun enabledAccessibility(ctx: Context): Set<String> {
        val s = try {
            Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        } catch (e: Exception) { null }
        if (s.isNullOrEmpty()) return emptySet()
        return s.split(":").map { it.substringBefore("/") }.filter { it.isNotEmpty() }.toSet()
    }

    @Suppress("DEPRECATION")
    fun scan(ctx: Context): List<AppRisk> {
        val pm = ctx.packageManager
        val power = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
        val enabledA11y = enabledAccessibility(ctx)
        val flags = PackageManager.GET_PERMISSIONS or PackageManager.GET_SERVICES
        val packages = if (Build.VERSION.SDK_INT >= 33)
            pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(flags.toLong()))
        else pm.getInstalledPackages(flags)

        val result = mutableListOf<AppRisk>()
        for (p in packages) {
            val ai = p.applicationInfo ?: continue
            if (ai.flags and ApplicationInfo.FLAG_SYSTEM != 0) continue

            var score = 0
            val findings = mutableListOf<Finding>()
            val (catKey, catDeclared) = detectCategory(ai, p.packageName)
            val expected = expectedByCat[catKey] ?: emptySet()
            val perms = p.requestedPermissions ?: emptyArray()
            val pflags = p.requestedPermissionsFlags
            for ((i, perm) in perms.withIndex()) {
                val w = weights[perm] ?: continue
                val granted = pflags != null && i < pflags.size &&
                    (pflags[i] and PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0
                var pts = if (granted) w.second else w.second / 3
                var note = ""
                if (granted && w.first in expected) { pts /= 2; note = "ilova turiga mos, yarmi hisoblandi" }
                score += pts
                findings.add(Finding(w.first, pts, granted, note))
            }

            if (p.services?.any { it.permission == ACCESSIBILITY } == true) {
                val on = p.packageName in enabledA11y
                val pts = if (on) 30 else 10
                score += pts
                findings.add(Finding(if (on) "Accessibility xizmati (YOQILGAN)" else "Accessibility xizmati (yoqilmagan)", pts, on))
            }

            val installer = try {
                if (Build.VERSION.SDK_INT >= 30) pm.getInstallSourceInfo(p.packageName).installingPackageName
                else pm.getInstallerPackageName(p.packageName)
            } catch (e: Exception) { null }
            val trusted = installer != null && installer in stores
            if (!trusted) {
                score += 15
                findings.add(Finding("Rasmiy do'kondan o'rnatilmagan", 15, true))
            }

            // fonda ishlash imkoniyati
            val ignoring = try { power.isIgnoringBatteryOptimizations(p.packageName) } catch (e: Exception) { false }
            val bg = listOf(
                Finding("Fon xizmati (foreground service)", 3, perms.contains("android.permission.FOREGROUND_SERVICE")),
                Finding("Telefon yonganda o'zi ishga tushadi", 2, perms.contains("android.permission.RECEIVE_BOOT_COMPLETED")),
                Finding("Aniq vaqtli signal (alarm)", 2,
                    perms.contains("android.permission.SCHEDULE_EXACT_ALARM") || perms.contains("android.permission.USE_EXACT_ALARM")),
                Finding("Batareya cheklovidan chiqarilgan", 5, ignoring)
            )
            score += bg.filter { it.granted }.sumOf { it.points }

            val warns = mutableListOf<String>()
            if (findings.any { it.granted && it.text in sensitive && it.text !in expected } && (bg[0].granted || bg[3].granted))
                warns.add("Mikrofon, kamera yoki joylashuv ruxsati fonda ishlash imkoniyati bilan birga bor. Bu ilova turiga mos kelishini tekshiring.")
            if (catKey == "GAME" && findings.any { it.granted && it.text in gameOdd })
                warns.add("O'yin uchun mikrofon, kamera, SMS yoki kontaktlar ruxsati odatiy emas.")
            val warning = if (warns.isEmpty()) null else warns.joinToString("\n")

            score = score.coerceAtMost(100)
            val source = when {
                installer != null && installer in stores -> stores[installer] ?: "Do'kon"
                installer == null -> "Noma'lum (APK fayl)"
                else -> "Boshqa manba (APK)"
            }
            result.add(AppRisk(
                pm.getApplicationLabel(ai).toString(), p.packageName, score, levelFor(score),
                findings.sortedByDescending { it.points }, bg, source, installer, warning,
                categoryNames[catKey] ?: "Aniqlanmagan", catDeclared
            ))
        }
        return result.sortedByDescending { it.score }
    }

    @Suppress("DEPRECATION")
    fun deviceChecks(ctx: Context): List<Check> {
        val list = mutableListOf<Check>()
        val pm = ctx.packageManager

        val km = ctx.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        val secure = km.isDeviceSecure
        list.add(Check("Ekran qulfi", secure,
            if (secure) "PIN, parol yoki barmoq izi o'rnatilgan" else "Ekran qulfi yo'q, telefon himoyasiz"))

        val patch = Build.VERSION.SECURITY_PATCH
        val days = try {
            val t = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(patch)?.time
            if (t == null) null else ((System.currentTimeMillis() - t) / 86400000L).toInt()
        } catch (e: Exception) { null }
        if (days != null) {
            list.add(Check("Xavfsizlik yangilanishi", days <= 180,
                "Oxirgi yangilanish: $patch ($days kun oldin)" + if (days > 180) ". Yangilash tavsiya etiladi" else ""))
        } else {
            list.add(Check("Xavfsizlik yangilanishi", true, "Sana aniqlanmadi: $patch"))
        }

        val dev = try { Settings.Global.getInt(ctx.contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1 } catch (e: Exception) { false }
        list.add(Check("Dasturchi rejimi", !dev,
            if (dev) "Yoqilgan. Oddiy foydalanuvchi uchun o'chirib qo'ygan ma'qul" else "O'chiq"))
        val adb = try { Settings.Global.getInt(ctx.contentResolver, Settings.Global.ADB_ENABLED, 0) == 1 } catch (e: Exception) { false }
        list.add(Check("USB debugging", !adb,
            if (adb) "Yoqilgan. Kompyuter orqali telefonga kirish mumkin" else "O'chiq"))

        val rootPaths = listOf("/system/bin/su", "/system/xbin/su", "/sbin/su", "/su/bin/su", "/system/app/Superuser.apk")
        val rooted = rootPaths.any { File(it).exists() } || (Build.TAGS?.contains("test-keys") == true)
        list.add(Check("Root belgilari", !rooted,
            if (rooted) "Root belgilari topildi (taxminiy tekshiruv)" else "Root belgilari topilmadi (taxminiy tekshiruv)"))

        val names = enabledAccessibility(ctx).map { pkg ->
            try { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() } catch (e: Exception) { pkg }
        }
        list.add(Check("Yoqilgan Accessibility xizmatlari", names.isEmpty(),
            if (names.isEmpty()) "Yoqilgan xizmat yo'q" else "Yoqilgan: " + names.joinToString(", ")))
        return list
    }
}
