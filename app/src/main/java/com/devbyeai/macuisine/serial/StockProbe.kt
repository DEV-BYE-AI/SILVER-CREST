package com.devbyeai.macuisine.serial

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import java.io.File

/** We run as uid=system (android.uid.system), the SAME uid as the stock
 *  Lidl app (de.silpion.mc2). That lets us (a) read which serial port its
 *  machine service has open, and (b) stop it from fighting us for that port. */
object StockProbe {
    private const val STOCK = "de.silpion.mc2"
    private const val TAG = "StockProbe"

    private fun pidDirs(): Array<File> =
        File("/proc").listFiles { f -> f.isDirectory && f.name.all { it.isDigit() } } ?: emptyArray()

    private fun cmdlineOf(pidDir: File): String =
        try { File(pidDir, "cmdline").readText().replace('\u0000', ' ').trim() } catch (e: Exception) { "" }

    /** Read /proc/<pid>/fd of every stock process and return the first
     *  /dev/tty* it has open — that is the real power-board UART. */
    fun findStockTty(): String? {
        try {
            for (p in pidDirs()) {
                val cmd = cmdlineOf(p)
                if (!cmd.startsWith(STOCK)) continue
                val fds = File(p, "fd").listFiles() ?: continue
                for (fd in fds) {
                    val target = try { fd.canonicalPath } catch (e: Exception) { continue }
                    if (target.contains("/dev/tty")) {
                        Log.i(TAG, "stock pid=${p.name} ($cmd) holds $target")
                        return target
                    }
                }
            }
        } catch (e: Exception) { Log.w(TAG, "findStockTty failed: ${e.message}") }
        Log.i(TAG, "no stock tty found")
        return null
    }

    /** Stop the stock app from grabbing the serial port.
     *  1) disable the package (clean + permanent) if our platform signature allows,
     *  2) otherwise SIGKILL its processes (allowed because we share its uid). */
    fun neutralize(ctx: Context): String {
        try {
            ctx.packageManager.setApplicationEnabledSetting(
                STOCK, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, 0)
            Log.i(TAG, "disabled $STOCK via PackageManager")
            return "disabled"
        } catch (e: Exception) {
            Log.w(TAG, "disable refused: ${e.message}")
        }
        var killed = 0
        for (p in pidDirs()) {
            if (cmdlineOf(p).startsWith(STOCK)) {
                try { android.os.Process.killProcess(p.name.toInt()); killed++ } catch (_: Exception) {}
            }
        }
        Log.i(TAG, "killed $killed stock pids")
        return "killed=$killed"
    }

    /** Re-enable the stock Lidl app (to compare its motor behaviour). */
    fun restore(ctx: Context): String {
        return try {
            ctx.packageManager.setApplicationEnabledSetting(
                STOCK, PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, 0)
            Log.i(TAG, "re-enabled $STOCK")
            "re-enabled"
        } catch (e: Exception) {
            Log.w(TAG, "re-enable failed: ${e.message}")
            "failed: ${e.message}"
        }
    }
}
