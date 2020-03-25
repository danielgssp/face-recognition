package com.facerecognition.facerecognition

import android.app.AlertDialog
import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Environment
import android.util.Log
import android.view.View
import android.widget.EditText
import com.luxand.FSDK
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock

// Draw graphics on top of the video
class ProcessImageAndDrawResults(var mContext: Context) : View(mContext) {
    var mTracker: FSDK.HTracker? = null

    constructor(context: Context, action: Int) : this(context) {
        this.action = action
    }

    private val MAX_FACES = 1
    private val mFacePositions = arrayOfNulls<FaceRectangle>(MAX_FACES)
    private val mIDs = LongArray(MAX_FACES)
    private val faceLock: Lock = ReentrantLock()
    private var action = 0

    var mTouchedIndex: Int = 0
    var mTouchedID: Long = 0
    var mStopping: Int = 0
    var mStopped: Int = 0
    var mPaintGreen: Paint
    var mPaintBlue: Paint
    var mPaintBlueTransparent: Paint
    var mYUVData: ByteArray? = null
    var mRGBData: ByteArray? = null
    var mImageWidth: Int = 0
    var mImageHeight: Int = 0
    var rotated: Boolean = false
    private var first_frame_saved: Boolean = false

    private fun getFaceFrame(Features: FSDK.FSDK_Features?, fr: FaceRectangle?): Int {
        if (Features == null || fr == null)
            return FSDK.FSDKE_INVALID_ARGUMENT

        val u1 = Features.features[0]?.x ?: 0
        val v1 = Features.features[0]?.y ?: 0
        val u2 = Features.features[1]?.x ?: 0
        val v2 = Features.features[1]?.y ?: 0
        val xc = (u1 + u2) / 2
        val yc = (v1 + v2) / 2
        val w = Math.pow(((u2 - u1) * (u2 - u1) + (v2 - v1) * (v2 - v1)).toDouble(), 0.5).toInt()

//        val detectFace = FSDK.SetFaceDetectionParameters(false, false, 384)
//        Log.d("detectface", "$detectFace")

        fr.x1 = (xc - w.toDouble() * 1.6 * 0.9).toInt()
        fr.y1 = (yc - w.toDouble() * 1.1 * 0.9).toInt()
        fr.x2 = (xc + w.toDouble() * 1.6 * 0.9).toInt()
        fr.y2 = (yc + w.toDouble() * 2.1 * 0.9).toInt()

        if (fr.x2 - fr.x1 > fr.y2 - fr.y1) {
            fr.x2 = fr.x1 + fr.y2 - fr.y1
        } else {
            fr.y2 = fr.y1 + fr.x2 - fr.x1
        }

        return 0
    }


    init {

        mTouchedIndex = -1

        mStopping = 0
        mStopped = 0
        rotated = false
        mPaintGreen = Paint()
        mPaintGreen.style = Paint.Style.FILL
        mPaintGreen.color = Color.GREEN
        mPaintGreen.textSize = 25F
        mPaintGreen.textAlign = Paint.Align.CENTER
        mPaintBlue = Paint()
        mPaintBlue.style = Paint.Style.FILL
        mPaintBlue.color = Color.BLUE
        mPaintBlue.textSize = 25F
        mPaintBlue.textAlign = Paint.Align.CENTER

        mPaintBlueTransparent = Paint()
        this.mPaintBlueTransparent.style = Paint.Style.STROKE
        mPaintBlueTransparent.strokeWidth = 2F
        mPaintBlueTransparent.color = Color.BLUE
        mPaintBlueTransparent.textSize = 25F

        //mBitmap = null;
        mYUVData = null
        mRGBData = null

        first_frame_saved = false
    }

    override fun onDraw(canvas: Canvas) {
        if (mStopping == 1) {
            mStopped = 1
            super.onDraw(canvas)
            return
        }

        if (mYUVData == null || mTouchedIndex != -1) {
            super.onDraw(canvas)
            return  //nothing to process or name is being entered now
        }

        val canvasWidth = width
        //int canvasHeight = canvas.getHeight();

        // Convert from YUV to RGB
        decodeYUV420SP(
            mRGBData,
            mYUVData!!,
            mImageWidth,
            mImageHeight
        )

        // Load image to FaceSDK
        val Image = FSDK.HImage()
        val imagemode = FSDK.FSDK_IMAGEMODE()
        imagemode.mode = FSDK.FSDK_IMAGEMODE.FSDK_IMAGE_GRAYSCALE_8BIT
        FSDK.LoadImageFromBuffer(
            Image,
            mRGBData!!,
            mImageWidth,
            mImageHeight,
            mImageWidth * 3,
            imagemode
        )

        var useVerticalMirroring = false

        if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE)
            useVerticalMirroring = true

        FSDK.MirrorImage(Image, useVerticalMirroring)
        val RotatedImage = FSDK.HImage()
        FSDK.CreateEmptyImage(RotatedImage)

        //it is necessary to work with local variables (onDraw called not the time when mImageWidth,... being reassigned, so swapping mImageWidth and mImageHeight may be not safe)
        var ImageWidth = mImageWidth
        //int ImageHeight = mImageHeight;
        if (rotated) {
            ImageWidth = mImageHeight
            //ImageHeight = mImageWidth;
            FSDK.RotateImage90(Image, -1, RotatedImage)
        } else {
            FSDK.CopyImage(Image, RotatedImage)
        }

        val galleryPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).absolutePath
        FSDK.SetJpegCompressionQuality(100)
        FSDK.SaveImageToFile(Image, "$galleryPath/test}.jpg")

        FSDK.FreeImage(Image)
        // Save first frame to gallery to debug (e.g. rotation angle)

