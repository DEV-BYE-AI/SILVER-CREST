package com.devbyeai.macuisine.serial

import android.util.Log
import android_serialport_api.SerialPort
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/** Opens /dev/ttyMT0, STREAMS the current command frame every ~250 ms
 *  (like the stock onPerformPeriodicHardwareAction), and LOGS the board's
 *  replies so we can diagnose why the motor does / doesn't run. */
object SerialManager {
    const val DEVICE = "/dev/ttyMT0"
    const val BAUD = 921600
    const val INTERVAL_MS = 250L

    @Volatile var simulated = true
        private set

    @Volatile private var currentFrame: ByteArray = PowerBoard.OFF
    @Volatile private var lastTxLogged: String = ""
    private var port: SerialPort? = null
    private var out: OutputStream? = null
    private var inp: InputStream? = null
    private var streamer: Thread? = null
    private var reader: Thread? = null

    @Synchronized
    fun start() {
        if (streamer != null) return
        try {
            val p = SerialPort(File(DEVICE), BAUD, 0)
            port = p
            out = p.outputStream
            inp = p.inputStream
            simulated = false
            Log.i("SerialManager", "Opened $DEVICE @ $BAUD")
        } catch (t: Throwable) {
            simulated = true
            Log.w("SerialManager", "Serial unavailable, simulating: ${t.message}")
        }

        streamer = Thread {
            while (!Thread.currentThread().isInterrupted) {
                val o = out
                if (o != null) {
                    try {
                        val f = currentFrame
                        o.write(f); o.flush()
                        val hex = PowerBoard.hex(f)
                        if (hex != lastTxLogged) { Log.i("SerialTX", hex); lastTxLogged = hex }
                    } catch (e: Exception) { Log.w("SerialManager", "tx failed", e) }
                }
                try { Thread.sleep(INTERVAL_MS) } catch (e: InterruptedException) { break }
            }
        }.apply { isDaemon = true; start() }

        reader = Thread {
            val buf = ByteArray(1024)
            var lastLog = 0L
            val i = inp
            while (i != null && !Thread.currentThread().isInterrupted) {
                try {
                    val n = i.read(buf)
                    val now = System.currentTimeMillis()
                    if (n > 0 && now - lastLog > 500) {           // rate-limit: ~2/s
                        val take = if (n > 32) 32 else n
                        val sb = StringBuilder()
                        for (k in 0 until take) sb.append(String.format("%02X ", buf[k].toInt() and 0xFF))
                        Log.i("SerialRX", "n=$n " + sb.toString().trim())
                        lastLog = now
                    }
                } catch (e: Exception) { break }
            }
        }.apply { isDaemon = true; start() }
    }

    fun setFrame(frame: ByteArray) { currentFrame = frame }
    fun allOff() { currentFrame = PowerBoard.OFF }

    @Synchronized
    fun stop() {
        allOff()
        streamer?.interrupt(); streamer = null
        reader?.interrupt(); reader = null
        try { port?.close() } catch (_: Exception) {}
        port = null; out = null; inp = null
    }
}
