package nodomain.widget.buttons.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.security.MessageDigest

object WidgetIconCache {

    fun getIcon(
        context: Context,
        uriString: String,
        maxSize: Int = 96
    ): Bitmap? {
        val uri = Uri.parse(uriString)
        val key = MessageDigest.getInstance("SHA-256")
            .digest("$uriString-$maxSize".toByteArray())
            .joinToString("") { "%02x".format(it) }

        val cacheFile = File(context.cacheDir, "widget_icon_$key.png")
        if (cacheFile.exists()) {
            val cached = BitmapFactory.decodeFile(cacheFile.absolutePath)
            if (cached != null) return cached
            cacheFile.delete()
        }

        return try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            if (options.outWidth <= 0 || options.outHeight <= 0) { return null }

            options.inSampleSize = calculateSampleSize(options.outWidth, options.outHeight, maxSize)
            options.inJustDecodeBounds = false

            val bitmap = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            } ?: return null
            cacheFile.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun calculateSampleSize(
        width: Int,
        height: Int,
        maxSize: Int
    ): Int {
        var sample = 1
        while (
            width / (sample * 2) >= maxSize &&
            height / (sample * 2) >= maxSize
        ) {
            sample *= 2
        }
        return sample
    }
}