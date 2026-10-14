package app.filebeam

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.TextView

class ReceiveActivity : Activity() {
    private var receiver: Receiver? = null
    private var discovery: Discovery? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val root = screenColumn()
        root.addView(titleView("Receive"))
        val info: TextView = bodyView("")
        val status: TextView = bodyView("Starting…")
        val log: TextView = bodyView("")
        root.addView(info)
        root.addView(status)
        root.addView(log)
        showScreen(root)

        val r = Receiver(
            this,
            onStatus = { msg -> runOnUiThread { status.text = msg } },
            onSaved = { where ->
                runOnUiThread {
                    log.append("Saved: $where\n")
                    status.text = "Received. Waiting for more…"
                }
            }
        )
        val port = try {
            r.start()
        } catch (e: Exception) {
            status.text = "Cannot start receiver: ${e.message}"
            return
        }
        receiver = r

        val name = Build.MODEL ?: "Android"
        discovery = Discovery(this).also { it.register(name, port) }

        val ip = localIp()
        info.text = if (ip == null) {
            "Not connected to WiFi. Join the same WiFi network as the sender."
        } else {
            "This device: $name\nAddress: $ip:$port"
        }
        status.text = "Waiting for files…"
    }

    override fun onDestroy() {
        receiver?.stop()
        discovery?.stop()
        super.onDestroy()
    }
}