//		if (!first_frame_saved) {
//            Log.d("teste", "saved")
//			first_frame_saved = true
//			val galleryPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).absolutePath
//			FSDK.SaveImageToFile(RotatedImage, "$galleryPath/first_frame.jpg") //frame is rotated!
//		}

        val IDs = LongArray(MAX_FACES)
        val face_count = LongArray(1)

        FSDK.FeedFrame(mTracker!!, 0, RotatedImage, face_count, IDs)
        FSDK.FreeImage(RotatedImage)

        faceLock.lock()

        for (i in 0 until MAX_FACES) {
            mFacePositions[i] = FaceRectangle()
            mFacePositions[i]?.x1 = 0
            mFacePositions[i]?.y1 = 0
            mFacePositions[i]?.x2 = 0
            mFacePositions[i]?.y2 = 0
            mIDs[i] = IDs[i]
        }

        val ratio = canvasWidth * 1.0f / ImageWidth
        for (i in 0 until face_count[0].toInt()) {
            val eyes = FSDK.FSDK_Features()
            FSDK.GetTrackerEyes(mTracker!!, 0, mIDs[i], eyes)

            getFaceFrame(eyes, mFacePositions[i])

            if (mFacePositions[i] != null) {
                mFacePositions[i]!!.x1 *= ratio.toInt()
                mFacePositions[i]!!.y1 *= ratio.toInt()
                mFacePositions[i]!!.x2 *= ratio.toInt()
                mFacePositions[i]!!.y2 *= ratio.toInt()
            }
        }

        faceLock.unlock()

        val shift = (22 * FaceRecognitionUtil.sDensity).toInt()

        // Mark and name faces
        for (i in 0 until face_count[0]) {

            val index = i.toInt()

            if (mFacePositions[i.toInt()] != null) {
                canvas.drawRect(
                    mFacePositions[index]!!.x1.toFloat(),
                    mFacePositions[index]!!.y1.toFloat(),
                    mFacePositions[index]!!.x2.toFloat(),
                    mFacePositions[index]!!.y2.toFloat(),
                    mPaintBlueTransparent
                )
            }

            var named = false
            if (IDs[index].toInt() != -1) {
                val names = arrayOf("")
                FSDK.GetAllNames(mTracker!!, IDs[index], names, 1024)

                if (names[0] != null && names[0].isNotEmpty()) {
                    Log.d("teste", names[0])

                    canvas.drawText(
                        names[0],
                        ((mFacePositions[index]?.x1 ?: 0 + (mFacePositions[index]?.x2 ?: 0)) / 2).toFloat(),
                        (mFacePositions[index]?.y2 ?: 0 + shift).toFloat(),
                        mPaintBlue
                    )
                    named = true
                }

            }

            if (!named) {
                registerFace()
            }
        }

        super.onDraw(canvas)
    } // end onDraw method


    fun registerFace() {
        faceLock.lock()
        val rects = arrayOfNulls<FaceRectangle>(MAX_FACES)
        val IDs = LongArray(MAX_FACES)
        for (i in 0 until MAX_FACES) {
            rects[i] = FaceRectangle()
            rects[i]?.x1 = mFacePositions[i]?.x1 ?: 0
            rects[i]?.y1 = mFacePositions[i]?.y1 ?: 0
            rects[i]?.x2 = mFacePositions[i]?.x2 ?: 0
            rects[i]?.y2 = mFacePositions[i]?.y2 ?: 0
            IDs[i] = mIDs[i]
        }
        faceLock.unlock()

        for (i in 0 until MAX_FACES) {
            if (rects[i] != null) {
                mTouchedID = IDs[i]

                mTouchedIndex = i

                // requesting name on tapping the face
                val input = EditText(mContext)
                val builder = AlertDialog.Builder(mContext)
                builder.setMessage("Seu nome")
                    .setView(input)
                    .setPositiveButton(
                        "Ok"
                    ) { _, _ ->
                        FSDK.LockID(mTracker!!, mTouchedID)
                        FSDK.SetName(mTracker!!, mTouchedID, input.text.toString())
                        FSDK.UnlockID(mTracker!!, mTouchedID)
                        mTouchedIndex = -1
                    }
                    .setNegativeButton("Cancel"
                    ) { _, _ ->
                        mTouchedIndex = -1
                    }
                    .setCancelable(false) // cancel with button only
                    .show()

                break
            }
        }
    }

    companion object {

        fun decodeYUV420SP(rgb: ByteArray?, yuv420sp: ByteArray, width: Int, height: Int) {
            val frameSize = width * height
            var yp = 0
            for (j in 0 until height) {
                var uvp = frameSize + (j shr 1) * width
                var u = 0
                var v = 0
                for (i in 0 until width) {
                    var y = (0xff and yuv420sp[yp].toInt()) - 16
                    if (y < 0) y = 0
                    if (i and 1 == 0) {
                        v = (0xff and yuv420sp[uvp++].toInt()) - 128
                        u = (0xff and yuv420sp[uvp++].toInt()) - 128
                    }
                    val y1192 = 1192 * y
                    var r = y1192 + 1634 * v
                    var g = y1192 - 833 * v - 400 * u
                    var b = y1192 + 2066 * u
                    if (r < 0) r = 0 else if (r > 262143) r = 262143
                    if (g < 0) g = 0 else if (g > 262143) g = 262143
                    if (b < 0) b = 0 else if (b > 262143) b = 262143

                    rgb?.set(3 * yp, (r shr 10 and 0xff).toByte())
                    rgb?.set(3 * yp + 1, (g shr 10 and 0xff).toByte())
                    rgb?.set(3 * yp + 2, (b shr 10 and 0xff).toByte())
                    ++yp
                }
            }
        }
    }
}