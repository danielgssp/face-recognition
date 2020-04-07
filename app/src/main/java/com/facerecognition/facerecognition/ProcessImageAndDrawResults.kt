package com.facerecognition.facerecognition

import android.app.AlertDialog
import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.util.Log
import android.view.View
import android.widget.EditText
import com.facerecognition.interfaces.RegisterFaceInterface
import com.luxand.FSDK
import com.luxand.FSDK.CopyImage
import com.luxand.FSDK.CreateEmptyImage
import com.luxand.FSDK.FSDK_Features
import com.luxand.FSDK.FSDK_IMAGEMODE
import com.luxand.FSDK.FeedFrame
import com.luxand.FSDK.FreeImage
import com.luxand.FSDK.GetTrackerEyes
import com.luxand.FSDK.HImage
import com.luxand.FSDK.HTracker
import com.luxand.FSDK.LoadImageFromBuffer
import com.luxand.FSDK.MirrorImage
import com.luxand.FSDK.RotateImage90
import kotlinx.android.synthetic.main.register_face.view.*
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock

class ProcessImageAndDrawResults(context: Context) : View(context) {
    var mTracker: HTracker? = null
    val MAX_FACES = 5
    var mTouchedID: Long = 0
    var mStopping: Int
    var mStopped: Int
    var mYUVData: ByteArray?
    var mRGBData: ByteArray?
    var mImageWidth = 0
    var mImageHeight = 0
    var first_frame_saved: Boolean
    var rotated: Boolean

    private var registerFaceInterface: RegisterFaceInterface? = null
    private var mTouchedIndex: Int
    private var mContext: Context
    private val mFacePositions = arrayOfNulls<FaceRectangle>(MAX_FACES)
    private val mIDs = LongArray(MAX_FACES)
    private val faceLock: Lock = ReentrantLock()

    constructor(context: Context,
                registerFaceInterface: RegisterFaceInterface
    ) : this(context) {
        this.registerFaceInterface = registerFaceInterface
    }

    init {
        mTouchedIndex = -1
        mStopping = 0
        mStopped = 0
        rotated = false
        mContext = context
        mYUVData = null
        mRGBData = null
        first_frame_saved = false
    }

    private fun getFaceFrame(Features: FSDK_Features?, fr: FaceRectangle?): Int {
        if (Features == null || fr == null)
            return FSDK.FSDKE_INVALID_ARGUMENT

        val u1 = Features.features[0]?.x ?: 0
        val v1 = Features.features[0]?.y ?: 0
        val u2 = Features.features[1]?.x ?: 0
        val v2 = Features.features[1]?.y ?: 0
        val xc = (u1 + u2) / 2
        val yc = (v1 + v2) / 2
        val w = Math.pow(((u2 - u1) * (u2 - u1) + (v2 - v1) * (v2 - v1)).toDouble(), 0.5).toInt()

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
        val canvasWidth = canvas.width
        //int canvasHeight = canvas.getHeight();

        // Convert from YUV to RGB
        decodeYUV420SP(
            mRGBData,
            mYUVData!!,
            mImageWidth,
            mImageHeight
        )


        // Load image to FaceSDK
        val Image = HImage()
        val imagemode = FSDK_IMAGEMODE()
        imagemode.mode = FSDK_IMAGEMODE.FSDK_IMAGE_COLOR_24BIT
        LoadImageFromBuffer(
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

        MirrorImage(Image, useVerticalMirroring)
        val RotatedImage = HImage()

        CreateEmptyImage(RotatedImage)

        var ImageWidth = mImageWidth

        if (rotated) {
            ImageWidth = mImageHeight
            RotateImage90(Image, -1, RotatedImage)
        } else {
            CopyImage(Image, RotatedImage)
        }
        FreeImage(Image)

        val IDs = LongArray(MAX_FACES)
        val face_count = LongArray(1)
        FeedFrame(mTracker!!, 0, RotatedImage, face_count, IDs)
        FreeImage(RotatedImage)

        faceLock.lock()
        for (i in 0 until MAX_FACES) {
            mFacePositions[i] = FaceRectangle()
            mFacePositions[i]!!.x1 = 0
            mFacePositions[i]!!.y1 = 0
            mFacePositions[i]!!.x2 = 0
            mFacePositions[i]!!.y2 = 0
            mIDs[i] = IDs[i]
        }
        val ratio = canvasWidth * 1.0f / ImageWidth
        for (i in 0 until face_count[0].toInt()) {
            val Eyes = FSDK_Features()
            GetTrackerEyes(mTracker!!, 0, mIDs[i], Eyes)
            getFaceFrame(Eyes, mFacePositions[i])
            mFacePositions[i]!!.x1 *= ratio.toInt()
            mFacePositions[i]!!.y1 *= ratio.toInt()
            mFacePositions[i]!!.x2 *= ratio.toInt()
            mFacePositions[i]!!.y2 *= ratio.toInt()
        }
        faceLock.unlock()

        // Mark and name faces
        for (i in 0 until face_count[0]) {
            val index = i.toInt()

            var named = false
            if (IDs[index].toInt() != -1) {
                val names = arrayOf("")
                mTracker?.let { FSDK.GetAllNames(it, IDs[index], names, 1024) }

                if (names.count() > 0 && names[0].isNotEmpty()) {
                    Log.d("test", names[0])
                    rootView.tvRegisterMsg.text = "${names[0].capitalize()} are already registered!"
                    named = true
                }
            }
            if (!named) {
                registerFace()
            }
        }

        super.onDraw(canvas)
    }

    private fun registerFace() {
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
                    .setCancelable(false)
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

                    if (rgb != null) {
                        rgb[3 * yp] = (r shr 10 and 0xff).toByte()
                        rgb[3 * yp + 1] = (g shr 10 and 0xff).toByte()
                        rgb[3 * yp + 2] = (b shr 10 and 0xff).toByte()
                    }

                    ++yp
                }
            }
        }
    }
}
