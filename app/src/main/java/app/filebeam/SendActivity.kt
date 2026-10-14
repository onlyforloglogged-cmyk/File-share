package app.filebeam

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

class SendActivity : Activity() {
    private val reqPick = 1
    private var files: List<Transfer.PickedFile> = emptyList()
    private val peers = LinkedHashMap<String, Peer>()
    @Volatile private var busy = false
    private var discovery: Discovery? = null

    private lateinit var filesLabel: TextView
    private lateinit var status: TextView
    private lateinit var deviceList: LinearLayout
    private lateinit var addressInput: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = screenColumn()
        root.addView(titleView("Send"))

        val pick = bigButton("1. Choose files") { pickFiles() }
        root.addView(pick)
        filesLabel = bodyView("No files chosen")
        root.addView(filesLabel)

        root.addView(bodyView("2. Tap the device that is waiting to receive:"))
        deviceList = LinearLayout(this)
        deviceList.orientation = LinearLayout.VERTICAL
        root.addView(deviceList)

        root.addView(bodyView("Device not listed? Type the address shown on its Receive screen:"))
        addressInput = EditText(this)
        addressInput.hint = "192.168.1.20:48484"
        addressInput.inputType = InputType.TYPE_CLASS_TEXT
        addressInput.setSingleLine()
        root.addView(addressInput)
        root.addView(bigButton("Send to this address") { sendToTypedAddress() })

        status = bodyView("")
        root.addView(status)
        showScreen(root)
        pick.requestFocus()
        refreshDevices()

        val d = Discovery(this)
        discovery = d
        d.discover(
            onFound = { peer -> peers[peer.name] = peer; refreshDevices() },
            onLost = { name -> peers.remove(name); refreshDevices() }
        )
    }

    private fun refreshDevices() {
        deviceList.removeAllViews()
        if (peers.isEmpty()) {
            deviceList.addView(bodyView("Searching…"))
            return
        }
        for (peer in peers.values) {
            deviceList.addView(bigButton("${peer.name}  (${peer.host})") { sendTo(peer.host, peer.port) })
        }
    }

    private fun pickFiles() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
        i.addCategory(Intent.CATEGORY_OPENABLE)
        i.type = "*/*"
        i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        try {
            startActivityForResult(i, reqPick)
        } catch (e: ActivityNotFoundException) {
            status.text = "This device has no file picker app."
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != reqPick || resultCode != RESULT_OK || data == null) return
        val uris = mutableListOf<Uri>()
        val clip = data.clipData
        if (clip != null) {
            for (k in 0 until clip.itemCount) uris.add(clip.getItemAt(k).uri)
        } else {
            data.data?.let { uris.add(it) }
        }
        val picked = uris.mapNotNull { describe(it) }
        files = picked
        filesLabel.text = if (picked.isEmpty()) "No readable files chosen" else "${picked.size} file(s) chosen"
    }

    private fun describe(uri: Uri): Transfer.PickedFile? {
        var name = "file"
        var size = -1L
        contentResolver.query(uri, null, null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val ni = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (ni >= 0) name = c.getString(ni) ?: name
                val si = c.getColumnIndex(OpenableColumns.SIZE)
                if (si >= 0 && !c.isNull(si)) size = c.getLong(si)
            }
        }
        if (size < 0) {
            size = contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
        }
        return if (size >= 0) Transfer.PickedFile(uri, name, size) else null
    }

    private fun sendToTypedAddress() {
        val text = addressInput.text.toString().trim()
        if (text.isEmpty()) {
            status.text = "Type the receiver's address first."
            return
        }
        val host = text.substringBefore(':')
        val port = if (text.contains(':')) text.substringAfter(':').toIntOrNull() else Transfer.PORT
        if (port == null) {
            status.text = "Port must be a number."
            return
        }
        sendTo(host, port)
    }

    private fun sendTo(host: String, port: Int) {
        if (files.isEmpty()) {
            status.text = "Choose files first."
            return
        }
        if (busy) return
        busy = true
        val toSend = files
        Thread {
            try {
                Transfer.send(applicationContext, host, port, toSend) { msg ->
                    runOnUiThread { status.text = msg }
                }
                runOnUiThread { status.text = "Sent ${toSend.size} file(s)." }
            } catch (e: Exception) {
                runOnUiThread { status.text = "Failed: ${e.message}" }
            } finally {
                busy = false
            }
        }.start()
    }

    override fun onDestroy() {
        discovery?.stop()
        super.onDestroy()
    }
}
