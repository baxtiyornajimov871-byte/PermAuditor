package uz.audit.permissions

import android.app.KeyguardManager
import android.app.admin.DevicePolicyManager
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

/** key: ruxsat yoki omil kaliti (nomi interfeysda tarjima qilinadi) */
data class Finding(val key: String, val points: Int, val granted: Boolean, val note: String = "")

data class AppRisk(
    val name: String,
    val pkg: String,
    val score: Int,
    val level: String,          // HIGH / MED / LOW
    val findings: List<Finding>,
    val background: List<Finding>,
    val source: String,         // play, getapps, galaxy, appgallery, amazon, unknown, other
    val installer: String?,
    val warnings: List<String>,
    val category: String,       // SOCIAL, MAPS, IMAGE, GAME, NONE
    val categoryDeclared: Boolean
)

data class Check(val key: String, val ok: Boolean, val arg: String = "")

object RiskScorer {
    // Android "dangerous" ruxsatlari va ko'p suiiste'mol qilinadigan maxsus ruxsatlar
    private val weights = mapOf(
        "android.permission.READ_SMS" to ("sms_read" to 20),
        "android.permission.RECEIVE_SMS" to ("sms_recv" to 15),
        "android.permission.SEND_SMS" to ("sms_send" to 20),
        "android.permission.RECORD_AUDIO" to ("mic" to 15),
        "android.permission.CAMERA" to ("camera" to 10),
        "android.permission.READ_CONTACTS" to ("contacts_read" to 10),
        "android.permission.READ_CALL_LOG" to ("calllog" to 15),
        "android.permission.CALL_PHONE" to ("call_phone" to 10),
        "android.permission.ANSWER_PHONE_CALLS" to ("answer_calls" to 5),
        "android.permission.PROCESS_OUTGOING_CALLS" to ("outgoing_calls" to 10),
        "android.permission.READ_PHONE_STATE" to ("phone_state" to 5),
        "android.permission.READ_PHONE_NUMBERS" to ("phone_number" to 5),
        "android.permission.ACCESS_FINE_LOCATION" to ("loc_fine" to 10),
        "android.permission.ACCESS_COARSE_LOCATION" to ("loc_coarse" to 5),
        "android.permission.ACCESS_BACKGROUND_LOCATION" to ("loc_bg" to 15),
        "android.permission.READ_CALENDAR" to ("calendar" to 5),
        "android.permission.BODY_SENSORS" to ("sensors" to 5),
        "android.permission.ACTIVITY_RECOGNITION" to ("activity" to 3),
        "android.permission.READ_EXTERNAL_STORAGE" to ("storage" to 5),
        "android.permission.READ_MEDIA_IMAGES" to ("storage" to 5),
        "android.permission.READ_MEDIA_VIDEO" to ("storage" to 5),
        "android.permission.READ_MEDIA_AUDIO" to ("storage" to 5),
        "android.permission.MANAGE_EXTERNAL_STORAGE" to ("all_files" to 15),
        "android.permission.BLUETOOTH_CONNECT" to ("bluetooth" to 3),
        "android.permission.BLUETOOTH_SCAN" to ("bluetooth" to 3),
        "android.permission.NEARBY_WIFI_DEVICES" to ("nearby" to 3),
        "android.permission.SYSTEM_ALERT_WINDOW" to ("overlay" to 20),
        "android.permission.REQUEST_INSTALL_PACKAGES" to ("install_pkgs" to 15),
        "android.permission.PACKAGE_USAGE_STATS" to ("usage_stats" to 15),
        "android.permission.WRITE_SETTINGS" to ("write_settings" to 5)
    )
    val permKeys: Set<String> = weights.values.map { it.first }.toSet()

