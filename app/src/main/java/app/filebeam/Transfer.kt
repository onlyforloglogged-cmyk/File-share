package app.filebeam

import android.content.Context
import android.net.Uri
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.IOException
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket

/**
 * Wire format (all big-endian, via DataOutputStream):
 *   int MAGIC, int fileCount,
 *   per file: UTF name, long size, <size> raw bytes
 * Receiver answers with int 1 after the last file is saved.
 * There is no encryption or authentication: use trusted networks only.
 */
object Transfer {
    const val PORT = 48484
    internal const val MAGIC = 0x46425431 // "FBT1"
    internal const val BUF = 64 * 1024

    class PickedFile(val uri: Uri, val name: String, val size: Long)

    internal fun pct(done: Long, total: Long): Int =
        if (total <= 0) 100 else (done * 100 / total).toInt()

    fun send(ctx: Context, host: String, port: Int, files: List<PickedFile>, progress: (String) -> Unit) {
        Socket().use { socket ->
            progress("Connecting to $host…")
            socket.connect(InetSocketAddress(host, port), 5000)
            socket.soTimeout = 30000
            val out = DataOutputStream(BufferedOutputStream(socket.getOutputStream(), BUF))
            val inp = DataInputStream(socket.getInputStream())
            out.writeInt(MAGIC)
            out.writeInt(files.size)
            val buf = ByteArray(BUF)
            for ((index, f) in files.withIndex()) {
                out.writeUTF(f.name)
                out.writeLong(f.size)
                var sent = 0L
                var lastPct = -1
                val input = ctx.contentResolver.openInputStream(f.uri)
                    ?: throw IOException("Cannot open ${f.name}")
                input.use {
                    while (true) {
                        val n = it.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        sent += n
                        val p = pct(sent, f.size)
                        if (p != lastPct) {
                            lastPct = p
                            progress("Sending ${index + 1}/${files.size}: ${f.name} ($p%)")
                        }
                    }
                }
                if (sent != f.size) throw IOException("${f.name} changed while sending")
            }
            out.flush()
            if (inp.readInt() != 1) throw IOException("Receiver reported an error")
        }
    }
}

class Receiver(
    context: Context,
    private val onStatus: (String) -> Unit,
    private val onSaved: (String) -> Unit
) {
    private val ctx = context.applicationContext
    @Volatile private var running = false
    private var server: ServerSocket? = null

    /** Starts listening and returns the port in use. */
    fun start(): Int {
        val s = try { ServerSocket(Transfer.PORT) } catch (e: IOException) { ServerSocket(0) }
        server = s
        running = true
        Thread { loop(s) }.start()
        return s.localPort
    }

    fun stop() {
        running = false
        try { server?.close() } catch (e: IOException) {}
    }

    private fun loop(s: ServerSocket) {
        while (running && !s.isClosed) {
            try {
                s.accept().use { handle(it) }
            } catch (e: Exception) {
                if (running && !s.isClosed) onStatus("Transfer failed: ${e.message}")
            }
        }
    }

    private fun handle(sock: Socket) {
        sock.soTimeout = 30000
        val inp = DataInputStream(BufferedInputStream(sock.getInputStream(), Transfer.BUF))
        val out = DataOutputStream(sock.getOutputStream())
        if (inp.readInt() != Transfer.MAGIC) throw IOException("Not a FileBeam sender")
        val count = inp.readInt()
        if (count < 1 || count > 10_000) throw IOException("Bad file count")
        val buf = ByteArray(Transfer.BUF)
        for (i in 1..count) {
            val name = cleanName(inp.readUTF())
            val size = inp.readLong()
            if (size < 0) throw IOException("Bad file size")
            val target = Storage.open(ctx, name)
            var ok = false
            try {
                var left = size
                var lastPct = -1
                while (left > 0) {
                    val n = inp.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
                    if (n < 0) throw EOFException("Connection closed early")
                    target.stream.write(buf, 0, n)
                    left -= n
                    val p = Transfer.pct(size - left, size)
                    if (p != lastPct) {
                        lastPct = p
                        onStatus("Receiving $i/$count: $name ($p%)")
                    }
                }
                ok = true
            } finally {
                try { target.stream.close() } catch (e: IOException) {}
                if (!ok) target.discard()
            }
            onSaved(target.label)
        }
        out.writeInt(1)
        out.flush()
    }

    private fun cleanName(raw: String): String {
        val n = raw.substringAfterLast('/').substringAfterLast('\\').trim()
        return if (n.isEmpty() || n == "." || n == "..") "file" else n
    }
}
