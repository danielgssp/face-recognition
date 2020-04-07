package com.facerecognition.view

import android.app.ActionBar
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.facerecognition.R
import com.facerecognition.facerecognition.FaceRecognitionUtil
import com.facerecognition.facerecognition.Preview
import com.facerecognition.facerecognition.ProcessImageAndDrawResults
import com.luxand.FSDK
import kotlinx.android.synthetic.main.layout_toolbar_solinftec.view.*
import kotlinx.android.synthetic.main.register_face.*
import kotlinx.android.synthetic.main.register_face.toolbar

class RegisterFace: AppCompatActivity() {
    private var mDraw: ProcessImageAndDrawResults? = null
    private var mPreview: Preview? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.register_face)

        initToolbar()
        initFaceRecognition()
    }

    private fun initToolbar(){
        toolbar.tvBarTitle.text = "Register Face"
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
            mDraw = ProcessImageAndDrawResults(this)
            mPreview = Preview(this, mDraw)

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

            register_frame.addView(
                mPreview,
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )

            addContentView(
                mDraw,
                ViewGroup.LayoutParams(
                    ActionBar.LayoutParams.MATCH_PARENT,
                    ActionBar.LayoutParams.MATCH_PARENT
                )
            )
        }
    }

    public override fun onResume() {
        super.onResume()
        if(mDraw != null) {
            resumeProcessingFrames()
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