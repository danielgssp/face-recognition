package com.facerecognition.facerecognition

import android.content.Context
import android.content.DialogInterface
import android.content.res.Configuration
import android.hardware.Camera
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.appcompat.app.AlertDialog

class Preview(var mContext: Context, var mDraw: ProcessImageAndDrawResults?) :
    SurfaceView(mContext), SurfaceHolder.Callback {
    var mHolder: SurfaceHolder? = null
    var mCamera: Camera? = null
    var mFinished: Boolean = false
    var mIsCameraOpen = false

    var mIsPreviewStarted = false

    init {

        //Install a SurfaceHolder.Callback so we get notified when the underlying surface is created and destroyed.
        mHolder = holder
        mHolder?.addCallback(this)
        mHolder?.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS)
    }

    //SurfaceView callback
    override fun surfaceCreated(holder: SurfaceHolder) {
        if (mIsCameraOpen) return  // surfaceCreated can be called several times
        mIsCameraOpen = true

        mFinished = false

        // Find the ID of the camera
        var cameraId = 0
        var frontCameraFound = false
        val cameraInfo = Camera.CameraInfo()
        for (i in 0 until Camera.getNumberOfCameras()) {
            Camera.getCameraInfo(i, cameraInfo)
            //if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_BACK)
            if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                cameraId = i
                frontCameraFound = true
            }
        }

        mCamera = if (frontCameraFound) {
            Camera.open(cameraId)
        } else {
            Camera.open()
        }

        try {
            mCamera?.setPreviewDisplay(holder)

            // Preview callback used whenever new viewfinder frame is available
            mCamera?.setPreviewCallback(Camera.PreviewCallback { data, camera ->
                if (mDraw == null || mFinished)
                    return@PreviewCallback

                if (mDraw?.mYUVData == null) {
                    // Initialize the draw-on-top companion
                    val params = camera.parameters
                    mDraw?.mImageWidth =  params.previewSize.width
                    mDraw?.mImageHeight =  params.previewSize.height
                    mDraw?.mRGBData = ByteArray(3 * (mDraw?.mImageWidth ?: 0) * (mDraw?.mImageHeight ?: 0))
                    mDraw?.mYUVData = ByteArray(data.size)
                }

                // Pass YUV data to draw-on-top companion
                System.arraycopy(data, 0, mDraw?.mYUVData, 0, data.size)
                mDraw?.invalidate()
            })
        } catch (exception: Exception) {
            val builder = AlertDialog.Builder(mContext)
            builder.setMessage("Cannot open camera")
                .setPositiveButton("Ok",
                    DialogInterface.OnClickListener { dialogInterface, i ->
                        android.os.Process.killProcess(
                            android.os.Process.myPid()
                        )
                    })
                .show()
            if (mCamera != null) {
                mCamera?.release()
                mCamera = null
            }
        }

    }


    fun releaseCallbacks() {
        if (mCamera != null) {
            mCamera?.setPreviewCallback(null)
        }
        if (mHolder != null) {
            mHolder?.removeCallback(this)
        }
        mDraw = null
        mHolder = null
    }

    //SurfaceView callback
    override fun surfaceDestroyed(holder: SurfaceHolder) {
        // Surface will be destroyed when we return, so stop the preview.
        // Because the CameraDevice object is not a shared resource, it's very
        // important to release it when the activity is paused.
        mFinished = true
        if (mCamera != null) {
            mCamera?.setPreviewCallback(null)
            mCamera?.stopPreview()
            mCamera?.release()
            mCamera = null
        }

        mIsCameraOpen = false
        mIsPreviewStarted = false
    }

    //SurfaceView callback, configuring camera
    override fun surfaceChanged(holder: SurfaceHolder, format: Int, w: Int, h: Int) {
        if (mCamera == null) return

        // Now that the size is known, set up the camera parameters and begin
        // the preview.
        var parameters: Camera.Parameters? = mCamera?.parameters

        //Keep uncommented to work correctly on phones:
        //This is an undocumented although widely known feature
        /**/
        if (this.resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE) {
            parameters?.set("orientation", "portrait")
            mCamera?.setDisplayOrientation(90) // For Android 2.2 and above
            mDraw?.rotated = true
        } else {
            parameters?.set("orientation", "landscape")
            mCamera?.setDisplayOrientation(0) // default 0 // For Android 2.2 and above
        }
        /**/

        // choose preview size closer to 640x480 for optimal performance
        val supportedSizes = parameters?.supportedPreviewSizes
        var width = 0
        var height = 0

        supportedSizes?.let {
            for (s in it) {
                if ((width - 640) * (width - 640) + (height - 480) * (height - 480) > (s.width - 640) * (s.width - 640) + (s.height - 480) * (s.height - 480)) {
                    width = s.width
                    height = s.height
                }
            }
        }

        //try to set preferred parameters
        try {
            if (width * height > 0) {
                parameters?.setPreviewSize(width, height)
            }
            //parameters.setPreviewFrameRate(10);
            parameters?.sceneMode = Camera.Parameters.SCENE_MODE_LANDSCAPE
            parameters?.focusMode = Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO
            mCamera?.parameters = parameters
        } catch (ex: Exception) {
        }

        if (!mIsPreviewStarted) {
            mCamera?.startPreview()
            mIsPreviewStarted = true
        }

        //parameters = mCamera!!.parameters
        //val previewSize = parameters.previewSize
        //makeResizeForCameraAspect(1.0f / (1.0f * previewSize.width / previewSize.height))
    }

    private fun makeResizeForCameraAspect(cameraAspectRatio: Float) {
        val layoutParams = this.layoutParams
        val matchParentWidth = this.width
        val newHeight = (matchParentWidth / cameraAspectRatio).toInt()
        if (newHeight != layoutParams.height) {
            layoutParams.height = newHeight
            layoutParams.width = matchParentWidth
            this.layoutParams = layoutParams
            this.invalidate()
        }
    }
} // end of Preview class