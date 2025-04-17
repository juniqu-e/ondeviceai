package com.ondevice.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.IOException
import java.io.InputStream

object BitmapUtils {
    /**
     * EXIF 정보에 따라 이미지를 적절하게 회전시킵니다.
     */
    private fun rotateBitmapIfNeeded(bitmap: Bitmap?, inputStream: InputStream): Bitmap? {
        if (bitmap == null) return null

        try {
            val exif = ExifInterface(inputStream)
            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )

            val matrix = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)
                else -> return bitmap
            }

            return Bitmap.createBitmap(
                bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
            )
        } catch (e: IOException) {
            e.printStackTrace()
            return bitmap
        }
    }

    /**
     * Uri에서 Bitmap을 로드합니다.
     * EXIF 정보를 기반으로 이미지를 올바르게 회전시킵니다.
     */
    fun loadBitmapFromUri(context: Context, uri: Uri): Bitmap? {
        try {
            // 이미지 로드
            val inputStream = context.contentResolver.openInputStream(uri)
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }

            BitmapFactory.decodeStream(inputStream, null, options)
            inputStream?.close()

            // 적절한 샘플 크기 계산 (메모리 효율성)
            val sampleSize = calculateInSampleSize(options, 1024, 1024)
            options.apply {
                inJustDecodeBounds = false
                inSampleSize = sampleSize
            }

            // 이미지 다시 로드
            val secondInputStream = context.contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(secondInputStream, null, options)
            secondInputStream?.close()

            // EXIF 정보에 따라 이미지 회전
            val exifInputStream = context.contentResolver.openInputStream(uri)
            val rotatedBitmap = exifInputStream?.use { stream ->
                rotateBitmapIfNeeded(bitmap, stream)
            } ?: bitmap

            return rotatedBitmap

        } catch (e: IOException) {
            e.printStackTrace()
            return null
        }
    }

    /**
     * 적절한 샘플 크기를 계산합니다 (메모리 효율을 위함).
     */
    private fun calculateInSampleSize(
        options: BitmapFactory.Options,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2

            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }

        return inSampleSize
    }
}