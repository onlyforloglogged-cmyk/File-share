package app.filebeam

import android.app.Activity
import android.content.Intent
import android.os.Bundle

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = screenColumn()
        root.addView(titleView("FileBeam"))
        root.addView(bodyView("Install this app on both devices and join the same WiFi network. Then choose Send on one and Receive on the other."))
        val send = bigButton("Send files") {
            startActivity(Intent(this@MainActivity, SendActivity::class.java))
        }
        val receive = bigButton("Receive files") {
            startActivity(Intent(this@MainActivity, ReceiveActivity::class.java))
        }
        root.addView(send)
        root.addView(receive)
        showScreen(root)
        send.requestFocus()
    }
}
