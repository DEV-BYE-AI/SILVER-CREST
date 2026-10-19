package com.devbyeai.macuisine.serial

import android.content.Context
import android.util.Log
import android_serialport_api.SerialPort
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/** Finds the real power-board UART from the stock app, neutralizes the stock
 *  service so nothing else writes to it, opens that port, STREAMS the current
 *  command frame every ~250 ms, and LOGS the board's replies. */
object SerialManager {
    const val BAUD = 921600
    const val INTERVAL_MS = 250L
    const val FALLBACK = "/dev/ttyMT0"

    @Volatile var device = FALLBACK
        private set
    @Volatile var note = ""
        private set
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
    fun start(ctx: Context) {
        if (streamer != null) return

        // 1) learn the real port from the stock service BEFORE we stop it
        val stockPort = StockProbe.findStockTty()
        // 2) stop the stock service fighting us for that port
        val action = StockProbe.neutralize(ctx)
        // 3) pick the discovered port (fallback to ttyMT0)
        device = stockPort ?: FALLBACK
        note = "port=$device (${if (stockPort != null) "from stock" else "fallback"}), stock=$action"
        Log.i("SerialManager", note)
        // give the stock process a moment to die and release the fd
        try { Thread.sleep(400) } catch (_: Exception) {}

        try {
            val p = SerialPort(File(device), BAUD, 0)
            port = p
            out = p.outputStream
            inp = p.inputStream
            simulated = false
            Log.i("SerialManager", "Opened $device @ $BAUD")
        } catch (t: Throwable) {
            simulated = true
            Log.w("SerialManager", "Serial unavailable on $device, simulating: ${t.message}")
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
