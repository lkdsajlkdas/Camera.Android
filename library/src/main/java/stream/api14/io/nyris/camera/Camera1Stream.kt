package io.nyris.camera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.hardware.Camera
import java.io.ByteArrayInputStream

@Suppress("DEPRECATION")
internal class Camera1Stream(callback: Callback?, preview: PreviewImpl, private val resizeWidth: Int,
                             private val resizeHeight: Int) :
        Camera1(callback, preview), Camera.PreviewCallback, StreamCamera {
    private var imageStreamingListener: ImageStreamingListener? = null
    private var frameToCropTransform : Matrix? = null

    override fun setImageStreamingListener(imageStreamingListener: ImageStreamingListener) {
        this.imageStreamingListener = imageStreamingListener
    }

    override fun onPreviewFrame(data: ByteArray, camera: Camera) {
        Thread(Runnable {
            val arrayInputStream = ByteArrayInputStream(data)
            val bitmap = BitmapFactory.decodeStream(arrayInputStream)

            frameToCropTransform?.let {
                frameToCropTransform =  ImageUtils.getTransformationMatrix(
                    bitmap.width,
                    bitmap.height,
                    resizeWidth,
                    resizeHeight,
                    mCameraRotation)

                val cropToFrameTransform = Matrix()
                it.invert(cropToFrameTransform)
            }


            val croppedBitmap = Bitmap.createBitmap(resizeWidth, resizeHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(croppedBitmap)
            canvas.drawBitmap(bitmap, frameToCropTransform, null)

            imageStreamingListener?.onImageReceived(croppedBitmap)
            mCamera.setOneShotPreviewCallback(this)
        }).start()
    }

    override fun adjustCameraParameters() {
        super.adjustCameraParameters()
        mCamera.setOneShotPreviewCallback(this)

        if (mShowingPreview) {
            mCamera.startPreview()
        }
    }
}
