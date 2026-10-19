package com.devbyeai.macuisine.serial

import android.util.Log
import android_serialport_api.SerialPort
import java.io.File
import java.io.OutputStream

/** Opens /dev/ttyMT0 and STREAMS the current command frame continuously
 *  (every ~250 ms), like the stock app's onPerformPeriodicHardwareAction().
 *  The power board stops the motor if commands stop arriving. */
object SerialManager {
    const val DEVICE = "/dev/ttyMT0"
    const val BAUD = 921600
    const val INTERVAL_MS = 250L

    @Volatile var simulated = true
        private set

    @Volatile private var currentFrame: ByteArray = PowerBoard.OFF
    private var port: SerialPort? = null
    private var out: OutputStream? = null
    private var streamer: Thread? = null

    @Synchronized
    fun start() {
        if (streamer != null) return
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
        streamer = Thread {
            while (!Thread.currentThread().isInterrupted) {
                val o = out
                if (o != null) {
                    try { o.write(currentFrame); o.flush() }
                    catch (e: Exception) { Log.w("SerialManager", "stream write failed", e) }
                }
                try { Thread.sleep(INTERVAL_MS) } catch (e: InterruptedException) { break }
            }
        }.apply { isDaemon = true; start() }
    }

    /** Set the frame that is streamed continuously to the board. */
    fun setFrame(frame: ByteArray) { currentFrame = frame }

    /** Immediately go to the all-off frame. */
    fun allOff() { currentFrame = PowerBoard.OFF }

    @Synchronized
    fun stop() {
        allOff()
        streamer?.interrupt()
        streamer = null
        try { port?.close() } catch (_: Exception) {}
        port = null; out = null
    }
}
