package com.hayatayubi.shelf

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb

/**
 * Bakes an app's whole Top Shelf background — artwork wash, colour glows and the
 * legibility gradients — into one small bitmap, off the main thread.
 *
 * On screen it's stretched to 1080p with bilinear filtering, which keeps it soft, and
 * costs a single full-screen draw. The Chromecast's GPU can't afford layering half a
 * dozen live full-screen gradients every frame.
 */
object Backdrop {
    private const val W = 320
    private const val H = 180

    val idle: Bitmap by lazy { render(IdleColors, null) }

    fun render(colors: AppColors, banner: Bitmap?): Bitmap {
        val bitmap = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val full = RectF(0f, 0f, W.toFloat(), H.toFloat())
        canvas.drawColor(lerp(colors.deep, Color.Black, 0.7f).toArgb())

        if (banner != null) {
            // Shrinking to 16×9 first is what blurs it; drawing straight from the banner would stay sharp.
            val wash = Bitmap.createScaledBitmap(banner, 16, 9, true)
            canvas.drawBitmap(wash, null, full, Paint(Paint.FILTER_BITMAP_FLAG).apply { alpha = 178 })
        }

        glow(canvas, colors.deep, 0.2f, 0.15f, 0.75f, 1f)
        glow(canvas, colors.glow, 0.78f, 0.12f, 0.5f, 0.55f)
        glow(canvas, lerp(colors.glow, colors.deep, 0.5f), 0.5f, 0.55f, 0.45f, 0.45f)

        val shade = Paint()
        shade.shader = LinearGradient(0f, 0f, W * 0.6f, 0f, argb(0.55f), 0, Shader.TileMode.CLAMP)
        canvas.drawRect(full, shade)
        shade.shader = LinearGradient(
            0f, 0f, 0f, H.toFloat(),
            intArrayOf(0, argb(0.2f), argb(0.9f)),
            floatArrayOf(0f, 0.45f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(full, shade)
        return bitmap
    }

    private fun glow(canvas: Canvas, color: Color, x: Float, y: Float, radius: Float, alpha: Float) {
        val center = color.copy(alpha = alpha).toArgb()
        val r = W * radius
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = RadialGradient(W * x, H * y, r, center, center and 0x00FFFFFF, Shader.TileMode.CLAMP)
        canvas.drawCircle(W * x, H * y, r, paint)
    }

    private fun argb(blackAlpha: Float) = Color.Black.copy(alpha = blackAlpha).toArgb()
}
