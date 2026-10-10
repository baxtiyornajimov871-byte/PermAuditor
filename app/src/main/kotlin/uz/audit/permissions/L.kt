package uz.audit.permissions

import android.content.Context

/** Til tanlash: uz / en / ru */
object L {
    var code = "uz"

    fun load(ctx: Context) {
        code = ctx.getSharedPreferences("sec", Context.MODE_PRIVATE).getString("lang", "uz") ?: "uz"
    }

    fun save(ctx: Context, c: String) {
        ctx.getSharedPreferences("sec", Context.MODE_PRIVATE).edit().putString("lang", c).apply()
        code = c
    }

    fun t(uz: String, en: String, ru: String): String = when (code) {
        "en" -> en
        "ru" -> ru
        else -> uz
    }
}
