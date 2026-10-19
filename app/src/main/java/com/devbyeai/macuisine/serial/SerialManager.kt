package com.devbyeai.macuisine.serial

import android.content.Context
import android.util.Log
import android_serialport_api.SerialPort
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/** Two-layer design:
 *  - The stock Lidl app stays installed and enabled (layer 1, the hardware).
 *  - This app is layer 2 (the UI). By default it is DISPLAY-ONLY: it reads the
 *    board telemetry but does NOT send commands, so it coexists with the Lidl
 *    app without fighting over the port.
 *  - Only when the user explicitly drives (takeControl) does it neutralize the
 *    stock service and start sending command frames.
 *  Everything is wrapped defensively so the UI can never crash on serial errors. */
object SerialManager {
    const val BAUD = 4800
    const val INTERVAL_MS = 100L
    const val FALLBACK = "/dev/ttyMT0"

    @Volatile var device = FALLBACK
        private set
    @Volatile var note = ""
        private set
    @Volatile var simulated = true
        private set
    @Volatile var driving = false
        private set

    @Volatile private var currentFrame: ByteArray = PowerBoard.OFF
    @Volatile private var kickFrame: ByteArray? = null
    @Volatile private var kickUntil = 0L
    @Volatile private var lastTxLogged: String = ""

    /** Kick-start: stream [kick] (full-power turbo) for [ms] to get the motor
     *  spinning, then fall back to [target] (the requested speed). Fixes motors
     *  that lack the torque to start at low variable speed. */
    fun kickStart(target: ByteArray, kick: ByteArray, ms: Long) {
        currentFrame = target
        kickFrame = kick
        kickUntil = System.currentTimeMillis() + ms
    }
    private var port: SerialPort? = null
    private var out: OutputStream? = null
    private var inp: InputStream? = null
    private var streamer: Thread? = null
    private var reader: Thread? = null

    @Synchronized
    fun start(ctx: Context) {
        if (streamer != null) return
        try {
            val stockPort = StockProbe.findStockTty()
            device = stockPort ?: FALLBACK
            note = "port=$device (${if (stockPort != null) "depuis Lidl" else "défaut"})"
            Log.i("SerialManager", note)
            try {
                val p = SerialPort(File(device), BAUD, 0)
                port = p
                out = p.outputStream
                inp = p.inputStream
                simulated = false
                Log.i("SerialManager", "Opened $device @ $BAUD")
            } catch (t: Throwable) {
                simulated = true
                Log.w("SerialManager", "Serial unavailable on $device, display only: ${t.message}")
            }
        } catch (t: Throwable) {
            simulated = true
            Log.w("SerialManager", "start failed, display only: ${t.message}")
        }

        streamer = Thread {
            while (!Thread.currentThread().isInterrupted) {
                val o = out
                // Only transmit while actively driving — otherwise stay silent so
                // the stock Lidl service keeps control of the board.
                if (o != null && driving) {
                    try {
                        val f = if (System.currentTimeMillis() < kickUntil) (kickFrame ?: currentFrame) else currentFrame
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
                    if (n > 0 && now - lastLog > 500) {
                        var p = -1
                        for (k in 0..(n - 27)) {
                            if ((buf[k].toInt() and 0xFF) == 0x55 && (buf[k+1].toInt() and 0xFF) == 0x1B
                                && (buf[k+2].toInt() and 0xFF) == 0xB1) { p = k; break }
                        }
                        if (p >= 0) {
                            val s = buf[p+3].toInt() and 0xFF
                            val rpm = ((buf[p+9].toInt() and 0xFF) shl 8) or (buf[p+10].toInt() and 0xFF)
                            Log.i("SerialStat", "rpm=$rpm coverOpen=${(s and 0x80)!=0} err=${(s and 0x10)!=0}")
                        }
                        lastLog = now
                    }
                } catch (e: Exception) { break }
            }
        }.apply { isDaemon = true; start() }
    }

    /** Take the board away from the stock service to drive it from here. */
    fun takeControl(ctx: Context) {
        try { StockProbe.neutralize(ctx) } catch (_: Exception) {}
        driving = true
    }

    /** Hand the board back to the stock service. */
    fun releaseControl() {
        driving = false
        currentFrame = PowerBoard.OFF
    }

    fun setFrame(frame: ByteArray) { currentFrame = frame }
    fun allOff() { currentFrame = PowerBoard.OFF }

    @Synchronized
    fun stop() {
        currentFrame = PowerBoard.OFF
        driving = false
        streamer?.interrupt(); streamer = null
        reader?.interrupt(); reader = null
        try { port?.close() } catch (_: Exception) {}
        port = null; out = null; inp = null
    }
}
