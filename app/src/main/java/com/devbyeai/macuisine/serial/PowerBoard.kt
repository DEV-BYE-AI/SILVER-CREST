package com.devbyeai.macuisine.serial

/** Frame builder for the Monsieur Cuisine power board (reverse engineered).
 *  TX frame = 15 bytes: 55 0F A1 <cmd> <flag> <speed> <temp> 00 00 00 00 <dir> <scale> <cksum> AA
 *  speed 0..10, temp level 0..18, cksum = sum(bytes[0..12]) & 0xFF.
 *  Command bytes (payload[1]) decoded from PowerBoard$2 remap table. */
object PowerBoard {

    // Work-command bytes written at frame[3] (confirmed from the switch map)
    const val CMD_MOTOR_ON_HEATER_ON = 0x0F   // motor (+ heat if temp>0)
    const val CMD_ALL_OFF            = 0x05
    const val CMD_HEATER_ONLY        = 0x04
    const val CMD_TURBO              = 0x03

    fun frame(cmd: Int, speed: Int, tempLevel: Int, reverse: Boolean, turbo: Boolean = false, scale: Int = 0x00): ByteArray {
        val sp = speed.coerceIn(0, 10)
        val tp = tempLevel.coerceIn(0, 18)
        val body = intArrayOf(
            0x55, 0x0F, 0xA1, cmd and 0xFF,
            if (turbo) 0xA9 else 0x00,
            sp, tp, 0, 0, 0, 0,
            if (reverse) 1 else 0,
            scale and 0xFF
        )
        var sum = 0
        for (b in body) sum = (sum + b) and 0xFF
        val out = ByteArray(15)
        for (i in body.indices) out[i] = body[i].toByte()
        out[13] = sum.toByte()
        out[14] = 0xAA.toByte()
        return out
    }

    /** Pick the work command. Heat is governed by the temp level, so motor-without-heat
     *  uses MOTOR_ON_HEATER_ON with tempLevel = 0. */
    fun autoCmd(speed: Int, tempLevel: Int, turbo: Boolean): Int = when {
        turbo                        -> CMD_TURBO
        speed > 0                    -> CMD_MOTOR_ON_HEATER_ON
        speed == 0 && tempLevel > 0  -> CMD_HEATER_ONLY
        else                         -> CMD_ALL_OFF
    }

    val OFF: ByteArray get() = frame(CMD_ALL_OFF, 0, 0, false)

    fun hex(frame: ByteArray): String =
        frame.joinToString(" ") { String.format("%02X", it.toInt() and 0xFF) }
}
