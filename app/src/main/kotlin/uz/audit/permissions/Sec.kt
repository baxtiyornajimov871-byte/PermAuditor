package uz.audit.permissions

import android.content.Context
import android.os.Build
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** Parolni saqlash va tekshirish (PBKDF2 + tuz). Ilova va qulf oynasi uchun umumiy. */
object Sec {
    private fun p(ctx: Context) = ctx.getSharedPreferences("sec", Context.MODE_PRIVATE)

    fun has(ctx: Context) = p(ctx).contains("hash")

    private fun hashPw(pw: String, salt: ByteArray, algo: String): ByteArray =
        SecretKeyFactory.getInstance(algo).generateSecret(PBEKeySpec(pw.toCharArray(), salt, 60000, 256)).encoded

    fun save(ctx: Context, pw: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val algo = if (Build.VERSION.SDK_INT >= 26) "PBKDF2WithHmacSHA256" else "PBKDF2WithHmacSHA1"
        val h = hashPw(pw, salt, algo)
        p(ctx).edit()
            .putString("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString("hash", Base64.encodeToString(h, Base64.NO_WRAP))
            .putString("algo", algo)
            .putInt("fails", 0).putLong("lock_until", 0L).apply()
    }

    fun verify(ctx: Context, pw: String): Boolean {
        val sp = p(ctx)
        val salt = Base64.decode(sp.getString("salt", null) ?: return false, Base64.NO_WRAP)
        val want = Base64.decode(sp.getString("hash", null) ?: return false, Base64.NO_WRAP)
        val algo = sp.getString("algo", "PBKDF2WithHmacSHA1") ?: return false
        return try { MessageDigest.isEqual(want, hashPw(pw, salt, algo)) } catch (e: Exception) { false }
    }

    fun remove(ctx: Context) {
        p(ctx).edit().remove("salt").remove("hash").remove("algo").putInt("fails", 0).putLong("lock_until", 0L).apply()
    }

    /** Parolni tekshiradi. To'g'ri bo'lsa null, aks holda foydalanuvchiga ko'rsatiladigan xabar. */
    fun attempt(ctx: Context, input: String): String? {
        val sp = p(ctx)
        val now = System.currentTimeMillis()
        val until = sp.getLong("lock_until", 0L)
        if (now < until) {
            val s = (until - now) / 1000 + 1
            return L.t("Ko'p xato kiritildi. $s soniya kuting.", "Too many wrong attempts. Wait $s seconds.", "Слишком много ошибок. Подождите $s сек.")
        }
        if (verify(ctx, input)) {
            sp.edit().putInt("fails", 0).putLong("lock_until", 0L).apply()
            return null
        }
        val f = sp.getInt("fails", 0) + 1
        var u = 0L
        if (f % 5 == 0) u = now + 30_000L * (1L shl (f / 5 - 1).coerceAtMost(6))
        sp.edit().putInt("fails", f).putLong("lock_until", u).apply()
        return if (u > 0) {
            val s = (u - now) / 1000
            L.t("5 marta xato. $s soniya kuting.", "5 wrong attempts. Wait $s seconds.", "5 ошибок. Подождите $s сек.")
        } else L.t("Parol noto'g'ri. Urinish: $f", "Wrong password. Attempt: $f", "Неверный пароль. Попытка: $f")
    }
}
