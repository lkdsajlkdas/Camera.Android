package io.nyris.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Environment
import android.preference.PreferenceManager
import java.io.ByteArrayOutputStream
import java.io.File

/**
 *
 *
 * @author Sidali Mellouk
 * Created by nyris GmbH
 * Copyright © 2018 nyris GmbH. All rights reserved.
 */
internal class ImageHelper{
    companion object {
        fun rotateBitmap(image : ByteArray): ByteArray {
            val bitmap = BitmapFactory.decodeByteArray(image, 0, image.size)
            val matrix = Matrix()
            matrix.setRotate(Exif.getOrientation(image))
            val bmp = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            val stream = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.JPEG, 100, stream)
            return stream.toByteArray()
        }

        fun resize(context: Context, data: ByteArray, width: Int, height: Int): ByteArray {
            var bitmap = BitmapFactory.decodeByteArray(data, 0, data.size)
            // Create and rotate the bitmap by rotationDegrees
            val matrix = Matrix()
            try {
                val orientation = Exif.getOrientation(data)
                matrix.postRotate(orientation)
            } catch (e: Exception) {
                e.printStackTrace()
            }

            bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)

            // calculate the new image size preserving the aspect ratio
            val originalHeight = bitmap.height
            val originalWidth = bitmap.width
            val proportionalScaleSize = calculateImageSizePreservingAspectRatio(context, Size(width, height), originalWidth, originalHeight)
            val scaledImage = Bitmap.createScaledBitmap(bitmap, proportionalScaleSize.width, proportionalScaleSize.height, true)

            return compressAndTransformToBytes(scaledImage)
        }

        fun compressAndTransformToBytes(scaledDownImage: Bitmap): ByteArray {
            val stream = ByteArrayOutputStream()
            scaledDownImage.compress(Bitmap.CompressFormat.JPEG, 100, stream)
            return stream.toByteArray()
        }

        fun getPhotoFileUri(context: Context, fileName: String): Uri? {
            if (isExternalStorageAvailable()) {
                val mediaStorageDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
                return Uri.fromFile(File(mediaStorageDir!!.path + File.separator + fileName))
            }
            return null
        }

        /*** Private **/
        private fun calculateImageSizePreservingAspectRatio(context: Context, size: Size, originalWidth: Int, originalHeight: Int): Size {
            val percentWidth = size.width / originalWidth.toFloat()
            val percentHeight = size.height / originalHeight.toFloat()

            val percent = if (percentHeight < percentWidth) percentHeight else percentWidth
            val newWidth = (originalWidth * percent).toInt()
            val newHeight = (originalHeight * percent).toInt()
            saveParam(context, "current_ratio", percent.toString() + "")
            return Size(newWidth, newHeight)
        }

        private fun saveParam(context: Context, key: String, value: String) {
            val mPrefs = PreferenceManager.getDefaultSharedPreferences(context)
            val editor = mPrefs.edit()
            editor.putString(key, value)
            editor.apply()
        }

        private fun getParam(context: Context, key: String): String {
            val mPrefs = PreferenceManager.getDefaultSharedPreferences(context)
            return mPrefs.getString(key, "")
        }

        private fun isExternalStorageAvailable(): Boolean {
            val state = Environment.getExternalStorageState()
            return state == Environment.MEDIA_MOUNTED
        }
    }
}