package com.devbyeai.macuisine.serial

import android.util.Log
import android_serialport_api.SerialPort
import java.io.File
import java.io.OutputStream

object SerialManager {
    const val DEVICE = "/dev/ttyMT0"
    const val BAUD = 921600

    var simulated = true
        private set

    private var port: SerialPort? = null
    private var out: OutputStream? = null

    fun start() {
        try {
            val p = SerialPort(File(DEVICE), BAUD, 0)
            port = p
            out = p.outputStream
            simulated = false
            Log.i("SerialManager", "Opened $DEVICE @ $BAUD")
        } catch (t: Throwable) {
            simulated = true
            Log.w("SerialManager", "Serial unavailable, simulating: ${t.message}")
        }
    }

    fun send(frame: ByteArray) {
        val o = out ?: return
        try { o.write(frame); o.flush() } catch (e: Exception) { Log.w("SerialManager", "send failed", e) }
    }

    fun stop() {
        try { port?.close() } catch (_: Exception) {}
        port = null; out = null
    }
}
