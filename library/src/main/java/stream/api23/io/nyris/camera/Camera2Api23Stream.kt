package io.nyris.camera

import android.annotation.TargetApi
import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.params.StreamConfigurationMap

@TargetApi(23)
internal class Camera2Api23Stream(callback: CameraViewImpl.Callback, preview: PreviewImpl, context: Context, resizeWidth: Int,
                                  resizeHeight: Int) : Camera2Stream(callback, preview, context, resizeWidth, resizeHeight) {

    override fun collectPictureSizes(sizes: SizeMap, map: StreamConfigurationMap) {
        // Try to get hi-res output sizes
        val outputSizes = map.getHighResolutionOutputSizes(ImageFormat.JPEG)
        if (outputSizes != null) {
            for (size in map.getHighResolutionOutputSizes(ImageFormat.JPEG)) {
                sizes.add(Size(size.width, size.height))
            }
        }
        if (sizes.isEmpty) {
            super.collectPictureSizes(sizes, map)
        }
    }
}
