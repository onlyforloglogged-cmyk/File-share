package app.filebeam

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream

class Target(val stream: OutputStream, val label: String, val discard: () -> Unit)

/** Where received files go: Downloads/FileBeam on Android 10+, app Downloads folder before that. */
object Storage {
    fun open(ctx: Context, name: String): Target {
        if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues()
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/FileBeam")
            val ext = name.substringAfterLast('.', "").lowercase()
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)?.let {
                values.put(MediaStore.MediaColumns.MIME_TYPE, it)
            }
            val resolver = ctx.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IOException("Cannot create $name")
            val os = resolver.openOutputStream(uri) ?: throw IOException("Cannot open $name")
            return Target(os, "Downloads/FileBeam/$name") {
                try { resolver.delete(uri, null, null) } catch (e: Exception) {}
            }
        }
        val dir = ctx.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: ctx.filesDir
        dir.mkdirs()
        val base = name.substringBeforeLast('.')
        val ext = name.substringAfterLast('.', "")
        var f = File(dir, name)
        var i = 1
        while (f.exists()) {
            f = File(dir, if (ext.isEmpty()) "$base ($i)" else "$base ($i).$ext")
            i++
        }
        val file = f
        return Target(FileOutputStream(file), file.absolutePath) { file.delete() }
    }
}
