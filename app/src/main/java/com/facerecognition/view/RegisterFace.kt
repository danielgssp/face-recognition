package com.facerecognition.view

import android.app.ActionBar
import android.graphics.*
import android.media.ImageReader
import android.os.Bundle
import android.os.Handler
import android.os.SystemClock
import android.util.Log
import android.util.Size
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Toast
import com.facerecognition.R
import com.facerecognition.facerecognition.FaceRecognitionUtil
import com.facerecognition.facerecognition.FaceRecognitionUtil.Companion.requestAllPermissions
import com.facerecognition.facerecognition.Preview
import com.facerecognition.facerecognition.ProcessImageAndDrawResults
import com.luxand.FSDK
import com.tensorflow.CameraActivity
import com.tensorflow.customview.OverlayView
import com.tensorflow.env.BorderedText
import com.tensorflow.env.ImageUtils
import com.tensorflow.tflite.Classifier
import com.tensorflow.tflite.TFLiteObjectDetectionAPIModel
import com.tensorflow.tracking.MultiBoxTracker
import kotlinx.android.synthetic.main.layout_toolbar_solinftec.toolbar
import kotlinx.android.synthetic.main.layout_toolbar_solinftec.view.*
import kotlinx.android.synthetic.main.register_face.*
import java.io.IOException
import java.util.*

class RegisterFace : CameraActivity(), ImageReader.OnImageAvailableListener {
    private var mPreview: Preview? = null

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        initToolbar()
        requestAllPermissions(this)
        initFaceRecognition()
    }

    private fun initToolbar(){
        toolbar.tvBarTitle.text = "Cadastrar Face"
        toolbar.btnMenu.visibility = View.GONE
        toolbar.imgBtnBack.setOnClickListener {
            finish()
        }
    }

    public override fun onPause() {
        super.onPause()
        if(mDraw != null) {
            pauseProcessingFrames()
            FSDK.SaveTrackerMemoryToFile(mDraw!!.mTracker!!,
                FaceRecognitionUtil.getTemplatePath(application)
            )
        }
    }

    public override fun onResume() {
        super.onResume()
        if(mDraw != null) {
            resumeProcessingFrames()
        }
    }

    private fun initFaceRecognition() {
        var res = FSDK.ActivateLibrary(FSDK.LICENSE_KEY)

        if (res != FSDK.FSDKE_OK) {
            FaceRecognitionUtil.showErrorAndClose("FaceSDK activation failed", res, this)
        } else {
            FaceRecognitionUtil.initializeFSDK()

            this.window.setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
            )

            // Camera layer and drawing layer
//            mDraw = ProcessImageAndDrawResults(this)
//            mPreview = Preview(this, mDraw)
            Log.i("Register", mDraw?.toString())
            mDraw!!.mTracker = FSDK.HTracker()

            if (FSDK.FSDKE_OK != FSDK.LoadTrackerMemoryFromFile(mDraw!!.mTracker!!,
                    FaceRecognitionUtil.getTemplatePath(application)
                )) {
                res = FSDK.CreateTracker(mDraw!!.mTracker!!)
                if (FSDK.FSDKE_OK != res) {
                    FaceRecognitionUtil.showErrorAndClose("Error creating tracker", res, this)
                }
            }

            FSDK.SetTrackerMultipleParameters(
                mDraw!!.mTracker!!,
                FaceRecognitionUtil.parameters,
                FaceRecognitionUtil.errpos
            )

//            frame_camera.addView(
//                mPreview,
//                ViewGroup.LayoutParams.MATCH_PARENT,
//                ViewGroup.LayoutParams.MATCH_PARENT
//            )

            addContentView(
                mDraw,
                ViewGroup.LayoutParams(
                    ActionBar.LayoutParams.MATCH_PARENT,
                    ActionBar.LayoutParams.MATCH_PARENT
                )
            )
        }
    }

    private fun pauseProcessingFrames() {
        mDraw!!.mStopping = 1

        // It is essential to limit wait time, because mStopped will not be set to 0, if no frames are feeded to mDraw
        for (i in 0..99) {
            if (mDraw!!.mStopped != 0) break
            try {
                Thread.sleep(10)
            } catch (ex: Exception) {
            }
        }
    }

    private fun resumeProcessingFrames() {
        mDraw!!.mStopped = 0
        mDraw!!.mStopping = 0
    }

    // Configuration values for the prepackaged SSD model.
    private val TF_OD_API_INPUT_SIZE = 300
    private val TF_OD_API_IS_QUANTIZED = true
    private val TF_OD_API_MODEL_FILE = "detect.tflite"
    private val TF_OD_API_LABELS_FILE = "file:///android_asset/labelmap.txt"
    private val MODE = DetectorMode.TF_OD_API

    // Minimum detection confidence to track a detection.
    private val MINIMUM_CONFIDENCE_TF_OD_API = 0.5f
    private val MAINTAIN_ASPECT = false
    private val DESIRED_PREVIEW_SIZE = Size(640, 480)
    private val SAVE_PREVIEW_BITMAP = false
    private val TEXT_SIZE_DIP = 10f
    var trackingOverlay: OverlayView? = null
    private var sensorOrientation: Int? = null

    private var detector: Classifier? = null

    private var width = 0
    private var height = 0

    private var lastProcessingTimeMs: Long = 0
    private var rgbFrameBitmap: Bitmap? = null
    private var croppedBitmap: Bitmap? = null
    private var cropCopyBitmap: Bitmap? = null

    private var computingDetection = false

    private var timestamp: Long = 0

    private var frameToCropTransform: Matrix? = null
    private var cropToFrameTransform: Matrix? = null

    private var tracker: MultiBoxTracker? = null

    private var borderedText: BorderedText? = null

    override fun onPreviewSizeChosen(size: Size, rotation: Int)
    {
        val textSizePx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, TEXT_SIZE_DIP, resources.displayMetrics
        )
        borderedText = BorderedText(textSizePx)
        borderedText!!.setTypeface(Typeface.MONOSPACE)
        tracker = MultiBoxTracker(this)
        var cropSize = TF_OD_API_INPUT_SIZE
        try
        {
            detector = TFLiteObjectDetectionAPIModel.create(
                assets, TF_OD_API_MODEL_FILE, TF_OD_API_LABELS_FILE, TF_OD_API_INPUT_SIZE, TF_OD_API_IS_QUANTIZED
            )
            cropSize = TF_OD_API_INPUT_SIZE
        }
        catch (e: IOException)
        {
            e.printStackTrace()
//            MainActivity.LOGGER.e(e, "Exception initializing classifier!")
            val toast = Toast.makeText(
                applicationContext, "Classifier could not be initialized", Toast.LENGTH_SHORT
            )
            toast.show()
            finish()
        }
        width = size.width
        height = size.height
        previewWidth = width
        previewHeight = height
        sensorOrientation = rotation - getScreenOrientation()
