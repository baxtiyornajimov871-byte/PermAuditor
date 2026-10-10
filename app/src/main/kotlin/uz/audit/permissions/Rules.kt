package uz.audit.permissions

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** limitMin = 0 bo'lsa, ilova har safar ochilganda parol so'raladi. mode: KICK yoki PASS */
data class Rule(val pkg: String, val name: String, val limitMin: Int, val mode: String)

object Rules {
    private fun p(ctx: Context) = ctx.getSharedPreferences("rules", Context.MODE_PRIVATE)

    fun load(ctx: Context): List<Rule> {
        val out = mutableListOf<Rule>()
        try {
            val arr = JSONArray(p(ctx).getString("list", "[]"))
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(Rule(o.getString("pkg"), o.getString("name"), o.getInt("limit"), o.getString("mode")))
            }
        } catch (e: Exception) { }
        return out
    }

    fun save(ctx: Context, list: List<Rule>) {
        val arr = JSONArray()
        for (r in list) arr.put(JSONObject().put("pkg", r.pkg).put("name", r.name).put("limit", r.limitMin).put("mode", r.mode))
        p(ctx).edit().putString("list", arr.toString()).apply()
    }

    fun todayKey(): String = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())

    fun usedMs(ctx: Context, pkg: String): Long =
        if (p(ctx).getString("d_$pkg", "") == todayKey()) p(ctx).getLong("u_$pkg", 0L) else 0L

    fun setUsed(ctx: Context, pkg: String, ms: Long) {
        p(ctx).edit().putString("d_$pkg", todayKey()).putLong("u_$pkg", ms).apply()
    }

    fun baselineDone(ctx: Context, pkg: String): Boolean = p(ctx).getString("b_$pkg", "") == todayKey()
    fun markBaseline(ctx: Context, pkg: String) { p(ctx).edit().putString("b_$pkg", todayKey()).apply() }

    fun grantUntil(ctx: Context, pkg: String): Long = p(ctx).getLong("g_$pkg", 0L)
    fun setGrant(ctx: Context, pkg: String, until: Long) { p(ctx).edit().putLong("g_$pkg", until).apply() }
}
