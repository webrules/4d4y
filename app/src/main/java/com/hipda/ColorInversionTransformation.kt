package com.hipda

import android.graphics.Bitmap
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Canvas
import android.graphics.Color
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation
import java.security.MessageDigest

class ColorInversionTransformation : BitmapTransformation() {
    override fun transform(pool: BitmapPool, toTransform: Bitmap, outWidth: Int, outHeight: Int): Bitmap {
        val output = pool.get(toTransform.width, toTransform.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        
        // Check if image has predominantly white background
        if (isPredominantlyWhite(toTransform)) {
            val paint = Paint()
            val colorMatrix = ColorMatrix(floatArrayOf(
                -1f, 0f, 0f, 0f, 255f,
                0f, -1f, 0f, 0f, 255f,
                0f, 0f, -1f, 0f, 255f,
                0f, 0f, 0f, 1f, 0f
            ))
            paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
            canvas.drawBitmap(toTransform, 0f, 0f, paint)
        } else {
            canvas.drawBitmap(toTransform, 0f, 0f, null)
        }
        
        return output
    }

    private fun isPredominantlyWhite(bitmap: Bitmap): Boolean {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        
        var whitePixels = 0
        val threshold = 240 // RGB threshold for "white-ish" colors
        
        pixels.forEach { pixel ->
            val red = Color.red(pixel)
            val green = Color.green(pixel)
            val blue = Color.blue(pixel)
            
            if (red > threshold && green > threshold && blue > threshold) {
                whitePixels++
            }
        }
        
        return whitePixels.toFloat() / pixels.size > 0.6f // If more than 60% is white
    }

    override fun updateDiskCacheKey(messageDigest: MessageDigest) {
        messageDigest.update("color_inversion_selective".toByteArray())
    }
}