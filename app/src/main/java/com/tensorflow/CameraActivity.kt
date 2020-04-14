package com.tensorflow

import android.Manifest
import android.app.Fragment
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.hardware.Camera
import android.hardware.Camera.PreviewCallback
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.Image.Plane
import android.media.ImageReader
import android.media.ImageReader.OnImageAvailableListener
import android.os.*
import android.util.Log
import android.util.Size
import android.view.Surface
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.facerecognition.R
import com.facerecognition.facerecognition.ProcessImageAndDrawResults
import com.tensorflow.CameraConnectionFragment.ConnectionCallback
import com.tensorflow.env.ImageUtils


abstract class CameraActivity : AppCompatActivity(), OnImageAvailableListener, PreviewCallback
{
    private val PERMISSIONS_REQUEST = 1
    private val PERMISSION_CAMERA = Manifest.permission.CAMERA

    protected var previewWidth = 0
    protected var previewHeight = 0
    protected var mDraw: ProcessImageAndDrawResults? = null

    private var handler: Handler? = null
    private var handlerThread: HandlerThread? = null
    private var postInferenceCallback: Runnable? = null

    private var useCamera2API = false
    private var isProcessingFrame = false

    private var yRowStride = 0
    private var yuvBytes = arrayOfNulls<ByteArray>(3)
    private var rgbBytes: IntArray? = null
    private var imageConverter: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.detection_activity)

        mDraw = ProcessImageAndDrawResults(this)

        if (hasPermission())
        {
            setFragment()
        }
        else
        {
            requestPermission()
        }
    }

    @Synchronized
    override fun onResume()
    {
        super.onResume()

        handlerThread = HandlerThread("inference")
        handlerThread?.apply {
            this.start()
            handler = Handler(this.looper)
        }
    }

    @Synchronized
    override fun onPause()
    {
        super.onPause()

        handlerThread?.apply {
            this.quitSafely()
            try
            {
                this.join()
                handlerThread = null
                handler = null
            }
            catch (e: InterruptedException)
            {
                Log.i("CameraActivity", "Error: ${e.message}")
            }
        }
    }

    @Synchronized
    protected open fun runInBackground(r: Runnable)
    {
        handler?.apply { this.post(r) }
    }

    override fun onPreviewFrame(data: ByteArray, camera: Camera)
    {
        if (isProcessingFrame)
        {
            return
        }

        try
        {
            //Verify orientation
            if (this.resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE) {
                camera.parameters?.set("orientation", "portrait")
                camera.setDisplayOrientation(180)
                mDraw?.rotated = true
            } else {
                camera.parameters?.set("orientation", "landscape")
                camera.setDisplayOrientation(0)
            }

            // Initialize the storage bitmaps once when the resolution is known.
            if (rgbBytes == null)
            {
                val previewSize = camera.parameters.previewSize
                previewHeight = previewSize.height
                previewWidth = previewSize.width
                rgbBytes = IntArray(previewWidth * previewHeight)
                onPreviewSizeChosen(Size(previewSize.width, previewSize.height), 90)
            }
        }
        catch (e: Exception)
        {
            Log.e("CameraActivity", "EXCEPTION", e)
            return
        }

        isProcessingFrame = true
        yuvBytes[0] = data
        yRowStride = previewWidth

        imageConverter = Runnable { ImageUtils.convertYUV420SPToARGB8888(data, previewWidth, previewHeight, rgbBytes) }

        postInferenceCallback = Runnable {
            camera.addCallbackBuffer(data)
            isProcessingFrame = false
        }

        processImage(data)
    }

    override fun onImageAvailable(reader: ImageReader?)
    {
        if (previewWidth == 0 || previewHeight == 0)
        {
            return
        }

        if (rgbBytes == null)
        {
            rgbBytes = IntArray(previewWidth * previewHeight)
        }

        try
        {
            val image = reader!!.acquireLatestImage() ?: return
            if (isProcessingFrame)
            {
                image.close()
                return
            }
            isProcessingFrame = true
            Trace.beginSection("imageAvailable")
            val planes = image.planes
            fillBytes(planes, yuvBytes)

            yRowStride = planes[0].rowStride
            val uvRowStride = planes[1].rowStride
            val uvPixelStride = planes[1].pixelStride
            imageConverter = Runnable {
                ImageUtils.convertYUV420ToARGB8888(
                    yuvBytes[0], yuvBytes[1], yuvBytes[2], previewWidth, previewHeight, yRowStride, uvRowStride, uvPixelStride, rgbBytes
                )
            }


            postInferenceCallback = Runnable {
                image.close()
                isProcessingFrame = false
            }

            val yuvData = ImageUtils.YUV420toNV21(image)
            processImage(yuvData)
        }
        catch (e: java.lang.Exception)
        {
            Log.e("CameraActivity", "Exception", e)
            Trace.endSection()
            return
        }
        Trace.endSection()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray)
    {
        if (requestCode == PERMISSIONS_REQUEST)
        {
            if (allPermissionsGranted(grantResults))
            {
                setFragment()
            }
            else
            {
                requestPermission()
            }
        }
    }

    private fun allPermissionsGranted(grantResults: IntArray): Boolean
    {
        for (result in grantResults)
        {
            if (result != PackageManager.PERMISSION_GRANTED)
            {
                return false
            }
        }
        return true
    }

    private fun hasPermission(): Boolean
    {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
        {
            return checkSelfPermission(PERMISSION_CAMERA) == PackageManager.PERMISSION_GRANTED
        }

        return true
    }

    private fun requestPermission()
    {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
        {
            if (shouldShowRequestPermissionRationale(PERMISSION_CAMERA))
            {
                val message = "Camera permission is required for this demo"
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            }
            requestPermissions(arrayOf(PERMISSION_CAMERA), PERMISSIONS_REQUEST)
        }
    }

    private fun isHardwareLevelSupported(characteristics: CameraCharacteristics): Boolean
    {
        val requiredLevel = CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL
        val deviceLevel = characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)!!
        if (deviceLevel == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY)
        {
            return requiredLevel == deviceLevel
        }

        return requiredLevel <= deviceLevel
    }

    private fun chooseCamera(): String?
    {
        val manager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try
        {
            for (cameraId in manager.cameraIdList)
            {
                val characteristics = manager.getCameraCharacteristics(cameraId)

                // We don't use a front facing camera in this sample.
                val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT)
                {
                    continue
                }
                val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP) ?: continue

                useCamera2API = facing == CameraCharacteristics.LENS_FACING_EXTERNAL || isHardwareLevelSupported(characteristics)
                Log.i("CameraActivity", "Camera API lv2?: $useCamera2API")
                return cameraId
            }
        }
        catch (e: CameraAccessException)
        {
            Log.e("CameraActivity", "Not allowed to access camera")
        }
        return null
    }

    protected open fun setFragment()
    {
        val cameraId = chooseCamera()
        val fragment: Fragment
        if (useCamera2API)
        {
            val camera2Fragment: CameraConnectionFragment = CameraConnectionFragment.newInstance(ConnectionCallback { size, rotation ->
                previewHeight = size.height
                previewWidth = size.width
                onPreviewSizeChosen(size, rotation)
            }, this, getLayoutId(), getDesiredPreviewFrameSize())
            camera2Fragment.setCamera(cameraId)
            fragment = camera2Fragment
        }
        else
        {
            fragment = LegacyCameraConnectionFragment(this, getLayoutId(), getDesiredPreviewFrameSize())
        }
        fragmentManager.beginTransaction().replace(R.id.container, fragment).commit()
    }

    protected open fun getRgbBytes(): IntArray?
    {
        imageConverter?.apply { this.run() }
        return rgbBytes
    }

    protected open fun fillBytes(planes: Array<Plane>, yuvBytes: Array<ByteArray?>)
    {
        // Because of the variable row stride it's not possible to know in
        // advance the actual necessary dimensions of the yuv planes.
        for (i in planes.indices)
        {
            val buffer = planes[i].buffer
            if (yuvBytes[i] == null) {
                Log.d("CameraActivity", "Initializing buffer $i at size ${buffer.capacity()}")
                yuvBytes[i] = ByteArray(buffer.capacity())
            }

            buffer.get(yuvBytes[i]!!)
        }
    }

    protected open fun readyForNextImage()
    {
        postInferenceCallback?.apply { this.run() }
    }

    protected open fun getScreenOrientation(): Int
    {
        return when (windowManager.defaultDisplay.rotation)
        {
            Surface.ROTATION_270 -> 270
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_90 -> 90
            else -> 0
        }
    }

    protected abstract fun processImage(yuvBytes: ByteArray)

    protected abstract fun onPreviewSizeChosen(size: Size, rotation: Int)

    protected abstract fun getLayoutId(): Int

    protected abstract fun getDesiredPreviewFrameSize(): Size
}