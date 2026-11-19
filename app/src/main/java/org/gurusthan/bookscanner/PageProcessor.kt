package org.gurusthan.bookscanner
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import kotlin.math.max
import kotlin.math.min
/** Experimental mild gutter compensation, not full 3D dewarping. */
object PageProcessor {
    fun split(original: Bitmap): Pair<Bitmap, Bitmap> {
        val w = original.width
        val h = original.height
        val mid = w / 2
        val left = Bitmap.createBitmap(original, 0, 0, mid, h)
        val right = Bitmap.createBitmap(original, mid, 0, w - mid, h)
        return correctGutter(left, true) to correctGutter(right, false)
    }
    private fun correctGutter(source: Bitmap, gutterOnRight: Boolean): Bitmap {
        val w = source.width
        val h = source.height
        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawColor(Color.WHITE)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        val strip = max(1, w / 160)
        for (x in 0 until w step strip) {
            val end = min(w, x + strip)
            val nearGutter = if (gutterOnRight) x.toFloat() / w else (w - x).toFloat() / w
            val distortion = if (nearGutter > 0.72f) (nearGutter - 0.72f) / 0.28f else 0f
            val shift = distortion * distortion * h * 0.008f
            val src = android.graphics.Rect(x, 0, end, h)
            val dst = android.graphics.RectF(x.toFloat(), shift, end.toFloat(), h.toFloat() - shift)
            canvas.drawBitmap(source, src, dst, paint)
        }
        return result
    }
}
