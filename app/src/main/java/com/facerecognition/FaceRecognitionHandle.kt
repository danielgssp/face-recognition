package com.facerecognition

import android.widget.FrameLayout
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentTransaction
import com.facerecognition.view.fragment.FaceRecognitionFragment
import java.lang.Exception

class FaceRecognitionHandle() {
    private var activity: FragmentActivity? = null
    private var faceRecognitionFragment: FaceRecognitionFragment? = null

    constructor(fragmentActivity: FragmentActivity) : this() {
        this.activity = fragmentActivity

        buildLayoutFragment()

        val ft: FragmentTransaction? = activity?.supportFragmentManager?.beginTransaction()
        faceRecognitionFragment = FaceRecognitionFragment()

        ft?.replace(R.id.face_recognition_fragment, faceRecognitionFragment!!)
        ft?.commit()
    }

    fun closeDetectionFragment() {
        activity?.supportFragmentManager
            ?.beginTransaction()
            ?.remove(faceRecognitionFragment!!)
            ?.commit()
    }

    private fun buildLayoutFragment() {
        try {
            val frameLayout = FrameLayout(activity?.applicationContext!!)
            val layoutParams: FrameLayout.LayoutParams = FrameLayout.LayoutParams(1, 1)
            frameLayout.id = R.id.face_recognition_fragment
            frameLayout.layoutParams = layoutParams

            activity?.setContentView(frameLayout)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}