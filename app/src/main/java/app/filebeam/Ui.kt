package app.filebeam

import android.app.Activity
import android.content.Context
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

fun Context.screenColumn(): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    setPadding(dp(32), dp(40), dp(32), dp(32))
}

fun Activity.showScreen(root: LinearLayout) {
    val scroll = ScrollView(this)
    scroll.isFillViewport = true
    scroll.addView(root)
    setContentView(scroll)
}

fun Context.titleView(label: String): TextView = TextView(this).apply {
    text = label
    textSize = 30f
    setPadding(0, 0, 0, dp(12))
}

fun Context.bodyView(label: String): TextView = TextView(this).apply {
    text = label
    textSize = 16f
    setPadding(0, dp(8), 0, dp(8))
}

fun Context.bigButton(label: String, onClick: () -> Unit): Button = Button(this).apply {
    text = label
    textSize = 18f
    isAllCaps = false
    layoutParams = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    ).apply { topMargin = dp(10) }
    setOnClickListener { onClick() }
}
