package io.nyris.camera

import android.annotation.TargetApi
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.hardware.camera2.*
import android.media.Image
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import io.nyris.camera.BaseCameraView.FACING_FRONT
import timber.log.Timber
import java.util.*


/**
 *
 *
 * @author Sidali Mellouk
 * Created by nyris GmbH
 * Copyright © 2018 nyris GmbH. All rights reserved.
 */
@TargetApi(21)
internal open class Camera2Stream(callback: Callback?, preview: PreviewImpl, val context: Context, private val resizeWidth: Int,
                                  private val resizeHeight: Int) :
        Camera2(callback, preview, context), StreamCamera {
    private lateinit var mRgbBytesArray: IntArray
    private lateinit var mRgbFrameBitmap: Bitmap

    private var listenerThread: HandlerThread? = null
    private var listenerHandler: Handler? = null
    private var imageStreamingListener: ImageStreamingListener? = null
    private var mYuvBytes : Array<ByteArray?> = arrayOfNulls(3)
    private var mProcessing = false
    private var frameToCropTransform : Matrix? = null

    private val imageListener = ImageReader.OnImageAvailableListener { reader ->
        listenerHandler?.post {
            val image = reader.acquireLatestImage() ?: return@post
            if(mProcessing){
                image.close()
                return@post
            }
            mProcessing = true
            val planes = image.planes
            fillBytes(planes, mYuvBytes)
            val yRowStride = planes[0].rowStride
            val uvRowStride = planes[1].rowStride
            val uvPixelStride = planes[1].pixelStride

            ImageUtils.convertYUV420ToARGB8888(
                    mYuvBytes[0],
                    mYuvBytes[1],
                    mYuvBytes[2],
                    mRgbBytesArray,
                    image.width,
                    image.height,
                    yRowStride,
                    uvRowStride,
                    uvPixelStride,
                    false)

            mRgbFrameBitmap.setPixels(mRgbBytesArray, 0, image.width, 0, 0, image.width, image.height)
            mProcessing = false
            image.close()

            frameToCropTransform =  ImageUtils.getTransformationMatrix(
                    mRgbFrameBitmap.width,
                    mRgbFrameBitmap.height,
                    resizeWidth,
                    resizeHeight,
                    sensorOrientation)

            val cropToFrameTransform = Matrix()
            frameToCropTransform?.invert(cropToFrameTransform)

            val croppedBitmap = Bitmap.createBitmap(resizeWidth, resizeHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(croppedBitmap)
            canvas.drawBitmap(mRgbFrameBitmap, frameToCropTransform, null)

            imageStreamingListener?.onImageReceived(croppedBitmap)
        }
    }

    // Because of the variable row stride it's not possible to know in
    // advance the actual necessary dimensions of the yuv planes.
    private fun fillBytes(planes: Array<Image.Plane>, yuvBytes: Array<ByteArray?>) {
        for (i in planes.indices) {
            val buffer = planes[i].buffer
            if (yuvBytes[i] == null) {
                yuvBytes[i] = ByteArray(buffer.capacity())
            }
            buffer.get(yuvBytes[i])
        }
    }

    override fun setImageStreamingListener(imageStreamingListener: ImageStreamingListener) {
        this.imageStreamingListener = imageStreamingListener
    }

    private val mCaptureCallback = object : CameraCaptureSession.CaptureCallback() {
        override fun onCaptureProgressed(
                session: CameraCaptureSession,
                request: CaptureRequest,
                partialResult: CaptureResult) {
        }

        override fun onCaptureCompleted(
                session: CameraCaptureSession,
                request: CaptureRequest,
                result: TotalCaptureResult) {
        }
    }

    private val mSessionCallback = object : CameraCaptureSession.StateCallback() {
        override fun onConfigured(session: CameraCaptureSession) {
            if (mCamera == null) {
                return
            }
            mCaptureSession = session
            updateAutoFocus()
            updateFlash()
            try {
                mCaptureSession.setRepeatingRequest(mPreviewRequestBuilder.build(),
                        mCaptureCallback, listenerHandler)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        override fun onConfigureFailed(session: CameraCaptureSession) {
            Timber.e("Failed to configure capture session.")
        }

        override fun onClosed(session: CameraCaptureSession) {
            if (mCaptureSession != null && mCaptureSession == session) {
                mCaptureSession = null
            }
        }
    }

    override fun start(): Boolean {
        listenerThread = HandlerThread("listenerThread")
        listenerThread?.start()
        listenerHandler = Handler(listenerThread?.looper)
        return super.start()
    }

    override fun stop() {
        super.stop()
        stopImageProcessorThread()
    }

    override fun stopPreview() {
        stopImageProcessorThread()
        super.stopPreview()
    }

    private fun stopImageProcessorThread() {
        listenerThread?.quitSafely()
        try {
            listenerThread?.join()
            listenerThread = null
            listenerHandler = null
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
    }

    override fun prepareImageReader() {
    }

    override fun setDisplayOrientation(displayOrientation: Int) {
        super.setDisplayOrientation(displayOrientation)
        if (mFacing == FACING_FRONT) {
            sensorOrientation = (sensorOrientation + displayOrientation) % 360
            sensorOrientation = (360 - sensorOrientation) % 360
        } else {
            sensorOrientation = (sensorOrientation - displayOrientation + 360) % 360
        }
    }

    override fun startCaptureSession() {
        if (!isCameraOpened || !mPreview.isReady) {
            return
        }
        val frameSize = chooseOptimalSize()
        mPreview.setBufferSize(frameSize.width, frameSize.height)
        val surface = mPreview.surface

        try {
            mPreviewRequestBuilder = mCamera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            mPreviewRequestBuilder.addTarget(surface)
            mImageReader = ImageReader.newInstance(frameSize.width, frameSize.height,
                    ImageFormat.YUV_420_888, /* maxImages */ 3)
            mImageReader.setOnImageAvailableListener(imageListener, listenerHandler)
            mPreviewRequestBuilder.addTarget(mImageReader.surface)
            mCamera.createCaptureSession(Arrays.asList(surface, mImageReader.surface),
                    mSessionCallback, null)

            mRgbBytesArray = IntArray(frameSize.width * frameSize.height)
            mRgbFrameBitmap = Bitmap.createBitmap(frameSize.width, frameSize.height, Bitmap.Config.ARGB_8888)
        } catch (e: CameraAccessException) {
            mCallback.onError("Failed to start camera session")
            return
        }
    }
}