//        MainActivity.LOGGER.i("Camera orientation relative to screen canvas: %d", sensorOrientation)
//        MainActivity.LOGGER.i("Initializing at size %dx%d", width, height)
        rgbFrameBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        croppedBitmap = Bitmap.createBitmap(cropSize, cropSize, Bitmap.Config.ARGB_8888)
        frameToCropTransform = ImageUtils.getTransformationMatrix(
            width, height, cropSize, cropSize, sensorOrientation!!, MAINTAIN_ASPECT
        )
        cropToFrameTransform = Matrix()
        frameToCropTransform!!.invert(cropToFrameTransform)
        trackingOverlay = findViewById<View>(R.id.tracking_overlay) as OverlayView
        trackingOverlay!!.addCallback {
            tracker!!.draw(it)
            if (false)
            {
                tracker!!.drawDebug(it)
            }
        }
        tracker!!.setFrameConfiguration(width, height, sensorOrientation!!)
    }

    override fun processImage(yuvData: ByteArray)
    {
        if (mDraw?.mYUVData == null) {
            // Initialize the draw-on-top companion
            mDraw?.mImageWidth =  previewWidth
            mDraw?.mImageHeight =  previewHeight
            mDraw?.mRGBData = ByteArray(3 * (mDraw?.mImageWidth ?: 0) * (mDraw?.mImageHeight ?: 0))
            mDraw?.mYUVData = yuvData
        }

        // Pass YUV data to draw-on-top companion
        System.arraycopy(yuvData, 0, mDraw?.mYUVData, 0, yuvData.size)
        mDraw?.invalidate()

        ++timestamp
        val currTimestamp = timestamp
        trackingOverlay!!.postInvalidate()

        // No mutex needed as this method is not reentrant.
        if (computingDetection)
        {
            readyForNextImage()
            return
        }

        computingDetection = true
//        MainActivity.LOGGER.i("Preparing image $currTimestamp for detection in bg thread.")
        rgbFrameBitmap!!.setPixels(getRgbBytes(), 0, width, 0, 0, width, height)
        readyForNextImage()
        val canvas = Canvas(croppedBitmap!!)
        canvas.drawBitmap(rgbFrameBitmap!!, frameToCropTransform!!, null)
        // For examining the actual TF input.
        if (SAVE_PREVIEW_BITMAP)
        {
            ImageUtils.saveBitmap(croppedBitmap)
        }

        runInBackground(Runnable {
//            MainActivity.LOGGER.i("Running detection on image $currTimestamp")
            val startTime = SystemClock.uptimeMillis()
            val results = detector!!.recognizeImage(croppedBitmap)
            lastProcessingTimeMs = SystemClock.uptimeMillis() - startTime
            cropCopyBitmap = Bitmap.createBitmap(croppedBitmap!!)
            val canvas = Canvas(cropCopyBitmap!!)
            val paint = Paint()
            paint.color = Color.RED
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2.0f
            var minimumConfidence = MINIMUM_CONFIDENCE_TF_OD_API
            minimumConfidence = when (MODE)
            {
                DetectorMode.TF_OD_API -> MINIMUM_CONFIDENCE_TF_OD_API
            }
            val mappedRecognitions: MutableList<Classifier.Recognition> = LinkedList()
            for (result in results)
            {
                val location = result.location
                if (location != null && result.confidence >= minimumConfidence)
                {
                    canvas.drawRect(location, paint)
                    cropToFrameTransform!!.mapRect(location)
                    mappedRecognitions.add(result)
                }
            }
            tracker!!.trackResults(mappedRecognitions, currTimestamp)
            trackingOverlay!!.postInvalidate()
            computingDetection = false
        })
    }

    override fun getLayoutId(): Int
    {
        return R.layout.camera_fragment_tracking
    }

    override fun getDesiredPreviewFrameSize(): Size
    {
        return DESIRED_PREVIEW_SIZE
    }

    private enum class DetectorMode
    {
        TF_OD_API
    }
}
