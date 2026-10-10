package uz.audit.permissions

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

/** Telefon yonganda himoya yoqilgan bo'lsa, xizmatni qayta ishga tushiradi. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        if (!ctx.getSharedPreferences("sec", Context.MODE_PRIVATE).getBoolean("guard", false)) return
        try {
            val i = Intent(ctx, GuardService::class.java)
            if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i) else ctx.startService(i)
        } catch (e: Exception) { }
    }
}
