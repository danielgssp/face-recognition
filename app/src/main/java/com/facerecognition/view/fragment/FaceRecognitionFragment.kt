package com.facerecognition.view.fragment

import android.app.ActionBar
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.facerecognition.R
import com.facerecognition.util.FaceRecognitionUtil
import com.facerecognition.facerecognition.Preview
import com.facerecognition.facerecognition.ProcessImageAndDrawResults
import com.luxand.FSDK
import kotlinx.android.synthetic.main.face_recognition.*
import kotlinx.android.synthetic.main.layout_toolbar_solinftec.view.*

class FaceRecognitionFragment: Fragment() {
    private var mDraw: ProcessImageAndDrawResults? = null
    private var mPreview: Preview? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        initFaceRecognition()
        return inflater.inflate(R.layout.face_recognition, container, false)
    }

    override fun onPause() {
        super.onPause()
        if(mDraw != null) {
            pauseProcessingFrames()
            FSDK.SaveTrackerMemoryToFile(mDraw!!.mTracker!!,
                FaceRecognitionUtil.getTemplatePath(activity?.application!!)
            )
        }
    }

    override fun onResume() {
        super.onResume()
        if(mDraw != null) {
            resumeProcessingFrames()
        }
    }

    private fun initFaceRecognition() {
        var res = FSDK.ActivateLibrary(FSDK.LICENSE_KEY)

        if (res != FSDK.FSDKE_OK) {
            FaceRecognitionUtil.showErrorAndClose("FaceSDK activation failed", res, activity!!)
        } else {
            FaceRecognitionUtil.initializeFSDK()

            // Camera layer and drawing layer
            mDraw = ProcessImageAndDrawResults(activity!!)
            mPreview = Preview(activity!!, mDraw)

            mDraw!!.mTracker = FSDK.HTracker()

            if (FSDK.FSDKE_OK != FSDK.LoadTrackerMemoryFromFile(mDraw!!.mTracker!!,
                    FaceRecognitionUtil.getTemplatePath(activity?.application!!)
                )) {
                res = FSDK.CreateTracker(mDraw!!.mTracker!!)
                if (FSDK.FSDKE_OK != res) {
                    FaceRecognitionUtil.showErrorAndClose("Error creating tracker", res, activity!!)
                }
            }

            FSDK.SetTrackerMultipleParameters(
                mDraw!!.mTracker!!,
                FaceRecognitionUtil.parameters,
                FaceRecognitionUtil.errpos
            )

            recon_frame.addView(
                mPreview,
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )

            activity?.addContentView(
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
}