    private val sensitive = setOf("mic", "camera", "loc_fine", "loc_bg")
    private val expectedByCat = mapOf(
        "SOCIAL" to setOf("camera", "mic", "contacts_read", "loc_fine", "loc_coarse", "phone_state",
            "phone_number", "storage", "bluetooth", "nearby"),
        "MAPS" to setOf("loc_fine", "loc_coarse", "loc_bg", "mic", "activity"),
        "IMAGE" to setOf("camera", "mic", "loc_fine", "loc_coarse", "storage"),
        "GAME" to setOf("storage", "phone_state")
    )
    private val gameOdd = setOf("mic", "camera", "sms_read", "sms_send", "sms_recv", "contacts_read", "calllog")
    private val stores = mapOf(
        "com.android.vending" to "play",
        "com.xiaomi.mipicks" to "getapps",
        "com.sec.android.app.samsungapps" to "galaxy",
        "com.huawei.appmarket" to "appgallery",
        "com.amazon.venezia" to "amazon"
    )
    private val socialPkgs = listOf("com.instagram.", "com.facebook.", "com.whatsapp", "org.telegram.", "com.snapchat.",
        "com.zhiliaoapp.musically", "com.twitter.", "com.viber.", "com.discord", "org.thoughtcrime.securesms",
        "com.skype.", "us.zoom.", "com.google.android.apps.tachyon", "com.vkontakte.", "ru.ok.", "com.imo.")
    private val mapsPkgs = listOf("com.google.android.apps.maps", "com.waze", "ru.yandex.yandexmaps",
        "ru.yandex.yandexnavi", "com.yandex.maps", "com.here.", "app.organicmaps", "com.mapswithme")
    private val imagePkgs = listOf("com.google.android.apps.photos", "com.google.android.GoogleCamera",
        "com.sec.android.app.camera", "com.adobe.lrmobile", "com.picsart.studio", "com.camerasideas")

