package io.nyris.camera

import android.graphics.Bitmap

interface ImageStreamingListener{
    fun onImageReceived(bitmap: Bitmap)
}
