package android_serialport_api

import java.io.File
import java.io.FileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.IOException
import java.io.OutputStream

/** Thin wrapper over libserial_port.so (reused from the stock app). */
class SerialPort(device: File, baudrate: Int, flags: Int) {

    private val fd: FileDescriptor =
        open(device.absolutePath, baudrate, flags) ?: throw IOException("Cannot open ${device.absolutePath}")

    val inputStream: InputStream = FileInputStream(fd)
    val outputStream: OutputStream = FileOutputStream(fd)

    external fun open(path: String, baudrate: Int, flags: Int): FileDescriptor?
    external fun close()

    companion object {
        init { System.loadLibrary("serial_port") }
    }
}