    private const val A11Y = "android.permission.BIND_ACCESSIBILITY_SERVICE"
    private const val LISTENER = "android.permission.BIND_NOTIFICATION_LISTENER_SERVICE"
    private const val ADMIN = "android.permission.BIND_DEVICE_ADMIN"

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
        score < 30 -> "LOW"
        score < 60 -> "MED"
        else -> "HIGH"
    }

    private fun enabledSet(ctx: Context, secureName: String): Set<String> {
        val s = try { Settings.Secure.getString(ctx.contentResolver, secureName) } catch (e: Exception) { null }
        if (s.isNullOrEmpty()) return emptySet()
        return s.split(":").map { it.substringBefore("/") }.filter { it.isNotEmpty() }.toSet()
    }

    fun enabledAccessibility(ctx: Context) = enabledSet(ctx, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
    private fun enabledListeners(ctx: Context) = enabledSet(ctx, "enabled_notification_listeners")

    @Suppress("DEPRECATION")
    fun scan(ctx: Context): List<AppRisk> {
        val pm = ctx.packageManager
        val power = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
        val a11y = enabledAccessibility(ctx)
        val listeners = enabledListeners(ctx)
        val admins: Set<String> = try {
            val dpm = ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            dpm.activeAdmins?.map { it.packageName }?.toSet() ?: emptySet()
        } catch (e: Exception) { emptySet() }

        val flags = PackageManager.GET_PERMISSIONS or PackageManager.GET_SERVICES or PackageManager.GET_RECEIVERS
        val packages = if (Build.VERSION.SDK_INT >= 33)
            pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(flags.toLong()))
        else pm.getInstalledPackages(flags)

        val result = mutableListOf<AppRisk>()
        for (p in packages) {
            val ai = p.applicationInfo ?: continue
            val isSystem = ai.flags and ApplicationInfo.FLAG_SYSTEM != 0
            val updatedSystem = ai.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0
            if (isSystem && !updatedSystem) continue   // faqat foydalanuvchi va yangilangan ilovalar

            var score = 0
            val fmap = LinkedHashMap<String, Finding>()
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
                if (granted && w.first in expected) { pts /= 2; note = "expected" }
                val old = fmap[w.first]
                if (old == null || pts > old.points) fmap[w.first] = Finding(w.first, pts, granted, note)
            }

            val findings = fmap.values.toMutableList()
            fun addSpecial(on: String, off: String, declared: Boolean, active: Boolean, onPts: Int, offPts: Int) {
                if (!declared) return
                findings.add(if (active) Finding(on, onPts, true) else Finding(off, offPts, false))
            }
            addSpecial("a11y_on", "a11y_off", p.services?.any { it.permission == A11Y } == true,
                p.packageName in a11y, 30, 10)
            addSpecial("notif_on", "notif_off", p.services?.any { it.permission == LISTENER } == true,
                p.packageName in listeners, 25, 8)
            addSpecial("admin_on", "admin_off", p.receivers?.any { it.permission == ADMIN } == true,
                p.packageName in admins, 30, 10)

            val installer = try {
                if (Build.VERSION.SDK_INT >= 30) pm.getInstallSourceInfo(p.packageName).installingPackageName
                else pm.getInstallerPackageName(p.packageName)
            } catch (e: Exception) { null }
            val trusted = installer != null && installer in stores
            if (!trusted) findings.add(Finding("not_official", 15, true))
            if (ai.targetSdkVersion < 26) findings.add(Finding("old_target", 5, true))

            val ignoring = try { power.isIgnoringBatteryOptimizations(p.packageName) } catch (e: Exception) { false }
            val bg = listOf(
                Finding("bg_fgs", 3, perms.contains("android.permission.FOREGROUND_SERVICE")),
                Finding("bg_boot", 2, perms.contains("android.permission.RECEIVE_BOOT_COMPLETED")),
                Finding("bg_alarm", 2,
                    perms.contains("android.permission.SCHEDULE_EXACT_ALARM") || perms.contains("android.permission.USE_EXACT_ALARM")),
                Finding("bg_battery", 5, ignoring)
            )

            score += findings.sumOf { it.points } + bg.filter { it.granted }.sumOf { it.points }

            val warns = mutableListOf<String>()
            if (findings.any { it.granted && it.key in sensitive && it.key !in expected } && (bg[0].granted || bg[3].granted))
                warns.add("warn_sensitive_bg")
            if (catKey == "GAME" && findings.any { it.granted && it.key in gameOdd }) warns.add("warn_game")
            if (p.packageName == ctx.packageName) warns.add("warn_self")

            score = score.coerceAtMost(100)
            val source = when {
                installer != null && installer in stores -> stores[installer] ?: "other"
                installer == null -> "unknown"
                else -> "other"
            }
            result.add(AppRisk(
                pm.getApplicationLabel(ai).toString(), p.packageName, score, levelFor(score),
                findings.sortedByDescending { it.points }, bg, source, installer, warns, catKey, catDeclared
            ))
        }
        return result.sortedByDescending { it.score }
    }

    @Suppress("DEPRECATION")
    fun deviceChecks(ctx: Context): List<Check> {
        val list = mutableListOf<Check>()
        val pm = ctx.packageManager

        val km = ctx.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        list.add(Check("lock", km.isDeviceSecure))

        val patch = Build.VERSION.SECURITY_PATCH
        val days = try {
            val t = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(patch)?.time
            if (t == null) null else ((System.currentTimeMillis() - t) / 86400000L).toInt()
        } catch (e: Exception) { null }
        list.add(Check("patch", days == null || days <= 180, patch + "|" + (days?.toString() ?: "")))

        val dev = try { Settings.Global.getInt(ctx.contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1 } catch (e: Exception) { false }
        list.add(Check("dev", !dev))
        val adb = try { Settings.Global.getInt(ctx.contentResolver, Settings.Global.ADB_ENABLED, 0) == 1 } catch (e: Exception) { false }
        list.add(Check("adb", !adb))

        val rootPaths = listOf("/system/bin/su", "/system/xbin/su", "/sbin/su", "/su/bin/su", "/system/app/Superuser.apk")
        val rooted = rootPaths.any { File(it).exists() } || (Build.TAGS?.contains("test-keys") == true)
        list.add(Check("root", !rooted))

        val enc = try {
            val dpm = ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val st = dpm.storageEncryptionStatus
            st == DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE ||
                st == DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE_PER_USER ||
                st == DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE_DEFAULT_KEY
        } catch (e: Exception) { false }
        list.add(Check("enc", enc))

        fun names(set: Set<String>) = set.map { pkg ->
            try { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() } catch (e: Exception) { pkg }
        }
        val a = names(enabledAccessibility(ctx))
        list.add(Check("a11y", a.isEmpty(), a.joinToString(", ")))
        val n = names(enabledListeners(ctx))
        list.add(Check("listeners", n.isEmpty(), n.joinToString(", ")))
        return list
    }
}
