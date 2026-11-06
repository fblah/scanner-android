package com.fblah.scan

import android.graphics.*
import java.io.File

object Images {
    fun decode(path: String): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 3000) sample *= 2
        val bitmap = requireNotNull(BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample }))
        val exif = androidx.exifinterface.media.ExifInterface(path)
        val transform = Matrix().apply { postRotate(exif.rotationDegrees.toFloat()); if(exif.isFlipped) postScale(-1f,1f) }
        val oriented = Bitmap.createBitmap(bitmap,0,0,bitmap.width,bitmap.height,transform,true)
        if(oriented !== bitmap)bitmap.recycle()
        return oriented
    }
    fun render(path: String, filter: String, rotation: Int): String {
        val source = decode(path)
        val result = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val paint = Paint()
        if (filter != "Original") {
            val matrix = ColorMatrix().apply { setSaturation(if (filter == "Magic") 1f else 0f) }
            if (filter == "Magic" || filter == "B&W") {
                val contrast = if (filter == "B&W") 8f else 1.25f
                matrix.postConcat(ColorMatrix(floatArrayOf(contrast,0f,0f,0f,-128f*(contrast-1),0f,contrast,0f,0f,-128f*(contrast-1),0f,0f,contrast,0f,-128f*(contrast-1),0f,0f,0f,1f,0f)))
            }
            paint.colorFilter = ColorMatrixColorFilter(matrix)
        }
        Canvas(result).apply { drawColor(Color.WHITE); drawBitmap(source,0f,0f,paint) }
        val rotated = Bitmap.createBitmap(result,0,0,result.width,result.height,Matrix().apply { postRotate(rotation.toFloat()) },true)
        val out = File(File(path).parentFile, "${java.util.UUID.randomUUID()}.jpg")
        File(out.path+".source").writeText(Scanner.source(path))
        out.outputStream().use { rotated.compress(Bitmap.CompressFormat.JPEG,90,it) }
        source.recycle(); if (rotated !== result) result.recycle(); rotated.recycle()
        return out.path
    }
}
